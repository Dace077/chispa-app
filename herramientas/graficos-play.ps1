# Genera los graficos que exige Google Play a partir del icono original.
#
# Se redibujan los vectores de res/drawable/ic_launcher_*.xml a tamano completo
# en vez de reescalar el PNG de 192px, que saldria borroso justo en lo que mas
# se mira de una ficha.
#
#     powershell -ExecutionPolicy Bypass -File herramientas\graficos-play.ps1
#
# Salida en play/graficos/:
#   icono-512.png      icono de la ficha (512x512, sin transparencia)
#   destacado-1024.png grafico destacado (1024x500)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$raiz = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$salida = Join-Path $raiz 'play\graficos'
New-Item -ItemType Directory -Force $salida | Out-Null

# --- Colores del icono, copiados de ic_launcher_background/foreground.xml ---
$fondo0 = [System.Drawing.Color]::FromArgb(255, 0x3A, 0x2B, 0xB5)
$fondo1 = [System.Drawing.Color]::FromArgb(255, 0x2A, 0x1D, 0x6E)
$fondo2 = [System.Drawing.Color]::FromArgb(255, 0x1A, 0x11, 0x45)
$chispa0 = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xC9, 0x4D)
$chispa1 = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xB0, 0x20)
$chispa2 = [System.Drawing.Color]::FromArgb(255, 0xFF, 0x6B, 0x5A)

# La chispa de cuatro puntas, en el sistema de 108x108 del vector original.
function New-ChispaPath([double]$cx, [double]$cy, [double]$radio, [double]$k) {
    # $radio = distancia a las puntas, $k = cuanto se hunde la cintura (0..1)
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    [double]$c = $radio * $k
    [double]$a = $c * 0.35
    [double]$b = $c * 0.60

    # Coordenadas relativas al centro, en pares X,Y. Cuatro curvas de Bezier
    # que van punta -> punta pasando por una cintura comida hacia dentro.
    [double[]]$xs = @(0, $a, $b, $radio,  $b, $a, 0,  -$a, -$b, -$radio,  -$b, -$a, 0)
    [double[]]$ys = @(-$radio, -$b, -$a, 0,  $a, $b, $radio,  $b, $a, 0,  -$a, -$b, -$radio)

    for ($i = 0; $i -lt 12; $i += 3) {
        $p.AddBezier(
            [float]($cx + $xs[$i]),     [float]($cy + $ys[$i]),
            [float]($cx + $xs[$i + 1]), [float]($cy + $ys[$i + 1]),
            [float]($cx + $xs[$i + 2]), [float]($cy + $ys[$i + 2]),
            [float]($cx + $xs[$i + 3]), [float]($cy + $ys[$i + 3])
        )
    }
    $p.CloseFigure()
    return $p
}

function Draw-Chispa($g, [double]$escala, [double]$offX, [double]$offY) {
    # Chispa principal: centro (54,53.5), punta a 31.5
    $cx = 54 * $escala + $offX; $cy = 53.5 * $escala + $offY
    $radio = 31.5 * $escala
    $path = New-ChispaPath $cx $cy $radio 0.62
    $rect = New-Object System.Drawing.RectangleF(
        [float]($cx - $radio), [float]($cy - $radio), [float]($radio * 2), [float]($radio * 2))
    $br = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect, $chispa0, $chispa2, 45.0)
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend(3)
    $blend.Colors = @($chispa0, $chispa1, $chispa2)
    $blend.Positions = @(0.0, 0.5, 1.0)
    $br.InterpolationColors = $blend
    $g.FillPath($br, $path)
    $path.Dispose(); $br.Dispose()

    # Chispa satelite: centro (79,36.5), punta a 8.5
    $sx = 79 * $escala + $offX; $sy = 36.5 * $escala + $offY
    $sr = 8.5 * $escala
    $sp = New-ChispaPath $sx $sy $sr 0.62
    $sb = New-Object System.Drawing.SolidBrush(
        [System.Drawing.Color]::FromArgb(230, 0xFF, 0x6B, 0x5A))
    $g.FillPath($sb, $sp)
    $sp.Dispose(); $sb.Dispose()
}

function New-Lienzo([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    # Fondo en degradado, igual que el icono
    $rect = New-Object System.Drawing.Rectangle(0, 0, $w, $h)
    $br = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $fondo0, $fondo2, 45.0)
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend(3)
    $blend.Colors = @($fondo0, $fondo1, $fondo2)
    $blend.Positions = @(0.0, 0.55, 1.0)
    $br.InterpolationColors = $blend
    $g.FillRectangle($br, $rect)
    $br.Dispose()
    return @($bmp, $g)
}

# ------------------------------- Icono 512 ------------------------------- #
# Play lo quiere cuadrado, sin transparencia y sin bordes redondeados propios:
# el recorte lo aplica la tienda.
$lienzo = New-Lienzo 512 512
$bmp = $lienzo[0]; $g = $lienzo[1]
Draw-Chispa $g (512 / 108) 0 0
$g.Dispose()
$bmp.Save((Join-Path $salida 'icono-512.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "icono-512.png        512x512"

# --------------------------- Grafico destacado --------------------------- #
# 1024x500. El texto se mantiene lejos de los bordes porque Play recorta esta
# imagen de formas distintas segun donde la enseñe.
$lienzo = New-Lienzo 1024 500
$bmp = $lienzo[0]; $g = $lienzo[1]
Draw-Chispa $g (330 / 108) 70 85

$fTitulo = New-Object System.Drawing.Font('Segoe UI', 62, [System.Drawing.FontStyle]::Bold)
$fSub    = New-Object System.Drawing.Font('Segoe UI', 27, [System.Drawing.FontStyle]::Regular)
$blanco  = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$suave   = New-Object System.Drawing.SolidBrush(
    [System.Drawing.Color]::FromArgb(220, 0xFF, 0xC9, 0x4D))

$g.DrawString('Chispa', $fTitulo, $blanco, 420, 150)
$g.DrawString(([char]0x41 + 'prende ingl' + [char]0xE9 + 's de cero a C2'), $fSub, $suave, 428, 265)
$g.DrawString(('Gratis, sin anuncios y sin conexi' + [char]0xF3 + 'n'), $fSub, $blanco, 428, 310)

$g.Dispose()
$bmp.Save((Join-Path $salida 'destacado-1024.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "destacado-1024.png   1024x500"
Write-Output "Guardado en play\graficos\"
