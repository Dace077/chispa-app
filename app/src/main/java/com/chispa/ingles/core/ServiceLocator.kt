package com.chispa.ingles.core

import android.content.Context
import com.chispa.ingles.data.content.ContentRepository
import com.chispa.ingles.data.db.ChispaDatabase
import com.chispa.ingles.data.prefs.SettingsStore
import com.chispa.ingles.data.repo.ProgressRepository
import com.chispa.ingles.notifications.Notifier
import com.chispa.ingles.speech.TtsManager

/**
 * Inyección de dependencias a mano.
 *
 * El proyecto tiene unas pocas dependencias de larga vida y ningún grafo
 * complicado, así que un localizador con inicialización perezosa hace el mismo
 * trabajo que Hilt sin sumar procesadores de anotaciones ni tiempo de build.
 * Si el día de mañana crece, migrar a Hilt es un cambio mecánico.
 */
class ServiceLocator private constructor(context: Context) {

    val appContext: Context = context.applicationContext

    val database: ChispaDatabase by lazy { ChispaDatabase.get(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val contentRepository: ContentRepository by lazy { ContentRepository(appContext) }

    val progressRepository: ProgressRepository by lazy {
        ProgressRepository(database, settingsStore)
    }

    val notifier: Notifier by lazy { Notifier(appContext) }

    /** TextToSpeech vive mientras viva el proceso: crearlo es caro. */
    val tts: TtsManager by lazy { TtsManager(appContext) }

    companion object {
        @Volatile private var instance: ServiceLocator? = null

        fun from(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context).also { instance = it }
            }
    }
}
