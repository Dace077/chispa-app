package com.chispa.ingles.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.data.content.Lesson
import com.chispa.ingles.data.content.LessonKind
import com.chispa.ingles.data.content.VocabItem
import com.chispa.ingles.data.db.SrsCardEntity
import com.chispa.ingles.data.db.UserProfileEntity
import com.chispa.ingles.data.prefs.Accent
import com.chispa.ingles.data.repo.SessionOutcome
import com.chispa.ingles.domain.AnswerChecker
import com.chispa.ingles.domain.CertificateRules
import com.chispa.ingles.domain.SrsPairing
import com.chispa.ingles.speech.SpeechRecognizerManager
import com.chispa.ingles.speech.SpeechState
import com.chispa.ingles.widget.StreakWidget
import android.speech.RecognizerIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SessionMode { LESSON, REVIEW, SPEAKING }

enum class SessionPhase { LOADING, ANSWERING, FEEDBACK, FINISHED, OUT_OF_HEARTS, EMPTY }

/** Una palabra arrastrable del ejercicio de ordenar. El id evita duplicados ambiguos. */
data class WordToken(val id: Int, val text: String)

data class MatchState(
    val left: List<String>,
    val right: List<String>,
    val solved: Set<String> = emptySet(),
    val selectedLeft: String? = null,
    val selectedRight: String? = null,
    val wrongPair: Pair<String, String>? = null,
    val mistakes: Int = 0
) {
    val isComplete: Boolean get() = solved.size == left.size
}

data class Feedback(
    val correct: Boolean,
    val headline: String,
    val correctAnswer: String? = null,
    val note: String? = null
)

data class LessonUiState(
    val phase: SessionPhase = SessionPhase.LOADING,
    val mode: SessionMode = SessionMode.LESSON,
    val title: String = "",
    /** Ficha de gramática de esta lección, si la tiene declarada en el JSON. */
    val grammarTopicId: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val index: Int = 0,
    val totalSteps: Int = 0,
    val hearts: Int = UserProfileEntity.MAX_HEARTS,
    val heartsEnabled: Boolean = true,
    val correctCount: Int = 0,
    val gradedTotal: Int = 0,
    val feedback: Feedback? = null,
    val outcome: SessionOutcome? = null,
    /** Nivel que esta lección acaba de cerrar, si ha cerrado alguno. */
    val levelJustCompleted: CefrLevel? = null,
    val accent: Accent = Accent.US,
    val autoPlay: Boolean = true,
    val speechRate: Float = 0.9f,

    // Borrador de la respuesta actual
    val selectedOption: String? = null,
    val textInput: String = "",
    val wordPool: List<WordToken> = emptyList(),
    val builtWords: List<WordToken> = emptyList(),
    val matchState: MatchState? = null,
    val speech: SpeechState = SpeechState.Idle,
    val speechScore: Float? = null
) {
    val current: Exercise? get() = exercises.getOrNull(index)
    val progress: Float
        get() = if (totalSteps == 0) 0f else (index.toFloat() / totalSteps).coerceIn(0f, 1f)
    val accuracy: Int
        get() = if (gradedTotal == 0) 100 else correctCount * 100 / gradedTotal
}

/**
 * Motor de una sesión de práctica: sirve los ejercicios, corrige, gestiona vidas
 * y cierra la sesión sumando XP y actualizando la repetición espaciada.
 *
 * Los tres modos (lección, repaso y pronunciación) comparten el mismo motor:
 * lo único que cambia es de dónde salen los ejercicios y si se pierden corazones.
 */
