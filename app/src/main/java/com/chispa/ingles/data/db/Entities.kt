package com.chispa.ingles.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Perfil único del usuario. Siempre hay exactamente una fila con id = 1.
 * Aquí vive todo el estado de gamificación que debe sobrevivir a cierres de app.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,

    /** Motivo elegido en el onboarding (personaliza mensajes, no el contenido). */
    val motive: String = "",

    /** Nivel sugerido por el test de nivel: A1 / A2 / B1. */
    val placementLevel: String = "A1",
    val onboardingDone: Boolean = false,
    val placementDone: Boolean = false,

    val totalXp: Int = 0,
    val dailyGoalXp: Int = 20,

    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    /** Último día (epochDay) en que el usuario cumplió su meta diaria. */
    val lastGoalDay: Long = 0L,
    /** Último día (epochDay) en que abrió la app. */
    val lastActiveDay: Long = 0L,

    /** Comodines para salvar la racha. Se gana 1 por semana practicada. */
    val streakFreezes: Int = 0,
    /** Semana ISO en la que se otorgó el último comodín, para no repetir. */
    val lastFreezeWeek: String = "",

    val hearts: Int = MAX_HEARTS,
    /** Momento en que se perdió el último corazón, base para la regeneración. */
    val heartsUpdatedAt: Long = 0L,

    val createdAt: Long = 0L
) {
    companion object {
        const val SINGLETON_ID = 1
        const val MAX_HEARTS = 5
        /** Un corazón se recupera cada 4 horas. */
        const val HEART_REFILL_MILLIS = 4L * 60L * 60L * 1000L
    }
}

/**
 * Progreso por lección. Solo existen filas para lecciones ya empezadas.
 * `crown` es el nivel de dominio (0-5): repetir una lección lo sube.
 */
@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val unitId: String,
    val trackId: String,
    val timesCompleted: Int = 0,
    val crown: Int = 0,
    val bestAccuracy: Int = 0,
    val xpEarned: Int = 0,
    val lastCompletedAt: Long = 0L
)

/**
 * Tarjeta del sistema de repetición espaciada (Leitner de 6 cajas).
 *
 * `strength` 0..5 y `dueAt` en epoch millis. El repaso prioriza lo más vencido.
 */
@Entity(
    tableName = "srs_card",
    indices = [Index("dueAt"), Index("strength")]
)
data class SrsCardEntity(
    @PrimaryKey val cardKey: String,
    val en: String,
    val es: String,
    val lessonId: String,
    val unitId: String,
    val level: String,
    @ColumnInfo(defaultValue = "0") val strength: Int = 0,
    val dueAt: Long = 0L,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long = 0L,
    /** Se marca cuando el usuario falla; alimenta el repaso de "errores frecuentes". */
    val wrongStreak: Int = 0
)

/** Un registro por día con actividad. Alimenta el calendario y la liga personal. */
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val epochDay: Long,
    val xp: Int = 0,
    val lessonsCompleted: Int = 0,
    val exercisesAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val goalMet: Boolean = false
)

/** Logros desbloqueados. La definición vive en código; aquí solo la fecha. */
@Entity(tableName = "achievement")
data class AchievementEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long
)
