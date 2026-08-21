package com.chispa.ingles.ui.toefl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.certificates.CertificateSharing
import com.chispa.ingles.certificates.ExamReportPdf
import com.chispa.ingles.core.Time
import com.chispa.ingles.data.content.ExamPart
import com.chispa.ingles.data.content.ExamQuestion
import com.chispa.ingles.data.content.ScriptLine
import com.chispa.ingles.data.content.ToeflExam
import com.chispa.ingles.data.db.ExamAttemptEntity
import com.chispa.ingles.domain.ExamProgress
import com.chispa.ingles.domain.ToeflItp
import com.chispa.ingles.domain.ToeflResult
import com.chispa.ingles.domain.ToeflSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExamPhase {
    LOADING, MISSING, INTRO, RESUME_ASK, SECTION_INTRO, ANSWERING, SECTION_DONE,
    FINISHED, REVIEW
}

/** Qué preguntas enseña la revisión. */
enum class ReviewFilter { FALLADAS, BLANCO, TODAS }

/**
 * Una pregunta ya corregida, lista para enseñársela al alumno.
 *
 * Lleva el guion y el texto de apoyo dentro porque en la revisión sí se pueden
 * mostrar: el examen terminó, y ver por qué la respuesta era esa es justo lo
 * que convierte el simulacro en estudio.
 */
data class ReviewItem(
    val section: ToeflSection,
    val numero: Int,
    val question: ExamQuestion,
    val given: Int?,
    val script: List<ScriptLine>,
    val passage: String
) {
    val correcta: Boolean get() = given == question.answer
    val enBlanco: Boolean get() = given == null
}

data class ExamUiState(
    val phase: ExamPhase = ExamPhase.LOADING,
    val exam: ToeflExam? = null,
    val sectionIndex: Int = 0,
    val questionIndex: Int = 0,
    /** Respuestas por id de pregunta. Lo que no está aquí quedó en blanco. */
    val answers: Map<String, Int> = emptyMap(),
    val secondsLeft: Int = 0,
    val result: ToeflResult? = null,
    /** Audios ya reproducidos: en el examen real cada uno suena una sola vez. */
    val played: Set<String> = emptySet(),
    val reviewFilter: ReviewFilter = ReviewFilter.FALLADAS,
    /** true si al abrir había un intento a medias que se puede retomar. */
    val puedeRetomar: Boolean = false,
    /** true si ya hay un intento terminado cuya revisión se puede volver a ver. */
    val revisionPrevia: Boolean = false
) {
    val section: ToeflSection? get() = ToeflSection.ORDER.getOrNull(sectionIndex)
    val examSection get() = section?.let { s -> exam?.section(s) }
    val questions: List<ExamQuestion> get() = examSection?.allQuestions.orEmpty()
    val current: ExamQuestion? get() = questions.getOrNull(questionIndex)
    val part: ExamPart? get() = examSection?.parts?.firstOrNull { p ->
        p.questions.any { it.id == current?.id }
    }

    val answeredInSection: Int get() = questions.count { it.id in answers }
    val blanksInSection: Int get() = questions.size - answeredInSection

    val minutes: Int get() = secondsLeft / 60
    val seconds: Int get() = secondsLeft % 60
    /** Aviso visual cuando quedan menos de dos minutos. */
    val tiempoBajo: Boolean get() = secondsLeft in 1..120
}

/**
 * Motor de un simulacro completo.
 *
 * Reproduce las reglas del examen real, y esas reglas son la mitad de la
 * dificultad:
 *
 * - Las secciones van en orden fijo y **no se puede volver** a una terminada.
 * - Cada sección tiene su propio reloj. Si te sobran cinco minutos en Structure
 *   no puedes gastarlos en Reading.
 * - Dentro de una sección sí puedes ir y venir entre preguntas, como en el
 *   cuadernillo de papel.
 * - Cuando el reloj llega a cero, la sección se cierra sola con lo que haya.
 * - Los audios de Listening suenan **una vez**. Repetirlos convertiría el
 *   simulacro en un ejercicio distinto del examen que prepara.
 */
