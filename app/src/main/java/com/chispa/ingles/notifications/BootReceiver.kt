package com.chispa.ingles.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.widget.StreakWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Al reiniciar el teléfono (o actualizar la app) WorkManager conserva el trabajo
 * periódico, pero el recordatorio diario es un trabajo único que se reprograma a
 * sí mismo: si el sistema lo descartó, aquí lo devolvemos a la cola.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val locator = ServiceLocator.from(appContext)
                locator.notifier.ensureChannels()
                ReminderScheduler.applyAll(appContext, locator.settingsStore.current())
                // El widget también hay que repintarlo: al reiniciar puede haber
                // cambiado el día, y con él la racha viva y la meta de hoy.
                StreakWidget.refresh(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
