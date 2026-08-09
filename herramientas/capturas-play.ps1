# Ajusta las capturas a la proporcion que exige Google Play.
#
# Play pide 16:9 o 9:16. El emulador da 1080x2400, que es 9:20 y lo rechaza.
# En vez de recortar -que se comeria contenido- se anaden barras laterales del
# color de fondo de la marca hasta llegar a 9:16 exacto. No se pierde nada de
# la pantalla.
#
#     powershell -ExecutionPolicy Bypass -File herramientas\capturas-play.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$raiz = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$origen = Join-Path $raiz 'play\capturas'
$destino = Join-Path $raiz 'play\capturas-play'
New-Item -ItemType Directory -Force $destino | Out-Null

$fondo = [System.Drawing.Color]::FromArgb(255, 0x1A, 0x11, 0x45)

Get-ChildItem $origen -Filter '*.png' | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    $alto = $img.Height
    $anchoObjetivo = [int][Math]::Round($alto * 9.0 / 16.0)

    if ($img.Width -ge $anchoObjetivo) {
        $img.Dispose()
        Write-Output ("{0,-22} ya cumple la proporcion" -f $_.Name)
        return
    }

    $bmp = New-Object System.Drawing.Bitmap($anchoObjetivo, $alto)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear($fondo)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $x = [int](($anchoObjetivo - $img.Width) / 2)
    $g.DrawImage($img, $x, 0, $img.Width, $alto)
    $g.Dispose()
    $img.Dispose()

    $salida = Join-Path $destino $_.Name
    $bmp.Save($salida, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    $mb = (Get-Item $salida).Length / 1MB
    Write-Output ("{0,-22} {1}x{2}   {3:N2} MB" -f $_.Name, $anchoObjetivo, $alto, $mb)
}

Write-Output ""
Write-Output "Listas en: play\capturas-play\"
