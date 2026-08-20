package com.chispa.ingles.domain

import com.chispa.ingles.data.db.ExerciseStatEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detección de puntos débiles.
 *
 * Lo delicado aquí no es calcular porcentajes: es no acusar a nadie de flojear
 * con cuatro respuestas, y no señalar diferencias que no significan nada.
 */
class ExerciseStatsTest {

    private fun stat(type: String, answered: Int, correct: Int) =
        ExerciseStatEntity(type = type, answered = answered, correct = correct)

    @Test
    fun `sin datos no se opina`() {
        val stats = ExerciseStats.read(emptyList())
        assertTrue(ExerciseStats.weakSpots(stats).isEmpty())
        assertTrue(ExerciseStats.summary(stats).contains("Practica un poco más"))
    }

    @Test
    fun `con muestra pequena no se senala nada`() {
        // 2 de 5 es un 40 %, pero cinco respuestas no dicen nada de nadie.
        val stats = ExerciseStats.read(listOf(stat("listen_and_type", 5, 2)))
        assertTrue(ExerciseStats.weakSpots(stats).isEmpty())
    }

    @Test
    fun `detecta el tipo que se queda muy por debajo de la media`() {
        val stats = ExerciseStats.read(
            listOf(
                stat("multiple_choice", 100, 92),
                stat("translate_to_en", 100, 88),
                stat("listen_and_type", 50, 27)     // 54 %
            )
        )
        val flojos = ExerciseStats.weakSpots(stats)
        assertEquals(listOf("listen_and_type"), flojos.map { it.type })
    }

    @Test
    fun `no senala diferencias pequenas`() {
        // 88 % contra una media de 90 % no es un problema, es ruido.
        val stats = ExerciseStats.read(
            listOf(
                stat("multiple_choice", 100, 91),
                stat("word_order", 100, 88)
            )
        )
        assertTrue(ExerciseStats.weakSpots(stats).isEmpty())
    }

    @Test
    fun `un tipo malo en terminos absolutos se senala aunque la media tambien sea baja`() {
        val stats = ExerciseStats.read(
            listOf(
                stat("multiple_choice", 60, 36),   // 60 %
                stat("listen_and_type", 60, 33)    // 55 %
            )
        )
        // Los dos bajan del suelo absoluto: los dos se señalan.
        assertEquals(2, ExerciseStats.weakSpots(stats).size)
    }

    @Test
    fun `los flojos salen del peor al menos malo`() {
        val stats = ExerciseStats.read(
            listOf(
                stat("multiple_choice", 100, 95),
                stat("listen_and_type", 40, 20),   // 50 %
                stat("speak_and_repeat", 40, 24)   // 60 %
            )
        )
        assertEquals(
            listOf("listen_and_type", "speak_and_repeat"),
            ExerciseStats.weakSpots(stats).map { it.type }
        )
    }

    @Test
    fun `la precision global pondera por numero de respuestas`() {
        val stats = ExerciseStats.read(
            listOf(
                stat("multiple_choice", 90, 90),   // 100 % con mucho peso
                stat("word_order", 10, 0)          // 0 % con poco peso
            )
        )
        assertEquals(90, ExerciseStats.overallAccuracy(stats))
    }

    @Test
    fun `los tipos informativos no aparecen`() {
        val stats = ExerciseStats.read(
            listOf(stat("info", 50, 50), stat("multiple_choice", 20, 18))
        )
        assertEquals(listOf("multiple_choice"), stats.map { it.type })
    }

    @Test
    fun `un tipo desconocido se ignora en vez de romper la pantalla`() {
        val stats = ExerciseStats.read(listOf(stat("tipo_del_futuro", 30, 10)))
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `el resumen nombra el punto debil cuando hay uno solo`() {
        val stats = ExerciseStats.read(
            listOf(
                stat("multiple_choice", 100, 92),
                stat("listen_and_type", 40, 20)
            )
        )
        val resumen = ExerciseStats.summary(stats)
        assertTrue(resumen, resumen.contains("escuchar y escribir"))
        assertTrue(resumen, resumen.contains("50 %"))
    }

    @Test
    fun `el resumen felicita cuando no hay puntos debiles`() {
        val stats = ExerciseStats.read(
            listOf(
                stat("multiple_choice", 100, 91),
                stat("word_order", 100, 89)
            )
        )
        assertTrue(ExerciseStats.summary(stats).contains("sin puntos débiles"))
    }
}
