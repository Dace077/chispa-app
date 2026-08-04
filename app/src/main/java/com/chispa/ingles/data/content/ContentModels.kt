package com.chispa.ingles.data.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
 *  DTOs — reflejan 1:1 los archivos JSON de `assets/content`.
 *
 *  Todos los campos son opcionales salvo los imprescindibles: así puedes
 *  añadir contenido nuevo sin romper versiones antiguas del parser, y un
 *  ejercicio mal formado no tumba la app entera (se descarta y ya).
 * ========================================================================= */

@Serializable
data class TrackJson(
    val id: String,
    val title: String,
    val category: String = CATEGORY_CORE,
    val description: String = "",
    val icon: String = "spark",
    /** XP total necesaria para que este módulo aparezca desbloqueado. */
    val unlockXp: Int = 0,
    val units: List<UnitJson> = emptyList()
) {
    companion object {
        const val CATEGORY_CORE = "core"
        const val CATEGORY_EXTRA = "extra"
    }
}

@Serializable
data class UnitJson(
    val id: String,
    val level: String = "A1",
    val title: String,
    val subtitle: String = "",
    val icon: String = "spark",
    val lessons: List<LessonJson> = emptyList()
)

@Serializable
data class LessonJson(
    val id: String,
    val title: String,
    val kind: String = "lesson",
    val vocab: List<VocabJson> = emptyList(),
    val exercises: List<ExerciseJson> = emptyList()
)

@Serializable
data class VocabJson(
    val en: String,
    val es: String,
    val ipa: String? = null,
    val note: String? = null
)

@Serializable
data class ExerciseJson(
    val type: String,
    val prompt: String? = null,
    val options: List<String> = emptyList(),
    val answer: String? = null,
    val alternatives: List<String> = emptyList(),
    @SerialName("audioText") val audioText: String? = null,
    val words: List<String> = emptyList(),
    val pairs: List<List<String>> = emptyList(),
    val sentence: String? = null,
    val translation: String? = null,
    val hint: String? = null,
    val title: String? = null,
    val body: String? = null,
    val examples: List<VocabJson> = emptyList(),
    val direction: String? = null,
    val key: String? = null
)

@Serializable
data class ContentIndexJson(
    val version: Int = 1,
    val files: List<String> = emptyList()
)

@Serializable
data class PlacementTestJson(
    val questions: List<PlacementQuestionJson> = emptyList()
)

@Serializable
data class PlacementQuestionJson(
    val level: String,
    val prompt: String,
    val options: List<String>,
    val answer: String,
    val hint: String? = null
)

data class PlacementQuestion(
    val level: CefrLevel,
    val prompt: String,
    val options: List<String>,
    val answer: String,
    val hint: String?
)

/* =========================================================================
 *  Modelo de dominio — lo que consume la UI.
 *  Sellado para que los `when` de Compose sean exhaustivos y no haya sorpresas.
 * ========================================================================= */

enum class CefrLevel(val label: String, val order: Int) {
    A1("A1", 0),
    A2("A2", 1),
    B1("B1", 2),
    B2("B2", 3),
    EXTRA("Extra", 4);

    companion object {
        fun from(raw: String?): CefrLevel =
            entries.firstOrNull { it.label.equals(raw?.trim(), ignoreCase = true) } ?: EXTRA
    }
}

data class Track(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlockXp: Int,
    val isExtra: Boolean,
    val units: List<LearningUnit>
) {
    val lessonCount: Int get() = units.sumOf { it.lessons.size }
}

data class LearningUnit(
    val id: String,
    val trackId: String,
    val level: CefrLevel,
    val title: String,
    val subtitle: String,
    val icon: String,
    val lessons: List<Lesson>
)

data class Lesson(
    val id: String,
    val unitId: String,
    val trackId: String,
    val level: CefrLevel,
    val title: String,
    val kind: LessonKind,
    val vocab: List<VocabItem>,
    val exercises: List<Exercise>
) {
    /** Ejercicios que puntúan (los informativos no cuentan para el progreso). */
    val gradedCount: Int get() = exercises.count { it.isGraded }
}

enum class LessonKind { LESSON, STORY, CULTURE, PRONUNCIATION }

data class VocabItem(
    val en: String,
    val es: String,
    val ipa: String?,
    val note: String?
) {
    /** Clave estable para el sistema de repetición espaciada. */
    val srsKey: String get() = en.trim().lowercase()
}

sealed interface Exercise {
    /** Identificador estable de lo que se está practicando (para el SRS). */
    val srsKey: String

    /** Los ejercicios informativos (tip, lectura, nota cultural) no se corrigen. */
    val isGraded: Boolean get() = true

    /** Texto en inglés que se puede leer en voz alta con TTS, si aplica. */
    val speakable: String? get() = null

    data class MultipleChoice(
        override val srsKey: String,
        val prompt: String,
        val subPrompt: String?,
        val options: List<String>,
        val answer: String,
        val speakPrompt: Boolean
    ) : Exercise {
        override val speakable: String? get() = if (speakPrompt) prompt else null
    }

    data class Translate(
        override val srsKey: String,
        val prompt: String,
        val answer: String,
        val alternatives: List<String>,
        val toEnglish: Boolean,
        val hint: String?
    ) : Exercise {
        override val speakable: String? get() = if (toEnglish) null else prompt
    }

    data class ListenAndType(
        override val srsKey: String,
        val audioText: String,
        val answer: String,
        val translation: String?
    ) : Exercise {
        override val speakable: String get() = audioText
    }

    data class WordOrder(
        override val srsKey: String,
        val prompt: String,
        val words: List<String>,
        val answer: String
    ) : Exercise {
        override val speakable: String get() = answer
    }

    data class SpeakAndRepeat(
        override val srsKey: String,
        val phrase: String,
        val translation: String?
    ) : Exercise {
        override val speakable: String get() = phrase
    }

    data class MatchingPairs(
        override val srsKey: String,
        val prompt: String,
        val pairs: List<Pair<String, String>>
    ) : Exercise

    data class FillInBlank(
        override val srsKey: String,
        val sentence: String,
        val answer: String,
        val options: List<String>,
        val translation: String?
    ) : Exercise {
        override val speakable: String get() = sentence.replace(BLANK, answer)

        companion object {
            const val BLANK = "___"
        }
    }

    data class Tip(
        override val srsKey: String,
        val title: String,
        val body: String,
        val examples: List<VocabItem>
    ) : Exercise {
        override val isGraded: Boolean get() = false
    }

    data class Reading(
        override val srsKey: String,
        val title: String,
        val body: String,
        val translation: String?
    ) : Exercise {
        override val isGraded: Boolean get() = false
        override val speakable: String get() = body
    }

    data class CultureNote(
        override val srsKey: String,
        val title: String,
        val body: String
    ) : Exercise {
        override val isGraded: Boolean get() = false
    }
}
