package com.chispa.ingles.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.chispa.ingles.core.Time
import com.chispa.ingles.data.prefs.Settings
import java.util.concurrent.TimeUnit

/**
 * Programa (y desprograma) todos los recordatorios.
 *
 * Nada de esto necesita servidor ni Firebase: WorkManager despierta la app
 * localmente y las notificaciones se construyen en el propio dispositivo.
 */
object ReminderScheduler {

    fun applyAll(context: Context, settings: Settings) {
        if (!settings.notificationsEnabled) {
            cancelAll(context)
            return
        }
        scheduleDailyReminder(context, settings)
        if (settings.streakAlertsEnabled) {
            scheduleStreakRisk(context, settings)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(StreakRiskWorker.NAME)
        }
        if (settings.reviewAlertsEnabled) {
            scheduleReviewCheck(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(ReviewReminderWorker.NAME)
        }
        if (settings.comebackAlertsEnabled) {
            scheduleComebackCheck(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(ComebackWorker.NAME)
        }
    }

    fun scheduleDailyReminder(context: Context, settings: Settings) {
        if (!settings.notificationsEnabled) return
        val delay = Time.millisUntilNext(settings.reminderHour, settings.reminderMinute)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DailyReminderWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleStreakRisk(context: Context, settings: Settings) {
        if (!settings.notificationsEnabled || !settings.streakAlertsEnabled) return
        val delay = Time.millisUntilNext(StreakRiskWorker.HOUR, StreakRiskWorker.MINUTE)
        val request = OneTimeWorkRequestBuilder<StreakRiskWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            StreakRiskWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun scheduleReviewCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(4, TimeUnit.HOURS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReviewReminderWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleComebackCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<ComebackWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Time.millisUntilNext(18, 0), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ComebackWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    private const val TAG = "chispa_reminders"
}
