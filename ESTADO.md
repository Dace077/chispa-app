# ESTADO — dónde nos quedamos

> Archivo de traspaso. Si abres un chat nuevo, **lee esto primero**: contiene
> el estado real del proyecto, las decisiones ya tomadas y lo que estaba a
> medias. Última actualización: **17 de agosto de 2026, versión 1.8.0**.

---

## 1. Qué es esto

**Chispa** — app Android para aprender inglés de cero (A1) a maestría (C2).
Gratis, sin anuncios, sin compras y **sin permiso de INTERNET** (la app es
técnicamente incapaz de conectarse; esa es la garantía de privacidad).

- **Repositorio**: https://github.com/Dace077/chispa-app (público)
- **Distribución**: **solo Google Play**. El release de GitHub y su APK se
  retiraron el 6 de agosto de 2026 (tenían 0 descargas, nadie se quedó tirado).
- **Versión**: 1.7.4 (versionCode 15) — **todavía sin publicar en Play**
- **Tamaño**: 1,97 MB de APK · 4,39 MB de bundle
- **Cuenta GitHub del usuario**: `Dace077` (sesión de `gh` ya iniciada en la máquina)

⚠️ **El repositorio ya NO distribuye la app.** No volver a crear un release con
un APK: se retiró a propósito para que exista una sola vía de actualización.
Lo único que Play necesita de GitHub es la **política de privacidad** en
`docs/privacidad.html`, publicada vía GitHub Pages. Si se borra el repositorio
o se hace privado, esa URL muere y **la ficha de Play se queda sin política de
privacidad**, que es motivo de retirada.

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

Publicar una versión nueva (**solo a Google Play**):

1. Subir `versionCode` y `versionName` en `app/build.gradle.kts`.
   El `versionCode` tiene que ser **mayor** que el de la última subida o Play
   la rechaza.
2. `./gradlew testDebugUnitTest :app:bundleRelease`
3. Subir `app/build/outputs/bundle/release/app-release.aab` a Play Console

Play no acepta APK para apps nuevas: se sube el `.aab`. El paso a paso completo,
con los formularios ya respondidos, está en [play/PLAYSTORE.md](play/PLAYSTORE.md).

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

---

## 11. Sesión del 17 de agosto de 2026

Versión en `app/build.gradle.kts`: **1.8.0 (versionCode 18)**, sin publicar.

### Lo que se añadió

| Bloque | Dónde |
|---|---|
| Room v2 con migración real (`MIGRATION_1_2`) — se quitó `fallbackToDestructiveMigration` | `data/db/Migrations.kt` |
| Ficha del alumno y certificado PDF por nivel | `ui/profile/StudentDataScreen.kt`, `certificates/` |
| 7 avatares dibujados en Canvas, uno por nivel | `domain/Avatars.kt`, `ui/components/AvatarArt.kt` |
| Repetir el test de nivel sin perder progreso (**el nivel solo sube**) | `ProgressRepository.retakePlacement` |
| Cada lección enlaza con su tema de gramática | `grammarTopicId` en los `*_core.json` |
| Estadísticas por tipo de ejercicio, exportar/importar progreso, widget de racha | `domain/ExerciseStats.kt`, `data/backup/`, `widget/` |
| Biblioteca de lecturas: 21 → **42** (7 por nivel) | `readings.json` |
| Módulo TOEFL ITP: 10 temas de apoyo y **5 simulacros de 140 preguntas** | `toefl.json`, `toefl_examen_0{1..5}.json` |
| Logo nuevo: colibrí blanco con letrero «Speak English» | `herramientas/generar-iconos.py` |

### Los simulacros TOEFL

Cinco exámenes completos, 700 preguntas **originales** (las de ETS son propiedad
suya y no se pueden reproducir). Cada uno: 50 Listening + 40 Structure + 50
Reading, igual que el examen real.

Dos cosas que costaron y conviene no repetir:

- **El reparto de letras.** Al escribir, la opción correcta va siempre la
  primera; si se deja así, el examen se aprueba marcando siempre A. El primer
  simulacro salió con el 69% de respuestas en B antes de detectarlo. Se reparte
  con una rotación determinista y `validar-simulacros.py` lo comprueba.
- **Las de `error_id` no se pueden rotar**, porque sus opciones son los
  segmentos (A)(B)(C)(D) de la frase en su orden. Ahí el equilibrio se consigue
  **re-segmentando la frase** para que el trozo con el error caiga en la letra
  que toca, no reescribiéndola.

### El español del curso era de España

