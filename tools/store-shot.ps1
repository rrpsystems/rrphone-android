# Captura a tela do celular (adb) e recorta para a Play Store.
#
#     .\tools\store-shot.ps1 02-chamada
#
# O Play aceita no máximo 2:1; a tela do aparelho (1080x2400) passa disso. O
# recorte tira a barra de status (relógio, ícones de notificação) e a barra de
# navegação, ficando em 1080x2160 (2:1) só com o app.

param([Parameter(Mandatory = $true)][string]$Name)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$adb = 'C:\dev\Android\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'store-listing\screenshots'
New-Item -ItemType Directory -Force $dir | Out-Null
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
Write-Host "$out (${w}x$h)"
