package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.Lesson
import com.chispa.ingles.data.content.LearningUnit
import com.chispa.ingles.data.content.Track
import com.chispa.ingles.data.db.LessonProgressEntity

enum class LessonState {
    /** Aún no accesible: falta terminar la anterior. */
    LOCKED,

    /** Siguiente lección a hacer. Es la que la home destaca. */
    CURRENT,

    /** Accesible pero aún sin completar. */
    AVAILABLE,

    /** Completada al menos una vez, pero sin corona llena. */
    IN_PROGRESS,

    /** Corona al máximo: dominada. */
    MASTERED
}

data class LessonNode(
    val lesson: Lesson,
    val state: LessonState,
    val crown: Int,
    val bestAccuracy: Int
) {
    val isPlayable: Boolean get() = state != LessonState.LOCKED
}

data class UnitNode(
    val unit: LearningUnit,
    val lessons: List<LessonNode>,
    val isUnlocked: Boolean
) {
    val completedLessons: Int get() = lessons.count { it.crown > 0 }
    val progress: Float
        get() = if (lessons.isEmpty()) 0f else completedLessons.toFloat() / lessons.size
    val isComplete: Boolean get() = lessons.isNotEmpty() && completedLessons == lessons.size
}

data class TrackNode(
    val track: Track,
    val units: List<UnitNode>,
    val isUnlocked: Boolean,
    val xpRequired: Int
) {
    val completedLessons: Int get() = units.sumOf { it.completedLessons }
    val totalLessons: Int get() = units.sumOf { it.lessons.size }
    val progress: Float
        get() = if (totalLessons == 0) 0f else completedLessons.toFloat() / totalLessons
}

/**
 * Decide qué está abierto y qué no.
 *
 * Reglas, en una frase: dentro de un módulo se avanza en orden; el test de nivel
 * puede abrir de golpe los niveles por debajo del tuyo; y los módulos extra
 * (idioms, business, viajes…) piden algo de XP para no abrumar en el día uno.
 */
object UnlockRules {

    /**
     * Camino principal (A1 → B2). Se recorre como una única secuencia continua:
     * terminar la última lección de A1 es lo que abre la primera de A2. Por eso
     * el estado de "la anterior está hecha" se arrastra de un nivel al siguiente
     * en vez de reiniciarse en cada archivo de contenido.
     */
    fun buildCorePath(
        tracks: List<Track>,
        progress: Map<String, LessonProgressEntity>,
        totalXp: Int,
        placementLevel: CefrLevel
    ): List<TrackNode> {
        val cursor = PathCursor()
        return tracks.map { track ->
            buildTrack(track, progress, totalXp, placementLevel, cursor)
        }
    }

    /** Estado que se arrastra entre unidades y niveles del camino principal. */
    class PathCursor(
        var previousCompleted: Boolean = true,
        var currentAssigned: Boolean = false
    )

    fun buildTrack(
        track: Track,
        progress: Map<String, LessonProgressEntity>,
        totalXp: Int,
        placementLevel: CefrLevel,
        cursor: PathCursor = PathCursor()
    ): TrackNode {
        val trackUnlocked = totalXp >= track.unlockXp

        val unitNodes = track.units.map { unit ->
            // El test de nivel abre los niveles estrictamente por debajo del asignado.
            val openedByPlacement = !track.isExtra &&
                unit.level.order < placementLevel.order

            val lessonNodes = unit.lessons.map { lesson ->
                val entity = progress[lesson.id]
                val completed = (entity?.timesCompleted ?: 0) > 0
                val crown = entity?.crown ?: 0

                val unlocked = trackUnlocked && (cursor.previousCompleted || openedByPlacement)
                val state = when {
                    !unlocked -> LessonState.LOCKED
                    crown >= ProgressCrowns.MAX -> LessonState.MASTERED
                    completed -> LessonState.IN_PROGRESS
                    !cursor.currentAssigned -> {
                        cursor.currentAssigned = true
                        LessonState.CURRENT
                    }
                    else -> LessonState.AVAILABLE
                }

                cursor.previousCompleted = completed || openedByPlacement
                LessonNode(
                    lesson = lesson,
                    state = state,
                    crown = crown,
                    bestAccuracy = entity?.bestAccuracy ?: 0
                )
            }

            UnitNode(
                unit = unit,
                lessons = lessonNodes,
                isUnlocked = lessonNodes.any { it.state != LessonState.LOCKED }
            )
        }

        return TrackNode(
            track = track,
            units = unitNodes,
            isUnlocked = trackUnlocked,
            xpRequired = track.unlockXp
        )
    }
}

object ProgressCrowns {
    const val MAX = 5
}
