package com.chispa.ingles.domain

/* =========================================================================
 *  XP
 * ========================================================================= */

object Xp {
    /** XP por ejercicio acertado a la primera. */
    const val PER_CORRECT = 2

    /** Bonus por terminar una lección. */
    const val LESSON_COMPLETE = 10

    /** Bonus extra si la lección se completó sin fallar ni una. */
    const val PERFECT_LESSON = 5

    /** XP por tarjeta acertada en el modo repaso. */
    const val PER_REVIEW_CORRECT = 3

    /** Bonus por terminar una sesión de repaso. */
    const val REVIEW_COMPLETE = 5

    fun forLesson(correct: Int, total: Int): Int {
        val base = correct * PER_CORRECT + LESSON_COMPLETE
        return if (total > 0 && correct == total) base + PERFECT_LESSON else base
    }
}

/* =========================================================================
 *  Metas diarias
 * ========================================================================= */

enum class DailyGoal(val xp: Int, val label: String, val description: String) {
    CASUAL(10, "Tranquilo", "Unos 3 minutos al día"),
    REGULAR(20, "Constante", "Unos 6 minutos al día"),
    SERIOUS(30, "En serio", "Unos 10 minutos al día"),
    INTENSE(50, "Intenso", "Unos 15 minutos al día");

    companion object {
        fun fromXp(xp: Int): DailyGoal = entries.minByOrNull { kotlin.math.abs(it.xp - xp) } ?: REGULAR
    }
}

/* =========================================================================
 *  Rangos por XP acumulada (nombres propios de Chispa)
 * ========================================================================= */

data class Rank(val name: String, val minXp: Int, val emoji: String)

object Ranks {
    val ALL = listOf(
        Rank("Chispita", 0, "✨"),
        Rank("Curioso", 100, "🔍"),
        Rank("Explorador", 300, "🧭"),
        Rank("Aventurero", 700, "🎒"),
        Rank("Viajero", 1_500, "✈️"),
        Rank("Conversador", 3_000, "💬"),
        Rank("Narrador", 6_000, "📖"),
        Rank("Estratega", 10_000, "♟️"),
        Rank("Maestro", 16_000, "🎓"),
        Rank("Políglota", 25_000, "🌍"),
        Rank("Leyenda", 40_000, "🔥")
    )

    fun current(totalXp: Int): Rank = ALL.last { totalXp >= it.minXp }

    fun next(totalXp: Int): Rank? = ALL.firstOrNull { totalXp < it.minXp }

    /** Progreso 0f..1f dentro del rango actual. */
    fun progress(totalXp: Int): Float {
        val current = current(totalXp)
        val next = next(totalXp) ?: return 1f
        val span = (next.minXp - current.minXp).coerceAtLeast(1)
        return ((totalXp - current.minXp).toFloat() / span).coerceIn(0f, 1f)
    }
}

/* =========================================================================
 *  Logros
 * ========================================================================= */

enum class AchievementKind { LESSONS, STREAK, XP, VOCAB, SPEAKING, REVIEW, PERFECT, SPECIAL }

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val kind: AchievementKind,
    val threshold: Int
)

object Achievements {

