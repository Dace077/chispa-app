package com.chispa.ingles.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        LessonProgressEntity::class,
        SrsCardEntity::class,
        DailyActivityEntity::class,
        AchievementEntity::class,
        CertificateEntity::class,
        ExerciseStatEntity::class,
        ExamAttemptEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ChispaDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun srsCardDao(): SrsCardDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun achievementDao(): AchievementDao
    abstract fun certificateDao(): CertificateDao
    abstract fun exerciseStatDao(): ExerciseStatDao
    abstract fun examAttemptDao(): ExamAttemptDao

    companion object {
        const val NAME = "chispa.db"

        @Volatile private var instance: ChispaDatabase? = null

        fun get(context: Context): ChispaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChispaDatabase::class.java,
                    NAME
                )
                    // Migraciones escritas a mano, sin red de seguridad destructiva.
                    //
                    // Antes había un `fallbackToDestructiveMigration()` aquí. Se
                    // quitó al añadir el nombre del alumno y los certificados: con
                    // él puesto, publicar esa versión habría borrado el progreso de
                    // todo el mundo en silencio. Si una migración falla preferimos
                    // que la app reviente y nos enteremos, antes que vaciarle la
                    // racha de un año a alguien sin decírselo.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
