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
        AchievementEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ChispaDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun srsCardDao(): SrsCardDao
    abstract fun dailyActivityDao(): DailyActivityDao
    abstract fun achievementDao(): AchievementDao

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
                    // El contenido real vive en assets; la BD solo guarda progreso.
                    // Si algún día cambia el esquema, preferimos recrear antes que
                    // dejar al usuario con una app que no abre.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
