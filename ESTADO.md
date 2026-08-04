# ESTADO — dónde nos quedamos

> Archivo de traspaso. Si abres un chat nuevo, **lee esto primero**: contiene
> el estado real del proyecto, las decisiones ya tomadas y lo que estaba a
> medias. Última actualización: **4 de agosto de 2026, versión 1.7.0**.

---

## 1. Qué es esto

**Chispa** — app Android para aprender inglés de cero (A1) a maestría (C2).
Gratis, sin anuncios, sin compras y **sin permiso de INTERNET** (la app es
técnicamente incapaz de conectarse; esa es la garantía de privacidad).

- **Repositorio**: https://github.com/Dace077/chispa-app (público)
- **Descarga**: https://github.com/Dace077/chispa-app/releases/download/v1.0.0/chispa-1.0.0.apk
- **Versión publicada**: 1.7.0 (versionCode 11)
- **Tamaño**: 1,97 MB
- **Cuenta GitHub del usuario**: `Dace077` (sesión de `gh` ya iniciada en la máquina)

⚠️ **El archivo del release se sigue llamando `chispa-1.0.0.apk` y la etiqueta
sigue siendo `v1.0.0` a propósito**: así el enlace y el QR que el usuario ya
repartió siguen funcionando. Por dentro es la versión que toque. No renombrar
sin avisarle.

---

## 2. Cómo compilar y publicar

El entorno se instaló desde cero en esta máquina (no había nada):

```
JDK 17    C:\Users\skate\.androidtools\jdk\jdk-17.0.20+8
SDK       C:\Users\skate\.androidtools\sdk       (android-35, build-tools 35.0.0)
Emulador  AVD llamado "chispa_test" (Android 14, google_apis, x86_64)
```

Antes de cualquier comando de Gradle:

```powershell
$env:JAVA_HOME='C:\Users\skate\.androidtools\jdk\jdk-17.0.20+8'
$env:ANDROID_HOME='C:\Users\skate\.androidtools\sdk'
```

Publicar una versión nueva:

1. Subir `versionCode` y `versionName` en `app/build.gradle.kts`
2. `./gradlew testDebugUnitTest :app:assembleRelease`
3. `Copy-Item app\build\outputs\apk\release\app-release.apk chispa-1.0.0.apk -Force`
4. `gh release upload v1.0.0 chispa-1.0.0.apk --repo Dace077/chispa-app --clobber`
5. Verificar descargando el enlace público y comprobando el `versionName`

También existe `PUBLICAR.ps1`, que hace todo esto de golpe.

---

## 3. Estado del contenido

| Nivel | Unidades | Lecciones |
|---|---|---|
| A1 | 4 (incluye la **unidad 0** de alfabeto y lectura) | 15 |
| A2 | 3 | 10 |
| B1 | 3 | 9 |
| B2 | 3 | 10 |
| C1 | 3 | 9 |
| C2 | 3 | 9 |
| Extras | 10 | 31 |

**Total: 29 unidades · 93 lecciones · 922 ejercicios · 961 entradas de vocabulario**

Extras: modismos, slang, business, travel, pronunciación, listening, historias
y notas culturales.

**Biblioteca de lectura (pestaña «Leer»)**: 21 lecturas — A1×4, A2×5, B1×4,
B2×3, C1×3, C2×2. Está en `assets/content/readings.json`.

Seis de ellas son **conversaciones largas** con turnos de habla: cada frase
lleva `speaker` y el lector pinta el nombre y una barra de color cuando cambia
el turno. Algunas frases llevan además `note`, que explica lo que de verdad se
está diciendo («a stretch» significa imposible) y aparece junto a la traducción,
no siempre, porque estorba cuando no hace falta.

⚠️ Al añadirlas se descubrió que las tres conversaciones antiguas metían las
comillas dentro del campo `en`, así que la primera y la última palabra de cada
frase se quedaban pegadas a un `"` y **no se podían consultar tocándolas**.
Están corregidas. No volver a meter comillas en `en`: para eso está `speaker`.

