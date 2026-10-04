# Captura a tela do celular (adb) e prepara para a Play Store.
#
#     .	ools\store-shot.ps1 02-chamada
#     .	ools\store-shot.ps1 -Reframe        # só reenquadra as capturas já tiradas
#
# O Play exige 9:16 exatos (1080x1920). A tela do aparelho é 1080x2400: o
# recorte tira a barra de status (relógio, ícones de notificação) e a de
# navegação, ficando em 2:1 só com o app; daí a imagem é reduzida para 1920 de
# altura e centralizada em 1080x1920 com faixas laterais na cor de fundo do app
# (#14181D), que somem na tela escura.

param([string]$Name, [switch]$Reframe)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Reframe([string]$path) {
    $src = [Drawing.Bitmap]::FromFile($path)
    if ($src.Width -eq 1080 -and $src.Height -eq 1920) { $src.Dispose(); return }
    $dst = New-Object Drawing.Bitmap 1080, 1920
    $g = [Drawing.Graphics]::FromImage($dst)
    $g.InterpolationMode = 'HighQualityBicubic'; $g.PixelOffsetMode = 'HighQuality'
    $g.Clear([Drawing.ColorTranslator]::FromHtml('#14181D'))
    $h = 1920; $w = [int]($src.Width * $h / $src.Height)
    $g.DrawImage($src, [int]((1080 - $w) / 2), 0, $w, $h)
    $src.Dispose()
    $tmp = "$path.tmp.png"
    $dst.Save($tmp, [Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $dst.Dispose()
    Move-Item -Force $tmp $path
    Write-Host "$path (1080x1920)"
}

$adb = 'C:\dev\Android\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'store-listing\screenshots'
New-Item -ItemType Directory -Force $dir | Out-Null
if ($Reframe) {
    Get-ChildItem $dir -Filter *.png | ForEach-Object { Reframe $_.FullName }
    return
}
if (-not $Name) { throw "Informe o nome da captura (ex.: 02-chamada) ou -Reframe." }
$raw = Join-Path $env:TEMP "rrp-shot-raw.png"

& $adb shell screencap -p /sdcard/rrp_shot.png | Out-Null
& $adb pull /sdcard/rrp_shot.png $raw | Out-Null
& $adb shell rm /sdcard/rrp_shot.png | Out-Null

$src = [Drawing.Bitmap]::FromFile($raw)
$w = $src.Width
$h = [int]($w * 2)                  # 2:1
$top = [int](($src.Height - $h) / 2) + 6
$dst = New-Object Drawing.Bitmap $w, $h
$g = [Drawing.Graphics]::FromImage($dst)
$g.DrawImage($src, (New-Object Drawing.Rectangle 0, 0, $w, $h), (New-Object Drawing.Rectangle 0, $top, $w, $h), [Drawing.GraphicsUnit]::Pixel)
$out = Join-Path $dir "$Name.png"
$dst.Save($out, [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $dst.Dispose(); $src.Dispose(); Remove-Item $raw
Reframe $out
