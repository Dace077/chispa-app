# PROGRESO — Chispa

Estado del proyecto y qué queda por hacer. Este archivo es para retomar el trabajo
en otra sesión sin tener que reconstruir el contexto.

---

## ✅ Estado actual: COMPLETO Y COMPILABLE

Las 10 fases del plan original están terminadas. El proyecto compila y genera un
APK firmado sin errores.

| Fase | Estado | Notas |
|---|---|---|
| 1. Setup | ✅ | Gradle KTS, catálogo de versiones, Material 3, tema propio |
| 2. Modelo de datos y Room | ✅ | 5 entidades, 5 DAOs, repositorio único de escritura |
| 3. Contenido | ✅ | 14 archivos JSON, 89 lecciones, 883 ejercicios (A1 → C2) |
| 4. Motor de lecciones | ✅ | 10 tipos de ejercicio, vidas, XP, reintentos |
| 5. Gamificación | ✅ | Rachas, comodines, metas, 36 logros, 11 rangos |
| 6. Repetición espaciada | ✅ | Leitner de 6 cajas, pantalla de repaso priorizada |
| 7. TTS y reconocimiento de voz | ✅ | Nativos, con acentos US/UK/AU y velocidad ajustable |
| 8. Notificaciones | ✅ | 4 workers, 72 mensajes en `motivation.xml` |
| 9. Pulido UI/UX | ✅ | Animaciones, modo oscuro, mascota en Canvas |
| 10. Build y distribución | ✅ | APK firmado + `COMO_COMPARTIRLA.md` |

### Verificaciones hechas
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- `./gradlew assembleRelease` → BUILD SUCCESSFUL, APK de 1,85 MB
- Firma verificada con `apksigner verify --print-certs`
- Manifiesto final **sin `android.permission.INTERNET`** (offline garantizado por construcción)
- Los 12 JSON de contenido parsean correctamente
- **Los 702 ejercicios validados contra las reglas del mapeador**: ninguno se
  descarta en silencio por opciones incoherentes, huecos sin `___` o fichas de
  ordenar palabras que no reconstruyen la respuesta
- Recorrido completo en emulador Android 14: onboarding → test de nivel → home →
  lección entera con los 7 tipos de ejercicio → pantalla de resultado, más las
  4 pestañas, configuración, modo oscuro y persistencia tras reiniciar

---

## 📁 Mapa del proyecto

```
app/src/main/
├── assets/content/          ← TODO el currículo (editable sin tocar código)
│   ├── index.json           ← lista de archivos a cargar
│   ├── placement.json       ← test de nivel inicial
│   ├── a1_core.json … b2_core.json
│   └── extra_*.json         ← idioms, slang, business, travel, pronunciación,
│                               listening, historias, cultura
├── res/values/motivation.xml ← 72 mensajes de notificación y feedback
└── java/com/chispa/ingles/
    ├── core/                 ← ServiceLocator (DI manual), Time
    ├── data/
    │   ├── content/          ← modelos JSON + dominio + repositorio de contenido
    │   ├── db/               ← Room: entidades, DAOs, base de datos
    │   ├── prefs/            ← DataStore de ajustes
    │   └── repo/             ← ProgressRepository (único punto de escritura)
    ├── domain/               ← Srs, Gamification, AnswerChecker, UnlockRules
    ├── speech/               ← TtsManager, SpeechRecognizerManager
    ├── notifications/        ← Notifier, 4 workers, scheduler, BootReceiver
    └── ui/                   ← theme, components, y una carpeta por pantalla
```

---

## 🧠 Decisiones de diseño que conviene conocer

**Sin Hilt.** Se usa un `ServiceLocator` con inicialización perezosa. El grafo de
dependencias es plano (base de datos, ajustes, contenido, progreso, TTS) y no
justificaba sumar un procesador de anotaciones al build. Migrar a Hilt más adelante
sería mecánico.

**El contenido nunca está en Kotlin.** Todo vive en JSON y se mapea a un modelo de
dominio sellado. Un ejercicio mal formado se descarta con un log en vez de tumbar
la app. Así ampliar el currículo no requiere recompilar la lógica.

**Un único punto de escritura.** Toda mutación de progreso pasa por
`ProgressRepository`, protegida por un mutex, porque terminar una lección dispara
varias escrituras concurrentes (XP, racha, actividad diaria, logros) sobre la
misma fila de perfil.

**Corrección tolerante.** `AnswerChecker` normaliza tildes, puntuación y
contracciones, y perdona hasta 3 erratas según la longitud de la respuesta. Se
avisa de la errata pero no cuesta un corazón: el usuario está aprendiendo un
idioma, no mecanografía.

**El camino principal es una única secuencia.** Aunque A1–B2 están en cuatro
archivos, `UnlockRules.buildCorePath` arrastra el estado de desbloqueo entre ellos:
terminar la última lección de A1 es lo que abre la primera de A2.

**Recordatorio diario auto-reprogramado.** En vez de trabajo periódico (que
acumula desfase), el `DailyReminderWorker` se vuelve a encolar a sí mismo tras
ejecutarse. Así la notificación cae siempre a la hora exacta configurada.

---

## 🔜 Ideas para seguir (nada de esto bloquea el uso)

### Contenido
- Añadir un módulo de **inglés con canciones** o **inglés para exámenes** (IELTS/TOEFL).
- Más historias: el módulo tiene 4 y da para muchas más.
- El camino ya llega a **C2**, que es el techo del marco europeo. Por encima no
  hay nivel: lo que queda es ampliar en anchura, no en altura.

### Sobre los niveles C
Se añadieron después del lanzamiento. Detalles a tener en cuenta:
- `CefrLevel` ahora tiene 7 valores; `EXTRA` pasó a `order = 6` para seguir
  ordenándose el último. Si añades otro nivel, respeta ese invariante.
- El test de nivel solo coloca hasta B2, y únicamente con pleno de aciertos.
  Con 8 preguntas no hay señal suficiente para mandar a nadie directo a C1.

### Funcionalidad
- **Exportar/importar progreso** a un archivo JSON, para cambiar de móvil sin
  perder la racha.
- **Widget de pantalla de inicio** con la racha y un acceso rápido a la lección.
- **Modo teclado inglés**: sugerir al usuario cambiar de teclado en ejercicios de
  escritura para practicar la ortografía real.
- **Estadísticas por tipo de ejercicio**: detectar si alguien falla sistemáticamente
  en listening y ofrecerle más.

### Técnico
- Tests unitarios de `AnswerChecker`, `Srs` y `UnlockRules` (son lógica pura y
  fáciles de cubrir).
- Migraciones de Room reales cuando cambie el esquema: ahora está en
  `fallbackToDestructiveMigration`, que borra el progreso. Antes de publicar una v2
  con cambios de esquema, **escribir la migración**.
- Baseline profile para acelerar el arranque.

---

## ⚠️ Antes de distribuir a mucha gente

1. **Genera tu propia keystore** y no la subas a ningún repositorio público. La que
   viene incluida es de ejemplo; si alguien la tiene, puede firmar actualizaciones
   falsas de tu app.
2. **Guarda la keystore a buen recaudo.** Si la pierdes, los usuarios tendrán que
   desinstalar y reinstalar para actualizar, perdiendo todo su progreso.
3. Sube el `versionCode` en cada versión nueva o Android rechazará la actualización.
