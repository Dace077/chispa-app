package com.chispa.ingles.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}

@Dao
interface LessonProgressDao {

    @Query("SELECT * FROM lesson_progress")
    fun observeAll(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId")
    suspend fun get(lessonId: String): LessonProgressEntity?

    @Upsert
    suspend fun upsert(progress: LessonProgressEntity)

    @Query("SELECT COUNT(*) FROM lesson_progress WHERE timesCompleted > 0")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM lesson_progress WHERE timesCompleted > 0")
    suspend fun completedCount(): Int

    /** Volcado completo, para el respaldo. */
    @Query("SELECT * FROM lesson_progress")
    suspend fun all(): List<LessonProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<LessonProgressEntity>)

    @Query("DELETE FROM lesson_progress")
    suspend fun clear()
}

@Dao
interface SrsCardDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(cards: List<SrsCardEntity>)

    @Upsert
    suspend fun upsert(card: SrsCardEntity)

    @Query("SELECT * FROM srs_card WHERE cardKey = :key")
    suspend fun get(key: String): SrsCardEntity?

    @Query("SELECT * FROM srs_card WHERE cardKey IN (:keys)")
    suspend fun getAll(keys: List<String>): List<SrsCardEntity>

    /**
     * Borra tarjetas por clave. Se usa para sacar del repaso las que se crearon
     * a partir de ejercicios que no eran parejas de traducción; se hace por
     * clave exacta y no por heurística de idioma porque hay inglés legítimo con
     * acentos ("café", "García") que una heurística marcaría por error.
     */
    @Query("DELETE FROM srs_card WHERE cardKey IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>): Int

    /**
     * Reescribe los dos lados de una tarjeta sin tocar su programación.
     *
     * Hace falta porque una clave legítima podía tener guardado un texto
     * equivocado: "cities" es vocabulario de verdad, pero su lado español había
     * quedado como "¿Cuál es el plural de city?". Borrarla perdería el progreso;
     * lo correcto es devolverle el texto que declaró el autor.
     */
    @Query("UPDATE srs_card SET en = :en, es = :es WHERE cardKey = :key")
    suspend fun retext(key: String, en: String, es: String)

    /** Vencidas primero: lo más atrasado y más débil encabeza la cola. */
    @Query(
        """
        SELECT * FROM srs_card
        WHERE dueAt <= :now AND reps > 0
        ORDER BY (:now - dueAt) DESC, strength ASC
        LIMIT :limit
        """
    )
    suspend fun dueCards(now: Long, limit: Int): List<SrsCardEntity>

    @Query("SELECT COUNT(*) FROM srs_card WHERE dueAt <= :now AND reps > 0")
    suspend fun dueCount(now: Long): Int

    @Query("SELECT COUNT(*) FROM srs_card WHERE dueAt <= :now AND reps > 0")
    fun observeDueCount(now: Long): Flow<Int>

    /** Palabras que más se resisten: base del repaso de errores frecuentes. */
    @Query(
        """
        SELECT * FROM srs_card
        WHERE lapses > 0
        ORDER BY wrongStreak DESC, lapses DESC, strength ASC
        LIMIT :limit
        """
    )
    suspend fun hardestCards(limit: Int): List<SrsCardEntity>

    /**
     * Las tarjetas menos consolidadas, se hayan fallado o no.
     *
     * `hardestCards` solo devuelve las que tienen fallos, así que al principio
     * está vacía y la pantalla de repaso se quedaba con medio hueco en blanco
     * justo cuando el usuario más necesita ver que está avanzando.
     */
    @Query(
        """
        SELECT * FROM srs_card
        WHERE reps > 0
        ORDER BY strength ASC, dueAt ASC
        LIMIT :limit
        """
    )
    suspend fun weakestCards(limit: Int): List<SrsCardEntity>

    @Query("SELECT COUNT(*) FROM srs_card WHERE reps > 0")
    fun observeSeenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM srs_card WHERE reps > 0")
    suspend fun seenCount(): Int

    @Query("SELECT COUNT(*) FROM srs_card WHERE strength >= :minStrength")
    fun observeMasteredCount(minStrength: Int): Flow<Int>

    @Query("SELECT * FROM srs_card ORDER BY strength DESC, en ASC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<SrsCardEntity>>

    /** Volcado completo, para el respaldo. */
    @Query("SELECT * FROM srs_card")
    suspend fun all(): List<SrsCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<SrsCardEntity>)

    @Query("DELETE FROM srs_card")
    suspend fun clear()
}

@Dao
interface DailyActivityDao {

    @Query("SELECT * FROM daily_activity WHERE epochDay = :day")
    suspend fun get(day: Long): DailyActivityEntity?

    @Query("SELECT * FROM daily_activity WHERE epochDay = :day")
    fun observe(day: Long): Flow<DailyActivityEntity?>

    @Upsert
    suspend fun upsert(activity: DailyActivityEntity)

    @Query("SELECT * FROM daily_activity WHERE epochDay >= :from ORDER BY epochDay ASC")
    fun observeSince(from: Long): Flow<List<DailyActivityEntity>>

