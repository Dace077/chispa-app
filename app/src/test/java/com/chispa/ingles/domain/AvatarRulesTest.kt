package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Desbloqueo de avatares.
 *
 * Lo importante aquí es que nadie luzca un avatar que no se ganó, incluso
 * después de reiniciar el progreso, y que siempre quede al menos uno puesto.
 */
class AvatarRulesTest {

    @Test
    fun `sin ningun nivel terminado solo esta Chispa`() {
        assertEquals(listOf(Avatar.CHISPA), AvatarRules.unlocked(emptySet()))
    }

    @Test
    fun `cada nivel terminado abre su avatar`() {
        val conA1 = AvatarRules.unlocked(setOf(CefrLevel.A1))
        assertEquals(listOf(Avatar.CHISPA, Avatar.TRUFA), conA1)

        val conA1A2 = AvatarRules.unlocked(setOf(CefrLevel.A1, CefrLevel.A2))
        assertEquals(listOf(Avatar.CHISPA, Avatar.TRUFA, Avatar.NUBE), conA1A2)
    }

    @Test
    fun `terminar C2 sin lo anterior solo abre el suyo`() {
        // Puede pasar: el test de nivel coloca alto y alguien va directo a C2.
        val abiertos = AvatarRules.unlocked(setOf(CefrLevel.C2))
        assertEquals(listOf(Avatar.CHISPA, Avatar.XOLOTL), abiertos)
    }

    @Test
    fun `con todos los niveles estan los siete`() {
        val todos = CefrLevel.entries.filter { it != CefrLevel.EXTRA }.toSet()
        assertEquals(Avatar.entries.size, AvatarRules.unlocked(todos).size)
        assertEquals(7, Avatar.entries.size)
    }

    @Test
    fun `el siguiente por conseguir es el primero bloqueado`() {
        assertEquals(Avatar.TRUFA, AvatarRules.next(emptySet()))
        assertEquals(Avatar.NUBE, AvatarRules.next(setOf(CefrLevel.A1)))
    }

    @Test
    fun `cuando estan todos no hay siguiente`() {
        val todos = CefrLevel.entries.filter { it != CefrLevel.EXTRA }.toSet()
        assertEquals(null, AvatarRules.next(todos))
    }

    @Test
    fun `un avatar guardado que ya no corresponde cae a Chispa`() {
        // Escenario real: el usuario tenia C1 y reinicio su progreso.
        assertEquals(Avatar.CHISPA, AvatarRules.resolve("flecha", emptySet()))
        assertEquals(Avatar.FLECHA, AvatarRules.resolve("flecha", setOf(CefrLevel.C1)))
    }

    @Test
    fun `un id desconocido o vacio no rompe nada`() {
        assertEquals(Avatar.CHISPA, AvatarRules.resolve(null, emptySet()))
        assertEquals(Avatar.CHISPA, AvatarRules.resolve("", emptySet()))
        assertEquals(Avatar.CHISPA, AvatarRules.resolve("dragon", emptySet()))
    }

    @Test
    fun `Chispa nunca esta bloqueada`() {
        assertTrue(AvatarRules.isUnlocked(Avatar.CHISPA, emptySet()))
        assertFalse(AvatarRules.isUnlocked(Avatar.XOLOTL, emptySet()))
    }

    @Test
    fun `los identificadores son unicos y hay uno por nivel`() {
        val ids = Avatar.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        val niveles = Avatar.entries.mapNotNull { it.unlockLevel }
        assertEquals(niveles.size, niveles.toSet().size)
        assertEquals(
            CefrLevel.entries.filter { it != CefrLevel.EXTRA }.toSet(),
            niveles.toSet()
        )
    }
}