El contenido estaba escrito en español peninsular y eso, con un alumno mexicano,
no es un matiz de estilo: «voy a coger un café» y «chaqueta» son vulgares en
México. Se corrigieron 113 sustituciones (coger→tomar/llevar, chaqueta→chamarra,
ordenador→computadora, fontanero→plomero, camarero→mesero, ascensor→elevador,
billete→boleto, céntimo→centavo, conducir→manejar, costes→costos, «vale»→«está
bien»…). Queda como red permanente: `herramientas/neutralizar-espanolismos.py`,
idempotente, pásalo tras escribir contenido nuevo.

### Interfaz

Se recorrió pantalla por pantalla **en el emulador**, mirando capturas, no a
ciegas. Lo que se arregló:

- **Aprender**: cabían dos lecciones donde caben cuatro; «60 / 20 XP» al superar
  la meta parecía un error y ahora dice «60 XP hoy».
- **Repaso**: media pantalla en blanco, porque «las que más se te resisten» solo
  aparece si ya fallaste algo — justo lo que no ha pasado al principio. Ahora cae
  a `weakestCards()` y el título cambia a «lo que llevas menos asentado».
- **Leer**: con 42 lecturas no había forma de saber cuáles ya leíste. Hay visto
  verde y «3 de 7 leídas»; se marca cuando la última frase lleva 1,5 s en
  pantalla (abrir y salir no cuenta).
- **Lección**: el contenido cortado a media tarjeta parecía el final de la
  pantalla. Ahora hay un desvanecido cuando queda algo por debajo.

### El simulacro guarda y se puede revisar (esquema v3)

Dos agujeros que se vieron al repasar el módulo terminado:

- **Las 700 explicaciones no las veía nadie.** El modelo las cargaba y ninguna
  pantalla las mostraba: al terminar solo salían las puntuaciones. Ahora hay una
  pantalla de revisión, filtrada por defecto a las falladas, con lo que marcaste,
  la buena, el porqué y —desplegable— el guion del Listening o el texto del
  Reading. Se entra al terminar y también desde la portada del simulacro, así que
  las explicaciones siguen ahí semanas después.
- **Un examen de 115 minutos se perdía entero** si Android mataba el proceso: la
  fila solo se escribía al terminar. Ahora se guarda en cada respuesta y cada 15
  segundos de reloj, y al volver a abrirlo ofrece retomarlo en el punto exacto.

Eso llevó la base a la **v3** (`MIGRATION_2_3`: `answers`, `played`,
`sectionIndex`, `questionIndex`, `secondsLeft` en `exam_attempt`).
`verificar-migracion.py` ahora recorre la cadena entera desde la v1, no solo la
1→2, y fue quien cazó que faltaban los `@ColumnInfo(defaultValue = ...)`.

La serialización vive en `domain/ExamProgress.kt` y no dentro del ViewModel a
propósito: si al descodificar se pierde una respuesta, el alumno la ve en blanco
y no hay forma de que lo note. Tiene 9 tests.

### Voces, letra grande e informe del simulacro

- **Dos voces en los diálogos.** El motor de Android trae una sola voz, así que
  las conversaciones del Listening se leían del tirón con una pausa entre turnos
  — y media sección pregunta *what does the **woman** mean*. Ahora cada personaje
  suena con un tono distinto (`domain/DialoguePitch.kt`, 10 tests): «Man» grave,
  «Woman» agudo, y los roles sin género («Student», «Librarian») se reparten los
  tonos libres por orden de aparición. Ojo con el orden de las comprobaciones:
  «woman» contiene «man», así que al revés toda mujer sonaría grave. Se aplica
  también al lector de lecturas, que tiene diálogos.

- **La app aguanta la letra del sistema al 200%.** Lo que estaba roto y ya no:
  «Aprender» se partía en dos líneas y se salía de la barra inferior (ahora el
  escalado se limita **solo en la barra**, que es alto fijo; el contenido sigue
  creciendo entero); «Ver todo» se partía en cuatro líneas de una letra; la
  tabla de secciones del simulacro cortaba «Comprensi/ón» y ahora se apila; y la
  letra de las opciones del examen tenía ancho fijo en dp, así que a tamaño
  grande se cortaba por la mitad.

- **Informe del simulacro en PDF.** Vertical, sobrio, con el puntaje, el detalle
  por sección y un bloque final titulado «qué es y qué no es este documento».
  **Es un informe, no un certificado, y no se llama así en ninguna parte**: un
  simulacro no acredita nada y el puntaje es nuestra estimación, no la de ETS.
  Por eso se ve deliberadamente distinto de la constancia de nivel — sin sello
  ni firma — y el aviso va dentro del propio PDF, que es lo que acaba reenviado
  por WhatsApp sin contexto alrededor.

