# Pulsa un elemento de la pantalla del emulador buscándolo por su TEXTO exacto.
#
# Mucho más fiable que usar coordenadas fijas: el alto de los enunciados cambia
# de una pregunta a otra y las coordenadas dejan de valer a la tercera pantalla.
#
#     .\herramientas\pulsar.ps1 'EMPEZAR'
#     .\herramientas\pulsar.ps1 -Siguiente        # atajo para el botón SIGUIENTE
param([string]$Texto, [switch]$Siguiente)

$adb = 'C:\Users\skate\.androidtools\sdk\platform-tools\adb.exe'
$tmp = "$env:TEMP\ui.xml"

& $adb shell uiautomator dump /sdcard/ui.xml *> $null
& $adb pull /sdcard/ui.xml $tmp *> $null
$xml = Get-Content $tmp -Raw -Encoding UTF8

if ($Siguiente) { $Texto = 'SIGUIENTE' }

# Busca el nodo cuyo text coincide y saca sus bounds
$pattern = 'text="' + [regex]::Escape($Texto) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
$m = [regex]::Match($xml, $pattern)
if (-not $m.Success) {
    # Segundo intento: bounds antes que text (el orden de atributos varía)
    $pattern2 = 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="' + [regex]::Escape($Texto) + '"'
    $m = [regex]::Match($xml, $pattern2)
}
if (-not $m.Success) { Write-Output "NO ENCONTRADO: $Texto"; exit 1 }

$x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
$y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
& $adb shell input tap $x $y
Write-Output ("tap '{0}' en ({1},{2})" -f $Texto, $x, $y)
Start-Sleep -Milliseconds 900
