package com.chispa.ingles.data.backup

import com.chispa.ingles.data.db.AchievementEntity
import com.chispa.ingles.data.db.CertificateEntity
import com.chispa.ingles.data.db.DailyActivityEntity
import com.chispa.ingles.data.db.ExamAttemptEntity
import com.chispa.ingles.data.db.ExerciseStatEntity
import com.chispa.ingles.data.db.LessonProgressEntity
import com.chispa.ingles.data.db.SrsCardEntity
import com.chispa.ingles.data.db.UserProfileEntity
import kotlinx.serialization.Serializable

/**
 * Formato del archivo de respaldo.
 *
 * Es el progreso entero de una persona en un JSON: sirve para cambiar de móvil
 * sin perder meses de trabajo, que hoy es imposible porque la app no tiene
 * cuenta ni nube (y no va a tenerlas: no declara permiso de INTERNET).
 *
 * El archivo lo genera y lo lee la propia app; el usuario decide dónde
 * guardarlo con el selector del sistema. Chispa no lo envía a ninguna parte
 * porque no puede.
 *
 * **Sobre la versión**: si algún día cambia el formato, `formatVersion` permite
 * leer los archivos viejos en vez de rechazarlos. Un respaldo que deja de
 * poder abrirse no es un respaldo.
 */
