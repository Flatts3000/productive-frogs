# Generate the mod logo at src/main/resources/logo.png.
#
# Output: 256x256 PNG. Pixel-art "PF" wordmark dominates the center
# (around 55 percent of canvas height). Vertical slime-green gradient
# background, dark pixel border frame, single glowing slime cube accent
# tucked behind the letters. Used by NeoForge as the mod thumbnail in the
# in-game mods list, by CurseForge / Modrinth for the project sidebar,
# and by Discord embeds when the mod is linked.
#
# This is a deterministic placeholder. Re-run anytime to regenerate.
# Replace with proper hand-drawn artwork when commissioning a real logo.
#
# Platform: Windows. Depends on System.Drawing (ships in-box on Windows).

[CmdletBinding()]
param(
    [int]$Size = 256,
    [string]$OutPath
)

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
if (-not $OutPath) { $OutPath = Join-Path $repoRoot "src\main\resources\logo.png" }

# Brand colors.
$bgTop      = [System.Drawing.Color]::FromArgb(255, 0x2A, 0x3D, 0x15)  # deep moss
$bgBot      = [System.Drawing.Color]::FromArgb(255, 0x4A, 0x68, 0x2A)  # darker slime
$letterFg   = [System.Drawing.Color]::FromArgb(255, 0xF4, 0xF4, 0xE8)  # cream
$letterHi   = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xFF, 0xFF)  # bright cream highlight
$letterShd  = [System.Drawing.Color]::FromArgb(255, 0xB8, 0xB8, 0xA8)  # dim cream shadow
$letterOl   = [System.Drawing.Color]::FromArgb(255, 0x1F, 0x2E, 0x12)  # dark outline
$slimeFill  = [System.Drawing.Color]::FromArgb(255, 0x9C, 0xD6, 0x5A)  # bright slime
$slimeHi    = [System.Drawing.Color]::FromArgb(255, 0xD4, 0xF0, 0xA0)  # slime highlight
$slimeShd   = [System.Drawing.Color]::FromArgb(255, 0x5C, 0x86, 0x30)  # slime shadow
$border     = [System.Drawing.Color]::FromArgb(255, 0x1F, 0x2E, 0x12)  # dark frame

# Pixel-grid letterforms. 6 cols wide, 9 rows tall per letter. 1 = fill, 0 = empty.
# Both letters share the same 6x9 grid so the visual rhythm matches.
$pPattern = @(
    "111111",
    "111111",
    "110011",
    "110011",
    "111111",
    "111111",
    "110000",
    "110000",
    "110000"
)
$fPattern = @(
    "111111",
    "111111",
    "110000",
    "110000",
    "111110",
    "111110",
    "110000",
    "110000",
    "110000"
)

