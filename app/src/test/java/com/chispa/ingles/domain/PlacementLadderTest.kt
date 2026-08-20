package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Recorre la escalera adaptativa entera para cada perfil de usuario.
 *
 * Esta lógica decide en qué nivel arranca alguien: si se equivoca, o mandas a
 * un principiante a C2 o haces que un C1 repita "hello, my name is". Merece
 * tests de verdad y no una comprobación a ojo.
 */
class PlacementLadderTest {

    /**
     * Simula el test completo. `passes` dice si el usuario supera el bloque de
     * cada nivel. Devuelve el nivel final y cuántos bloques contestó.
     */
    private fun simulate(passes: Map<CefrLevel, Boolean>): Pair<CefrLevel, Int> {
        var level = PlacementLadder.START
        var direction = PlacementLadder.Direction.UP
        var best: CefrLevel? = null
        var blocks = 0

        while (true) {
            blocks++
            val passed = passes[level] ?: false
            // Se pasa el mejor nivel ANTERIOR, igual que hace el ViewModel.
            val previousBest = best
            if (passed) best = level

            when (val step = PlacementLadder.nextStep(level, passed, direction, previousBest)) {
                is PlacementLadder.Step.Finish -> return step.level to blocks
                is PlacementLadder.Step.Continue -> {
                    level = step.level
                    direction = step.direction
                }
            }
            if (blocks > 10) error("La escalera no termina: bucle infinito")
        }
    }

    private fun allOf(vararg levels: CefrLevel) = levels.associateWith { true }

    @Test
    fun `usuario que lo aprueba todo acaba en C2`() {
        val (level, blocks) = simulate(
            allOf(CefrLevel.B1, CefrLevel.B2, CefrLevel.C1, CefrLevel.C2)
        )
        assertEquals(CefrLevel.C2, level)
        assertEquals(4, blocks)   // B1, B2, C1, C2 = 12 preguntas
    }

    @Test
    fun `falla solo el ultimo bloque y se queda en C1`() {
        val (level, blocks) = simulate(allOf(CefrLevel.B1, CefrLevel.B2, CefrLevel.C1))
        assertEquals(CefrLevel.C1, level)
        assertEquals(4, blocks)
    }

    @Test
    fun `aprueba hasta B2 y se queda ahi`() {
        val (level, _) = simulate(allOf(CefrLevel.B1, CefrLevel.B2))
        assertEquals(CefrLevel.B2, level)
    }

    @Test
    fun `aprueba solo el bloque inicial y se queda en B1`() {
        val (level, blocks) = simulate(allOf(CefrLevel.B1))
        assertEquals(CefrLevel.B1, level)
        assertEquals(2, blocks)   // B1 y el intento de B2 = 6 preguntas
    }

    @Test
    fun `suspende B1 pero aprueba A2`() {
        val (level, blocks) = simulate(allOf(CefrLevel.A2))
        assertEquals(CefrLevel.A2, level)
        assertEquals(2, blocks)   // B1 y A2 = 6 preguntas
    }

    @Test
    fun `no aprueba nada y acaba en A1 sin preguntas de mas`() {
        val (level, blocks) = simulate(emptyMap())
        assertEquals(CefrLevel.A1, level)
        // B1 y A2. No se pregunta el bloque de A1: el resultado ya no puede
        // cambiar y solo serviría para alargar el test a quien menos aguanta.
        assertEquals(2, blocks)
    }

    @Test
    fun `nadie contesta mas preguntas de las prometidas`() {
        val perfiles = listOf(
            emptyMap(),
            allOf(CefrLevel.A2),
            allOf(CefrLevel.B1),
            allOf(CefrLevel.B1, CefrLevel.B2),
            allOf(CefrLevel.B1, CefrLevel.B2, CefrLevel.C1),
            allOf(CefrLevel.B1, CefrLevel.B2, CefrLevel.C1, CefrLevel.C2)
        )
        perfiles.forEach { perfil ->
            val (_, blocks) = simulate(perfil)
            val preguntas = blocks * PlacementLadder.BLOCK_SIZE
            assert(preguntas <= PlacementLadder.MAX_QUESTIONS) {
                "Un perfil llegó a $preguntas preguntas, más del máximo prometido"
            }
        }
    }

    @Test
    fun `la escalera siempre termina, se conteste lo que se conteste`() {
        // Fuerza bruta sobre las 64 combinaciones posibles de aprobado/suspenso.
        val niveles = PlacementLadder.LEVELS
        for (mask in 0 until (1 shl niveles.size)) {
            val passes = niveles.mapIndexed { i, lvl -> lvl to ((mask shr i) and 1 == 1) }.toMap()
            val (level, _) = simulate(passes)   // lanza si entra en bucle
            assert(level in niveles) { "Nivel resultante fuera de la escala: $level" }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Repetición del test                                                */
    /* ------------------------------------------------------------------ */

    @Test
    fun `repetir el test puede subir el nivel`() {
        assertEquals(
            CefrLevel.C1,
            PlacementLadder.afterRetake(actual = CefrLevel.B1, nuevo = CefrLevel.C1)
        )
    }

    @Test
    fun `repetir el test nunca baja el nivel`() {
        // Es el invariante que protege el contenido ya desbloqueado: si bajara,
        // UnlockRules volvería a cerrar niveles que el usuario ya tenía abiertos.
        assertEquals(
            CefrLevel.C1,
            PlacementLadder.afterRetake(actual = CefrLevel.C1, nuevo = CefrLevel.A1)
        )
    }

    @Test
    fun `repetir y sacar lo mismo lo deja igual`() {
        PlacementLadder.LEVELS.forEach { nivel ->
            assertEquals(nivel, PlacementLadder.afterRetake(nivel, nivel))
        }
    }

    @Test
    fun `desde cualquier par, el resultado nunca es menor que el actual`() {
        val niveles = PlacementLadder.LEVELS
        niveles.forEach { actual ->
            niveles.forEach { nuevo ->
                val salida = PlacementLadder.afterRetake(actual, nuevo)
                assert(salida.order >= actual.order) {
                    "Repetir con actual=$actual y nuevo=$nuevo bajó a $salida"
                }
            }
        }
    }
}
