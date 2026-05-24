# Generate the mod logo at src/main/resources/logo.png.
#
# Output: 256x256 PNG, slime-green background with a stylized "PF" wordmark
# and a 2x2 frog-egg cluster motif. Used by NeoForge as the mod thumbnail in
# the in-game mods list, by CurseForge / Modrinth for the project sidebar,
# and by Discord embeds when the mod is linked.
#
# This is a deterministic placeholder — re-run anytime to regenerate. Replace
# with proper hand-drawn artwork when commissioning a real logo.
#
# Platform: Windows. Depends on System.Drawing (ships in-box on Windows).

[CmdletBinding()]
param(
    [int]$Size = 256
)

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$outPath = Join-Path $repoRoot "src\main\resources\logo.png"

# Brand colors. Slime-green base derived from the Bog category tint.
$bgTop    = [System.Drawing.Color]::FromArgb(255, 0x4A, 0x68, 0x2A)  # darker slime
$bgBot    = [System.Drawing.Color]::FromArgb(255, 0x7A, 0xA0, 0x4A)  # lighter slime
$eggLight = [System.Drawing.Color]::FromArgb(255, 0xC4, 0xE0, 0x9A)  # frog-egg jelly
$eggDark  = [System.Drawing.Color]::FromArgb(255, 0x2A, 0x40, 0x18)  # embryo dot
$textFg   = [System.Drawing.Color]::FromArgb(255, 0xF4, 0xF4, 0xE8)  # cream wordmark
$border   = [System.Drawing.Color]::FromArgb(255, 0x1F, 0x2E, 0x12)  # dark frame

$bmp = New-Object System.Drawing.Bitmap $Size, $Size
$g = [System.Drawing.Graphics]::FromImage($bmp)
try {
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    # 1. Gradient background.
    $rect = New-Object System.Drawing.Rectangle 0, 0, $Size, $Size
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $bgTop, $bgBot, 90)
    try { $g.FillRectangle($brush, $rect) } finally { $brush.Dispose() }

    # 2. Four frog-egg jelly orbs in a 2x2 cluster, top-half of the image.
    $eggR = [int]($Size * 0.16)
    $cellW = [int]($Size * 0.22)
    $cx = [int]($Size * 0.5)
    $cy = [int]($Size * 0.32)
    $offsets = @(
        @{ x = -$cellW; y = -$cellW + 10 },
        @{ x =  $cellW; y = -$cellW + 10 },
        @{ x = -$cellW; y =  $cellW - 10 },
        @{ x =  $cellW; y =  $cellW - 10 }
    )
    foreach ($o in $offsets) {
        $eggX = $cx + $o.x - $eggR
        $eggY = $cy + $o.y - $eggR
        $eggBrush = New-Object System.Drawing.SolidBrush $eggLight
        try { $g.FillEllipse($eggBrush, $eggX, $eggY, $eggR * 2, $eggR * 2) } finally { $eggBrush.Dispose() }
        # Inner embryo dot.
        $dotR = [int]($eggR * 0.35)
        $dotBrush = New-Object System.Drawing.SolidBrush $eggDark
        try { $g.FillEllipse($dotBrush, $cx + $o.x - $dotR, $cy + $o.y - $dotR, $dotR * 2, $dotR * 2) } finally { $dotBrush.Dispose() }
    }

    # 3. "PF" wordmark in the lower band.
    $fontSize = [int]($Size * 0.32)
    $fontFamily = "Segoe UI"
    $font = New-Object System.Drawing.Font($fontFamily, $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    try {
        $fmt = New-Object System.Drawing.StringFormat
        $fmt.Alignment = [System.Drawing.StringAlignment]::Center
        $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
        $wordRect = New-Object System.Drawing.RectangleF 0, ($Size * 0.62), $Size, ($Size * 0.30)
        $textBrush = New-Object System.Drawing.SolidBrush $textFg
        try { $g.DrawString("PF", $font, $textBrush, $wordRect, $fmt) } finally { $textBrush.Dispose() }
    } finally { $font.Dispose() }

    # 4. Subtle dark border frame.
    $borderPen = New-Object System.Drawing.Pen $border, 4
    try { $g.DrawRectangle($borderPen, 2, 2, $Size - 4, $Size - 4) } finally { $borderPen.Dispose() }

    $outDir = Split-Path $outPath -Parent
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $outPath ($Size x $Size)"
} finally {
    $g.Dispose()
    $bmp.Dispose()
}