$bmp = New-Object System.Drawing.Bitmap $Size, $Size
$g = [System.Drawing.Graphics]::FromImage($bmp)
try {
    # Pixel art: turn off interpolation smoothing so cells stay crisp.
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

    # 1. Vertical gradient background.
    $rect = New-Object System.Drawing.Rectangle 0, 0, $Size, $Size
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $bgTop, $bgBot, 90)
    try { $g.FillRectangle($brush, $rect) } finally { $brush.Dispose() }

    # 2. Slime cube accent (behind the letters, slightly bottom-right of center).
    # Drawn as a square with a highlight and shadow band, no outline so it sits
    # softly in the background. Band thickness scales with cube size so the
    # accent reads correctly at non-default $Size values (e.g., 512, 1024).
    $cubeSize = [int]($Size * 0.30)
    $cubeX = [int]($Size * 0.58)
    $cubeY = [int]($Size * 0.45)
    $cubeBandThick = [Math]::Max(2, [int]($cubeSize / 12))  # ~6 at default $Size=256
    $cubeBrush = New-Object System.Drawing.SolidBrush $slimeFill
    try { $g.FillRectangle($cubeBrush, $cubeX, $cubeY, $cubeSize, $cubeSize) } finally { $cubeBrush.Dispose() }
    # Highlight band (top-left edge of cube).
    $hiBrush = New-Object System.Drawing.SolidBrush $slimeHi
    try {
        $g.FillRectangle($hiBrush, $cubeX, $cubeY, $cubeSize, $cubeBandThick)
        $g.FillRectangle($hiBrush, $cubeX, $cubeY, $cubeBandThick, $cubeSize)
    } finally { $hiBrush.Dispose() }
    # Shadow band (bottom-right edge of cube).
    $shdBrush = New-Object System.Drawing.SolidBrush $slimeShd
    try {
        $g.FillRectangle($shdBrush, $cubeX, $cubeY + $cubeSize - $cubeBandThick, $cubeSize, $cubeBandThick)
        $g.FillRectangle($shdBrush, $cubeX + $cubeSize - $cubeBandThick, $cubeY, $cubeBandThick, $cubeSize)
    } finally { $shdBrush.Dispose() }

    # 3. Pixel-art "PF" letters.
    # Sized to fill about 55 percent of canvas height with a small gap between.
    # Dimensions derived from the pattern arrays so future tweaks to
    # $pPattern / $fPattern can't silently drift out of sync with the sizing math.
    $rows = $pPattern.Length
    $cols = $pPattern[0].Length
    if ($fPattern.Length -ne $rows -or $fPattern[0].Length -ne $cols) {
        throw "Letter pattern dimensions must match: pPattern=${rows}x${cols}, fPattern=$($fPattern.Length)x$($fPattern[0].Length)"
    }
    $cell = [int]([Math]::Floor($Size * 0.55 / $rows))  # ~15 at $Size=256, rows=9
    $letterW = $cols * $cell
    $gap = [int]($cell * 1.3)
    $totalW = ($letterW * 2) + $gap
    $startX = [int](($Size - $totalW) / 2)
    $startY = [int](($Size - ($rows * $cell)) / 2)

    function Draw-Letter {
        param([string[]]$pattern, [int]$ox, [int]$oy)
        # Brushes are constant colors, so allocate once per letter and reuse
        # across every cell instead of allocating ~4 GDI brushes per filled
        # cell. Cuts allocation traffic from O(filled_cells * 4) to O(4) per
        # letter without changing any output pixels.
        $fillBrush = New-Object System.Drawing.SolidBrush $letterFg
        $hiBrush2  = New-Object System.Drawing.SolidBrush $letterHi
        $shdBrush2 = New-Object System.Drawing.SolidBrush $letterShd
        $olBrush   = New-Object System.Drawing.SolidBrush $letterOl
        try {
            $shdInset = [Math]::Max(1, [int]($cell / 8))
            $olThick = [Math]::Max(2, [int]($cell / 6))
            for ($r = 0; $r -lt $pattern.Length; $r++) {
                $row = $pattern[$r]
                for ($c = 0; $c -lt $row.Length; $c++) {
                    if ($row[$c] -ne '1') { continue }
                    $px = $ox + ($c * $cell)
                    $py = $oy + ($r * $cell)
                    # Main cell fill.
                    $g.FillRectangle($fillBrush, $px, $py, $cell, $cell)
                    # Subtle pixel-cell shading: highlight on top-left, shadow on bottom-right.
                    # Only paint these if the neighbor in the relevant direction is empty (gives
                    # the letterforms a 3D-block feel without painting interior cell boundaries).
                    $neighborBelow = if ($r + 1 -lt $pattern.Length) { $pattern[$r + 1][$c] } else { '0' }
                    $neighborRight = if ($c + 1 -lt $row.Length) { $row[$c + 1] } else { '0' }
                    $neighborAbove = if ($r - 1 -ge 0) { $pattern[$r - 1][$c] } else { '0' }
                    $neighborLeft  = if ($c - 1 -ge 0) { $row[$c - 1] } else { '0' }
                    if ($neighborAbove -ne '1') { $g.FillRectangle($hiBrush2, $px, $py, $cell, $shdInset) }
                    if ($neighborLeft  -ne '1') { $g.FillRectangle($hiBrush2, $px, $py, $shdInset, $cell) }
                    if ($neighborBelow -ne '1') { $g.FillRectangle($shdBrush2, $px, $py + $cell - $shdInset, $cell, $shdInset) }
                    if ($neighborRight -ne '1') { $g.FillRectangle($shdBrush2, $px + $cell - $shdInset, $py, $shdInset, $cell) }
                }
            }
            # Bold outline around the whole letter shape: re-walk the grid and paint
            # a dark border on any edge where the adjacent cell is empty (or off-grid).
            for ($r = 0; $r -lt $pattern.Length; $r++) {
                $row = $pattern[$r]
                for ($c = 0; $c -lt $row.Length; $c++) {
                    if ($row[$c] -ne '1') { continue }
                    $px = $ox + ($c * $cell)
                    $py = $oy + ($r * $cell)
                    $above = if ($r - 1 -ge 0) { $pattern[$r - 1][$c] } else { '0' }
                    $below = if ($r + 1 -lt $pattern.Length) { $pattern[$r + 1][$c] } else { '0' }
                    $left  = if ($c - 1 -ge 0) { $row[$c - 1] } else { '0' }
                    $right = if ($c + 1 -lt $row.Length) { $row[$c + 1] } else { '0' }
                    if ($above -ne '1') { $g.FillRectangle($olBrush, $px, $py, $cell, $olThick) }
                    if ($below -ne '1') { $g.FillRectangle($olBrush, $px, $py + $cell - $olThick, $cell, $olThick) }
                    if ($left  -ne '1') { $g.FillRectangle($olBrush, $px, $py, $olThick, $cell) }
                    if ($right -ne '1') { $g.FillRectangle($olBrush, $px + $cell - $olThick, $py, $olThick, $cell) }
                }
            }
        } finally {
            $fillBrush.Dispose()
            $hiBrush2.Dispose()
            $shdBrush2.Dispose()
            $olBrush.Dispose()
        }
    }

    Draw-Letter $pPattern $startX $startY
    Draw-Letter $fPattern ($startX + $letterW + $gap) $startY

    # 4. Pixel border frame around the entire canvas.
    $bThick = [int]([Math]::Max(4, $Size / 64))
    $borderBrush = New-Object System.Drawing.SolidBrush $border
    try {
        $g.FillRectangle($borderBrush, 0, 0, $Size, $bThick)
        $g.FillRectangle($borderBrush, 0, $Size - $bThick, $Size, $bThick)
        $g.FillRectangle($borderBrush, 0, 0, $bThick, $Size)
        $g.FillRectangle($borderBrush, $Size - $bThick, 0, $bThick, $Size)
    } finally { $borderBrush.Dispose() }

    $outDir = Split-Path $OutPath -Parent
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "wrote $OutPath ($Size x $Size)"
} finally {
    $g.Dispose()
    $bmp.Dispose()
}