class ExamViewModel(
    private val locator: ServiceLocator,
    private val examId: String
) : ViewModel() {

    private val _state = MutableStateFlow(ExamUiState())
    val state: StateFlow<ExamUiState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var startedAt = 0L

    private val attemptDao get() = locator.database.examAttemptDao()

    /** Fila del intento en curso. Se reusa para no crear una por guardado. */
    private var attemptId = 0L

    init {
        viewModelScope.launch {
            val examen = locator.contentRepository.toeflExam(examId)
            if (examen == null) {
                _state.value = _state.value.copy(phase = ExamPhase.MISSING)
                return@launch
            }
            val aMedias = attemptDao.unfinished(examId)
            // Un intento terminado guarda lo que se contestó, así que su
            // revisión sigue disponible aunque hayan pasado semanas.
            val terminado = attemptDao.lastCompleted(examId)
            _state.value = _state.value.copy(
                phase = if (aMedias != null) ExamPhase.RESUME_ASK else ExamPhase.INTRO,
                exam = examen,
                puedeRetomar = aMedias != null,
                revisionPrevia = terminado != null
            )
        }
    }

    /**
     * Abre la revisión de un intento anterior, sin volver a examinarse.
     *
     * Hasta ahora los fallos solo se podían mirar en los segundos siguientes a
     * terminar. Quien salía de esa pantalla perdía el acceso a las 140
     * explicaciones, que es justo el material de estudio que deja el simulacro.
     */
    fun openPreviousReview() {
        viewModelScope.launch {
            val fila = attemptDao.lastCompleted(examId) ?: return@launch
            _state.value = _state.value.copy(
                phase = ExamPhase.REVIEW,
                answers = ExamProgress.decode(fila.answers),
                result = ToeflItp.evaluate(
                    listeningRaw = fila.listeningRaw,
                    structureRaw = fila.structureRaw,
                    readingRaw = fila.readingRaw
                )
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Guardar y retomar                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Vuelca el punto exacto del examen a la base.
     *
     * Se llama en cada respuesta y en cada cambio de pregunta, no con un
     * temporizador: son escrituras diminutas y así lo que se pierde si Android
     * mata el proceso es, como mucho, una pulsación. Un simulacro dura 115
     * minutos; darlo por perdido entero no es una opción.
     */
    private fun guardar(completado: Boolean = false, resultado: ToeflResult? = null) {
        val s = _state.value
        val fila = ExamAttemptEntity(
            id = attemptId,
            examId = examId,
            startedAt = startedAt,
            finishedAt = if (completado) Time.nowMillis() else 0L,
            listeningRaw = resultado?.listeningRaw ?: 0,
            structureRaw = resultado?.structureRaw ?: 0,
            readingRaw = resultado?.readingRaw ?: 0,
            scaledScore = resultado?.total ?: 0,
            completed = completado,
            answers = ExamProgress.encode(s.answers),
            played = ExamProgress.encodeIds(s.played),
            sectionIndex = s.sectionIndex,
            questionIndex = s.questionIndex,
            secondsLeft = s.secondsLeft
        )
        viewModelScope.launch {
            val id = attemptDao.upsert(fila)
            if (attemptId == 0L) attemptId = id
        }
    }

    /** Retoma el intento a medias en el punto donde se cortó. */
    fun resume() {
        viewModelScope.launch {
            val fila = attemptDao.unfinished(examId) ?: run {
                _state.value = _state.value.copy(phase = ExamPhase.INTRO)
                return@launch
            }
            attemptId = fila.id
            startedAt = fila.startedAt
            _state.value = _state.value.copy(
                phase = ExamPhase.ANSWERING,
                sectionIndex = fila.sectionIndex,
                questionIndex = fila.questionIndex,
                secondsLeft = fila.secondsLeft,
                answers = ExamProgress.decode(fila.answers),
                played = ExamProgress.decodeIds(fila.played)
            )
            arrancarReloj()
        }
    }

    /** Descarta el intento a medias y empieza de cero. */
    fun discardAndRestart() {
        viewModelScope.launch {
            attemptDao.clearUnfinished(examId)
            attemptId = 0L
            _state.value = _state.value.copy(
                phase = ExamPhase.INTRO,
                puedeRetomar = false,
                answers = emptyMap(),
                played = emptySet(),
                sectionIndex = 0,
                questionIndex = 0,
                secondsLeft = 0
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Avance                                                             */
    /* ------------------------------------------------------------------ */

    fun begin() {
        startedAt = Time.nowMillis()
        _state.value = _state.value.copy(phase = ExamPhase.SECTION_INTRO, sectionIndex = 0)
    }

    /** Arranca la sección actual y pone su reloj en marcha. */
    fun startSection() {
        val seccion = _state.value.section ?: return
        _state.value = _state.value.copy(
            phase = ExamPhase.ANSWERING,
            questionIndex = 0,
            secondsLeft = seccion.minutes * 60
        )
        guardar()
        arrancarReloj()
    }

    private fun arrancarReloj() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (_state.value.secondsLeft > 0 && _state.value.phase == ExamPhase.ANSWERING) {
                delay(1000)
                val quedan = _state.value.secondsLeft - 1
                _state.value = _state.value.copy(secondsLeft = quedan.coerceAtLeast(0))
                // Cada quince segundos, para que al retomar el reloj no regale
                // ni robe minutos. Guardarlo cada segundo sería escribir 6.900
                // veces por examen sin ganar nada.
                if (quedan % 15 == 0) guardar()
            }
            // Se acabó el tiempo: la sección se cierra con lo que haya.
            if (_state.value.phase == ExamPhase.ANSWERING) closeSection()
        }
    }

    fun answer(questionId: String, option: Int) {
        _state.value = _state.value.copy(
            answers = _state.value.answers + (questionId to option)
        )
        guardar()
    }

    fun goTo(index: Int) {
        val total = _state.value.questions.size
        if (index in 0 until total) {
            _state.value = _state.value.copy(questionIndex = index)
            guardar()
        }
    }

    fun next() = goTo(_state.value.questionIndex + 1)
    fun previous() = goTo(_state.value.questionIndex - 1)

    /**
     * Reproduce el audio de la pregunta actual y lo marca como oído.
     *
     * Cada personaje suena con un tono distinto (ver `DialoguePitch`). No son
     * dos voces de verdad —el motor de Android solo trae una— pero media
     * sección pregunta *what does the woman mean*, y con los dos turnos leídos
     * igual había que adivinar quién hablaba por el contenido: una dificultad
     * que el examen real no tiene.
     */
    fun play(question: ExamQuestion) {
        if (question.id in _state.value.played) return

        val guion = question.script.ifEmpty {
            _state.value.part?.script.orEmpty()
        }
        if (guion.isNotEmpty()) {
            locator.tts.speakDialogue(guion.map { it.speaker to it.spoken })
        }

        _state.value = _state.value.copy(played = _state.value.played + question.id)
        guardar()
    }

    /** Cierra la sección: por tiempo agotado o porque el usuario la da por hecha. */
    fun closeSection() {
        ticker?.cancel()
        val siguiente = _state.value.sectionIndex + 1
        if (siguiente >= ToeflSection.ORDER.size) {
            finish()
        } else {
            _state.value = _state.value.copy(
                phase = ExamPhase.SECTION_DONE,
                sectionIndex = _state.value.sectionIndex
            )
        }
    }

    /** Pasa a la siguiente sección tras la pantalla de corte. */
    fun nextSection() {
        _state.value = _state.value.copy(
            phase = ExamPhase.SECTION_INTRO,
            sectionIndex = _state.value.sectionIndex + 1
        )
        guardar()
    }

    /* ------------------------------------------------------------------ */
    /*  Cierre                                                             */
    /* ------------------------------------------------------------------ */

    private fun finish() {
        ticker?.cancel()
        val examen = _state.value.exam ?: return
        val respuestas = _state.value.answers

        fun aciertos(s: ToeflSection): Int =
            examen.section(s)?.allQuestions?.count { respuestas[it.id] == it.answer } ?: 0

        val resultado = ToeflItp.evaluate(
            listeningRaw = aciertos(ToeflSection.LISTENING),
            structureRaw = aciertos(ToeflSection.STRUCTURE),
            readingRaw = aciertos(ToeflSection.READING)
        )

        _state.value = _state.value.copy(phase = ExamPhase.FINISHED, result = resultado)
        guardar(completado = true, resultado = resultado)
    }

    /**
     * Salir a medias. El intento queda guardado tal cual, así que al volver a
     * entrar la app ofrece retomarlo.
     */
    fun abandon() {
        ticker?.cancel()
        guardar()
    }

    /**
     * Genera el informe del simulacro en PDF y lo abre.
     *
     * Es un informe, no un certificado: ver `ExamReportPdf`. Si el teléfono no
     * tiene visor de PDF se cae al diálogo de compartir, que siempre existe;
     * quedarse sin hacer nada tras pulsar un botón es la peor opción.
     */
    fun exportarInforme(context: android.content.Context) {
        val resultado = _state.value.result ?: return
        viewModelScope.launch {
            val perfil = locator.progressRepository.ensureProfile()
            val nombre = listOf(perfil.studentName, perfil.studentSurname)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val archivo = ExamReportPdf.render(
                context,
                ExamReportPdf.Data(
                    studentName = nombre,
                    examTitle = _state.value.exam?.title.orEmpty(),
                    result = resultado,
                    takenAt = Time.nowMillis()
                )
            )
            if (!CertificateSharing.abrir(context, archivo)) {
                CertificateSharing.compartir(context, archivo, "Informe de simulacro")
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Revisión                                                           */
    /* ------------------------------------------------------------------ */

    fun review() {
        _state.value = _state.value.copy(phase = ExamPhase.REVIEW)
    }

    fun backToResult() {
        _state.value = _state.value.copy(phase = ExamPhase.FINISHED)
    }

    fun setReviewFilter(filtro: ReviewFilter) {
        _state.value = _state.value.copy(reviewFilter = filtro)
    }

    /**
     * Todas las preguntas del examen ya corregidas, con su guion o su texto.
     *
     * Se numeran dentro de cada sección, como en el cuadernillo, y no de 1 a
     * 140: quien revisa quiere localizar «la 12 de Structure».
     */
    fun revision(): List<ReviewItem> {
        val examen = _state.value.exam ?: return emptyList()
        val respuestas = _state.value.answers
        return ToeflSection.ORDER.flatMap { seccion ->
            val partes = examen.section(seccion)?.parts.orEmpty()
            var n = 0
            partes.flatMap { parte ->
                parte.questions.map { pregunta ->
                    n++
                    ReviewItem(
                        section = seccion,
                        numero = n,
                        question = pregunta,
                        given = respuestas[pregunta.id],
                        // El guion propio de la pregunta manda; si no tiene, es
                        // una conversación larga y el guion vive en la parte.
                        script = pregunta.script.ifEmpty { parte.script },
                        passage = parte.passage
                    )
                }
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }
}
