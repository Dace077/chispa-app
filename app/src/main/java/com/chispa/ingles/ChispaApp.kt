package com.chispa.ingles

import android.app.Application
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChispaApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val locator = ServiceLocator.from(this)
        locator.notifier.ensureChannels()

        appScope.launch {
            // Asegura que exista el perfil y que los recordatorios queden en cola
            // aunque el usuario nunca haya entrado en Configuración.
            locator.progressRepository.ensureProfile()
            ReminderScheduler.applyAll(this@ChispaApp, locator.settingsStore.current())
        }
    }
}