**Guía de gramática** (se entra desde la tarjeta de arriba de «Leer»): 25 temas —
A1×5, A2×5, B1×6, B2×4, C1×3, C2×2. Está en `assets/content/grammar.json`.
Buscable, filtrable por nivel, con audio en los ejemplos y sección de errores
típicos del hispanohablante. El campo `keywords` existe porque nadie encuentra
«A, an y the» escribiendo «artículos»: son los sinónimos por los que se busca.

Todo el contenido vive en `app/src/main/assets/content/*.json`. Para ampliar no
hace falta tocar Kotlin: se crea un archivo y se lista en `index.json`.
`readings.json` y `grammar.json` se cargan aparte y **no** van en el índice.

---

## 4. Decisiones ya tomadas (no volver a plantearlas)

- **Sin Hilt.** Inyección manual con `ServiceLocator`.
- **El contenido nunca se escribe en Kotlin.** Siempre JSON en assets.
- **Un único punto de escritura de progreso**: `ProgressRepository`, con mutex.
- **El camino principal es una secuencia continua** A1→C2: `UnlockRules.buildCorePath`
  arrastra el desbloqueo entre niveles. `CefrLevel.EXTRA` tiene `order = 6` para
  quedar siempre el último; respetar ese invariante si se añade otro nivel.
- **El nombre del APK y la etiqueta del release no cambian** (ver aviso arriba).
- **La keystore no se sube** al repositorio (está en `.gitignore`). Si se pierde,
  los usuarios tendrán que desinstalar para actualizar y perderán su progreso.

---

## 5. Tests

36 tests unitarios, todos en verde. Correr con `./gradlew testDebugUnitTest`.

- `PlacementLadderTest` (8) — la escalera adaptativa del test de nivel, incluida
  una prueba de fuerza bruta sobre las 64 combinaciones posibles de respuestas.
- `UnlockRulesTest` (7) — qué se desbloquea y **cuál se marca como "siguiente"**.
- `LessonPedagogyTest` (9) — que no se pueda examinar de algo que no se ha enseñado.
- `GrammarModelsTest` (12) — qué temas se descartan al cargar y que la búsqueda
  encuentre pese a tildes, mayúsculas y sinónimos.

Además, en `herramientas/` hay tres utilidades **ya dentro del repo**:

```powershell
# Comprueba que ningún ejercicio se descarte en silencio al cargarlo.
powershell -ExecutionPolicy Bypass -File herramientas\validar-contenido.ps1

# Pulsa un elemento del emulador buscándolo por su texto (no por coordenadas).
.\herramientas\pulsar.ps1 'EMPEZAR'

# Genera códigos QR en PNG, sin dependencias ni red.
node herramientas\qr.js "https://..." salida.png
node herramientas\qr.js --test        # 12 comprobaciones contra la norma ISO
```

---

## 6. Errores ya corregidos (contexto para no repetirlos)

1. **Micrófono colgado.** Se exigía `EXTRA_PREFER_OFFLINE`; si falta el paquete
   de idioma inglés el motor no responde nunca (error 13). Ahora se reintenta en
   modo normal y hay un perro guardián de 7-15 s.
2. **Error 11 (`ERROR_SERVER_DISCONNECTED`).** Se creaba y destruía un
   `SpeechRecognizer` en cada intento. Ahora se reutiliza una sola instancia.
3. **Se examinaba de palabras nunca enseñadas.** `loadLessonExercises` devolvía
   solo `lesson.exercises`; la lista `vocab` solo alimentaba el repaso espaciado
   y no se mostraba jamás. Ahora `LessonPedagogy` antepone una tarjeta de
   vocabulario y ordena los ejercicios de menor a mayor exigencia.
4. **Al colocar en un nivel alto se marcaba A1 como "siguiente lección".**
   Corregido en `UnlockRules`: los niveles por debajo quedan jugables pero no
   destacados.

---

## 7. Lo que estaba a medias

### 🔴 Pregunta sin responder por el usuario

Al terminar la 1.5.0 le pregunté y **no llegó a contestar**:

