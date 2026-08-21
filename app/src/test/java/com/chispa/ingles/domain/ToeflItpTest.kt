package com.chispa.ingles.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Puntuación del simulacro TOEFL ITP.
 *
 * La conversión es una estimación declarada, pero lo que sí tiene que ser
 * exacto es la estructura: los extremos de la escala, la fórmula del total y
 * que más aciertos nunca den menos puntos.
 */
class ToeflItpTest {

    @Test
    fun `el examen tiene 140 preguntas y 115 minutos`() {
        assertEquals(140, ToeflSection.TOTAL_QUESTIONS)
        assertEquals(115, ToeflSection.TOTAL_MINUTES)
        assertEquals(3, ToeflSection.ORDER.size)
    }

    @Test
    fun `cero aciertos da el minimo de la escala`() {
        val r = ToeflItp.evaluate(0, 0, 0)
        assertEquals(ToeflItp.MIN_TOTAL, r.total)
    }

    @Test
    fun `todo correcto da el maximo de la escala`() {
        val r = ToeflItp.evaluate(50, 40, 50)
        assertEquals(ToeflItp.MAX_TOTAL, r.total)
    }

    @Test
    fun `el total nunca se sale de 310 a 677`() {
        for (l in 0..50 step 5) {
            for (s in 0..40 step 5) {
                for (rd in 0..50 step 5) {
                    val t = ToeflItp.evaluate(l, s, rd).total
                    assertTrue("total fuera de escala: $t", t in ToeflItp.MIN_TOTAL..ToeflItp.MAX_TOTAL)
                }
            }
        }
    }

    @Test
    fun `acertar mas nunca baja el puntaje`() {
        // Es la propiedad que no se puede romper nunca: si mejoras, sube.
        ToeflSection.entries.forEach { seccion ->
            var previo = -1
            for (aciertos in 0..seccion.questions) {
                val p = ToeflItp.scaled(seccion, aciertos)
                assertTrue(
                    "en ${seccion.id}, $aciertos aciertos dio $p tras $previo",
                    p >= previo
                )
                previo = p
            }
        }
    }

    @Test
    fun `cada seccion respeta su propio rango`() {
        ToeflSection.entries.forEach { s ->
            assertEquals(s.minScaled, ToeflItp.scaled(s, 0))
            assertEquals(s.maxScaled, ToeflItp.scaled(s, s.questions))
        }
    }

    @Test
    fun `aciertos fuera de rango no rompen nada`() {
        assertEquals(ToeflSection.LISTENING.minScaled, ToeflItp.scaled(ToeflSection.LISTENING, -5))
        assertEquals(ToeflSection.LISTENING.maxScaled, ToeflItp.scaled(ToeflSection.LISTENING, 999))
        val r = ToeflItp.evaluate(-3, 200, 50)
        assertEquals(0, r.listeningRaw)
        assertEquals(40, r.structureRaw)
    }

    @Test
    fun `la formula del total es la oficial`() {
        // (suma de secciones) x 10 / 3
        assertEquals(500, ToeflItp.total(50, 50, 50))
        assertEquals(610, ToeflItp.total(61, 61, 61))
    }

    @Test
    fun `los umbrales se alcanzan en orden`() {
        assertNull(ToeflItp.umbralAlcanzado(350))
        assertEquals(400, ToeflItp.umbralAlcanzado(430)?.puntos)
        assertEquals(500, ToeflItp.umbralAlcanzado(520)?.puntos)
        assertEquals(600, ToeflItp.umbralAlcanzado(677)?.puntos)
    }

    @Test
    fun `siempre hay un siguiente objetivo salvo en lo mas alto`() {
        assertEquals(400, ToeflItp.siguienteUmbral(310)?.puntos)
        assertEquals(550, ToeflItp.siguienteUmbral(500)?.puntos)
        assertNull(ToeflItp.siguienteUmbral(677))
    }

    @Test
    fun `el resumen no destroza las mayusculas del umbral`() {
        // «Nivel intermedio. Muchas licenciaturas...» pasaba a «...intermedio.
        // muchas licenciaturas», con minúscula después del punto.
        val r = ToeflItp.evaluate(10, 8, 10)
        val resumen = ToeflItp.resumen(r)
        assertFalse(resumen, resumen.contains(". muchas"))
        assertTrue(resumen, resumen.contains("nivel intermedio"))
    }

    @Test
    fun `el resumen dice cuantas acertaste y que falta`() {
        val r = ToeflItp.evaluate(30, 24, 30)
        val resumen = ToeflItp.resumen(r)
        assertTrue(resumen, resumen.contains("84 de 140"))
        assertTrue(resumen, resumen.contains("puntos para") || resumen.contains("por encima"))
    }

    @Test
    fun `el aviso de estimacion existe y menciona que no predice el resultado real`() {
        assertTrue(ToeflItp.AVISO_ESTIMACION.contains("estimado"))
        assertTrue(ToeflItp.AVISO_ESTIMACION.contains("no"))
        assertNotNull(ToeflItp.nivelAproximado(500))
    }
}