class LessonViewModel(
    private val locator: ServiceLocator,
    private val lessonId: String,
    private val mode: SessionMode
) : ViewModel() {

    private val _state = MutableStateFlow(LessonUiState(mode = mode))
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    val speechRecognizer = SpeechRecognizerManager(locator.appContext)

    private var lesson: Lesson? = null
    private val queue = mutableListOf<Exercise>()
    private val requeued = mutableSetOf<String>()
    private val answeredCorrectly = mutableSetOf<String>()
    private var speakingAnswered = 0

    /** Vocabulario que puede resolver el par (inglés, español) de una clave SRS. */
    private var vocabLookup: Map<String, VocabItem> = emptyMap()

    init {
        viewModelScope.launch {
            val settings = locator.settingsStore.current()
            val curriculum = locator.contentRepository.curriculum()
            vocabLookup = curriculum.vocabIndex

            val exercises = when (mode) {
                SessionMode.LESSON -> loadLessonExercises(curriculum.lesson(lessonId))
                SessionMode.REVIEW -> buildReviewExercises()
                SessionMode.SPEAKING -> buildSpeakingExercises()
            }

            queue.clear()
            queue.addAll(exercises)

            val graded = exercises.count { it.isGraded }
            _state.value = _state.value.copy(
                phase = if (exercises.isEmpty()) SessionPhase.EMPTY else SessionPhase.ANSWERING,
                title = when (mode) {
                    SessionMode.LESSON -> lesson?.title.orEmpty()
                    SessionMode.REVIEW -> "Repaso"
                    SessionMode.SPEAKING -> "Pronunciación"
                },
                // Solo en una lección: en repaso y pronunciación los ejercicios
                // vienen de sitios distintos y no hay una regla única que explicar.
                grammarTopicId = if (mode == SessionMode.LESSON) lesson?.grammarTopicId else null,
                exercises = queue.toList(),
                totalSteps = exercises.size,
                gradedTotal = graded,
                hearts = if (mode == SessionMode.LESSON) locator.progressRepository.refreshHearts() else 5,
                heartsEnabled = mode == SessionMode.LESSON,
                accent = settings.accent,
                autoPlay = settings.autoPlayAudio,
                speechRate = settings.speechRate
            )
            prepareCurrentExercise(autoSpeak = settings.autoPlayAudio)
        }

        viewModelScope.launch {
            speechRecognizer.state.collect { speechState ->
                _state.value = _state.value.copy(speech = speechState)
                if (speechState is SpeechState.Result) evaluateSpeech(speechState.hypotheses)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Construcción de la cola de ejercicios                              */
    /* ------------------------------------------------------------------ */

    private suspend fun loadLessonExercises(loaded: Lesson?): List<Exercise> {
        lesson = loaded ?: return emptyList()
        locator.progressRepository.seedVocab(loaded)
        return loaded.exercises
    }

    /**
     * El repaso mezcla lo más vencido con lo que más se resiste, y genera un tipo
     * de ejercicio distinto para cada tarjeta para que no sea siempre lo mismo.
     */
    private suspend fun buildReviewExercises(): List<Exercise> {
        val due = locator.progressRepository.dueCards(REVIEW_SIZE)
        val cards = if (due.size >= REVIEW_SIZE) due else {
            (due + locator.progressRepository.hardestCards(REVIEW_SIZE - due.size))
                .distinctBy { it.cardKey }
        }
        if (cards.isEmpty()) return emptyList()

        val distractorPool = cards.map { it.es }.distinct()
        return cards.mapIndexed { index, card ->
            when (index % 3) {
                0 -> multipleChoiceFor(card, distractorPool)
                1 -> Exercise.Translate(
                    srsKey = card.cardKey,
                    prompt = card.es,
                    answer = card.en,
                    alternatives = emptyList(),
                    toEnglish = true,
                    hint = "Escríbelo en inglés"
                )
                else -> Exercise.ListenAndType(
                    srsKey = card.cardKey,
                    audioText = card.en,
                    answer = card.en,
                    translation = card.es
                )
            }
        }.shuffled()
    }

    private fun multipleChoiceFor(card: SrsCardEntity, pool: List<String>): Exercise {
        val distractors = pool.filter { it != card.es }.shuffled().take(3)
        val options = (distractors + card.es).distinct().shuffled()
        return if (options.size < 2) {
            Exercise.Translate(
                srsKey = card.cardKey,
                prompt = card.en,
                answer = card.es,
                alternatives = emptyList(),
                toEnglish = false,
                hint = null
            )
        } else {
            Exercise.MultipleChoice(
                srsKey = card.cardKey,
                prompt = card.en,
                subPrompt = "¿Qué significa?",
                options = options,
                answer = card.es,
                speakPrompt = true
            )
        }
    }

    /** Sesión libre de pronunciación con frases que el usuario ya ha visto. */
    private suspend fun buildSpeakingExercises(): List<Exercise> {
        val seen = locator.progressRepository.dueCards(SPEAKING_SIZE * 2)
            .ifEmpty { locator.progressRepository.hardestCards(SPEAKING_SIZE * 2) }

        val fromProgress = seen.map { card ->
            Exercise.SpeakAndRepeat(
                srsKey = card.cardKey,
                phrase = card.en,
                translation = card.es
            )
        }

        // Si aún no hay historial, tiramos del vocabulario de las primeras lecciones.
        val fallback = if (fromProgress.size < SPEAKING_SIZE) {
            locator.contentRepository.curriculum().allLessons
                .asSequence()
                .filter { it.level == CefrLevel.A1 }
                .flatMap { it.vocab.asSequence() }
                .take(SPEAKING_SIZE * 2)
                .map { item ->
                    Exercise.SpeakAndRepeat(
                        srsKey = item.srsKey,
                        phrase = item.en,
                        translation = item.es
                    )
                }
                .toList()
        } else {
            emptyList()
        }

        return (fromProgress + fallback).distinctBy { it.srsKey }.shuffled().take(SPEAKING_SIZE)
    }

    /* ------------------------------------------------------------------ */
    /*  Preparación de cada ejercicio                                      */
    /* ------------------------------------------------------------------ */

    private fun prepareCurrentExercise(autoSpeak: Boolean) {
        val state = _state.value
        val exercise = state.exercises.getOrNull(state.index)

        var updated = state.copy(
            selectedOption = null,
            textInput = "",
            wordPool = emptyList(),
            builtWords = emptyList(),
            matchState = null,
            speechScore = null,
            feedback = null
        )

        when (exercise) {
            is Exercise.WordOrder -> {
                val tokens = exercise.words.shuffled().mapIndexed { i, w -> WordToken(i, w) }
                updated = updated.copy(wordPool = tokens)
            }

            is Exercise.MatchingPairs -> {
                updated = updated.copy(
                    matchState = MatchState(
                        left = exercise.pairs.map { it.first }.shuffled(),
                        right = exercise.pairs.map { it.second }.shuffled()
                    )
                )
            }

            else -> Unit
        }

        _state.value = updated
        speechRecognizer.reset()

        // Los ejercicios de escucha reproducen solos: es su razón de ser.
        if (exercise is Exercise.ListenAndType && autoSpeak) {
            speak(exercise.audioText)
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Entrada del usuario                                                */
    /* ------------------------------------------------------------------ */

    fun selectOption(option: String) {
        if (_state.value.phase != SessionPhase.ANSWERING) return
        _state.value = _state.value.copy(selectedOption = option)
    }

    fun updateText(text: String) {
        if (_state.value.phase != SessionPhase.ANSWERING) return
        _state.value = _state.value.copy(textInput = text)
    }

    fun pickWord(token: WordToken) {
        val state = _state.value
        if (state.phase != SessionPhase.ANSWERING) return
        _state.value = state.copy(
            wordPool = state.wordPool - token,
            builtWords = state.builtWords + token
        )
    }

    fun unpickWord(token: WordToken) {
        val state = _state.value
        if (state.phase != SessionPhase.ANSWERING) return
        _state.value = state.copy(
            builtWords = state.builtWords - token,
            wordPool = state.wordPool + token
        )
    }

    fun selectMatchLeft(item: String) {
        val state = _state.value
        val match = state.matchState ?: return
        if (item in match.solved) return
        val updated = match.copy(selectedLeft = item, wrongPair = null)
        _state.value = state.copy(matchState = resolveMatch(updated))
    }

    fun selectMatchRight(item: String) {
        val state = _state.value
        val match = state.matchState ?: return
        val exercise = state.current as? Exercise.MatchingPairs ?: return
        if (exercise.pairs.any { it.second == item && it.first in match.solved }) return
        val updated = match.copy(selectedRight = item, wrongPair = null)
        _state.value = state.copy(matchState = resolveMatch(updated))
    }

    /** Cuando hay una selección a cada lado, se comprueba la pareja. */
    private fun resolveMatch(match: MatchState): MatchState {
        val left = match.selectedLeft ?: return match
        val right = match.selectedRight ?: return match
        val exercise = _state.value.current as? Exercise.MatchingPairs ?: return match

        val isPair = exercise.pairs.any { it.first == left && it.second == right }
        return if (isPair) {
            match.copy(
                solved = match.solved + left,
                selectedLeft = null,
                selectedRight = null,
                wrongPair = null
            )
        } else {
            match.copy(
                selectedLeft = null,
                selectedRight = null,
                wrongPair = left to right,
                mistakes = match.mistakes + 1
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Audio                                                              */
    /* ------------------------------------------------------------------ */

    fun speak(text: String) {
        locator.tts.speak(text, _state.value.accent, _state.value.speechRate)
    }

    fun speakSlowly(text: String) {
        locator.tts.speakSlowly(text, _state.value.accent)
    }

    fun startListening() {
        locator.tts.stop()
        speechRecognizer.start(_state.value.accent)
    }

    /**
     * El usuario ha terminado de hablar. NO se cancela el reconocimiento: se le
     * pide que cierre y devuelva lo que haya oído. Cancelarlo aquí (como hacía
     * la primera versión) tiraba el resultado antes de que llegara.
     */
    fun stopListening() {
        speechRecognizer.stopAndEvaluate()
    }

    /** Cancela del todo, sin evaluar. Para salir de la pantalla. */
    fun cancelListening() {
        speechRecognizer.stop()
    }

    /** El sistema denegó el micrófono: que la pantalla lo diga en vez de callarse. */
    fun reportMicPermissionDenied() {
        _state.value = _state.value.copy(speech = SpeechState.PermissionNeeded)
    }

    /** Intent para abrir el asistente de voz del sistema como plan B. */
    fun systemDialogIntent(): android.content.Intent =
        SpeechRecognizerManager.systemDialogIntent(
            accent = _state.value.accent,
            prompt = (_state.value.current as? Exercise.SpeakAndRepeat)?.phrase.orEmpty()
        )

    /**
     * Resultado del asistente de voz del sistema. Se evalúa igual que el del
     * reconocedor propio: al usuario le da lo mismo por qué camino llegó.
     */
    fun evaluateFromSystemDialog(hypotheses: List<String>) {
        val exercise = _state.value.current as? Exercise.SpeakAndRepeat ?: return
        speechRecognizer.reset()
        if (hypotheses.isEmpty()) {
            _state.value = _state.value.copy(speech = SpeechState.NoMatch)
            return
        }
        val score = AnswerChecker.speechSimilarity(hypotheses, exercise.phrase)
        _state.value = _state.value.copy(
            speech = SpeechState.Result(hypotheses),
            speechScore = score
        )
        submitSpeech(score >= AnswerChecker.SPEECH_PASS_THRESHOLD, score)
    }

    /**
     * Salida de emergencia: si el motor de voz del dispositivo no funciona,
     * el usuario marca que lo dijo bien y sigue. Vale como acierto pero no
     * alimenta el SRS, porque nadie ha comprobado la pronunciación de verdad.
     */
    fun markSpokenManually() {
        val exercise = _state.value.current as? Exercise.SpeakAndRepeat ?: return
        speechRecognizer.reset()
        speakingAnswered += 1
        finishAnswer(
            exercise = exercise,
            correct = true,
            feedback = Feedback(
                correct = true,
                headline = "Anotado",
                correctAnswer = exercise.phrase,
                note = "Sin micrófono no puedo evaluarte, así que te creo."
            ),
            recordSrs = false
        )
    }

    private fun evaluateSpeech(hypotheses: List<String>) {
        val exercise = _state.value.current as? Exercise.SpeakAndRepeat ?: return
        // Ya tenemos lo que dijo: soltamos el micrófono antes de puntuar.
        speechRecognizer.reset()
        val score = AnswerChecker.speechSimilarity(hypotheses, exercise.phrase)
        _state.value = _state.value.copy(speechScore = score)
        submitSpeech(score >= AnswerChecker.SPEECH_PASS_THRESHOLD, score)
    }

    private fun submitSpeech(passed: Boolean, score: Float) {
        val exercise = _state.value.current as? Exercise.SpeakAndRepeat ?: return
        speakingAnswered += 1
        finishAnswer(
            exercise = exercise,
            correct = passed,
            feedback = Feedback(
                correct = passed,
                headline = if (passed) "¡Bien dicho!" else "Casi. Inténtalo otra vez",
                correctAnswer = exercise.phrase,
                note = "Coincidencia: ${(score * 100).toInt()}%"
            )
        )
    }

    /** Salta un ejercicio de habla sin penalizar: el micro no siempre coopera. */
    fun skipSpeaking() {
        val exercise = _state.value.current as? Exercise.SpeakAndRepeat ?: return
        speechRecognizer.reset()
        finishAnswer(
            exercise = exercise,
            correct = true,
            feedback = Feedback(
                correct = true,
                headline = "Sin problema, seguimos",
                correctAnswer = exercise.phrase,
                note = "Este ejercicio no cuenta como error"
            ),
            recordSrs = false
        )
    }

    /* ------------------------------------------------------------------ */
    /*  Corrección                                                         */
    /* ------------------------------------------------------------------ */

    fun submit() {
        val state = _state.value
        if (state.phase != SessionPhase.ANSWERING) return
        val exercise = state.current ?: return

        when (exercise) {
            is Exercise.Tip, is Exercise.Reading, is Exercise.CultureNote,
            is Exercise.VocabIntro -> advance()

            is Exercise.MultipleChoice -> {
                val chosen = state.selectedOption ?: return
                val correct = chosen == exercise.answer
                finishAnswer(
                    exercise, correct,
                    Feedback(
                        correct = correct,
                        headline = if (correct) "¡Exacto!" else "No era esa",
                        correctAnswer = exercise.answer
                    )
                )
            }

            is Exercise.Translate -> {
                val result = AnswerChecker.check(
                    typed = state.textInput,
                    expected = exercise.answer,
                    alternatives = exercise.alternatives
                )
                finishAnswer(
                    exercise, result.isAccepted,
                    feedbackFor(result, exercise.answer)
                )
            }

            is Exercise.ListenAndType -> {
                val result = AnswerChecker.check(state.textInput, exercise.answer)
                finishAnswer(exercise, result.isAccepted, feedbackFor(result, exercise.answer))
            }

            is Exercise.FillInBlank -> {
                val typed = state.selectedOption ?: state.textInput
                val result = AnswerChecker.check(typed, exercise.answer, allowTypos = exercise.options.isEmpty())
                finishAnswer(exercise, result.isAccepted, feedbackFor(result, exercise.answer))
            }

            is Exercise.WordOrder -> {
                val result = AnswerChecker.checkWordOrder(
                    state.builtWords.map { it.text },
                    exercise.answer
                )
                finishAnswer(exercise, result.isAccepted, feedbackFor(result, exercise.answer))
            }

            is Exercise.MatchingPairs -> {
                val match = state.matchState ?: return
                if (!match.isComplete) return
                val correct = match.mistakes == 0
                // Cada pareja alimenta el SRS por separado: son palabras distintas.
                viewModelScope.launch {
                    exercise.pairs.forEach { (en, es) ->
                        locator.progressRepository.recordAnswer(
                            cardKey = en.lowercase(),
                            correct = correct,
                            en = en,
                            es = es,
                            lesson = lesson
                        )
                    }
                }
                finishAnswer(
                    exercise, correct,
                    Feedback(
                        correct = correct,
                        headline = if (correct) "¡Todas correctas!" else "Completado con ${match.mistakes} fallo(s)",
                        correctAnswer = null
                    ),
                    recordSrs = false
                )
            }

            is Exercise.SpeakAndRepeat -> Unit // se resuelve por voz
        }
    }

    private fun feedbackFor(result: AnswerChecker.Result, expected: String): Feedback = when (result) {
        is AnswerChecker.Result.Correct -> Feedback(true, "¡Perfecto!", expected)
        is AnswerChecker.Result.Typo -> Feedback(
            correct = true,
            headline = "¡Correcto! Ojo con la ortografía",
            correctAnswer = result.correct
        )
        is AnswerChecker.Result.Wrong -> Feedback(false, "Casi. La respuesta era:", result.correct)
    }

    private fun finishAnswer(
        exercise: Exercise,
        correct: Boolean,
        feedback: Feedback,
        recordSrs: Boolean = true
    ) {
        val firstAttempt = exercise.srsKey !in requeued
        if (correct && firstAttempt) answeredCorrectly += exercise.srsKey

        // Estadística por tipo de ejercicio.
        //
        // Solo cuenta el PRIMER intento: el reintento de un fallo se acierta casi
        // siempre —acabas de ver la solución— y contarlo maquillaría el dato justo
        // en los tipos donde el usuario flojea, que son los que queremos detectar.
        if (firstAttempt && exercise.isGraded) {
            viewModelScope.launch {
                locator.progressRepository.recordExerciseStat(exercise.statType(), correct)
            }
        }

        // Solo entra en el repaso lo que de verdad es una pareja de traducción.
        // Un ejercicio de gramática se corrige y suma, pero no se convierte en
        // tarjeta de vocabulario.
        if (recordSrs) {
            vocabPairFor(exercise)?.let { (en, es) ->
                viewModelScope.launch {
                    locator.progressRepository.recordAnswer(
                        cardKey = exercise.srsKey,
                        correct = correct,
                        en = en,
                        es = es,
                        lesson = lesson
                    )
                }
            }
        }

        // Fallar reintroduce el ejercicio al final de la cola, una sola vez.
        if (!correct && exercise.srsKey !in requeued) {
            requeued += exercise.srsKey
            queue.add(exercise)
        }

        val state = _state.value
        var hearts = state.hearts
        if (!correct && state.heartsEnabled) {
            viewModelScope.launch { locator.progressRepository.consumeHeart() }
            hearts = (hearts - 1).coerceAtLeast(0)
        }

        _state.value = state.copy(
            phase = if (hearts == 0 && state.heartsEnabled) SessionPhase.OUT_OF_HEARTS else SessionPhase.FEEDBACK,
            feedback = feedback,
            hearts = hearts,
            exercises = queue.toList(),
            totalSteps = queue.size,
            correctCount = answeredCorrectly.size
        )
    }

    /**
     * Deduce el par inglés/español del ejercicio para poder crear su tarjeta SRS.
     *
     * Devuelve null cuando el ejercicio **no es** una pareja de traducción. Antes
     * esta función siempre devolvía algo, y para un ejercicio de gramática como
     * "¿Cuál está bien escrito?" → "two yellow bananas" guardaba en el repaso una
     * tarjeta con el inglés y el español cambiados de sitio. Después el repaso la
     * mostraba como "¿Qué significa? two yellow bananas" con la instrucción de
     * otro ejercicio de respuesta correcta.
     *
     * La regla es no adivinar: si no consta de dónde sale cada lado, no hay tarjeta.
     */
    private fun vocabPairFor(exercise: Exercise): Pair<String, String>? =
        SrsPairing.pairFor(exercise, vocabLookup)

    /* ------------------------------------------------------------------ */
    /*  Avance                                                             */
    /* ------------------------------------------------------------------ */

    fun advance() {
        val state = _state.value
        val nextIndex = state.index + 1

        if (nextIndex >= queue.size) {
            finishSession()
            return
        }

        _state.value = state.copy(
            index = nextIndex,
            phase = SessionPhase.ANSWERING,
            exercises = queue.toList(),
            totalSteps = queue.size
        )
        prepareCurrentExercise(autoSpeak = state.autoPlay)
    }

    private fun finishSession() {
        val state = _state.value
        viewModelScope.launch {
            // Qué niveles estaban completos ANTES de guardar esta lección. La
            // diferencia con los de después es el nivel que se acaba de cerrar,
            // y ese es el momento en que un certificado significa algo.
            val nivelesAntes = nivelesCompletos()

            val outcome = when (mode) {
                SessionMode.LESSON -> lesson?.let {
                    locator.progressRepository.completeLesson(
                        lesson = it,
                        correct = answeredCorrectly.size,
                        totalGraded = state.gradedTotal,
                        speakingAnswered = speakingAnswered
                    )
                }
                SessionMode.REVIEW -> locator.progressRepository.completeReview(
                    correct = answeredCorrectly.size,
                    total = state.gradedTotal
                )
                SessionMode.SPEAKING -> locator.progressRepository.completeSpeakingSession(
                    correct = answeredCorrectly.size,
                    total = state.gradedTotal
                )
            }
            val nivelCerrado = if (mode == SessionMode.LESSON) {
                (nivelesCompletos() - nivelesAntes).firstOrNull()
            } else {
                null
            }

            // La racha y la meta de hoy acaban de cambiar: el widget de la
            // pantalla de inicio tiene que enterarse ahora, no dentro de media
            // hora. Es el único momento en que hace falta refrescarlo.
            StreakWidget.refresh(locator.appContext)

            _state.value = _state.value.copy(
                phase = SessionPhase.FINISHED,
                outcome = outcome,
                levelJustCompleted = nivelCerrado
            )
        }
    }

    /** Niveles del camino principal terminados del todo, ahora mismo. */
    private suspend fun nivelesCompletos(): Set<CefrLevel> {
        if (mode != SessionMode.LESSON) return emptySet()
        val curriculum = locator.contentRepository.curriculum()
        val progreso = locator.database.lessonProgressDao().all().associateBy { it.lessonId }
        return CertificateRules.earnedLevels(curriculum, progreso).map { it.level }.toSet()
    }

    /** Repaso exprés desde la pantalla de "sin corazones": los recupera todos. */
    fun recoverHearts() {
        viewModelScope.launch {
            locator.progressRepository.restoreHearts()
            _state.value = _state.value.copy(
                hearts = UserProfileEntity.MAX_HEARTS,
                phase = SessionPhase.FEEDBACK
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Aquí sí se destruye el motor de voz: la pantalla se va.
        speechRecognizer.release()
        locator.tts.stop()
    }

    companion object {
        const val REVIEW_SIZE = 15
        const val SPEAKING_SIZE = 10
    }
}

/**
 * Clave estable con la que se guardan las estadísticas por tipo.
 *
 * Es un `when` explícito y no `this::class.simpleName` a propósito: el nombre de
 * la clase se guarda en la base de datos, y renombrar un `Exercise` en Kotlin no
 * puede partir en dos el historial de nadie.
 */
fun Exercise.statType(): String = when (this) {
    is Exercise.MultipleChoice -> "multiple_choice"
    is Exercise.Translate -> if (toEnglish) "translate_to_en" else "translate_to_es"
    is Exercise.ListenAndType -> "listen_and_type"
    is Exercise.WordOrder -> "word_order"
    is Exercise.SpeakAndRepeat -> "speak_and_repeat"
    is Exercise.MatchingPairs -> "matching_pairs"
    is Exercise.FillInBlank -> "fill_in_blank"
    is Exercise.VocabIntro, is Exercise.Tip,
    is Exercise.Reading, is Exercise.CultureNote -> "info"
}

/** Etiqueta corta del tipo de ejercicio, para la cabecera de la pantalla. */
fun Exercise.instruction(): String = when (this) {
    is Exercise.MultipleChoice -> subPrompt ?: "Elige la opción correcta"
    is Exercise.Translate -> if (toEnglish) "Traduce al inglés" else "Traduce al español"
    is Exercise.ListenAndType -> "Escucha y escribe lo que oyes"
    is Exercise.WordOrder -> "Ordena las palabras"
    is Exercise.SpeakAndRepeat -> "Di la frase en voz alta"
    is Exercise.MatchingPairs -> prompt
    is Exercise.FillInBlank -> "Completa la frase"
    is Exercise.VocabIntro -> "Empezamos por aquí"
    is Exercise.Tip -> "Antes de seguir"
    is Exercise.Reading -> "Lectura"
    is Exercise.CultureNote -> "Nota cultural"
}

/** Solo para depurar tipos de lección en la UI. */
fun LessonKind.label(): String = when (this) {
    LessonKind.LESSON -> "Lección"
    LessonKind.STORY -> "Historia"
    LessonKind.CULTURE -> "Cultura"
    LessonKind.PRONUNCIATION -> "Pronunciación"
}
