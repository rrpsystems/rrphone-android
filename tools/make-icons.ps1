# Gera os ícones do app a partir do logo (app/src/main/res/drawable/logo_rrp_clean.png).
#
#     .\tools\make-icons.ps1
#
# - mipmap-*/ic_launcher_foreground.png  camada do ícone adaptável (logo na área segura)
# - mipmap-*/ic_launcher_monochrome.png  silhueta branca, para ícones temáticos (Android 13+)
# - mipmap-*/ic_launcher.png e _round.png  versões prontas (fundo + logo), para launchers antigos
# - store-listing/icon-512.png           ícone da Play Store
#
# O fundo é a cor de fundo do app (Rrp.Background, #14181D): o quadrado verde do
# logo se destaca nele, e o ícone combina com o app aberto.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$res = Join-Path $root 'app\src\main\res'
$logoPath = Join-Path $res 'drawable\logo_rrp_clean.png'
$background = [Drawing.ColorTranslator]::FromHtml('#14181D')

$logo = [Drawing.Bitmap]::FromFile($logoPath)

# Recorte justo no que não é transparente, para o dimensionamento valer pelo desenho.
$minX = $logo.Width; $minY = $logo.Height; $maxX = 0; $maxY = 0
for ($y = 0; $y -lt $logo.Height; $y++) {
    for ($x = 0; $x -lt $logo.Width; $x++) {
        if ($logo.GetPixel($x, $y).A -gt 8) {
            if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
$crop = New-Object Drawing.Rectangle $minX, $minY, ($maxX - $minX + 1), ($maxY - $minY + 1)

function New-Canvas([int]$size) {
    $bmp = New-Object Drawing.Bitmap $size, $size, ([Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'; $g.PixelOffsetMode = 'HighQuality'
    $g.Clear([Drawing.Color]::Transparent)
    return @($bmp, $g)
}

# Desenha o logo centrado ocupando `fraction` do lado do canvas.
function Draw-Logo($g, [int]$size, [double]$fraction) {
    $box = $size * $fraction
    $scale = [Math]::Min($box / $crop.Width, $box / $crop.Height)
    $w = $crop.Width * $scale; $h = $crop.Height * $scale
    $dest = New-Object Drawing.RectangleF (($size - $w) / 2), (($size - $h) / 2), $w, $h
    $g.DrawImage($logo, $dest, $crop, [Drawing.GraphicsUnit]::Pixel)
}

function Save($bmp, $g, [string]$path) {
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# Ícone adaptável: canvas de 108dp, área segura de 66dp. O logo ocupa ~48%
# do canvas, dentro do círculo seguro mesmo com as órbitas nos cantos.
$densities = @{ 'mdpi' = 1.0; 'hdpi' = 1.5; 'xhdpi' = 2.0; 'xxhdpi' = 3.0; 'xxxhdpi' = 4.0 }
foreach ($d in $densities.Keys) {
    $f = $densities[$d]
    $dir = Join-Path $res "mipmap-$d"

    $fg = [int](108 * $f)
    $c = New-Canvas $fg; Draw-Logo $c[1] $fg 0.48
    Save $c[0] $c[1] (Join-Path $dir 'ic_launcher_foreground.png')

    # Monocromático: mesma silhueta, toda branca (o sistema aplica a cor do tema).
    $c = New-Canvas $fg; Draw-Logo $c[1] $fg 0.48
    $bmp = $c[0]
    for ($y = 0; $y -lt $fg; $y++) { for ($x = 0; $x -lt $fg; $x++) {
        $a = $bmp.GetPixel($x, $y).A
        $bmp.SetPixel($x, $y, [Drawing.Color]::FromArgb($a, 255, 255, 255))
    } }
    Save $bmp $c[1] (Join-Path $dir 'ic_launcher_monochrome.png')

    # Versões prontas (48dp): quadrado arredondado e círculo.
    $leg = [int](48 * $f)
    foreach ($shape in 'square', 'round') {
        $c = New-Canvas $leg; $g = $c[1]
        $brush = New-Object Drawing.SolidBrush $background
        if ($shape -eq 'round') { $g.FillEllipse($brush, 0, 0, $leg, $leg) }
        else {
            $r = $leg * 0.22; $p = New-Object Drawing.Drawing2D.GraphicsPath
            $p.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90); $p.AddArc($leg - 2 * $r, 0, 2 * $r, 2 * $r, 270, 90)
            $p.AddArc($leg - 2 * $r, $leg - 2 * $r, 2 * $r, 2 * $r, 0, 90); $p.AddArc(0, $leg - 2 * $r, 2 * $r, 2 * $r, 90, 90)
            $p.CloseFigure(); $g.FillPath($brush, $p)
        }
        Draw-Logo $g $leg 0.66
        $name = if ($shape -eq 'round') { 'ic_launcher_round.png' } else { 'ic_launcher.png' }
        Save $c[0] $g (Join-Path $dir $name)
    }
    # As .webp do modelo do Android Studio têm o mesmo nome de recurso: saem.
    Get-ChildItem $dir -Filter 'ic_launcher*.webp' | Remove-Item
}

# Play Store: 512x512, quadrado cheio (a loja aplica a máscara).
$c = New-Canvas 512; $c[1].Clear($background); Draw-Logo $c[1] 512 0.70
Save $c[0] $c[1] (Join-Path $root 'store-listing\icon-512.png')

$logo.Dispose()
Write-Host "Ícones gerados." -ForegroundColor Green
