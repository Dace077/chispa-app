package com.chispa.ingles.domain

import com.chispa.ingles.data.db.SrsCardEntity
import kotlin.math.max

/**
 * Repetición espaciada — sistema de Leitner de 6 cajas.
 *
 * Cada tarjeta tiene una "fuerza" de 0 a 5. Acertar la sube una caja y aleja la
 * siguiente revisión; fallar la hace retroceder dos cajas y la devuelve a la
 * cola casi de inmediato. Es la versión simple de SM-2: sin factores de
 * facilidad, porque en la práctica sobra para vocabulario de idiomas y es
 * infinitamente más fácil de razonar y depurar.
 */
object Srs {

    const val MAX_STRENGTH = 5

    /** Días hasta la próxima revisión, indexado por fuerza resultante. */
    private val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 14, 30)

    private const val MINUTE = 60_000L
    private const val DAY = 24L * 60L * 60L * 1000L

    /** Al fallar, la tarjeta vuelve en 10 minutos: dentro de la misma sesión. */
    private const val RELEARN_DELAY = 10 * MINUTE

    /**
     * Aplica el resultado de una respuesta a una tarjeta y devuelve su nuevo estado.
     *
     * @param correct si el usuario acertó
     * @param now instante actual en epoch millis
     */
    fun review(card: SrsCardEntity, correct: Boolean, now: Long): SrsCardEntity {
        return if (correct) {
            val strength = (card.strength + 1).coerceAtMost(MAX_STRENGTH)
            card.copy(
                strength = strength,
                dueAt = now + intervalMillis(strength),
                reps = card.reps + 1,
                lastReviewedAt = now,
                wrongStreak = 0
            )
        } else {
            val strength = max(0, card.strength - 2)
            card.copy(
                strength = strength,
                dueAt = now + RELEARN_DELAY,
                reps = card.reps + 1,
                lapses = card.lapses + 1,
                lastReviewedAt = now,
                wrongStreak = card.wrongStreak + 1
            )
        }
    }

    fun intervalMillis(strength: Int): Long {
        val days = INTERVAL_DAYS[strength.coerceIn(0, MAX_STRENGTH)]
        // La caja 0 no espera un día entero: reaparece en la misma sesión.
        return if (days == 0) RELEARN_DELAY else days * DAY
    }

    fun intervalLabel(strength: Int): String = when (strength.coerceIn(0, MAX_STRENGTH)) {
        0 -> "ahora mismo"
        1 -> "en 1 día"
        2 -> "en 3 días"
        3 -> "en 1 semana"
        4 -> "en 2 semanas"
        else -> "en 1 mes"
    }

    /** Etiqueta legible del dominio de una palabra, para la pantalla de repaso. */
    fun strengthLabel(strength: Int): String = when (strength.coerceIn(0, MAX_STRENGTH)) {
        0 -> "Recién vista"
        1 -> "Frágil"
        2 -> "En construcción"
        3 -> "Sólida"
        4 -> "Casi tuya"
        else -> "Dominada"
    }

    /**
     * Cuánto de "vencida" está una tarjeta, normalizado.
     * Se usa solo para ordenar visualmente; la cola real la ordena SQL.
     */
    fun overdueRatio(card: SrsCardEntity, now: Long): Float {
        if (card.dueAt >= now) return 0f
        val interval = intervalMillis(card.strength).coerceAtLeast(MINUTE)
        return ((now - card.dueAt).toFloat() / interval).coerceIn(0f, 10f)
    }
}
