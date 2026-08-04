package com.chispa.ingles.data.content

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Carga el currículo completo desde `assets/content/`.
 *
 * El contenido vive en JSON justamente para que ampliarlo no requiera tocar
 * Kotlin: añades un archivo, lo listas en `content/index.json` y listo.
 * Todo el parseo ocurre una sola vez y se cachea en memoria.
 */
class ContentRepository(private val appContext: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val mutex = Mutex()
    @Volatile private var cache: Curriculum? = null

    suspend fun curriculum(): Curriculum {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: load().also { cache = it }
        }
    }

    private suspend fun load(): Curriculum = withContext(Dispatchers.IO) {
        val assets = appContext.assets
        val files = runCatching {
            json.decodeFromString<ContentIndexJson>(
                assets.open("$CONTENT_DIR/index.json").bufferedReader().use { it.readText() }
            ).files
        }.getOrElse { error ->
            Log.e(TAG, "No se pudo leer index.json, se listará el directorio", error)
            (assets.list(CONTENT_DIR) ?: emptyArray())
                .filter { it.endsWith(".json") && it != "index.json" }
                .sorted()
        }

        val tracks = files.mapNotNull { fileName ->
            runCatching {
                val raw = assets.open("$CONTENT_DIR/$fileName").bufferedReader().use { it.readText() }
                json.decodeFromString<TrackJson>(raw).toDomain()
            }.onFailure { error ->
                Log.e(TAG, "Contenido inválido en $fileName — se omite", error)
            }.getOrNull()
        }

        Curriculum(tracks)
    }

    /** Test de nivel inicial. Vive en su propio archivo para poder ajustarlo aparte. */
    suspend fun placementTest(): List<PlacementQuestion> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = appContext.assets.open("$CONTENT_DIR/placement.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString<PlacementTestJson>(raw).questions.map { question ->
                PlacementQuestion(
                    level = CefrLevel.from(question.level),
                    prompt = question.prompt,
                    options = question.options,
                    answer = question.answer,
                    hint = question.hint
                )
            }
        }.getOrElse {
            Log.e(TAG, "No se pudo leer placement.json", it)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ContentRepository"
        const val CONTENT_DIR = "content"
    }
}

/** Vista ya resuelta e indexada de todo el contenido de la app. */
class Curriculum(val tracks: List<Track>) {

    val coreTracks: List<Track> = tracks.filter { !it.isExtra }
    val extraTracks: List<Track> = tracks.filter { it.isExtra }

    /** Todas las unidades del camino principal, en orden pedagógico (A1 → B2). */
    val coreUnits: List<LearningUnit> = coreTracks
        .flatMap { it.units }
        .sortedBy { it.level.order }

    val allUnits: List<LearningUnit> = tracks.flatMap { it.units }

    private val lessonsById: Map<String, Lesson> =
        allUnits.flatMap { it.lessons }.associateBy { it.id }

    private val unitsById: Map<String, LearningUnit> = allUnits.associateBy { it.id }

    private val trackByUnitId: Map<String, Track> =
        tracks.flatMap { track -> track.units.map { it.id to track } }.toMap()

    val allLessons: List<Lesson> = lessonsById.values.toList()

    /** Orden global de lecciones del camino principal: define qué se desbloquea. */
    val coreLessonOrder: List<String> = coreUnits.flatMap { unit -> unit.lessons.map { it.id } }

    fun lesson(id: String): Lesson? = lessonsById[id]
    fun unit(id: String): LearningUnit? = unitsById[id]
    fun trackOfUnit(unitId: String): Track? = trackByUnitId[unitId]
    fun track(id: String): Track? = tracks.firstOrNull { it.id == id }

    /** Todo el vocabulario del currículo, deduplicado por clave SRS. */
    val vocabIndex: Map<String, VocabItem> =
        allLessons.flatMap { it.vocab }.associateBy { it.srsKey }

    val totalLessons: Int = allLessons.size
    val totalExercises: Int = allLessons.sumOf { it.exercises.size }
    val totalVocab: Int = vocabIndex.size
}

/* ---------------------------------------------------------------------------
 *  Mapeo DTO -> dominio
 * ------------------------------------------------------------------------- */

private fun TrackJson.toDomain(): Track {
    val isExtra = category.equals(TrackJson.CATEGORY_EXTRA, ignoreCase = true)
    return Track(
        id = id,
        title = title,
        description = description,
        icon = icon,
        unlockXp = unlockXp,
        isExtra = isExtra,
        units = units.map { it.toDomain(trackId = id) }
    )
}

