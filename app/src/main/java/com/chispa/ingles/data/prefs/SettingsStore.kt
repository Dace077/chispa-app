package com.chispa.ingles.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chispa_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Acento con el que hablará el TTS. Todos son voces locales del dispositivo. */
enum class Accent(val label: String, val language: String, val country: String, val flag: String) {
    US("Estadounidense", "en", "US", "🇺🇸"),
    UK("Británico", "en", "GB", "🇬🇧"),
    AU("Australiano", "en", "AU", "🇦🇺")
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val streakAlertsEnabled: Boolean = true,
    val reviewAlertsEnabled: Boolean = true,
    val comebackAlertsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val speechRate: Float = 0.9f,
    val accent: Accent = Accent.US,
    val autoPlayAudio: Boolean = true,
    /** Estadísticas ligeras que alimentan logros y no encajan en el modelo de progreso. */
    val speakingExercises: Int = 0,
    val reviewSessions: Int = 0,
    val perfectLessons: Int = 0,
    val specialFlags: Set<String> = emptySet(),
    val lastOpenedDay: Long = 0L
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val STREAK_ALERTS = booleanPreferencesKey("streak_alerts")
        val REVIEW_ALERTS = booleanPreferencesKey("review_alerts")
        val COMEBACK_ALERTS = booleanPreferencesKey("comeback_alerts")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val ACCENT = stringPreferencesKey("accent")
        val AUTOPLAY = booleanPreferencesKey("autoplay_audio")
        val SPEAKING_COUNT = intPreferencesKey("speaking_count")
        val REVIEW_SESSIONS = intPreferencesKey("review_sessions")
        val PERFECT_LESSONS = intPreferencesKey("perfect_lessons")
        val SPECIAL_FLAGS = stringPreferencesKey("special_flags")
        val LAST_OPENED_DAY = longPreferencesKey("last_opened_day")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { error ->
            // Un archivo corrupto no debe impedir abrir la app: se cae a los valores por defecto.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            Settings(
                themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: "SYSTEM") }
                    .getOrDefault(ThemeMode.SYSTEM),
                notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
                reminderHour = prefs[Keys.REMINDER_HOUR] ?: 19,
                reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
                streakAlertsEnabled = prefs[Keys.STREAK_ALERTS] ?: true,
                reviewAlertsEnabled = prefs[Keys.REVIEW_ALERTS] ?: true,
                comebackAlertsEnabled = prefs[Keys.COMEBACK_ALERTS] ?: true,
                soundEnabled = prefs[Keys.SOUND] ?: true,
                hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
                speechRate = prefs[Keys.SPEECH_RATE] ?: 0.9f,
                accent = runCatching { Accent.valueOf(prefs[Keys.ACCENT] ?: "US") }
                    .getOrDefault(Accent.US),
                autoPlayAudio = prefs[Keys.AUTOPLAY] ?: true,
                speakingExercises = prefs[Keys.SPEAKING_COUNT] ?: 0,
                reviewSessions = prefs[Keys.REVIEW_SESSIONS] ?: 0,
                perfectLessons = prefs[Keys.PERFECT_LESSONS] ?: 0,
                specialFlags = (prefs[Keys.SPECIAL_FLAGS] ?: "")
                    .split(',').filter { it.isNotBlank() }.toSet(),
                lastOpenedDay = prefs[Keys.LAST_OPENED_DAY] ?: 0L
            )
        }

    suspend fun current(): Settings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.NOTIFICATIONS] = enabled }
    suspend fun setReminderTime(hour: Int, minute: Int) = edit {
        it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
        it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }
    suspend fun setStreakAlerts(enabled: Boolean) = edit { it[Keys.STREAK_ALERTS] = enabled }
    suspend fun setReviewAlerts(enabled: Boolean) = edit { it[Keys.REVIEW_ALERTS] = enabled }
    suspend fun setComebackAlerts(enabled: Boolean) = edit { it[Keys.COMEBACK_ALERTS] = enabled }
    suspend fun setSound(enabled: Boolean) = edit { it[Keys.SOUND] = enabled }
    suspend fun setHaptics(enabled: Boolean) = edit { it[Keys.HAPTICS] = enabled }
    suspend fun setSpeechRate(rate: Float) = edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.4f, 1.4f) }
    suspend fun setAccent(accent: Accent) = edit { it[Keys.ACCENT] = accent.name }
    suspend fun setAutoPlay(enabled: Boolean) = edit { it[Keys.AUTOPLAY] = enabled }
    suspend fun setLastOpenedDay(day: Long) = edit { it[Keys.LAST_OPENED_DAY] = day }

    suspend fun incrementSpeaking(by: Int = 1) = edit {
        it[Keys.SPEAKING_COUNT] = (it[Keys.SPEAKING_COUNT] ?: 0) + by
    }

    suspend fun incrementReviewSessions() = edit {
        it[Keys.REVIEW_SESSIONS] = (it[Keys.REVIEW_SESSIONS] ?: 0) + 1
    }

    suspend fun incrementPerfectLessons() = edit {
        it[Keys.PERFECT_LESSONS] = (it[Keys.PERFECT_LESSONS] ?: 0) + 1
    }

    suspend fun addSpecialFlag(flag: String) = edit { prefs ->
        val flags = (prefs[Keys.SPECIAL_FLAGS] ?: "").split(',').filter { it.isNotBlank() }.toMutableSet()
        flags += flag
        prefs[Keys.SPECIAL_FLAGS] = flags.joinToString(",")
    }

    /** Borra estadísticas derivadas; las preferencias de la app se conservan. */
    suspend fun resetStats() = edit { prefs ->
        prefs[Keys.SPEAKING_COUNT] = 0
        prefs[Keys.REVIEW_SESSIONS] = 0
        prefs[Keys.PERFECT_LESSONS] = 0
        prefs[Keys.SPECIAL_FLAGS] = ""
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