De paso, un fallo que solo se vio al mirar el PDF impreso: `ToeflItp.resumen`
hacía `lowercase()` a toda la descripción del umbral y se llevaba por delante la
mayúscula de después del punto («intermedio. muchas licenciaturas»). Corregido
con test de regresión.

### Doce lecciones nuevas en A1 y A2

El curso tenía 62 lecciones para cubrir A1→C2 y los huecos estaban en la base,
que es justo donde está casi todo el mundo. Faltaban cosas que no son un extra:

| Nivel | Lo que no se enseñaba |
|---|---|
| A1 | Números por encima de 20, posesivos (my/his/her), **there is / there are**, presente continuo, la hora y las preguntas con WH |
| A2 | Futuro con **will** (solo estaba «going to»), pasado continuo, adverbios de frecuencia, some/any/much/many, dar consejos con «should» y el restaurante entero |

Un caso llamativo: `g_there_is` ya existía en la guía de gramática, con su
explicación escrita, y **ninguna lección lo enseñaba**. Lo mismo con los
posesivos, que aparecían en las frases de ejemplo desde A1 sin haberse
presentado nunca.

Después se hizo lo mismo con **B1**, que se había quedado en nueve lecciones y
sin media narración: faltaban el pasado perfecto (sin él no se puede decir que
algo pasó antes que otra cosa), «used to», los modales de obligación
(don't have to y mustn't son opuestos y en español suenan igual), los de
deducción, los conectores y las preposiciones que van pegadas al verbo. Y una
de B2 para cerrar `g_uncountable`.

| Nivel | Antes | Ahora |
|---|---|---|
| A1 | 15 | **21** |
| A2 | 10 | **16** |
| B1 | 9 | **15** |
| B2 | 10 | **11** |

El curso pasa de 922 a **1112 ejercicios**. Todo se insertó con
`herramientas/formato_contenido.py`, que escribe solo el bloque nuevo y deja
intacto lo demás: en A1 el diff fueron 588 líneas añadidas y 2 tocadas (las
comas de empalme).

**Un bug que se coló y ahora se caza solo.** Al etiquetar dos temas de gramática
se escribió un segundo `grammarTopicId` en lecciones que ya tenían uno. `json`
se queda con el último y no dice nada: ni el parseo ni el validador fallaban, y
la etiqueta simplemente no existía. Se revirtió, y `auditar-ejercicios.py`
detecta ahora las **claves repetidas** y las marca como GRAVE. Comprobado
inyectando un duplicado a propósito: lo encuentra.

### El test de nivel estaba roto (y no se notaba)

Se probó por primera vez el arranque desde cero, como usuario nuevo, y salieron
tres cosas:

1. **Pulsando siempre la primera opción, el test colocaba en C2.** La respuesta
   correcta estaba en la posición A en 12 de las 18 preguntas y en la D en
   ninguna; en B2, C1 y C2 estaban todas en la primera salvo una. Comprobado en
   el emulador: se llegaba a «Nivel C2» sin saber nada. Y no tiene arreglo desde
   dentro, porque `retakePlacement` solo deja **subir** de nivel a propósito, así
   que quien queda mal colocado arriba se queda ahí. Ahora el reparto es
   5/4/5/4 y las dos comprobaciones dan lo que deben: siempre-A → A1,
   todo bien → C2.

2. **Diez preguntas llevaban una pista que decía la respuesta.** «Elige la forma
   correcta: I ___ in London since 2019» con la pista *Presente perfecto con
   "since"*. Un test mide; enseñar es cosa de las lecciones. Quitadas.

3. **El permiso de notificaciones saltaba encima de la primera pregunta**, porque
   se pedía en el mismo momento de navegar al test. Ahora se pide al salir del
   test, ya en la pantalla principal.

`auditar-ejercicios.py` vigila desde ahora el reparto de respuestas del test y
marca como GRAVE que una posición doble a la media o que alguna no salga nunca.
Comprobado devolviendo el archivo viejo: lo caza.

### Chispa Kids (2 a 5 años)

Etapa nueva para prelectores. Se investigaron las apps que funcionan a esa edad
(Studycat, Lingokids, Duolingo ABC, Papumba) y todas coinciden en lo mismo:
audio primero, dibujo como enunciado, un toque grande, y **nada de cronómetros**
— Studycat los quitó de sus juegos a propósito.

