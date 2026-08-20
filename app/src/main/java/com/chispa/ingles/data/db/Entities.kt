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

    /*
     * Datos del alumno. Son los que se imprimen en el certificado, y por eso
     * existen: sin nombre no hay constancia que valga nada.
     *
     * No salen del teléfono nunca. La app no declara INTERNET, así que esto no
     * es una promesa de intenciones: no hay forma física de enviarlos. Todos
     * son opcionales salvo el nombre, y solo se piden cuando hacen falta.
     */
    @ColumnInfo(defaultValue = "''") val studentName: String = "",
    @ColumnInfo(defaultValue = "''") val studentSurname: String = "",
    /** Ciudad y país, solo para la línea de lugar del certificado. */
    @ColumnInfo(defaultValue = "''") val studentCity: String = "",
    /** Momento en que rellenó la hoja de datos. 0 = todavía no la ha rellenado. */
    @ColumnInfo(defaultValue = "0") val studentRegisteredAt: Long = 0L,

    /** Avatar elegido. Se desbloquean avanzando de nivel; ver `Avatars`. */
    @ColumnInfo(defaultValue = "'chispa'") val avatarId: String = "chispa",

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
    /** Nombre completo tal y como debe aparecer impreso. Vacío si no lo ha dado. */
    val fullName: String
        get() = listOf(studentName.trim(), studentSurname.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /** Sin nombre no se puede emitir un certificado a nadie. */
    val canReceiveCertificate: Boolean get() = fullName.isNotBlank()

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

/**
 * Un certificado emitido. Se guarda la fila, no el PDF: el archivo se regenera
 * a partir de estos datos cuando el usuario lo quiere compartir otra vez.
 *
 * `studentName` se guarda como copia del nombre del momento de la emisión, a
 * propósito: si alguien corrige su nombre después, el certificado que ya
 * enseñó a alguien no debe cambiar de titular a sus espaldas.
 */
@Entity(tableName = "certificate", indices = [Index("level")])
data class CertificateEntity(
    @PrimaryKey val folio: String,
    val level: String,
    val studentName: String,
    val issuedAt: Long,
    val lessonsCompleted: Int,
    val accuracy: Int,
    val totalXp: Int
)

/**
 * Aciertos y fallos acumulados por tipo de ejercicio.
 *
 * Existe para responder a una pregunta que hoy la app no sabe contestar: ¿en
 * qué falla sistemáticamente este usuario? Alguien que acierta el 90 % salvo en
 * `listen_and_type` no tiene un problema de inglés, tiene un problema de oído, y
 * merece que se lo digan y le ofrezcan práctica de eso.
 */
@Entity(tableName = "exercise_stat")
data class ExerciseStatEntity(
    @PrimaryKey val type: String,
    val answered: Int = 0,
    val correct: Int = 0,
    val lastAnsweredAt: Long = 0L
) {
    val accuracy: Int get() = if (answered == 0) 0 else (correct * 100) / answered
}

/**
 * Un intento de simulacro de examen de certificación.
 *
 * Se guardan las respuestas correctas por sección (crudas) además del puntaje
 * convertido, porque la conversión a la escala oficial puede afinarse después y
 * los intentos viejos deben poder recalcularse sin haberse perdido el dato.
 */
@Entity(tableName = "exam_attempt", indices = [Index("examId")])
data class ExamAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val examId: String,
    val startedAt: Long,
    val finishedAt: Long = 0L,
    val listeningRaw: Int = 0,
    val structureRaw: Int = 0,
    val readingRaw: Int = 0,
    /** Puntaje en la escala del examen simulado. 0 si el intento no se terminó. */
    val scaledScore: Int = 0,
    val completed: Boolean = false,
    /**
     * Lo que contestó, como `idPregunta:opción` separado por comas.
     *
     * Se guarda por dos motivos distintos y los dos importan: sin esto no se
     * puede revisar el examen después —y revisar los fallos es lo único que
     * enseña de un simulacro— ni reanudar uno que se quedó a medias.
     *
     * Va como texto plano y no como JSON serializado porque son 140 pares de
     * `id:entero` sin nada que escapar, y así se puede leer con sqlite3 al
     * depurar.
     */
    @ColumnInfo(defaultValue = "''") val answers: String = "",
    /** Audios ya reproducidos, separados por comas. Suenan una sola vez. */
    @ColumnInfo(defaultValue = "''") val played: String = "",
    /* --- Punto exacto donde se quedó, para poder retomarlo --- */
    @ColumnInfo(defaultValue = "0") val sectionIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val questionIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val secondsLeft: Int = 0
)
