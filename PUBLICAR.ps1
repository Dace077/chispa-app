<#
    PUBLICAR.ps1 — Sube Chispa a GitHub Releases y genera el QR de descarga.

    Uso:
        .\PUBLICAR.ps1
        .\PUBLICAR.ps1 -Repo mi-nombre-de-repo

    Lo unico que tienes que hacer tu una sola vez es iniciar sesion en GitHub:
        gh auth login
    (elige: GitHub.com -> HTTPS -> autenticar con el navegador)

    A partir de ahi este script hace el resto: crea el repositorio, sube el APK
    como release, obtiene el enlace directo y genera el codigo QR en PNG.
#>

[CmdletBinding()]
param(
    [string]$Repo = 'chispa-app',
    [string]$Tag = 'v1.0.0',
    [string]$Apk = 'chispa-1.0.0.apk',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Paso($n, $texto) { Write-Host "`n[$n] $texto" -ForegroundColor Cyan }
function Bien($texto)     { Write-Host "    $texto" -ForegroundColor Green }
function Aviso($texto)    { Write-Host "    $texto" -ForegroundColor Yellow }
function Malo($texto)     { Write-Host "    $texto" -ForegroundColor Red }

Write-Host ""
Write-Host "  Publicar Chispa" -ForegroundColor Magenta
Write-Host "  ===============" -ForegroundColor Magenta

# ---------------------------------------------------------------- 0. Requisitos
Paso 0 'Comprobando lo que hace falta'

if (-not (Test-Path $Apk)) {
    Malo "No encuentro $Apk en esta carpeta."
    Aviso "Genera el APK primero con:  .\gradlew assembleRelease"
    exit 1
}
$apkMB = [math]::Round((Get-Item $Apk).Length / 1MB, 2)
Bien "APK encontrado: $Apk ($apkMB MB)"

foreach ($cmd in @('git', 'gh', 'node')) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Malo "Falta '$cmd'. Instalalo y vuelve a ejecutar."
        exit 1
    }
}
Bien 'git, gh y node disponibles'

# gh necesita sesion iniciada. Es lo unico que no puede hacer el script por ti:
# son tus credenciales y solo tu debes introducirlas.
$authOk = $true
try { & gh auth status 2>&1 | Out-Null; if ($LASTEXITCODE -ne 0) { $authOk = $false } }
catch { $authOk = $false }

if (-not $authOk) {
    Malo 'No has iniciado sesion en GitHub.'
    Write-Host ""
    Write-Host "    Ejecuta esto una sola vez y vuelve a lanzar el script:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "        gh auth login" -ForegroundColor White
    Write-Host ""
    Write-Host "    Elige: GitHub.com -> HTTPS -> Yes -> Login with a web browser" -ForegroundColor Gray
    Write-Host ""
    exit 1
}
$usuario = (& gh api user --jq .login 2>$null)
Bien "Sesion iniciada como: $usuario"

# ------------------------------------------------------------------- 1. Aviso
Paso 1 'Lo que va a pasar'
Write-Host "    - Se creara el repositorio PUBLICO '$usuario/$Repo' en GitHub" -ForegroundColor Gray
Write-Host "    - Se subira el codigo fuente y el APK como release '$Tag'" -ForegroundColor Gray
Write-Host "    - Cualquiera con el enlace podra descargar la app" -ForegroundColor Gray
Write-Host ""
Write-Host "    El repositorio debe ser publico para que la descarga funcione" -ForegroundColor Gray
Write-Host "    sin pedir cuenta de GitHub a quien reciba el enlace." -ForegroundColor Gray
Write-Host ""

if (-not $Force) {
    $r = Read-Host "    Escribe SI para continuar"
    if ($r -notmatch '^(si|SI|s|S|yes|y)$') { Aviso 'Cancelado. No se ha subido nada.'; exit 0 }
}

# ---------------------------------------------------------- 2. Repo local (git)
Paso 2 'Preparando el repositorio local'

if (-not (Test-Path '.git')) {
    & git init -q
    & git branch -M main
    Bien 'Repositorio git creado'
} else {
    Bien 'Ya habia repositorio git'
}

# La keystore NO debe subirse: quien la tenga puede firmar actualizaciones falsas.
$gi = Get-Content '.gitignore' -Raw -ErrorAction SilentlyContinue
if ($gi -notmatch '(?m)^\*\.jks') {
    Add-Content '.gitignore' "`n# Firma: nunca subir a un repositorio publico`n*.jks`nkeystore.properties`n"
    Aviso 'Anadida la keystore al .gitignore (no se subira, y es lo correcto)'
}
& git rm --cached 'chispa-release.jks' -q 2>$null | Out-Null
& git rm --cached 'keystore.properties' -q 2>$null | Out-Null

& git add -A 2>$null | Out-Null
$pendiente = & git status --porcelain
if ($pendiente) {
    & git -c user.email="$usuario@users.noreply.github.com" -c user.name="$usuario" commit -q -m "Chispa 1.0.0 - app para aprender ingles, 100% offline y gratuita"
    Bien 'Cambios confirmados en git'
} else {
    Bien 'No habia cambios pendientes'
}

