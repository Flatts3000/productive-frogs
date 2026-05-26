# Generate the Spawnery block textures by deriving them from the Slime Milker's,
# so the two read as the same appliance family (cobblestone + wood-plank build,
# wood-framed window) while staying visually distinct: the Spawnery's window
# shows FROGSPAWN (a dark teal egg pool, glowing teal when running) where the
# Milker shows green slime.
#
# Output (src/main/resources/assets/productivefrogs/textures/block/):
#   spawnery_top.png  spawnery_front.png  spawnery_side.png  spawnery_bottom.png
#   spawnery_top_on.png  spawnery_front_on.png  spawnery_side_on.png
#
# How: the Milker's *_working textures mark the window with bright lime green.
# We use those green pixels as a mask. In the mask:
#   - idle  -> a dark teal frogspawn pool with a stippled "egg" checker
#   - lit   -> a bright teal frogspawn glow
# Outside the mask we copy the Milker's cobble/wood pixels unchanged, so the
# construction matches. The bottom has no window - it's a straight copy.
#
# Re-run after editing the Milker textures. Hand-tweak the palette constants
# below if the teal needs tuning on the runClient pass.

Add-Type -AssemblyName System.Drawing

$dir = (Resolve-Path "$PSScriptRoot\..\src\main\resources\assets\productivefrogs\textures\block").Path

function Is-Window([System.Drawing.Color]$p) {
    # Bright lime green = the Milker's slime window. Wood (R>=G) and stone (R~=G)
    # both fail the green-dominance test, so only the window matches.
    return ($p.G -gt 100) -and (($p.G - $p.R) -gt 15) -and (($p.G - $p.B) -gt 30)
}

function Build-Face($idleName, $workName, $outName, [bool]$lit) {
    $idlePath = Join-Path $dir "$idleName.png"
    $idle = New-Object System.Drawing.Bitmap $idlePath
    $work = $null
    if ($workName -and (Test-Path (Join-Path $dir "$workName.png"))) {
        $work = New-Object System.Drawing.Bitmap (Join-Path $dir "$workName.png")
    }
    $out = New-Object System.Drawing.Bitmap 16, 16
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $ip = $idle.GetPixel($x, $y)
            $window = $false
            $g = 0
            if ($work -ne $null) {
                $wp = $work.GetPixel($x, $y)
                if (Is-Window $wp) { $window = $true; $g = $wp.G }
            }
            if ($window) {
                if ($lit) {
                    # Bright teal frogspawn glow - preserve the window's shading via g.
                    $r = [int]($g * 0.20); $gg = [int]($g * 0.88); $b = [int]($g * 0.82)
                } else {
                    # Dark teal pool; darken every other pixel for an egg-cluster stipple.
                    $r = [int]($g * 0.10); $gg = [int]($g * 0.34); $b = [int]($g * 0.32)
                    if ((($x + $y) % 2) -eq 0) { $r = [int]($r * 0.5); $gg = [int]($gg * 0.55); $b = [int]($b * 0.55) }
                }
                $r = [Math]::Min(255, $r); $gg = [Math]::Min(255, $gg); $b = [Math]::Min(255, $b)
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $gg, $b))
            } else {
                $out.SetPixel($x, $y, $ip)
            }
        }
    }
    $out.Save((Join-Path $dir "$outName.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose(); $idle.Dispose(); if ($work -ne $null) { $work.Dispose() }
    Write-Output "wrote $outName.png"
}

# Idle faces (window = dark frogspawn pool).
Build-Face 'slime_milker_top'    'slime_milker_top_working'   'spawnery_top'    $false
Build-Face 'slime_milker_front'  'slime_milker_front_working' 'spawnery_front'  $false
Build-Face 'slime_milker_side'   'slime_milker_side_working'  'spawnery_side'   $false
# Bottom: no window, straight copy of the Milker's cobble underside.
Build-Face 'slime_milker_bottom' $null                        'spawnery_bottom' $false

# Lit faces (window = bright teal frogspawn glow).
Build-Face 'slime_milker_top'    'slime_milker_top_working'   'spawnery_top_on'   $true
Build-Face 'slime_milker_front'  'slime_milker_front_working' 'spawnery_front_on' $true
Build-Face 'slime_milker_side'   'slime_milker_side_working'  'spawnery_side_on'  $true

Write-Output 'done -- Spawnery textures derived from the Slime Milker (frogspawn window).'
