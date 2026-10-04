# Gera o recurso gráfico (banner) da Play Store: store-listing/feature-graphic.png (1024x500).
#
#     .\tools\make-banner.ps1
#
# Na identidade do RRPBX (azul-marinho, verde-água): logo e nome à esquerda,
# e à direita o teclado com as teclas arredondadas do próprio app. O Google
# pode cortar as bordas e sobrepor o botão de play no centro: o texto fica
# longe das bordas.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$logoPath = Join-Path $root 'app\src\main\res\drawable\logo_rrp_clean.png'
$out = Join-Path $root 'store-listing\feature-graphic.png'

function C([string]$hex) { [Drawing.ColorTranslator]::FromHtml($hex) }
$navyTop = C '#0B1726'; $navyBottom = C '#0E2436'; $key = C '#1B2C3D'
$textPrimary = C '#EEF3F7'; $textSecondary = C '#9FB0C2'; $green = C '#22A45D'; $teal = C '#2DD4BF'

$W = 1024; $H = 500
$bmp = New-Object Drawing.Bitmap $W, $H
$g = [Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'
$g.TextRenderingHint = 'AntiAliasGridFit'; $g.PixelOffsetMode = 'HighQuality'

# Fundo em degradê azul-marinho e brilho verde-água atrás do teclado.
$bgBrush = New-Object Drawing.Drawing2D.LinearGradientBrush (New-Object Drawing.Point 0, 0), (New-Object Drawing.Point $W, $H), $navyTop, $navyBottom
$g.FillRectangle($bgBrush, 0, 0, $W, $H)
$glow = New-Object Drawing.Drawing2D.GraphicsPath
$glow.AddEllipse(560, -40, 560, 580)
$pgb = New-Object Drawing.Drawing2D.PathGradientBrush $glow
$pgb.CenterColor = [Drawing.Color]::FromArgb(55, $teal)
$pgb.SurroundColors = @([Drawing.Color]::FromArgb(0, $teal))
$g.FillPath($pgb, $glow)

function RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $p.AddArc($x, $y, 2*$r, 2*$r, 180, 90); $p.AddArc($x+$w-2*$r, $y, 2*$r, 2*$r, 270, 90)
    $p.AddArc($x+$w-2*$r, $y+$h-2*$r, 2*$r, 2*$r, 0, 90); $p.AddArc($x, $y+$h-2*$r, 2*$r, 2*$r, 90, 90)
    $p.CloseFigure(); return $p
}

# Teclado: 4 linhas x 3 teclas em pílula, como no app, mais o botão Ligar.
$digits = @('1','2','3','4','5','6','7','8','9','*','0','#')
$letters = @('','ABC','DEF','GHI','JKL','MNO','PQRS','TUV','WXYZ','','+','')
$kw = 104; $kh = 58; $gx = 14; $gy = 14
$x0 = 610; $y0 = 62
$keyBrush = New-Object Drawing.SolidBrush $key
$digitFont = New-Object Drawing.Font 'Segoe UI', 22, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
$letterFont = New-Object Drawing.Font 'Segoe UI', 10, ([Drawing.FontStyle]::Bold), ([Drawing.GraphicsUnit]::Pixel)
$center = New-Object Drawing.StringFormat; $center.Alignment = 'Center'; $center.LineAlignment = 'Center'
$tp = New-Object Drawing.SolidBrush $textPrimary; $ts = New-Object Drawing.SolidBrush $textSecondary
for ($i = 0; $i -lt 12; $i++) {
    $col = $i % 3; $row = [math]::Floor($i / 3)
    $x = $x0 + $col * ($kw + $gx); $y = $y0 + $row * ($kh + $gy)
    $g.FillPath($keyBrush, (RoundRect $x $y $kw $kh ($kh/2)))
    if ($letters[$i]) {
        $g.DrawString($digits[$i], $digitFont, $tp, (New-Object Drawing.RectangleF $x, ($y+4), $kw, ($kh-22)), $center)
        $g.DrawString($letters[$i], $letterFont, $ts, (New-Object Drawing.RectangleF $x, ($y+$kh-24), $kw, 16), $center)
    } else {
        $g.DrawString($digits[$i], $digitFont, $tp, (New-Object Drawing.RectangleF $x, $y, $kw, $kh), $center)
    }
}
# Botão Ligar (verde) abaixo, largura do teclado.
$cy = $y0 + 4 * ($kh + $gy)
$callW = 3*$kw + 2*$gx
$g.FillPath((New-Object Drawing.SolidBrush $green), (RoundRect $x0 $cy $callW $kh ($kh/2)))
$callFont = New-Object Drawing.Font 'Segoe UI Semibold', 22, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
$g.DrawString([string][char]0x2706 + '  Ligar', $callFont, $tp, (New-Object Drawing.RectangleF $x0, $cy, $callW, $kh), $center)

