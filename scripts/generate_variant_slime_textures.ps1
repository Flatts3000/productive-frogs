# Generate the 12 per-variant Resource Slime textures by compositing each
# variant's canonical vanilla block texture into the SlimeModel inner-cube
# UV layout on a per-category template.
#
# Output: src/main/resources/assets/productivefrogs/textures/entity/slime/
#         <variant>_resource_slime.png  (12 files, 64x32 each)
#
# Inputs:
# - Existing category templates already in the repo (preserves the outer
#   translucent shell + eyes/mouth region of the existing PNGs)
# - Vanilla minecraft block textures, auto-extracted at runtime from the
#   NeoForge dev artifact found via glob at
#   build/moddev/artifacts/neoforge-*-client-extra-aka-minecraft-resources.jar
#   so the script keeps working across NeoForge version bumps.
#
# UV layout (SlimeModel inner cube, texOffs(0, 16), size 6x6x6, on a 64x32
# texture -- vanilla slime.png shape):
#   top    (6, 16) - (12, 22)    6x6 face
#   bottom (12, 16) - (18, 22)   6x6 face
#   west   (0, 22) - (6, 28)     6x6 face
#   front  (6, 22) - (12, 28)    6x6 face
#   east   (12, 22) - (18, 28)   6x6 face
#   back   (18, 22) - (24, 28)   6x6 face
# Each face gets the same nearest-neighbor 6x6 downsample of the variant's
# 16x16 block texture. Solid-resource blocks (iron, copper, gold, diamond,
# emerald, lapis, coal, redstone) are uniform across the cube, so a single
# downsampled tile reads correctly on all six faces.
#
# Re-run whenever a variant's canonical block texture changes upstream, or
# when adding a new variant -- drop the new entry into the $variants list at
# the bottom and re-run.
#
# Platform: Windows only. Depends on System.Drawing (which on .NET Core /
# PowerShell 7+ requires libgdiplus on Linux/macOS — easier to just run
# this on Windows where the assembly ships in-box). The cross-platform
# temp-path helper is here just to fail with a clear message if anyone
# tries it elsewhere.

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$textureDir = Join-Path $repoRoot "src\main\resources\assets\productivefrogs\textures\entity\slime"
$mcExtract = Join-Path ([System.IO.Path]::GetTempPath()) "mc-extra"
$artifactsDir = Join-Path $repoRoot "build\moddev\artifacts"

# Auto-extract: if the cache directory doesn't already have the minecraft
# block textures, find the NeoForge minecraft-resources jar via glob and
# unpack it. Jar files are zips under the hood, so we use the .NET
# ZipFile API rather than depending on a working `jar.exe` (which would
# require a valid JAVA_HOME — and on this machine the system JAVA_HOME
# is known-stale per docs and memory notes).
if (-not (Test-Path (Join-Path $mcExtract "assets\minecraft\textures\block"))) {
    $jarMatches = @(Get-ChildItem -Path $artifactsDir -Filter "neoforge-*-client-extra-aka-minecraft-resources.jar" -ErrorAction SilentlyContinue)
    if ($jarMatches.Count -eq 0) {
        Write-Error ("No NeoForge minecraft-resources jar found under $artifactsDir.`n" +
            "Run ``./gradlew createMinecraftArtifacts`` (or any task that pulls deps) to populate it, then re-run this script.")
        exit 1
    }
    # Most-recently-modified wins on version bumps.
    $jar = ($jarMatches | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    New-Item -ItemType Directory -Force -Path $mcExtract | Out-Null
    Write-Output "extracting $(Split-Path $jar -Leaf) -> $mcExtract"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jar, $mcExtract)
}

# Inner-cube face anchors: { x, y } of each 6x6 face's top-left corner on
# the 64x32 texture canvas.
$innerFaces = @(
    @{ x =  6; y = 16 },  # top
    @{ x = 12; y = 16 },  # bottom
    @{ x =  0; y = 22 },  # west
    @{ x =  6; y = 22 },  # front
    @{ x = 12; y = 22 },  # east
    @{ x = 18; y = 22 }   # back
)

function Get-Downsampled6x6 {
    param([string]$blockTexturePath)
    $source = [System.Drawing.Image]::FromFile($blockTexturePath)
    try {
        $bmp = New-Object System.Drawing.Bitmap 6, 6
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        try {
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $g.DrawImage($source, 0, 0, 6, 6)
        } finally { $g.Dispose() }
        return $bmp
    } finally { $source.Dispose() }
}

function Build-VariantTexture {
    param(
        [string]$variant,
        [string]$category,
        [string]$blockTextureName
    )

    $templatePath = Join-Path $textureDir "${category}_resource_slime.png"
    $blockPath = Join-Path $mcExtract "assets\minecraft\textures\block\${blockTextureName}.png"
    $outputPath = Join-Path $textureDir "${variant}_resource_slime.png"

    if (-not (Test-Path $templatePath)) {
        Write-Error "Missing template: $templatePath"
        return
    }
    if (-not (Test-Path $blockPath)) {
        Write-Error "Missing block texture: $blockPath"
        return
    }

    $tile = Get-Downsampled6x6 -blockTexturePath $blockPath
    try {
        $template = [System.Drawing.Image]::FromFile($templatePath)
        try {
            $canvas = New-Object System.Drawing.Bitmap $template.Width, $template.Height
            $g = [System.Drawing.Graphics]::FromImage($canvas)
            try {
                $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                # Copy the template (outer cube shell + eyes/mouth) first.
                $g.DrawImage($template, 0, 0, $template.Width, $template.Height)
                # Overwrite the six inner-cube face regions with the
                # downsampled variant block tile.
                foreach ($face in $innerFaces) {
                    $g.DrawImage($tile, [int]$face.x, [int]$face.y, 6, 6)
                }
            } finally { $g.Dispose() }
            $canvas.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            $canvas.Dispose()
            Write-Output "wrote $outputPath"
        } finally { $template.Dispose() }
    } finally { $tile.Dispose() }
}

# variant -> { category template, vanilla block texture name (no .png) }
$variants = @(
    @{ variant = "iron";        category = "metallic"; block = "iron_block"      },
    @{ variant = "copper";      category = "metallic"; block = "copper_block"    },
    @{ variant = "gold";        category = "metallic"; block = "gold_block"      },
    @{ variant = "redstone";    category = "mineral";  block = "redstone_block"  },
    @{ variant = "lapis";       category = "mineral";  block = "lapis_block"     },
    @{ variant = "coal";        category = "mineral";  block = "coal_block"      },
    @{ variant = "diamond";     category = "gem";      block = "diamond_block"   },
    @{ variant = "emerald";     category = "gem";      block = "emerald_block"   },
    @{ variant = "prismarine";  category = "aquatic";  block = "prismarine"      },
    @{ variant = "sponge";      category = "aquatic";  block = "sponge"          },
    @{ variant = "magma_cream"; category = "infernal"; block = "magma"           },
    @{ variant = "ender_pearl"; category = "arcane";   block = "end_stone"       }
)

foreach ($spec in $variants) {
    Build-VariantTexture -variant $spec.variant -category $spec.category -blockTextureName $spec.block
}

Write-Output "done -- 12 variant PNGs regenerated in $textureDir"
