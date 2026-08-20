package com.chispa.ingles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chispa.ingles.MainActivity
import com.chispa.ingles.R
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.core.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget de racha para la pantalla de inicio.
 *
 * Escrito con `RemoteViews` y no con Glance: Glance traería una dependencia
 * nueva y todo Compose dentro del proceso del launcher, para pintar dos líneas
 * de texto y una barra. No compensa.
 *
 * **Cuándo se actualiza.** `updatePeriodMillis` está a 0 a propósito: Android no
 * refresca por debajo de 30 minutos y aun así despierta el dispositivo cada vez.
 * La racha solo cambia cuando el usuario practica o cuando cambia el día, así
 * que se refresca desde la app ([refresh]) y al arrancar el sistema. Un widget
 * que despierta el móvil cada media hora para no cambiar nada es una batería
 * malgastada.
 */
class StreakWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context)
    }

    override fun onEnabled(context: Context) {
        refresh(context)
    }

    companion object {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Repinta todos los widgets con el estado actual.
         *
         * Se puede llamar desde cualquier sitio y en cualquier momento: si no
         * hay ningún widget puesto, no hace nada.
         */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, StreakWidget::class.java))
            if (ids.isEmpty()) return

            scope.launch {
                val repo = ServiceLocator.from(app).progressRepository
                val perfil = repo.ensureProfile()
                val hoy = repo.xpToday()

                val meta = perfil.dailyGoalXp.coerceAtLeast(1)
                val porcentaje = (hoy * 100 / meta).coerceIn(0, 100)

                // La racha se enseña viva si hoy o ayer hubo meta. Con dos días
                // sin practicar ya está rota, y decir "12 días" sería mentir.
                val viva = perfil.lastGoalDay >= Time.todayEpochDay() - 1
                val racha = if (viva) perfil.currentStreak else 0

                val vistas = RemoteViews(app.packageName, R.layout.widget_streak).apply {
                    setTextViewText(R.id.widget_streak, racha.toString())
                    setTextViewText(
                        R.id.widget_streak_label,
                        if (racha == 1) "día seguido" else app.getString(R.string.widget_days)
                    )
                    setTextViewText(R.id.widget_flame, if (racha > 0) "🔥" else "💤")
                    setTextViewText(R.id.widget_goal, "$hoy/$meta XP")
                    setProgressBar(R.id.widget_progress, 100, porcentaje, false)
                    setOnClickPendingIntent(R.id.widget_root, abrirApp(app))
                }

                ids.forEach { id -> manager.updateAppWidget(id, vistas) }
            }
        }

        private fun abrirApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
