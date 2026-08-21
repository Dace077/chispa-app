# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> El repositorio está íntegramente en español (código, comentarios, docs) y el
> usuario trabaja en español. Responde en español.

## Qué es

**Chispa** (`com.chispa.ingles`) — app Android nativa para aprender inglés de A1 a C2.
Gratis, sin anuncios, sin backend y **sin permiso de `INTERNET`**: la app es
técnicamente incapaz de conectarse, y eso está declarado en la ficha de Google Play
y en la política de privacidad. Ver [Invariantes](#invariantes-del-proyecto).

## Comandos

Antes de cualquier comando de Gradle en esta máquina:

```bash
$env:JAVA_HOME='C:\Users\skate\.androidtools\jdk\jdk-17.0.20+8'; $env:ANDROID_HOME='C:\Users\skate\.androidtools\sdk'
```

| Tarea | Comando |
|---|---|
| Tests unitarios (JVM, sin emulador) | `./gradlew testDebugUnitTest` |
| Un solo test | `./gradlew testDebugUnitTest --tests "com.chispa.ingles.domain.UnlockRulesTest"` |
| Un solo método | `./gradlew testDebugUnitTest --tests "*UnlockRulesTest.terminar una leccion abre la siguiente"` |
| APK debug | `./gradlew assembleDebug` |
| APK release firmado | `./gradlew assembleRelease` → `app/build/outputs/apk/release/` |
| Bundle para Play | `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |

No hay linter configurado más allá del compilador de Kotlin y Android Lint por defecto.

### Validar el contenido JSON (obligatorio tras tocar `assets/content/`)

```bash
powershell -ExecutionPolicy Bypass -File herramientas\validar-contenido.ps1
```

Replica las reglas de `ContentRepository.toDomain()`. **Es la única red de seguridad
del contenido**: un ejercicio mal formado no rompe el build ni falla ningún test —
se descarta en silencio con un `Log.e` y desaparece de la lección.

Además hay tres comprobaciones que miran el *sentido* del contenido, no su forma:

| Herramienta | Qué caza |
|---|---|
| `python herramientas\auditar-ejercicios.py` | Enunciado y opciones en idiomas que no cuadran, `direction` que miente, opciones duplicadas. Con `--csv` vuelca todo. |
| `python herramientas\validar-simulacros.py` | Que cada simulacro TOEFL tenga 50/40/50 preguntas, reparto equilibrado de letras y ninguna repetida entre exámenes. |
| `python herramientas\neutralizar-espanolismos.py` | Español peninsular en el curso. **El alumno es mexicano**: "coger un café" y "chaqueta" no son un matiz de estilo. Es idempotente; pásalo tras escribir contenido nuevo. |

### Verificar en emulador

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk; adb shell am start -n com.chispa.ingles.debug/com.chispa.ingles.MainActivity
```

El build debug lleva `applicationIdSuffix = ".debug"`, así que el paquete a lanzar
es `com.chispa.ingles.debug`. Para pulsar elementos, **busca por texto, no por
coordenadas** (los enunciados cambian de alto y las coordenadas fallan a la tercera
pantalla): `.\herramientas\pulsar.ps1 'EMPEZAR'`.

El emulador no tiene micrófono real: la captura de voz solo se valida en un móvil.

## Arquitectura

Kotlin · Compose (Material 3) · MVVM + Repository · Room · WorkManager · DataStore ·
Navigation Compose · kotlinx.serialization · TTS y SpeechRecognizer nativos.
compileSdk/targetSdk 36, minSdk 24, JDK 17.

```
core/          ServiceLocator (DI manual), Time, AppInfo
data/content/  DTOs JSON → modelo de dominio sellado + ContentRepository
data/db/       Room: 5 entidades, 5 DAOs
data/prefs/    SettingsStore (DataStore)
data/repo/     ProgressRepository — único punto de escritura del progreso
domain/        Srs, SrsPairing, Gamification, AnswerChecker, UnlockRules,
               LessonPedagogy, PlacementLadder   ← lógica pura, aquí van los tests
speech/        TtsManager, SpeechRecognizerManager
notifications/ Notifier, 4 workers, scheduler, BootReceiver
ui/            Navigation.kt (Routes) + una carpeta por pantalla
```

### El contenido nunca se escribe en Kotlin

Todo el currículo vive en `app/src/main/assets/content/*.json`. Para ampliar: creas
el archivo y lo listas en `index.json`. Para **añadir lecciones a un nivel que ya
existe** usa `herramientas/formato_contenido.py`:

```python
from formato_contenido import insertar_lecciones
insertar_lecciones("a1_core.json", "a1_u3", [leccion_nueva])
```

Inserta solo el bloque nuevo, con el estilo de la casa, y no toca un byte de lo
que ya había. No reserialices el archivo entero con `json.dumps`: el estilo está
escrito a mano y no sigue una regla mecánica, así que convertirías un cambio de
veinte líneas en un diff de mil. **`readings.json`, `grammar.json`,
`placement.json`, `kids.json` y `speaking.json` se cargan aparte y NO van en el
índice.**

El parseo es deliberadamente tolerante (`ignoreUnknownKeys`, todo opcional salvo lo
imprescindible): un ejercicio inválido devuelve `null` en `ExerciseJson.toDomain()`
y se descarta; un archivo inválido se omite entero. Nunca tumba la app — y nunca
avisa. De ahí `validar-contenido.ps1`.

### Cadena de carga de una lección

`ContentRepository.load()` → `TrackJson.toDomain()` → `LessonJson.toDomain()` →
**`LessonPedagogy.prepare()`**. Ese último paso es donde una lista de ejercicios se
convierte en secuencia didáctica: antepone una tarjeta `VocabIntro` generada desde
el `vocab` de la lección y ordena todo por `rank()` (0 exposición → 9 producción
oral). Es imposible que un ejercicio pida escribir algo que la app no ha enseñado
antes. La bandera `orderedByAuthor: true` en el JSON es la vía de escape.

### Chispa Kids: la etapa de 2 a 5 años

`kids.json` + `domain/Kids.kt` + `ui/kids/`. **La regla que manda: aquí no se
lee.** Un niño de tres años no lee ni en español, así que ninguna instrucción
puede ser texto: el enunciado es un dibujo grande y una voz. Eso deja fuera casi
todo el motor de adultos (`fill_in_blank`, `translate`, `word_order`… todos dan
por hecho que se lee y se escribe).

Lo demás sale de mirar qué hacen Studycat, Lingokids y Duolingo ABC:

- **Sin reloj, sin corazones y sin fallo que penalice.** Un error solo sacude el
  dibujo y repite la palabra. A esa edad el castigo no corrige: enseña a dejar
  de tocar.
- **El audio se repite siempre**, con un botón enorme. Preguntar dos veces no es
  hacer trampa.
- **La posición correcta rota** (`KidsRules.ronda`). Si cayera siempre en el
  mismo sitio se aprendería el sitio, no la palabra — que es exactamente lo que
  le pasaba al test de nivel de los adultos.
- **No escribe nada en el progreso del curso**: ni racha, ni XP, ni SRS. Si un
  niño juega media hora, el adulto no debe encontrarse su repaso lleno de
  palabras que él nunca vio.
- **Los dibujos son Canvas o emoji**, nunca PNG. Los animales de siempre
  reutilizan los avatares y las formas se dibujan en `ui/kids/KidsArt.kt`; para
  el resto (comida, casa, ropa, transporte) se usa `KidsArtKind.EMOJI`, porque
  dibujar a mano doscientos objetos costaría meses y el repertorio de Android ya
  está hecho por ilustradores. Se eligen emoji antiguos, que existan en
  Android 7.
- **Dos palabras del mismo mundo no pueden compartir dibujo.** Si suena
  «sister» y hay dos dibujos idénticos, no hay respuesta posible y el niño no
  tiene texto que se lo aclare. Lo vigila `auditar-ejercicios.py`.

Va a pantalla completa y **sin la barra inferior**, para que un niño no salte
por accidente a los ajustes o al examen TOEFL.

**La puerta de entrada** (`ui/ModePickerScreen.kt`) sale al abrir la app y
pregunta quién va a practicar. No recuerda la elección a propósito: el teléfono
es del adulto, pero quien lo agarra a veces es el niño, y acertar siempre vale
más que ahorrar un toque. La X de Chispa Kids devuelve a esa puerta, nunca al
curso de adultos. Desde Perfil hay además un atajo, que sí vuelve al Perfil.
El modo vive en memoria (`rememberSaveable`), no en disco.

### Desbloqueo: una sola secuencia continua

`UnlockRules.buildCorePath` recorre A1→C2 con un `PathCursor` compartido, así que
terminar la última lección de A1 abre la primera de A2 aunque estén en archivos
distintos. Dos matices que ya costaron un bug:
- El test de nivel abre los niveles **estrictamente por debajo** del asignado, pero
  esos no pueden ser `CURRENT` (a quien se coloca en C2 no se le señala "Saludos").
- `CefrLevel.EXTRA` tiene `order = 6` para quedar siempre el último. Si añades un
  nivel, respeta ese invariante.

### SRS: no adivinar nunca

`SrsPairing.pairFor()` decide si un ejercicio genera tarjeta de repaso y con qué par
en/es. **Una opción múltiple nunca genera tarjeta**: el campo `direction` solo
decide si el enunciado se lee en voz alta y en el contenido real miente a menudo.
Si no consta de dónde sale cada lado, el ejercicio puntúa pero no genera tarjeta.
El vocabulario declarado en `vocab` siempre manda sobre cualquier deducción.

### Escritura de progreso

Toda mutación pasa por `ProgressRepository` bajo un `Mutex`, porque terminar una
lección dispara varias lecturas-modificación-escritura concurrentes (XP, racha,
actividad diaria, SRS, logros) sobre la misma fila de perfil. No escribas en los
DAOs desde fuera del repositorio.

### Otros puntos no obvios

- **Sin Hilt.** `ServiceLocator` con inicialización perezosa. Decisión tomada, no
  volver a plantearla.
- **Fechas siempre por `core/Time`**: la racha depende de días de calendario en la
  zona del usuario, no de bloques de 24 h.
- **`AnswerChecker` es tolerante**: normaliza tildes, puntuación y contracciones, y
  perdona hasta 3 erratas según longitud. Avisa de la errata pero no cuesta corazón.
- **El recordatorio diario se re-encola a sí mismo** tras ejecutarse (en vez de
  trabajo periódico, que acumula desfase).
- Los textos de notificación están en `res/values/motivation.xml`, editables sin
  tocar código.

## Invariantes del proyecto

1. **No añadir `android.permission.INTERNET`** sin pedirlo explícitamente al usuario.
   El manifiesto además elimina `ACCESS_NETWORK_STATE` que inyecta WorkManager. La
   promesa de privacidad es estructural, no textual: si se añade red hay que
   reescribir el "Acerca de" de Configuración, el README y la política de privacidad.
2. **Distribución solo por Google Play.** No crear releases de GitHub con APK: se
   retiraron a propósito para que exista una sola vía de actualización. Lo único que
   el repo aporta a Play es `docs/privacidad.html` vía GitHub Pages — si esa URL
   muere, la ficha se queda sin política de privacidad.
3. **Las migraciones de Room se escriben a mano** (esquema actual: versión 3).
   Se quitó `fallbackToDestructiveMigration`, que borraba la base entera y en
   silencio ante cualquier cambio. Tras tocar una entidad: añade la `Migration`
   en `data/db/Migrations.kt`, súbela a `ALL_MIGRATIONS`, sube `version` en
   `ChispaDatabase` y **compila antes de validar** (Room exporta el esquema
   nuevo a `app/schemas/` al compilar). Después:

   ```bash
   python herramientas\verificar-migracion.py
   ```

   Levanta una base v1 real, aplica toda la cadena y compara columna a columna
   con lo que Room espera. Si una columna nueva lleva `DEFAULT` en el SQL, la
   entidad necesita `@ColumnInfo(defaultValue = ...)` o el esquema no cuadra.
4. **Subir `versionCode` en `app/build.gradle.kts`** en cada publicación, o Play la
   rechaza.
5. **La keystore no se sube** al repositorio (`.gitignore`). Si `keystore.properties`
   no existe, `assembleRelease` cae automáticamente a la firma de debug.
6. **No meter comillas dentro del campo `en`** de las lecturas: rompen la consulta
   de palabras al tocarlas. Para indicar quién habla está `speaker`.

## Documentos de traspaso

- [ESTADO.md](ESTADO.md) — estado real, decisiones cerradas y lo que quedó a medias.
  Léelo antes de tocar nada en una sesión nueva. Sus números de versión pueden ir
  por detrás de `app/build.gradle.kts`, que es la fuente de verdad.
- [PROGRESO.md](PROGRESO.md) — fases del plan original e ideas pendientes.
- [play/PLAYSTORE.md](play/PLAYSTORE.md) — paso a paso de publicación con los
  formularios ya respondidos.