private fun UnitJson.toDomain(trackId: String): LearningUnit {
    val cefr = CefrLevel.from(level)
    return LearningUnit(
        id = id,
        trackId = trackId,
        level = cefr,
        title = title,
        subtitle = subtitle,
        icon = icon,
        lessons = lessons.map { it.toDomain(unitId = id, trackId = trackId, level = cefr) }
    )
}

private fun LessonJson.toDomain(unitId: String, trackId: String, level: CefrLevel): Lesson =
    Lesson(
        id = id,
        unitId = unitId,
        trackId = trackId,
        level = level,
        title = title,
        kind = when (kind.lowercase()) {
            "story" -> LessonKind.STORY
            "culture" -> LessonKind.CULTURE
            "pronunciation" -> LessonKind.PRONUNCIATION
            else -> LessonKind.LESSON
        },
        vocab = vocab.map { VocabItem(it.en.trim(), it.es.trim(), it.ipa, it.note) },
        exercises = exercises.mapNotNull { it.toDomain() }
    )

private fun ExerciseJson.toDomain(): Exercise? {
    val fallbackKey = (key ?: answer ?: audioText ?: sentence ?: prompt ?: title ?: return null)
        .trim().lowercase()

    return when (type.lowercase()) {
        "multiple_choice" -> {
            val a = answer?.trim() ?: return null
            val opts = options.map { it.trim() }.distinct()
            if (opts.size < 2 || a !in opts) return null
            Exercise.MultipleChoice(
                srsKey = fallbackKey,
                prompt = prompt?.trim().orEmpty(),
                subPrompt = hint?.trim(),
                options = opts,
                answer = a,
                speakPrompt = direction?.startsWith("en") == true
            )
        }

        "translate" -> {
            val a = answer?.trim() ?: return null
            val p = prompt?.trim() ?: return null
            Exercise.Translate(
                srsKey = fallbackKey,
                prompt = p,
                answer = a,
                alternatives = alternatives.map { it.trim() },
                toEnglish = direction?.lowercase() != "en_es",
                hint = hint?.trim()
            )
        }

        "listen_and_type" -> {
            val audio = audioText?.trim() ?: answer?.trim() ?: return null
            Exercise.ListenAndType(
                srsKey = fallbackKey,
                audioText = audio,
                answer = (answer ?: audio).trim(),
                translation = translation?.trim()
            )
        }

        "word_order" -> {
            val a = answer?.trim() ?: return null
            val pool = words.map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { a.split(" ").filter { it.isNotBlank() } }
            if (pool.size < 2) return null
            Exercise.WordOrder(
                srsKey = fallbackKey,
                prompt = prompt?.trim().orEmpty(),
                words = pool,
                answer = a
            )
        }

        "speak_and_repeat" -> {
            val phrase = (prompt ?: answer)?.trim() ?: return null
            Exercise.SpeakAndRepeat(
                srsKey = fallbackKey,
                phrase = phrase,
                translation = translation?.trim()
            )
        }

        "matching_pairs" -> {
            val clean = pairs.filter { it.size >= 2 }.map { it[0].trim() to it[1].trim() }
            if (clean.size < 2) return null
            Exercise.MatchingPairs(
                srsKey = fallbackKey,
                prompt = prompt?.trim().orEmpty().ifEmpty { "Une cada palabra con su traducción" },
                pairs = clean
            )
        }

        "fill_in_blank" -> {
            val s = sentence?.trim() ?: return null
            val a = answer?.trim() ?: return null
            if (!s.contains(Exercise.FillInBlank.BLANK)) return null
            val opts = options.map { it.trim() }.distinct()
            Exercise.FillInBlank(
                srsKey = fallbackKey,
                sentence = s,
                answer = a,
                options = if (opts.size >= 2 && a in opts) opts else emptyList(),
                translation = translation?.trim()
            )
        }

        "tip" -> Exercise.Tip(
            srsKey = fallbackKey,
            title = title?.trim() ?: "Apunta esto",
            body = body?.trim() ?: return null,
            examples = examples.map { VocabItem(it.en.trim(), it.es.trim(), it.ipa, it.note) }
        )

        "reading" -> Exercise.Reading(
            srsKey = fallbackKey,
            title = title?.trim() ?: "Lectura",
            body = body?.trim() ?: return null,
            translation = translation?.trim()
        )

        "culture_note" -> Exercise.CultureNote(
            srsKey = fallbackKey,
            title = title?.trim() ?: "Nota cultural",
            body = body?.trim() ?: return null
        )

        else -> null
    }
}
