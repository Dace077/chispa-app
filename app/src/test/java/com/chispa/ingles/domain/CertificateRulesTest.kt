package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.Curriculum
import com.chispa.ingles.data.content.LearningUnit
import com.chispa.ingles.data.content.Lesson
import com.chispa.ingles.data.content.LessonKind
import com.chispa.ingles.data.content.Track
import com.chispa.ingles.data.db.LessonProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas del certificado de nivel.
 *
 * Lo crítico aquí es que un certificado no se emita antes de tiempo: es un
 * documento con el nombre de una persona, y decir que alguien terminó B2 cuando
 * le faltaban tres lecciones no es un fallo cosmético.
 */
class CertificateRulesTest {

    private fun lesson(id: String, unitId: String, trackId: String, level: CefrLevel) =
        Lesson(
            id = id, unitId = unitId, trackId = trackId, level = level,
            title = id, kind = LessonKind.LESSON, vocab = emptyList(), exercises = emptyList()
        )

    /** Un track por nivel con tres lecciones, más un extra que no debe contar. */
    private fun trackFor(level: CefrLevel, isExtra: Boolean = false): Track {
        val trackId = if (isExtra) "extra_${level.label}" else "core_${level.label.lowercase()}"
        val unitId = "${trackId}_u1"
        return Track(
            id = trackId, title = level.label, description = "", icon = "spark",
            unlockXp = 0, isExtra = isExtra,
            units = listOf(
                LearningUnit(
                    id = unitId, trackId = trackId, level = level,
                    title = level.label, subtitle = "", icon = "spark",
                    lessons = (1..3).map { lesson("${unitId}_l$it", unitId, trackId, level) }
                )
            )
        )
    }

    private val curriculum = Curriculum(
        PlacementLadder.LEVELS.map { trackFor(it) } + trackFor(CefrLevel.EXTRA, isExtra = true)
    )

    private fun hecha(lessonId: String, accuracy: Int = 90, xp: Int = 20) =
        lessonId to LessonProgressEntity(
            lessonId = lessonId, unitId = "u", trackId = "t",
            timesCompleted = 1, crown = 1, bestAccuracy = accuracy, xpEarned = xp
        )

    private fun todasLasDe(level: CefrLevel, accuracy: Int = 90) =
        (1..3).associate { hecha("core_${level.label.lowercase()}_u1_l$it", accuracy) }

    @Test
    fun `sin progreso ningun nivel esta completo`() {
        val estado = CertificateRules.levelStatus(curriculum, emptyMap())
        assertTrue(estado.none { it.isComplete })
        assertTrue(CertificateRules.earnedLevels(curriculum, emptyMap()).isEmpty())
    }

    @Test
    fun `terminar todas las lecciones de un nivel lo completa`() {
        val ganados = CertificateRules.earnedLevels(curriculum, todasLasDe(CefrLevel.A1))
        assertEquals(listOf(CefrLevel.A1), ganados.map { it.level })
    }

    @Test
    fun `faltando una sola leccion el nivel no esta completo`() {
        val casi = todasLasDe(CefrLevel.A1).filterKeys { !it.endsWith("l3") }
        val a1 = CertificateRules.levelStatus(curriculum, casi).first { it.level == CefrLevel.A1 }

        assertFalse(a1.isComplete)
        assertEquals(1, a1.remaining)
    }

    @Test
    fun `una leccion empezada pero no terminada no cuenta`() {
        val empezada = mapOf(
            "core_a1_u1_l1" to LessonProgressEntity(
                lessonId = "core_a1_u1_l1", unitId = "u", trackId = "t",
                timesCompleted = 0, crown = 0, bestAccuracy = 40
            )
        )
        val a1 = CertificateRules.levelStatus(curriculum, empezada).first { it.level == CefrLevel.A1 }
        assertEquals(0, a1.completedLessons)
    }

    @Test
    fun `los modulos extra no cuentan para ningun nivel`() {
        val soloExtra = (1..3).associate { hecha("extra_Extra_u1_l$it") }
        val estado = CertificateRules.levelStatus(curriculum, soloExtra)

        assertTrue(estado.none { it.isComplete })
        assertTrue(estado.none { it.level == CefrLevel.EXTRA })
    }

    @Test
    fun `el estado se devuelve en orden A1 a C2`() {
        val niveles = CertificateRules.levelStatus(curriculum, emptyMap()).map { it.level }
        assertEquals(
            listOf(
                CefrLevel.A1, CefrLevel.A2, CefrLevel.B1,
                CefrLevel.B2, CefrLevel.C1, CefrLevel.C2
            ),
            niveles
        )
    }

    @Test
    fun `la precision es la media de las lecciones hechas`() {
        val mezcla = mapOf(
            hecha("core_a1_u1_l1", accuracy = 60),
            hecha("core_a1_u1_l2", accuracy = 90),
            hecha("core_a1_u1_l3", accuracy = 90)
        )
        val a1 = CertificateRules.levelStatus(curriculum, mezcla).first { it.level == CefrLevel.A1 }
        assertEquals(80, a1.accuracy)
    }

    @Test
    fun `el folio es estable para el mismo certificado`() {
        val a = CertificateRules.folio(CefrLevel.B2, "Ana Ruiz", 20_300L)
        val b = CertificateRules.folio(CefrLevel.B2, "Ana Ruiz", 20_300L)
        assertEquals(a, b)
    }

    @Test
    fun `el folio distingue nivel, persona y dia`() {
        val base = CertificateRules.folio(CefrLevel.B2, "Ana Ruiz", 20_300L)
        assertNotEquals(base, CertificateRules.folio(CefrLevel.C1, "Ana Ruiz", 20_300L))
        assertNotEquals(base, CertificateRules.folio(CefrLevel.B2, "Luis Ruiz", 20_300L))
        assertNotEquals(base, CertificateRules.folio(CefrLevel.B2, "Ana Ruiz", 20_301L))
    }

    @Test
    fun `la frase motivacional lleva el nombre de pila y no cambia entre visitas`() {
        val folio = CertificateRules.folio(CefrLevel.B2, "Ana Ruiz", 20_300L)
        val frase = CertificateRules.frase(CefrLevel.B2, "Ana Ruiz", folio)

        assertTrue("la frase debe nombrar a la persona", frase.contains("Ana"))
        assertFalse("no debe quedar el marcador sin sustituir", frase.contains("{n}"))
        assertEquals(frase, CertificateRules.frase(CefrLevel.B2, "Ana Ruiz", folio))
    }

    @Test
    fun `todos los niveles certificables tienen frase y descripcion`() {
        PlacementLadder.LEVELS.forEach { level ->
            val folio = CertificateRules.folio(level, "Ana", 20_300L)
            assertTrue(
                "falta frase para $level",
                CertificateRules.frase(level, "Ana", folio).isNotBlank()
            )
            assertTrue(
                "falta descripcion para $level",
                CertificateRules.descripcion(level).isNotBlank()
            )
        }
    }
}