# ------------------------------------------------------------ 3. Repo en GitHub
Paso 3 'Creando el repositorio en GitHub'

$existe = $false
try { & gh repo view "$usuario/$Repo" 2>&1 | Out-Null; if ($LASTEXITCODE -eq 0) { $existe = $true } } catch {}

if ($existe) {
    Bien "El repositorio '$usuario/$Repo' ya existia, lo reutilizo"
    $remotos = & git remote
    if ($remotos -notcontains 'origin') { & git remote add origin "https://github.com/$usuario/$Repo.git" }
    & git push -u origin main --force-with-lease 2>&1 | Out-Null
} else {
    & gh repo create $Repo --public --source=. --remote=origin --push `
        --description "Chispa - aprende ingles de A1 a B2. Gratis, sin anuncios y 100% sin conexion." 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Malo 'No se pudo crear el repositorio.'; exit 1 }
    Bien "Repositorio creado: https://github.com/$usuario/$Repo"
}

# --------------------------------------------------------------- 4. El release
Paso 4 "Publicando el release $Tag con el APK"

$notas = @"
## Chispa $Tag

App Android para aprender ingles de cero (A1) a intermedio alto (B2).
**Gratis, sin anuncios, sin compras y 100% sin conexion.**

La app ni siquiera declara el permiso de INTERNET: es tecnicamente incapaz de
conectarse a la red. Puedes comprobarlo en Ajustes > Aplicaciones > Chispa > Permisos.

### Contenido
22 unidades, 71 lecciones, 702 ejercicios y unas 740 palabras y frases.
A1, A2, B1 y B2 completos, mas modulos de modismos, jerga, ingles de negocios,
viajes, pronunciacion, listening con acentos, historias y notas culturales.

### Como instalar
1. Descarga ``$Apk`` desde aqui abajo.
2. Abrelo en tu movil. Android te avisara de que no puede instalar apps de origen
   desconocido: pulsa **Configuracion** y activa **Permitir de esta fuente**.
3. Vuelve atras y pulsa **Instalar**.
4. Si Play Protect avisa, pulsa **Instalar de todos modos**. Sale siempre con apps
   que no vienen de la Play Store.

Requiere Android 7.0 o superior. Ocupa menos de 2 MB.
"@

$releaseExiste = $false
try { & gh release view $Tag --repo "$usuario/$Repo" 2>&1 | Out-Null; if ($LASTEXITCODE -eq 0) { $releaseExiste = $true } } catch {}

if ($releaseExiste) {
    Aviso "El release $Tag ya existia: reemplazo el APK"
    & gh release upload $Tag $Apk --repo "$usuario/$Repo" --clobber 2>&1 | Out-Null
} else {
    $notasFile = Join-Path $env:TEMP 'chispa-notas.md'
    Set-Content -Path $notasFile -Value $notas -Encoding UTF8
    & gh release create $Tag $Apk --repo "$usuario/$Repo" --title "Chispa $Tag" --notes-file $notasFile 2>&1 | Out-Null
    Remove-Item $notasFile -ErrorAction SilentlyContinue
}
if ($LASTEXITCODE -ne 0) { Malo 'Fallo al publicar el release.'; exit 1 }
Bien 'Release publicado'

# ------------------------------------------------------------------- 5. El QR
$url = "https://github.com/$usuario/$Repo/releases/download/$Tag/$Apk"

Paso 5 'Generando el codigo QR'
& node (Join-Path $root 'herramientas\qr.js') $url (Join-Path $root 'chispa-qr.png') --ecl M --scale 12
& node (Join-Path $root 'herramientas\qr.js') $url (Join-Path $root 'chispa-qr-imprimir.png') --ecl Q --scale 24 | Out-Null
Bien 'chispa-qr.png (pantalla) y chispa-qr-imprimir.png (impresion) creados'

# ---------------------------------------------------------------- 6. Resultado
Write-Host ""
Write-Host "  LISTO" -ForegroundColor Green
Write-Host "  =====" -ForegroundColor Green
Write-Host ""
Write-Host "  Enlace de descarga directa:" -ForegroundColor White
Write-Host "  $url" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Pagina del release (para compartir con contexto):" -ForegroundColor White
Write-Host "  https://github.com/$usuario/$Repo/releases/tag/$Tag" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Codigo QR: chispa-qr.png" -ForegroundColor White
Write-Host "  Escanealo con la camara del movil y descargara la app." -ForegroundColor Gray
Write-Host ""

Set-Clipboard -Value $url -ErrorAction SilentlyContinue
Write-Host "  (El enlace ya esta copiado en tu portapapeles)" -ForegroundColor DarkGray
Write-Host ""

Start-Process (Join-Path $root 'chispa-qr.png') -ErrorAction SilentlyContinue
