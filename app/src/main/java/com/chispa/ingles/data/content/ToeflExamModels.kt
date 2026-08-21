package com.chispa.ingles.data.content

import com.chispa.ingles.domain.ToeflSection
import kotlinx.serialization.Serializable

/* =========================================================================
 *  Simulacros del TOEFL ITP
 *
 *  Un simulacro son 140 preguntas repartidas en tres secciones con formatos
 *  distintos, así que el modelo tiene que dar cabida a:
 *
 *    - conversaciones cortas que se OYEN una vez (Listening A)
 *    - conversaciones y charlas largas con varias preguntas (Listening B y C)
 *    - frases con hueco y frases con cuatro trozos subrayados (Structure)
 *    - textos académicos con diez preguntas cada uno (Reading)
 *
 *  Todo el contenido es ORIGINAL. Las preguntas reales del TOEFL son de ETS y
 *  están protegidas; lo que sí es público es el formato, y es lo que se imita.
 * ========================================================================= */

@Serializable
data class ToeflExamJson(
    val id: String,
    val title: String = "",
    val subtitle: String = "",
    val sections: List<ExamSectionJson> = emptyList()
)

@Serializable
data class ExamSectionJson(
    /** listening | structure | reading */
    val id: String,
    val parts: List<ExamPartJson> = emptyList()
)

@Serializable
data class ExamPartJson(
    val id: String,
    /**
     * short_conversation | long_conversation | talk | completion | error_id | passage
     */
    val kind: String,
    val title: String = "",
    val instructions: String = "",
    /** Guion que lee el TTS. Solo en las partes de listening largas. */
    val script: List<ScriptLineJson> = emptyList(),
    /** Texto académico. Solo en reading. */
    val passage: String = "",
    val questions: List<ExamQuestionJson> = emptyList()
)

@Serializable
data class ScriptLineJson(val speaker: String = "", val text: String)

@Serializable
data class ExamQuestionJson(
    val id: String,
    /** Guion propio de la pregunta. Solo en conversaciones cortas. */
    val script: List<ScriptLineJson> = emptyList(),
    val stem: String = "",
    /**
     * Para `error_id`: los cuatro fragmentos subrayados, en orden.
     * Para el resto: las cuatro opciones de respuesta.
     */
    val options: List<String> = emptyList(),
    /** Índice de la respuesta correcta, de 0 a 3. */
    val answer: Int = 0,
    /** Por qué esa y no las otras. Se enseña al revisar, nunca durante el examen. */
    val explanation: String = ""
)

/* ---------------------------- Modelo de dominio ------------------------- */

data class ToeflExam(
    val id: String,
    val title: String,
    val subtitle: String,
    val sections: List<ExamSection>
) {
    val questionCount: Int get() = sections.sumOf { it.questionCount }

    /** Todas las preguntas en orden, que es como se sirven. */
    val allQuestions: List<ExamQuestion> get() = sections.flatMap { it.allQuestions }

    fun section(section: ToeflSection): ExamSection? =
        sections.firstOrNull { it.section == section }
}

data class ExamSection(
    val section: ToeflSection,
    val parts: List<ExamPart>
) {
    val questionCount: Int get() = parts.sumOf { it.questions.size }
    val allQuestions: List<ExamQuestion> get() = parts.flatMap { it.questions }
}

data class ExamPart(
    val id: String,
    val kind: ExamPartKind,
    val title: String,
    val instructions: String,
    val script: List<ScriptLine>,
    val passage: String,
    val questions: List<ExamQuestion>
)

data class ScriptLine(val speaker: String, val text: String) {
    /** Lo que se manda al TTS: sin el nombre del hablante. */
    val spoken: String get() = text
}

data class ExamQuestion(
    val id: String,
    val partId: String,
    val kind: ExamPartKind,
    val script: List<ScriptLine>,
    val stem: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
) {
    val correctOption: String get() = options.getOrElse(answer) { "" }

    /** En error_id no se elige la respuesta buena: se señala la que está mal. */
    val isErrorId: Boolean get() = kind == ExamPartKind.ERROR_ID
}

enum class ExamPartKind {
    SHORT_CONVERSATION,
    LONG_CONVERSATION,
    TALK,
    COMPLETION,
    ERROR_ID,
    PASSAGE;

    /** Si el enunciado hay que oírlo en vez de leerlo. */
    val isAudio: Boolean
        get() = this == SHORT_CONVERSATION || this == LONG_CONVERSATION || this == TALK

    companion object {
        fun from(raw: String?): ExamPartKind = when (raw?.trim()?.lowercase()) {
            "short_conversation" -> SHORT_CONVERSATION
            "long_conversation" -> LONG_CONVERSATION
            "talk" -> TALK
            "completion" -> COMPLETION
            "error_id" -> ERROR_ID
            "passage" -> PASSAGE
            else -> COMPLETION
        }
    }
}

/* ------------------------------ Conversión ------------------------------ */

/**
 * Devuelve null si el simulacro no está completo.
 *
 * A diferencia del resto del contenido, aquí NO se sirve lo que se pueda: un
 * examen al que le faltan preguntas da un puntaje falso, y el usuario tomaría
 * decisiones reales con ese número (presentarse o no, pagar la cuota o no).
 * Mejor no ofrecerlo que ofrecerlo mal.
 */
fun ToeflExamJson.toDomain(): ToeflExam? {
    val secciones = sections.mapNotNull { s ->
        val seccion = ToeflSection.ORDER.firstOrNull { it.id == s.id } ?: return@mapNotNull null
        val partes = s.parts.map { p ->
            val kind = ExamPartKind.from(p.kind)
            ExamPart(
                id = p.id,
                kind = kind,
                title = p.title,
                instructions = p.instructions,
                script = p.script.map { ScriptLine(it.speaker, it.text) },
                passage = p.passage,
                questions = p.questions.mapNotNull { q ->
                    // Una pregunta sin cuatro opciones o con respuesta fuera de
                    // rango está rota. Se descarta aquí y el recuento de abajo
                    // hará que el examen entero no se ofrezca.
                    if (q.options.size != 4 || q.answer !in 0..3) return@mapNotNull null
                    ExamQuestion(
                        id = q.id,
                        partId = p.id,
                        kind = kind,
                        script = q.script.map { ScriptLine(it.speaker, it.text) },
                        stem = q.stem,
                        options = q.options,
                        answer = q.answer,
                        explanation = q.explanation
                    )
                }
            )
        }
        ExamSection(seccion, partes)
    }

    val examen = ToeflExam(id, title, subtitle, secciones)

    // Cada sección tiene que traer EXACTAMENTE sus preguntas.
    val completo = ToeflSection.ORDER.all { s ->
        examen.section(s)?.questionCount == s.questions
    }
    return if (completo) examen else null
}
