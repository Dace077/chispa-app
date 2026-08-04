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

    @Query("SELECT COUNT(*) FROM srs_card WHERE reps > 0")
    fun observeSeenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM srs_card WHERE reps > 0")
    suspend fun seenCount(): Int

    @Query("SELECT COUNT(*) FROM srs_card WHERE strength >= :minStrength")
    fun observeMasteredCount(minStrength: Int): Flow<Int>

    @Query("SELECT * FROM srs_card ORDER BY strength DESC, en ASC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<SrsCardEntity>>

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

    @Query("DELETE FROM achievement")
    suspend fun clear()
}