    val ALL: List<Achievement> = listOf(
        // Primeros pasos
        Achievement("first_lesson", "Primera chispa", "Completa tu primera lección", "🎉", AchievementKind.LESSONS, 1),
        Achievement("lessons_5", "Calentando", "Completa 5 lecciones", "🔥", AchievementKind.LESSONS, 5),
        Achievement("lessons_15", "Ritmo propio", "Completa 15 lecciones", "🚀", AchievementKind.LESSONS, 15),
        Achievement("lessons_30", "Imparable", "Completa 30 lecciones", "⚡", AchievementKind.LESSONS, 30),
        Achievement("lessons_60", "Coleccionista", "Completa 60 lecciones", "🏆", AchievementKind.LESSONS, 60),
        Achievement("lessons_120", "Devorador", "Completa 120 lecciones", "🦖", AchievementKind.LESSONS, 120),

        // Rachas
        Achievement("streak_3", "Tres seguidos", "3 días de racha", "3️⃣", AchievementKind.STREAK, 3),
        Achievement("streak_7", "Semana completa", "7 días de racha", "📅", AchievementKind.STREAK, 7),
        Achievement("streak_14", "Dos semanas", "14 días de racha", "💪", AchievementKind.STREAK, 14),
        Achievement("streak_30", "Un mes entero", "30 días de racha", "🌙", AchievementKind.STREAK, 30),
        Achievement("streak_100", "Cien días", "100 días de racha", "💯", AchievementKind.STREAK, 100),
        Achievement("streak_365", "Un año contigo", "365 días de racha", "🎂", AchievementKind.STREAK, 365),

        // XP
        Achievement("xp_100", "Primeros 100", "Acumula 100 XP", "✨", AchievementKind.XP, 100),
        Achievement("xp_500", "Medio millar", "Acumula 500 XP", "🌟", AchievementKind.XP, 500),
        Achievement("xp_2000", "Dos mil", "Acumula 2.000 XP", "💫", AchievementKind.XP, 2_000),
        Achievement("xp_10000", "Cinco cifras", "Acumula 10.000 XP", "🌠", AchievementKind.XP, 10_000),

        // Vocabulario
        Achievement("vocab_25", "25 palabras", "Aprende 25 palabras nuevas", "📗", AchievementKind.VOCAB, 25),
        Achievement("vocab_100", "100 palabras", "Aprende 100 palabras nuevas", "📘", AchievementKind.VOCAB, 100),
        Achievement("vocab_300", "300 palabras", "Aprende 300 palabras nuevas", "📚", AchievementKind.VOCAB, 300),
        Achievement("vocab_800", "800 palabras", "Aprende 800 palabras nuevas", "🗂️", AchievementKind.VOCAB, 800),

        // Habla
        Achievement("speak_1", "Primera vez en voz alta", "Completa tu primer ejercicio de pronunciación", "🎤", AchievementKind.SPEAKING, 1),
        Achievement("speak_25", "Sin vergüenza", "25 ejercicios de pronunciación", "🗣️", AchievementKind.SPEAKING, 25),
        Achievement("speak_100", "Voz entrenada", "100 ejercicios de pronunciación", "🎙️", AchievementKind.SPEAKING, 100),

        // Repaso
        Achievement("review_1", "Memoria fresca", "Completa tu primera sesión de repaso", "🔁", AchievementKind.REVIEW, 1),
        Achievement("review_20", "Nada se olvida", "Completa 20 sesiones de repaso", "🧠", AchievementKind.REVIEW, 20),

        // Perfección
        Achievement("perfect_1", "Sin un rasguño", "Termina una lección sin fallar", "💎", AchievementKind.PERFECT, 1),
        Achievement("perfect_10", "Precisión quirúrgica", "10 lecciones perfectas", "🎯", AchievementKind.PERFECT, 10),
        Achievement("perfect_50", "Máquina", "50 lecciones perfectas", "🤖", AchievementKind.PERFECT, 50),

        // Especiales
        Achievement("night_owl", "Ave nocturna", "Practica después de medianoche", "🦉", AchievementKind.SPECIAL, 0),
        Achievement("early_bird", "Madrugador", "Practica antes de las 7 de la mañana", "🌅", AchievementKind.SPECIAL, 0),
        Achievement("comeback", "El regreso", "Vuelve tras 7 días sin practicar", "🔙", AchievementKind.SPECIAL, 0),
        Achievement("first_story", "Primer relato", "Lee tu primera historia completa", "📖", AchievementKind.SPECIAL, 0),
        Achievement("idiom_hunter", "Cazador de modismos", "Completa una lección de idioms", "🎣", AchievementKind.SPECIAL, 0),
        Achievement("business_ready", "Modo oficina", "Completa una lección de Business English", "💼", AchievementKind.SPECIAL, 0),
        Achievement("traveler", "Pasaporte sellado", "Completa una lección de Travel English", "🧳", AchievementKind.SPECIAL, 0)
    )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String): Achievement? = byId[id]

    /**
     * Devuelve los ids que deberían estar desbloqueados con estas estadísticas.
     * Comparar contra lo ya guardado da los logros NUEVOS de esta sesión.
     */
    fun evaluate(stats: PlayerStats): Set<String> {
        val unlocked = mutableSetOf<String>()
        ALL.forEach { achievement ->
            val value = when (achievement.kind) {
                AchievementKind.LESSONS -> stats.lessonsCompleted
                AchievementKind.STREAK -> stats.currentStreak
                AchievementKind.XP -> stats.totalXp
                AchievementKind.VOCAB -> stats.vocabLearned
                AchievementKind.SPEAKING -> stats.speakingExercises
                AchievementKind.REVIEW -> stats.reviewSessions
                AchievementKind.PERFECT -> stats.perfectLessons
                AchievementKind.SPECIAL -> return@forEach
            }
            if (value >= achievement.threshold) unlocked += achievement.id
        }
        unlocked += stats.specialFlags
        return unlocked
    }
}

/** Instantánea de las estadísticas que alimentan los logros. */
data class PlayerStats(
    val lessonsCompleted: Int = 0,
    val currentStreak: Int = 0,
    val totalXp: Int = 0,
    val vocabLearned: Int = 0,
    val speakingExercises: Int = 0,
    val reviewSessions: Int = 0,
    val perfectLessons: Int = 0,
    val specialFlags: Set<String> = emptySet()
)
