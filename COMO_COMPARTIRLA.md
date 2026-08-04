# 📲 Cómo compartir Chispa con quien quieras

El APK ya está firmado y listo, en la raíz del proyecto:

```
chispa-1.0.0.apk
```

---

## ⚡ Opción automática — un solo comando

Hay un script que lo hace todo: crea el repositorio en GitHub, sube el APK como
release, obtiene el enlace directo y **genera el código QR en PNG**.

Solo tienes que iniciar sesión en GitHub una vez en tu vida:

```bash
gh auth login
```

Elige **GitHub.com → HTTPS → Yes → Login with a web browser**. Es lo único que no
puede hacer el script por ti: son tus credenciales.

Después, ejecuta:

```bash
powershell -ExecutionPolicy Bypass -File PUBLICAR.ps1
```

Te pedirá confirmación antes de subir nada. Al terminar tendrás:

- El enlace directo de descarga (copiado ya en el portapapeles)
- **`chispa-qr.png`** — el QR para pantalla
- **`chispa-qr-imprimir.png`** — el mismo QR en alta resolución para pegatinas o carteles

El script no sube nunca la keystore: la añade al `.gitignore` automáticamente.

Si prefieres hacerlo a mano, sigue leyendo.

---

## Opción 1 — Google Drive (la más rápida, 2 minutos)

1. Entra en [drive.google.com](https://drive.google.com) y sube `chispa-1.0.0.apk`.
2. Clic derecho sobre el archivo → **Compartir** → **Cambiar a cualquier persona con el enlace**.
3. Asegúrate de que el rol es **Lector**.
4. Copia el enlace. Tendrá esta forma:
   `https://drive.google.com/file/d/ABC123XYZ/view?usp=sharing`

**Truco para descarga directa** (evita la pantalla intermedia de Drive):
coge el `ABC123XYZ` de tu enlace y móntalo así:

```
https://drive.google.com/uc?export=download&id=ABC123XYZ
```

⚠️ Drive muestra un aviso de "no se ha analizado en busca de virus" en archivos
grandes. Es normal con APK; el usuario solo tiene que pulsar **Descargar de todos modos**.

---

## Opción 2 — GitHub Releases (la mejor si quiere llegar a mucha gente)

Es gratis, no caduca, no tiene límite de descargas y el enlace es permanente y limpio.

1. Crea una cuenta en [github.com](https://github.com) si no la tienes.
2. Crea un repositorio nuevo, por ejemplo `chispa-app`. Puede ser público o privado
   (si es privado, los releases no serán accesibles sin cuenta — para repartir la app
   hazlo **público**).
3. Ve a la pestaña **Releases** → **Create a new release**.
4. Pon una etiqueta (`v1.0.0`), un título (`Chispa 1.0.0`) y en la caja de descripción
   pega las instrucciones de instalación.
5. Arrastra `chispa-1.0.0.apk` a la zona de **Attach binaries**.
6. **Publish release**.

Tu enlace directo quedará así:

```
https://github.com/TU-USUARIO/chispa-app/releases/download/v1.0.0/chispa-1.0.0.apk
```

Ese enlace descarga el archivo directamente, sin pantallas intermedias. Es el que
conviene poner en WhatsApp, en una web o en un código QR.

---

## Generar el QR por tu cuenta

Si ya tienes el enlace (de Drive, de GitHub o de donde sea), puedes generar el QR
de dos maneras, ambas **sin conexión**:

**Desde el navegador:** abre `GENERAR-QR.html` con doble clic, pega el enlace y
descarga el PNG.

**Desde la terminal:**

```bash
node herramientas/qr.js "https://tu-enlace-aqui/chispa-1.0.0.apk" chispa-qr.png --scale 12
```

Opciones: `--ecl L|M|Q|H` (resistencia a daños; usa `Q` o `H` si vas a imprimirlo
en algo que se pueda rayar o doblar) y `--scale` (píxeles por módulo).

Para comprobar que el generador funciona bien:

```bash
node herramientas/qr.js --test
```

---

## Opción 3 — Enviarlo por mensajería

- **Telegram**: acepta archivos de hasta 2 GB y no toca el APK. Funciona perfecto.
- **WhatsApp**: permite hasta 100 MB. El APK pesa menos de 2 MB, así que también vale.
- **Correo electrónico**: Gmail **bloquea los archivos .apk** adjuntos. Si quieres
  mandarlo por correo, comprímelo en `.zip` primero o usa un enlace de Drive.

---

## 📱 Lo que tiene que hacer quien lo recibe

Pásale este texto tal cual:

> **Cómo instalar Chispa**
>
> 1. Descarga el archivo `chispa-1.0.0.apk` desde el enlace.
> 2. Abre el archivo descargado (desde la notificación de descarga o desde la app *Archivos* → *Descargas*).
> 3. Android te avisará de que no puede instalar apps de fuentes desconocidas.
>    Pulsa **Configuración** y activa **Permitir de esta fuente**.
> 4. Vuelve atrás y pulsa **Instalar**.
> 5. Si Play Protect muestra un aviso, pulsa **Instalar de todos modos**. Aparece
>    siempre con apps que no vienen de la Play Store; no significa que haya nada malo.
>
> Necesitas Android 7.0 o superior. La app ocupa menos de 2 MB y funciona sin internet.

---

## ❓ Preguntas que te van a hacer

**«¿Es seguro?»**
Sí. La app no pide permiso de internet, así que ni siquiera puede enviar datos a
ningún sitio aunque quisiera. Puedes verificarlo tú mismo: en Android, Ajustes →
Aplicaciones → Chispa → Permisos.

**«¿Por qué avisa Play Protect?»**
Porque el APK no viene de la Play Store y no está firmado por un desarrollador
registrado. Es un aviso automático por procedencia, no una detección de amenaza.

**«¿Se puede subir a la Play Store?»**
Sí, pero requiere una cuenta de Google Play Developer (25 USD, pago único) y pasar
la revisión. Distribuir por enlace es gratis y no necesita nada de eso.

**«¿Cómo actualizo la app?»**
Cuando compiles una versión nueva, sube el `versionCode` y el `versionName` en
`app/build.gradle.kts`, genera el APK y compártelo igual. Android instalará encima
conservando el progreso, siempre que uses **la misma keystore**. Si cambias de
keystore, el usuario tendrá que desinstalar y perderá su racha — guarda bien
`chispa-release.jks`.