> ¿Sigo con las descargas por internet, o prefieres que primero llene la
> biblioteca con más lecturas de cada nivel?

En 1.6.0 se hizo la mitad que no necesitaba su permiso (llenar la biblioteca y
montar la gramática). **Lo de internet sigue sin empezar y sigue necesitando
que él lo confirme**, porque obliga a añadir un permiso que hoy la app presume
de no tener.

### Descargas por internet — decidido pero SIN EMPEZAR

El usuario eligió **«añadir internet opcional»** cuando se le plantearon las
cuatro funciones nuevas. No se ha tocado ni una línea: la app sigue sin el
permiso.

Cuando se haga, el plan propuesto (y aceptado de palabra) era:

- La app sigue funcionando entera sin conexión.
- La red se usa **solo** para descargar lecturas nuevas.
- Desactivado por defecto, con interruptor explícito.
- Hay que reescribir el "Acerca de" de Configuración, el README y las notas del
  release: hoy los tres dicen que la app no puede conectarse.

⚠️ Aviso que ya se le dio: **noticias y podcasts reales tienen derechos de autor
y no se pueden redistribuir**. Lo viable es servir contenido propio.

### Las cuatro funciones que pidió: estado

| Función | Estado |
|---|---|
| Lectura bilingüe con audio sincronizado | ✅ **Hecha** en 1.5.0 |
| Aprender leyendo contenido real | ⚠️ A medias: 21 lecturas graduadas propias (1.7.0). Noticias y podcasts reales siguen descartados por derechos y por tamaño |
| Explica gramática y conversaciones reales | ✅ **Hecha**. Gramática en 1.6.0 (25 temas consultables) y 6 conversaciones largas con turnos y notas de registro en 1.7.0 |
| Vídeos de personas reales | ❌ **Descartado y explicado**. Un minuto de vídeo pesa 3-5 veces más que toda la app. Alternativa propuesta: módulo de *shadowing* con voz a velocidad natural, contracciones habladas y acentos |

---

## 8. Ideas pendientes de menor tamaño

- **Repetir el test de nivel** sin reiniciar el progreso (hoy solo sale la
  primera vez). Se le ofreció y no contestó.
- Enlazar cada lección con su tema de gramática: hoy la guía solo se alcanza
  desde «Leer», y el momento en que hace falta es dentro de una lección.
- Más lecturas por nivel: hay 2-3 de cada, da para muchas más.
- Módulo de inglés para exámenes (IELTS/TOEFL) o inglés con canciones.
- **Migraciones de Room reales.** Hoy está en `fallbackToDestructiveMigration`,
  que borra el progreso. Antes de publicar un cambio de esquema, escribir la
  migración.
- Exportar/importar progreso para cambiar de móvil.

---

## 9. Cómo trabaja el usuario

- Escribe en español y espera respuestas en español.
- Va rápido y con energía; prefiere que se actúe a que se le pregunte de más.
  Pero **agradece que se le avise de los costes reales antes de romper algo**:
  las dos veces que se le explicó un límite técnico con números, lo entendió y
  decidió bien.
- Detecta problemas de fondo, no cosméticos. La crítica de que la app "no servía
  partiendo de cero" era correcta y llevó a los dos mejores cambios del proyecto.
- Su Google Drive está lleno (30,6 GB de 15), lo que además le bloquea Gmail.
  Se le avisó. Por eso se publicó en GitHub y no en Drive.

## 10. Cómo verificar de verdad

Nada se da por bueno sin comprobarlo en el emulador. El patrón que funcionó:

```powershell
# arrancar
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd chispa_test -no-snapshot -no-boot-anim -gpu swiftshader_indirect

# instalar y lanzar
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.chispa.ingles.debug/com.chispa.ingles.MainActivity
```

Para pulsar elementos **buscarlos por texto** en vez de usar coordenadas fijas:
los enunciados cambian de alto y las coordenadas fallan a la tercera pantalla.
Para eso está `herramientas\pulsar.ps1`.

El emulador no tiene micrófono real: la captura de voz solo se puede validar en
un móvil de verdad.