Doce mundos y **220 palabras**: Letras (26), Animales (40), Comida (25), Mi
casa (20), Familia (15), Mi cuerpo (15), Ropa (15), Transporte (15), Afuera
(15), Colores (14), Formas (10) y Números (10).

Sobre el «mínimo 50 por mundo» que se pidió: en Colores y Formas no se llega, y
es a propósito. A un niño de tres años se le enseñan los colores que puede
nombrar, no cincuenta matices; «turquesa» y «cian» no le suman nada y le quitan
claridad al juego. Donde el volumen sí ayuda —animales, comida, casa— sí se
subió de verdad.
Dos modos por mundo: **Oír** (tocas y suena, sin acierto ni error) y **Jugar**
(suena una palabra, tocas el dibujo; 6 rondas y a celebrar).

Decisiones que conviene no deshacer:

- **Ni una instrucción escrita.** Los textos que se ven son para el adulto que
  acompaña. Si se borraran todos, la pantalla seguiría siendo jugable.
- **Sin corazones, sin reloj y sin restar.** Fallar sacude el dibujo y repite la
  palabra, nada más.
- **No toca el progreso del curso.** Ni racha, ni XP, ni tarjetas de repaso.
- **Cero dibujos nuevos en disco**: los animales son los avatares que ya
  existían y el resto es Canvas. La etapa entera no añade ni un kilobyte de
  imagen al APK.
- La voz va más lenta que en el curso de adultos (rate 0.75): es la primera vez
  que el niño oye ese sonido y lo va a imitar.
- **El abecedario no dice solo la letra.** «B» a secas hace que el motor de voz
  suelte «bi» y punto; lo que enseña el sonido es la palabra detrás. Por eso el
  modelo tiene un campo `say` aparte de `en`: la tarjeta muestra **B** y la voz
  dice «B. Ball.». Las letras van en mayúscula porque es la que se aprende
  primero: la de los cubos y la del nombre propio.

Dos animales hubo que rehacerlos después de verlos en pantalla, que es la única
forma de saberlo: la **vaca** tenía las manchas justo donde van los ojos y
parecía tener cuatro, y la **abeja** era un bloque de rayas sin cara, con las
alas blancas invisibles sobre fondo blanco.

Se probó en el emulador: los cuatro mundos, los dibujos de las cinco formas, los
puntos de contar, y el juego con su acierto en verde. La rejilla del juego se
centró en pantalla porque al principio quedaba pegada arriba, fuera del alcance
del pulgar de un niño.

### Puerta de entrada: Chispa o Chispa Kids

Al abrir la app ya no se entra directo al curso: sale una pantalla que pregunta
quién va a practicar, con dos puertas. La del adulto es una fila normal con la
mascota; la del niño ocupa el doble, lleva tres animales y un botón «Jugar»
enorme — si el niño llega solo a esa pantalla tiene que poder reconocer la suya
sin leer.

**No recuerda la última elección, y es deliberado.** Si recordara, el niño se
encontraría el curso de adultos al abrir y el adulto tendría que salir del modo
infantil cada mañana. Un toque de más al abrir vale menos que equivocarse de
persona.

La X de Chispa Kids devuelve a esa puerta y no al curso: si el niño la toca sin
querer, no acaba en el examen TOEFL de su papá. El atajo del Perfil se
mantiene para cambiar de modo sin cerrar la app, y ese sí vuelve al Perfil.

### Hablar: de 29 ejemplos sueltos a 134 frases

La sección tenía nueve sonidos con tres o cuatro ejemplos cada uno. Eso afina la
boca —la diferencia entre *ship* y *sheep*— pero no enseña a hablar: nadie sale
a la calle a decir vocales.

Ahora hay **134 frases en 12 categorías** (`speaking.json`): saludos,
presentarte, aeropuerto, hotel, restaurante, compras, direcciones, trabajo y
entrevista, teléfono, médico, conversación y qué decir cuando no entiendes.
Cada una con su traducción y, donde hace falta, el apunte de por qué se dice
así: «I'd like» en vez de «I want», «this is Ana» al teléfono y no «I am Ana».

Van plegadas y solo una abierta a la vez: 134 frases desplegadas de golpe son
un muro que nadie recorre.

### Lo que sigue pendiente

- Publicar la 1.8.0 en Play (subir `versionCode`, `bundleRelease`).
- Los avatares solo se han visto en emulador; la voz sigue sin validarse en un
  móvil real.
