package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.LearningUnit
import com.chispa.ingles.data.content.Lesson
import com.chispa.ingles.data.content.LessonKind
import com.chispa.ingles.data.content.Track
import com.chispa.ingles.data.db.LessonProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de desbloqueo del camino principal.
 *
 * Lo que más importa aquí no es qué está abierto, sino cuál se marca como
 * "la siguiente". A quien el test coloca en C2 no se le puede señalar la
 * primera lección de A1 como próximo paso.
 */
class UnlockRulesTest {

    private fun lesson(id: String, unitId: String, trackId: String, level: CefrLevel) =
        Lesson(
            id = id, unitId = unitId, trackId = trackId, level = level,
            title = id, kind = LessonKind.LESSON, vocab = emptyList(), exercises = emptyList()
        )

    /** Un track por nivel, con una unidad de dos lecciones cada uno. */
    private fun trackFor(level: CefrLevel): Track {
        val trackId = "core_${level.label.lowercase()}"
        val unitId = "${level.label.lowercase()}_u1"
        return Track(
            id = trackId,
            title = level.label,
            description = "",
            icon = "spark",
            unlockXp = 0,
            isExtra = false,
            units = listOf(
                LearningUnit(
                    id = unitId, trackId = trackId, level = level,
                    title = level.label, subtitle = "", icon = "spark",
                    lessons = listOf(
                        lesson("${unitId}_l1", unitId, trackId, level),
                        lesson("${unitId}_l2", unitId, trackId, level)
                    )
                )
            )
        )
    }

    private val camino = PlacementLadder.LEVELS.map(::trackFor)

    private fun construir(
        placement: CefrLevel,
        progress: Map<String, LessonProgressEntity> = emptyMap()
    ) = UnlockRules.buildCorePath(camino, progress, totalXp = 0, placementLevel = placement)

    private fun lecciones(nodes: List<TrackNode>) =
        nodes.flatMap { it.units }.flatMap { it.lessons }

    @Test
    fun `sin test de nivel la actual es la primerisima leccion`() {
        val actual = lecciones(construir(CefrLevel.A1))
            .first { it.state == LessonState.CURRENT }
        assertEquals("a1_u1_l1", actual.lesson.id)
    }

    @Test
    fun `colocado en C2 la actual es la primera de C2, no la de A1`() {
        val nodos = construir(CefrLevel.C2)
        val actuales = lecciones(nodos).filter { it.state == LessonState.CURRENT }

        assertEquals("Debe haber exactamente una lección marcada como actual", 1, actuales.size)
        assertEquals("c2_u1_l1", actuales.first().lesson.id)
    }

    @Test
    fun `colocado en C1 la actual es la primera de C1`() {
        val actual = lecciones(construir(CefrLevel.C1))
            .first { it.state == LessonState.CURRENT }
        assertEquals("c1_u1_l1", actual.lesson.id)
    }

    @Test
    fun `los niveles por debajo del test quedan jugables para repasar`() {
        val nodos = construir(CefrLevel.C2)
        val deA1 = lecciones(nodos).filter { it.lesson.level == CefrLevel.A1 }
        assertTrue("A1 debe quedar abierto para repaso", deA1.all { it.isPlayable })
        assertTrue(
            "A1 no debe estar destacado como siguiente paso",
            deA1.none { it.state == LessonState.CURRENT }
        )
    }

    @Test
    fun `el camino es secuencial entre niveles distintos`() {
        // Sin test de nivel, la primera de A2 sigue bloqueada hasta acabar A1.
        val nodos = construir(CefrLevel.A1)
        val primeraA2 = lecciones(nodos).first { it.lesson.id == "a2_u1_l1" }
        assertEquals(LessonState.LOCKED, primeraA2.state)
    }

    @Test
    fun `terminar una leccion abre la siguiente`() {
        val hecha = mapOf(
            "a1_u1_l1" to LessonProgressEntity(
                lessonId = "a1_u1_l1", unitId = "a1_u1", trackId = "core_a1",
                timesCompleted = 1, crown = 1
            )
        )
        val nodos = construir(CefrLevel.A1, hecha)
        val segunda = lecciones(nodos).first { it.lesson.id == "a1_u1_l2" }
        assertEquals(LessonState.CURRENT, segunda.state)
    }

    @Test
    fun `nunca hay mas de una leccion marcada como actual`() {
        PlacementLadder.LEVELS.forEach { nivel ->
            val actuales = lecciones(construir(nivel)).count { it.state == LessonState.CURRENT }
            assertTrue(
                "Con placement $nivel había $actuales lecciones actuales",
                actuales <= 1
            )
        }
    }
}
