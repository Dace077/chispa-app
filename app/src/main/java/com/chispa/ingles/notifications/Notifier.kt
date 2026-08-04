package com.chispa.ingles.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ArrayRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chispa.ingles.MainActivity
import com.chispa.ingles.R
import kotlin.random.Random

/**
 * Construye y lanza los recordatorios locales.
 *
 * Todos los textos salen de `res/values/motivation.xml` y se eligen al azar sin
 * repetir el último mostrado: recibir once veces la misma frase es la forma más
 * rápida de que alguien desactive las notificaciones para siempre.
 */
class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_DAILY,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_reminder_desc) },

            NotificationChannel(
                CHANNEL_STREAK,
                context.getString(R.string.channel_streak_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_streak_desc)
                enableVibration(true)
            },

            NotificationChannel(
                CHANNEL_REVIEW,
                context.getString(R.string.channel_review_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_review_desc) },

            NotificationChannel(
                CHANNEL_COMEBACK,
                context.getString(R.string.channel_comeback_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_comeback_desc) }
        )
        channels.forEach(system::createNotificationChannel)
    }

    private fun canPost(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            manager.areNotificationsEnabled()
        }

    /** Escoge una frase al azar, evitando repetir la última usada de ese array. */
    private fun pick(@ArrayRes arrayRes: Int, vararg formatArgs: Any): String {
        val options = context.resources.getStringArray(arrayRes)
        if (options.isEmpty()) return ""
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "last_$arrayRes"
        val last = prefs.getInt(key, -1)

        var index = Random.nextInt(options.size)
        if (options.size > 1 && index == last) index = (index + 1) % options.size
        prefs.edit().putInt(key, index).apply()

        val template = options[index]
        return if (formatArgs.isEmpty()) template else String.format(template, *formatArgs)
    }

    private fun openAppIntent(destination: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            destination?.let { putExtra(MainActivity.EXTRA_DESTINATION, it) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, destination.hashCode(), intent, flags)
    }

    private fun build(
        channel: String,
        title: String,
        text: String,
        priority: Int,
        destination: String?
    ): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_violet_500))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(destination))
            .build()

    private fun post(id: Int, notification: Notification) {
        if (!canPost()) return
        runCatching { manager.notify(id, notification) }
    }

    /* ---------------------------------------------------------------- */

    fun showDailyReminder(streak: Int) {
        post(
            ID_DAILY,
            build(
                channel = CHANNEL_DAILY,
                title = context.getString(R.string.app_name),
                text = pick(R.array.reminder_daily, streak),
                priority = NotificationCompat.PRIORITY_DEFAULT,
                destination = DEST_HOME
            )
        )
    }

    fun showStreakRisk(streak: Int) {
        post(
            ID_STREAK,
            build(
                channel = CHANNEL_STREAK,
                title = "Racha de $streak días en peligro",
                text = pick(R.array.reminder_streak_risk, streak),
                priority = NotificationCompat.PRIORITY_HIGH,
                destination = DEST_HOME
            )
        )
    }

    fun showReviewReady(dueCount: Int) {
        post(
            ID_REVIEW,
            build(
                channel = CHANNEL_REVIEW,
                title = "Repaso listo",
                text = pick(R.array.reminder_review, dueCount),
                priority = NotificationCompat.PRIORITY_LOW,
                destination = DEST_REVIEW
            )
        )
    }

    fun showComeback(daysAway: Int) {
        val array = when {
            daysAway >= 14 -> R.array.reminder_comeback_14
            daysAway >= 7 -> R.array.reminder_comeback_7
            else -> R.array.reminder_comeback_3
        }
        post(
            ID_COMEBACK,
            build(
                channel = CHANNEL_COMEBACK,
                title = context.getString(R.string.app_name),
                text = pick(array),
                priority = NotificationCompat.PRIORITY_DEFAULT,
                destination = DEST_HOME
            )
        )
    }

    companion object {
        private const val PREFS = "chispa_notifier"

        const val CHANNEL_DAILY = "daily_reminder"
        const val CHANNEL_STREAK = "streak_risk"
        const val CHANNEL_REVIEW = "review_ready"
        const val CHANNEL_COMEBACK = "comeback"

        private const val ID_DAILY = 1001
        private const val ID_STREAK = 1002
        private const val ID_REVIEW = 1003
        private const val ID_COMEBACK = 1004

        const val DEST_HOME = "home"
        const val DEST_REVIEW = "review"
    }
}