    @Query("SELECT * FROM daily_activity WHERE epochDay BETWEEN :from AND :to")
    suspend fun range(from: Long, to: Long): List<DailyActivityEntity>

    @Query("SELECT COALESCE(SUM(xp), 0) FROM daily_activity WHERE epochDay BETWEEN :from AND :to")
    suspend fun xpBetween(from: Long, to: Long): Int

    @Query("SELECT * FROM daily_activity")
    suspend fun all(): List<DailyActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<DailyActivityEntity>)

    @Query("DELETE FROM daily_activity")
    suspend fun clear()
}

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievement")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT achievementId FROM achievement")
    suspend fun unlockedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(achievement: AchievementEntity)

    @Transaction
    suspend fun unlockAll(achievements: List<AchievementEntity>) {
        achievements.forEach { unlock(it) }
    }

    @Query("SELECT * FROM achievement")
    suspend fun all(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<AchievementEntity>)

    @Query("DELETE FROM achievement")
    suspend fun clear()
}

@Dao
interface CertificateDao {

    @Query("SELECT * FROM certificate ORDER BY issuedAt DESC")
    fun observeAll(): Flow<List<CertificateEntity>>

    @Query("SELECT * FROM certificate WHERE level = :level")
    suspend fun forLevel(level: String): CertificateEntity?

    @Query("SELECT * FROM certificate WHERE folio = :folio")
    suspend fun get(folio: String): CertificateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun issue(certificate: CertificateEntity)

    @Query("SELECT * FROM certificate")
    suspend fun all(): List<CertificateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CertificateEntity>)

    @Query("DELETE FROM certificate")
    suspend fun clear()
}

@Dao
interface ExerciseStatDao {

    @Query("SELECT * FROM exercise_stat ORDER BY answered DESC")
    fun observeAll(): Flow<List<ExerciseStatEntity>>

    @Query("SELECT * FROM exercise_stat")
    suspend fun all(): List<ExerciseStatEntity>

    @Query("SELECT * FROM exercise_stat WHERE type = :type")
    suspend fun get(type: String): ExerciseStatEntity?

    @Upsert
    suspend fun upsert(stat: ExerciseStatEntity)

    @Query("INSERT OR IGNORE INTO exercise_stat (type, answered, correct, lastAnsweredAt) VALUES (:type, 0, 0, 0)")
    suspend fun ensure(type: String)

    @Query(
        """
        UPDATE exercise_stat
        SET answered = answered + 1,
            correct = correct + :correct,
            lastAnsweredAt = :now
        WHERE type = :type
        """
    )
    suspend fun bump(type: String, correct: Int, now: Long)

    /**
     * Suma una respuesta al contador de su tipo.
     *
     * Son dos sentencias y no un `INSERT ... ON CONFLICT DO UPDATE` porque el
     * upsert de SQLite necesita 3.24, que no llegó a Android hasta la 11. Con
     * minSdk 24 hay que servir a teléfonos con SQLite 3.9.
     */
    @Transaction
    suspend fun record(type: String, correct: Boolean, now: Long) {
        ensure(type)
        bump(type, if (correct) 1 else 0, now)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExerciseStatEntity>)

    @Query("DELETE FROM exercise_stat")
    suspend fun clear()
}

@Dao
interface ExamAttemptDao {

    @Query("SELECT * FROM exam_attempt WHERE completed = 1 ORDER BY finishedAt DESC")
    fun observeCompleted(): Flow<List<ExamAttemptEntity>>

    @Query("SELECT * FROM exam_attempt WHERE examId = :examId AND completed = 1 ORDER BY scaledScore DESC LIMIT 1")
    suspend fun bestFor(examId: String): ExamAttemptEntity?

    @Query("SELECT MAX(scaledScore) FROM exam_attempt WHERE completed = 1")
    suspend fun bestScore(): Int?

    /** El intento a medias de este examen, si lo hay. Solo puede haber uno. */
    @Query("SELECT * FROM exam_attempt WHERE examId = :examId AND completed = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun unfinished(examId: String): ExamAttemptEntity?

    /** El último intento terminado, que es el que se puede revisar. */
    @Query("SELECT * FROM exam_attempt WHERE examId = :examId AND completed = 1 ORDER BY finishedAt DESC LIMIT 1")
    suspend fun lastCompleted(examId: String): ExamAttemptEntity?

    @Query("DELETE FROM exam_attempt WHERE examId = :examId AND completed = 0")
    suspend fun clearUnfinished(examId: String)

    @Query("SELECT COUNT(*) FROM exam_attempt WHERE completed = 1")
    fun observeCompletedCount(): Flow<Int>

    @Upsert
    suspend fun upsert(attempt: ExamAttemptEntity): Long

    @Query("SELECT * FROM exam_attempt")
    suspend fun all(): List<ExamAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExamAttemptEntity>)

    @Query("DELETE FROM exam_attempt")
    suspend fun clear()
}