# Logo e textos à esquerda.
$logo = [Drawing.Bitmap]::FromFile($logoPath)
$lh = 120; $lw = [int]($logo.Width * $lh / $logo.Height)
$g.DrawImage($logo, 72, 70, $lw, $lh)
$titleFont = New-Object Drawing.Font 'Segoe UI Semibold', 58, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
$subFont = New-Object Drawing.Font 'Segoe UI', 30, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
$smallFont = New-Object Drawing.Font 'Segoe UI', 20, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
# "RRP" branco + "Softphone" verde-água, como o "RRP" + "BX" do RRPBX.
$g.DrawString('RRP', $titleFont, $tp, 64, 206)
$rrpW = $g.MeasureString('RRP', $titleFont, 1000, [Drawing.StringFormat]::GenericTypographic).Width
$g.DrawString('Softphone', $titleFont, (New-Object Drawing.SolidBrush $teal), (64 + $rrpW + 14), 206)
$g.DrawString('Seu ramal no celular', $subFont, $tp, 68, 282)
# Linha de destaque verde-água, como na identidade do RRPBX.
$g.FillRectangle((New-Object Drawing.SolidBrush $teal), 70, 332, 56, 4)
$g.DrawString('Ligue, transfira e receba com o app fechado', $smallFont, $ts, 70, 350)

# Selo do RRPBX: o app fala com qualquer PABX SIP, mas o recebimento com o app
# fechado (push) passa pelo Flexisip da RRP e só vale para o RRPBX.
$badgeFont = New-Object Drawing.Font 'Segoe UI Semibold', 17, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
$badgeRest = New-Object Drawing.Font 'Segoe UI', 17, ([Drawing.FontStyle]::Regular), ([Drawing.GraphicsUnit]::Pixel)
$gt = [Drawing.StringFormat]::GenericTypographic
$t1 = 'RRP'; $t2 = 'BX'; $t3 = '  ·  PABX em Nuvem'
$w1 = $g.MeasureString($t1, $badgeFont, 1000, $gt).Width
$w2 = $g.MeasureString($t2, $badgeFont, 1000, $gt).Width
$w3 = $g.MeasureString($t3, $badgeRest, 1000, $gt).Width
$bx = 70; $by = 428; $padX = 16; $bh = 34
$badge = RoundRect $bx $by ($w1 + $w2 + $w3 + 2*$padX) $bh ($bh/2)
$g.FillPath((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(40, $teal))), $badge)
$g.DrawPath((New-Object Drawing.Pen ([Drawing.Color]::FromArgb(110, $teal)), 1.2), $badge)
$ty = $by + 7
$g.DrawString($t1, $badgeFont, $tp, ($bx + $padX), $ty, $gt)
$g.DrawString($t2, $badgeFont, (New-Object Drawing.SolidBrush $teal), ($bx + $padX + $w1), $ty, $gt)
$g.DrawString($t3, $badgeRest, $ts, ($bx + $padX + $w1 + $w2), $ty, $gt)

$bmp.Save($out, [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $logo.Dispose()
Write-Host "Banner gerado: $out" -ForegroundColor Green
