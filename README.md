# ✨ Chispa — Aprende inglés sin excusas

App Android nativa para aprender inglés de cero (A1) a intermedio alto (B2).
**100% gratis, 100% offline, sin anuncios, sin compras y sin recoger un solo dato tuyo.**

> La app ni siquiera declara el permiso `android.permission.INTERNET`: es
> técnicamente **incapaz** de conectarse a la red. Esa es la garantía de privacidad,
> no una promesa.

---

## 📦 Descargar e instalar (para usuarios)

1. Descarga el archivo **`chispa-1.0.0.apk`** en tu móvil Android.
2. Al abrirlo, Android te dirá que no puede instalar apps de origen desconocido.
   Pulsa **Configuración** y activa **Permitir de esta fuente**.
3. Vuelve atrás y pulsa **Instalar**.
4. Listo. Puedes desactivar de nuevo esa opción si quieres.

**Requisitos:** Android 7.0 (API 24) o superior. Ocupa menos de 2 MB.

### Para que la voz funcione

La app usa el motor de voz que ya trae tu Android. Si no oyes nada:

- **Ajustes → Sistema → Idiomas → Salida de texto a voz** → instala los datos de voz en inglés.
- Para el micrófono, en Android 12+ puedes descargar el reconocimiento de voz sin
  conexión desde los ajustes de teclado de Google (Voz → Idiomas sin conexión → English).

---

## 🎯 Qué incluye

### Camino principal (A1 → B2)
| Nivel | Contenido |
|---|---|
| **A1** | Alfabeto y saludos, presentarse, números, colores, familia, verbo *to be*, artículos y plurales, comida, la casa, presente simple, días y meses |
| **A2** | Pasado simple regular e irregular, preguntas en pasado, futuro con *going to*, comparativos y superlativos, preposiciones, direcciones, compras, clima, *can/must* |
| **B1** | Presente perfecto, narrar experiencias, *phrasal verbs*, condicionales 0 y 1, voz pasiva, expresar opiniones, trabajo, salud, viajes |
| **B2** | Condicionales 2 y 3, *wish*, estilo indirecto, registro formal e informal, argumentación, sinónimos con matiz, *phrasal verbs* avanzados, gerundio vs infinitivo, oraciones de relativo, subjuntivo e inversión |

### Módulos extra (el diferenciador)
- **🎣 Modismos** — más de 60 expresiones idiomáticas con contexto real
- **😎 Slang y conversación** — cómo se habla de verdad, siglas de internet, reacciones
- **💼 Business English** — correos, reuniones, presentaciones y negociación
- **🧳 Travel English** — aeropuerto, hotel, restaurante y emergencias
- **🗣️ Pronunciación enfocada** — /θ/, /ð/, vocales cortas vs largas, *schwa*, terminaciones *-ed*
- **🎧 Listening real** — diálogos naturales con acentos americano, británico y australiano
- **📖 Historias progresivas** — relatos que crecen contigo y reutilizan lo aprendido
- **🌍 Notas culturales** — costumbres, cortesía y malentendidos frecuentes

**Total: 22 unidades · 71 lecciones · 702 ejercicios · ~740 palabras y frases.**

### Mecánicas
- **7 tipos de ejercicio**: opción múltiple, traducir, escuchar y escribir, ordenar palabras,
  hablar y repetir, unir parejas y rellenar huecos. Más tarjetas de gramática,
  lecturas y notas culturales.
- **Gamificación**: XP, racha diaria con comodines, corazones que se regeneran,
  meta diaria configurable, 36 logros y 11 rangos.
- **Repetición espaciada** (Leitner de 6 cajas): cada palabra reaparece justo antes
  de que la olvides — 10 min → 1 día → 3 → 7 → 14 → 30.
- **Liga personal**: compites contra tu propia semana anterior, sin rivales inventados.
- **Recordatorios locales** con más de 70 mensajes distintos que nunca se repiten seguidos.

---

## 🛠 Compilar desde el código (para desarrolladores)

### Requisitos
- JDK 17
- Android SDK con `platforms;android-35` y `build-tools;35.0.0`

### Compilar

```bash
./gradlew assembleRelease
```

El APK firmado aparece en `app/build/outputs/apk/release/app-release.apk`.

### Firma

El repositorio incluye `chispa-release.jks` (autofirmado, válido ~27 años) y
`keystore.properties`. Se generó con:

```bash
keytool -genkeypair -v -keystore chispa-release.jks -alias chispa -keyalg RSA -keysize 2048 -validity 10000 -storepass chispa2026 -keypass chispa2026 -dname "CN=Chispa App, OU=Educacion, O=Chispa, L=Ciudad, S=Estado, C=MX"
```

> Si vas a distribuir la app públicamente, **genera tu propia keystore** y guárdala
> fuera del repositorio. Con una firma autofirmada el APK se instala perfectamente
> por sideload; solo Google Play exigiría una firma gestionada.

Si borras `keystore.properties`, el build de release cae automáticamente a la firma
de debug y `assembleRelease` sigue funcionando.

### Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Repository · Room · WorkManager ·
DataStore · Navigation Compose · kotlinx.serialization · TextToSpeech y
SpeechRecognizer nativos.

Sin Hilt (inyección manual vía `ServiceLocator`), sin librerías de pago, sin claves
de API, sin backend.

---

## ➕ Ampliar el contenido

Todo el currículo vive en `app/src/main/assets/content/` como JSON. Para añadir
material **no hay que tocar una línea de Kotlin**:

1. Crea un archivo nuevo, por ejemplo `extra_music.json`.
2. Añádelo a la lista de `content/index.json`.
3. Recompila.

Estructura mínima:

```json
{
  "id": "extra_music",
  "title": "Inglés con canciones",
  "category": "extra",
  "icon": "listening",
  "unlockXp": 200,
  "description": "...",
  "units": [{
    "id": "music_u1",
    "level": "Extra",
    "title": "Unidad 1",
    "subtitle": "...",
    "lessons": [{
      "id": "music_l1",
      "title": "Lección 1",
      "vocab": [{ "en": "lyrics", "es": "letra", "ipa": "/ˈlɪrɪks/" }],
      "exercises": [
        { "type": "multiple_choice", "direction": "en_es", "prompt": "...", "options": ["a","b"], "answer": "a" }
      ]
    }]
  }]
}
```

Tipos de ejercicio disponibles: `multiple_choice`, `translate`, `listen_and_type`,
`word_order`, `speak_and_repeat`, `matching_pairs`, `fill_in_blank`, `tip`,
`reading`, `culture_note`.

Los mensajes de las notificaciones están en `app/src/main/res/values/motivation.xml`,
también editables sin tocar código.

---

## 📄 Licencia y originalidad

Todo el contenido —textos, ejercicios, explicaciones, mascota, paleta y nombre— es
original. La app replica **mecánicas de gamificación** (rachas, XP, árbol de
lecciones), que no son propiedad de nadie, pero no reutiliza ningún elemento
visual, textual ni de marca de otras aplicaciones.

**Chispa** es un colibrí dibujado íntegramente con Compose Canvas: no hay un solo
archivo de imagen de la mascota en el proyecto.
