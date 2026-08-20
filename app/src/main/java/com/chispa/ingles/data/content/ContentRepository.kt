package com.chispa.ingles.data.content

import android.content.Context
import android.util.Log
import com.chispa.ingles.domain.LessonPedagogy
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

    @Volatile private var libraryCache: ReadingLibrary? = null

    /** Biblioteca de lecturas bilingües. Se cachea igual que el currículo. */
    suspend fun library(): ReadingLibrary {
        libraryCache?.let { return it }
        return mutex.withLock {
            libraryCache ?: loadLibrary().also { libraryCache = it }
        }
    }

    private suspend fun loadLibrary(): ReadingLibrary = withContext(Dispatchers.IO) {
        runCatching {
            val raw = appContext.assets.open("$CONTENT_DIR/readings.json")
                .bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<ReadingLibraryJson>(raw)
            ReadingLibrary(parsed.readings.mapNotNull { it.toDomain() })
        }.getOrElse { error ->
            Log.e(TAG, "No se pudo leer readings.json", error)
            ReadingLibrary(emptyList())
        }
    }

    @Volatile private var grammarCache: GrammarGuide? = null

    /** Guía de gramática consultable. Igual que la biblioteca: fuera del índice. */
    suspend fun grammar(): GrammarGuide {
        grammarCache?.let { return it }
        return mutex.withLock {
            grammarCache ?: loadGrammar().also { grammarCache = it }
        }
    }

    private suspend fun loadGrammar(): GrammarGuide = withContext(Dispatchers.IO) {
        runCatching {
            val raw = appContext.assets.open("$CONTENT_DIR/grammar.json")
                .bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<GrammarGuideJson>(raw)
            GrammarGuide(parsed.topics.mapNotNull { it.toDomain() })
        }.getOrElse { error ->
            Log.e(TAG, "No se pudo leer grammar.json", error)
            GrammarGuide(emptyList())
        }
    }

    @Volatile private var kidsCache: List<com.chispa.ingles.domain.KidsWorld>? = null

    /**
     * Mundos de la etapa infantil. Fuera del índice, como la gramática: no
     * forman parte del camino A1→C2 ni desbloquean nada.
     */
    suspend fun kidsWorlds(): List<com.chispa.ingles.domain.KidsWorld> {
        kidsCache?.let { return it }
        return mutex.withLock {
            kidsCache ?: loadKids().also { kidsCache = it }
        }
    }

    private suspend fun loadKids(): List<com.chispa.ingles.domain.KidsWorld> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = appContext.assets.open("$CONTENT_DIR/kids.json")
                    .bufferedReader().use { it.readText() }
                json.decodeFromString<KidsFileJson>(raw).worlds.mapNotNull { it.toDomain() }
            }.getOrElse { error ->
                Log.e(TAG, "No se pudo leer kids.json", error)
                emptyList()
            }
        }

    @Volatile private var toeflCache: ToeflGuide? = null

    /**
     * Material de apoyo del TOEFL ITP. Fuera del índice, como la gramática y
     * las lecturas: no forma parte del camino de niveles.
     */
    suspend fun toefl(): ToeflGuide {
        toeflCache?.let { return it }
        return mutex.withLock {
            toeflCache ?: loadToefl().also { toeflCache = it }
        }
    }

    private suspend fun loadToefl(): ToeflGuide = withContext(Dispatchers.IO) {
        runCatching {
            val raw = appContext.assets.open("$CONTENT_DIR/toefl.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString<ToeflGuideJson>(raw).toDomain()
        }.getOrElse { error ->
            Log.e(TAG, "No se pudo leer toefl.json", error)
            ToeflGuide("", "", emptyList(), emptyList(), emptyList())
        }
    }

    /**
     * Simulacros disponibles, por id. Se leen del índice `toefl_examenes.json`
     * para poder añadir exámenes sin tocar código.
     */
    suspend fun toeflExamIds(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = appContext.assets.open("$CONTENT_DIR/toefl_examenes.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString<ContentIndexJson>(raw).files
        }.getOrElse { emptyList() }
    }

    /**
     * Un simulacro completo. Devuelve null si falta o si está incompleto.
     *
     * No se cachean: son archivos grandes y solo se abren de uno en uno, cuando
     * el usuario empieza ese examen concreto. Mantener diez en memoria para el
     * que está usando uno sería tirar RAM.
     */
    suspend fun toeflExam(examId: String): ToeflExam? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = appContext.assets.open("$CONTENT_DIR/$examId.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString<ToeflExamJson>(raw).toDomain()
        }.getOrElse { error ->
            Log.e(TAG, "No se pudo leer el simulacro $examId", error)
            null
        }
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

private fun ReadingJson.toDomain(): Reading? {
    val frases = sentences
        .filter { it.en.isNotBlank() }
        .mapIndexed { index, s ->
            Sentence(
                index = index,
                en = s.en.trim(),
                es = s.es.trim(),
                startsParagraph = s.paragraph,
                speaker = s.speaker.trim(),
                note = s.note.trim()
            )
        }
    if (frases.isEmpty()) return null

    val palabras = frases.sumOf { it.en.split(" ").size }
    return Reading(
        id = id,
        title = title,
        level = CefrLevel.from(level),
        category = ReadingCategory.from(category),
        summary = summary,
        // 130 palabras por minuto es un ritmo realista leyendo en un idioma
        // que no es el tuyo. Mejor pasarse de generoso que asustar.
        minutes = if (minutes > 0) minutes else ((palabras / 130) + 1),
        sentences = frases,
        glossary = glossary.map { VocabItem(it.en.trim(), it.es.trim(), it.ipa, it.note) }
    )
}

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

private fun LessonJson.toDomain(unitId: String, trackId: String, level: CefrLevel): Lesson {
    val palabras = vocab.map { VocabItem(it.en.trim(), it.es.trim(), it.ipa, it.note) }

    // Aquí es donde una lección se convierte en una secuencia didáctica: se
    // antepone la presentación del vocabulario y se ordenan los ejercicios de
    // menor a mayor exigencia. Sin esto la app examina de palabras que nunca
    // ha enseñado, que es exactamente lo que hacía antes.
    val preparados = LessonPedagogy.prepare(
        exercises = exercises.mapNotNull { it.toDomain() },
        vocab = palabras,
        lessonId = id,
        lessonTitle = title,
        respectAuthorOrder = orderedByAuthor
    )

    return Lesson(
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
        vocab = palabras,
        exercises = preparados,
        orderedByAuthor = orderedByAuthor,
        grammarTopicId = grammarTopicId?.trim()?.takeIf { it.isNotBlank() }
    )
}

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