@Serializable
data class BackupFile(
    val formatVersion: Int = FORMAT_VERSION,
    /** Versión de la app que lo generó. Solo informativo, para diagnosticar. */
    val appVersion: String = "",
    val createdAt: Long = 0L,
    val profile: ProfileBackup? = null,
    val lessons: List<LessonBackup> = emptyList(),
    val cards: List<CardBackup> = emptyList(),
    val activity: List<ActivityBackup> = emptyList(),
    val achievements: List<AchievementBackup> = emptyList(),
    val certificates: List<CertificateBackup> = emptyList(),
    val stats: List<StatBackup> = emptyList(),
    val exams: List<ExamBackup> = emptyList()
) {
    /** Cifras que se le enseñan al usuario ANTES de restaurar nada. */
    val resumen: String
        get() = buildString {
            append("${lessons.count { it.timesCompleted > 0 }} lecciones")
            append(" · ${profile?.totalXp ?: 0} XP")
            append(" · racha de ${profile?.currentStreak ?: 0}")
            append(" · ${cards.size} palabras")
        }

    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class ProfileBackup(
    val motive: String = "",
    val studentName: String = "",
    val studentSurname: String = "",
    val studentCity: String = "",
    val studentRegisteredAt: Long = 0L,
    val avatarId: String = "chispa",
    val placementLevel: String = "A1",
    val onboardingDone: Boolean = false,
    val placementDone: Boolean = false,
    val totalXp: Int = 0,
    val dailyGoalXp: Int = 30,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastGoalDay: Long = 0L,
    val lastActiveDay: Long = 0L,
    val streakFreezes: Int = 0,
    val lastFreezeWeek: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class LessonBackup(
    val lessonId: String,
    val unitId: String = "",
    val trackId: String = "",
    val timesCompleted: Int = 0,
    val crown: Int = 0,
    val bestAccuracy: Int = 0,
    val xpEarned: Int = 0,
    val lastCompletedAt: Long = 0L
)

@Serializable
data class CardBackup(
    val cardKey: String,
    val en: String = "",
    val es: String = "",
    val lessonId: String = "",
    val unitId: String = "",
    val level: String = "A1",
    val strength: Int = 0,
    val dueAt: Long = 0L,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long = 0L,
    val wrongStreak: Int = 0
)

@Serializable
data class ActivityBackup(
    val epochDay: Long,
    val xp: Int = 0,
    val lessonsCompleted: Int = 0,
    val exercisesAnswered: Int = 0,
    val correctAnswers: Int = 0,
    val goalMet: Boolean = false
)

@Serializable
data class AchievementBackup(val achievementId: String, val unlockedAt: Long = 0L)

@Serializable
data class CertificateBackup(
    val folio: String,
    val level: String = "",
    val studentName: String = "",
    val issuedAt: Long = 0L,
    val lessonsCompleted: Int = 0,
    val accuracy: Int = 0,
    val totalXp: Int = 0
)

@Serializable
data class StatBackup(
    val type: String,
    val answered: Int = 0,
    val correct: Int = 0,
    val lastAnsweredAt: Long = 0L
)

@Serializable
data class ExamBackup(
    val examId: String,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val listeningRaw: Int = 0,
    val structureRaw: Int = 0,
    val readingRaw: Int = 0,
    val scaledScore: Int = 0,
    val completed: Boolean = false
)

/* =========================================================================
 *  Conversión entidad <-> respaldo
 *
 *  Se escribe a mano en vez de serializar las entidades de Room directamente:
 *  así el formato del archivo no queda atado al esquema de la base, y un
 *  cambio interno de Room no invalida los respaldos que la gente ya guardó.
 * ========================================================================= */

fun UserProfileEntity.toBackup() = ProfileBackup(
    motive = motive,
    studentName = studentName,
    studentSurname = studentSurname,
    studentCity = studentCity,
    studentRegisteredAt = studentRegisteredAt,
    avatarId = avatarId,
    placementLevel = placementLevel,
    onboardingDone = onboardingDone,
    placementDone = placementDone,
    totalXp = totalXp,
    dailyGoalXp = dailyGoalXp,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastGoalDay = lastGoalDay,
    lastActiveDay = lastActiveDay,
    streakFreezes = streakFreezes,
    lastFreezeWeek = lastFreezeWeek,
    createdAt = createdAt
)

fun ProfileBackup.toEntity(hearts: Int, heartsUpdatedAt: Long) = UserProfileEntity(
    motive = motive,
    studentName = studentName,
    studentSurname = studentSurname,
    studentCity = studentCity,
    studentRegisteredAt = studentRegisteredAt,
    avatarId = avatarId,
    placementLevel = placementLevel,
    onboardingDone = onboardingDone,
    placementDone = placementDone,
    totalXp = totalXp,
    dailyGoalXp = dailyGoalXp,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastGoalDay = lastGoalDay,
    lastActiveDay = lastActiveDay,
    streakFreezes = streakFreezes,
    lastFreezeWeek = lastFreezeWeek,
    // Los corazones NO se restauran: son un límite de ritmo del momento, no
    // progreso. Restaurar un respaldo con cero corazones sería un castigo
    // absurdo por cambiar de teléfono.
    hearts = hearts,
    heartsUpdatedAt = heartsUpdatedAt,
    createdAt = createdAt
)

fun LessonProgressEntity.toBackup() = LessonBackup(
    lessonId, unitId, trackId, timesCompleted, crown, bestAccuracy, xpEarned, lastCompletedAt
)

fun LessonBackup.toEntity() = LessonProgressEntity(
    lessonId = lessonId, unitId = unitId, trackId = trackId,
    timesCompleted = timesCompleted, crown = crown, bestAccuracy = bestAccuracy,
    xpEarned = xpEarned, lastCompletedAt = lastCompletedAt
)

fun SrsCardEntity.toBackup() = CardBackup(
    cardKey, en, es, lessonId, unitId, level, strength, dueAt, reps, lapses,
    lastReviewedAt, wrongStreak
)

fun CardBackup.toEntity() = SrsCardEntity(
    cardKey = cardKey, en = en, es = es, lessonId = lessonId, unitId = unitId,
    level = level, strength = strength, dueAt = dueAt, reps = reps,
    lapses = lapses, lastReviewedAt = lastReviewedAt, wrongStreak = wrongStreak
)

fun DailyActivityEntity.toBackup() = ActivityBackup(
    epochDay, xp, lessonsCompleted, exercisesAnswered, correctAnswers, goalMet
)

fun ActivityBackup.toEntity() = DailyActivityEntity(
    epochDay = epochDay, xp = xp, lessonsCompleted = lessonsCompleted,
    exercisesAnswered = exercisesAnswered, correctAnswers = correctAnswers, goalMet = goalMet
)

fun AchievementEntity.toBackup() = AchievementBackup(achievementId, unlockedAt)
fun AchievementBackup.toEntity() = AchievementEntity(achievementId, unlockedAt)

fun CertificateEntity.toBackup() = CertificateBackup(
    folio, level, studentName, issuedAt, lessonsCompleted, accuracy, totalXp
)

fun CertificateBackup.toEntity() = CertificateEntity(
    folio = folio, level = level, studentName = studentName, issuedAt = issuedAt,
    lessonsCompleted = lessonsCompleted, accuracy = accuracy, totalXp = totalXp
)

fun ExerciseStatEntity.toBackup() = StatBackup(type, answered, correct, lastAnsweredAt)
fun StatBackup.toEntity() = ExerciseStatEntity(type, answered, correct, lastAnsweredAt)

fun ExamAttemptEntity.toBackup() = ExamBackup(
    examId, startedAt, finishedAt, listeningRaw, structureRaw, readingRaw, scaledScore, completed
)

fun ExamBackup.toEntity() = ExamAttemptEntity(
    examId = examId, startedAt = startedAt, finishedAt = finishedAt,
    listeningRaw = listeningRaw, structureRaw = structureRaw, readingRaw = readingRaw,
    scaledScore = scaledScore, completed = completed
)
