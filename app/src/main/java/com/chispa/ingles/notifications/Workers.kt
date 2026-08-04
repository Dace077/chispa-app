package com.chispa.ingles.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.core.Time

/**
 * Recordatorio diario a la hora elegida por el usuario.
 *
 * Se reprograma a sí mismo al terminar, en vez de usar trabajo periódico: así la
 * notificación cae siempre a la hora exacta configurada aunque el usuario la
 * cambie, y no se acumula el desfase que arrastra el trabajo periódico.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.from(applicationContext)
        val settings = locator.settingsStore.current()

        if (settings.notificationsEnabled) {
            val profile = locator.progressRepository.ensureProfile()
            val today = locator.database.dailyActivityDao().get(Time.todayEpochDay())
            val goalMet = today?.goalMet == true

            // Si ya cumplió la meta hoy, no hay nada que recordar.
            if (!goalMet) {
                locator.notifier.showDailyReminder(profile.currentStreak)
            }
        }

        ReminderScheduler.scheduleDailyReminder(applicationContext, settings)
        return Result.success()
    }

    companion object {
        const val NAME = "chispa_daily_reminder"
    }
}

/**
 * Aviso urgente en las últimas horas del día si la racha sigue sin asegurarse.
 */
class StreakRiskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.from(applicationContext)
        val settings = locator.settingsStore.current()

        if (settings.notificationsEnabled && settings.streakAlertsEnabled) {
            val profile = locator.progressRepository.ensureProfile()
            val today = locator.database.dailyActivityDao().get(Time.todayEpochDay())
            val goalMet = today?.goalMet == true

            // Solo tiene sentido asustar a quien tiene algo que perder.
            if (!goalMet && profile.currentStreak > 0) {
                locator.notifier.showStreakRisk(profile.currentStreak)
            }
        }

        ReminderScheduler.scheduleStreakRisk(applicationContext, settings)
        return Result.success()
    }

    companion object {
        const val NAME = "chispa_streak_risk"
        /** Hora a la que se avisa: suficiente margen para hacer una lección. */
        const val HOUR = 21
        const val MINUTE = 30
    }
}

/**
 * Avisa cuando el algoritmo de repetición espaciada acumula suficientes tarjetas
 * vencidas como para que merezca la pena abrir la app.
 */
class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.from(applicationContext)
        val settings = locator.settingsStore.current()

        if (settings.notificationsEnabled && settings.reviewAlertsEnabled) {
            val due = locator.progressRepository.dueCount()
            val hour = Time.nowDateTime().hour
            // Nada de avisos de madrugada, y solo si hay volumen que justifique el aviso.
            if (due >= MIN_DUE_CARDS && hour in 9..21) {
                locator.notifier.showReviewReady(due)
            }
        }
        return Result.success()
    }

    companion object {
        const val NAME = "chispa_review_check"
        private const val MIN_DUE_CARDS = 8
    }
}

/**
 * Mensajes de re-enganche a los 3, 7 y 14 días sin abrir la app.
 * Se envía como mucho uno por hito, nunca en bucle.
 */
class ComebackWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.from(applicationContext)
        val settings = locator.settingsStore.current()

        if (settings.notificationsEnabled && settings.comebackAlertsEnabled) {
            val profile = locator.progressRepository.ensureProfile()
            val lastDay = maxOf(settings.lastOpenedDay, profile.lastActiveDay)
            if (lastDay > 0L) {
                val daysAway = (Time.todayEpochDay() - lastDay).toInt()
                if (daysAway in MILESTONES) {
                    locator.notifier.showComeback(daysAway)
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val NAME = "chispa_comeback_check"
        private val MILESTONES = setOf(3, 7, 14)
    }
}
