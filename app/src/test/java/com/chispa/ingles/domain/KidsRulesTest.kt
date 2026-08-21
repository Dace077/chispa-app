package com.chispa.ingles.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsRulesTest {

    private fun items(n: Int) = (1..n).map {
        KidsItem("k$it", "word$it", "palabra$it", KidsArtKind.COLOR, "#FFFFFF")
    }

    @Test
    fun `con menos de dos dibujos no hay ronda posible`() {
        // Con uno solo no se puede preguntar «¿cuál es?»: no hay elección.
        assertNull(KidsRules.ronda(items(1), 0, emptySet()))
    }

    @Test
    fun `empieza con dos opciones y sube a tres`() {
        assertEquals(2, KidsRules.opcionesEnRonda(0))
        assertEquals(2, KidsRules.opcionesEnRonda(2))
        assertEquals(3, KidsRules.opcionesEnRonda(3))
    }

    @Test
    fun `la opcion correcta esta siempre entre las que se enseñan`() {
        repeat(50) { i ->
            val r = KidsRules.ronda(items(6), i % KidsRules.RONDAS, emptySet())
            assertNotNull(r)
            assertTrue(r!!.opciones.any { it.id == r.correcto.id })
        }
    }

    @Test
    fun `no se repiten dibujos dentro de una misma ronda`() {
        repeat(50) { i ->
            val r = KidsRules.ronda(items(6), i % KidsRules.RONDAS, emptySet())!!
            assertEquals(r.opciones.size, r.opciones.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `la respuesta no cae siempre en el mismo sitio`() {
        // Es el fallo que tenía el test de nivel de los adultos: 12 de 18
        // respuestas en la primera posición, así que se aprobaba tocando
        // siempre la misma. A los tres años eso se aprende todavía más rápido.
        val posiciones = (0 until KidsRules.RONDAS).map { ronda ->
            val r = KidsRules.ronda(items(6), ronda, emptySet())!!
            r.opciones.indexOfFirst { it.id == r.correcto.id }
        }
        assertTrue("posiciones=$posiciones", posiciones.toSet().size >= 2)
    }

    @Test
    fun `prefiere preguntar por lo que aun no ha salido`() {
        val todos = items(4)
        val yaVistos = todos.take(3).map { it.id }.toSet()
        repeat(20) {
            val r = KidsRules.ronda(todos, 0, yaVistos)!!
            assertEquals("k4", r.correcto.id)
        }
    }

    @Test
    fun `cuando ya salieron todos vuelve a empezar sin romperse`() {
        val todos = items(3)
        val r = KidsRules.ronda(todos, 0, todos.map { it.id }.toSet())
        assertNotNull(r)
    }

    @Test
    fun `nunca pide mas opciones de las que hay`() {
        // Un mundo de dos elementos no puede enseñar tres tarjetas.
        val r = KidsRules.ronda(items(2), 5, emptySet())!!
        assertEquals(2, r.opciones.size)
    }

    @Test
    fun `un mundo con dos elementos es jugable y con uno no`() {
        assertTrue(KidsWorld("w", "Mundo", "⭐", "#FFFFFF", items(2)).jugable)
        assertTrue(!KidsWorld("w", "Mundo", "⭐", "#FFFFFF", items(1)).jugable)
    }

    @Test
    fun `una letra suena con su palabra de ejemplo, no sola`() {
        // «B» a secas hace que el motor de voz diga «bi» y punto. Lo que enseña
        // el sonido es la palabra detrás.
        val letra = KidsItem(
            "k_b", "B", "B de ball", KidsArtKind.LETTER, "B", say = "B. Ball."
        )
        assertEquals("B. Ball.", letra.spoken)
    }

    @Test
    fun `sin say se pronuncia la palabra tal cual`() {
        val gato = KidsItem("k_cat", "cat", "gato", KidsArtKind.ANIMAL, "michi")
        assertEquals("cat", gato.spoken)
    }

    @Test
    fun `el tipo de dibujo se lee sin distinguir mayusculas y lo raro se descarta`() {
        assertEquals(KidsArtKind.ANIMAL, KidsArtKind.from("Animal"))
        assertEquals(KidsArtKind.COUNT, KidsArtKind.from("  count "))
        assertEquals(KidsArtKind.UNKNOWN, KidsArtKind.from("dibujo raro"))
        assertEquals(KidsArtKind.LETTER, KidsArtKind.from("letter"))
        assertEquals(KidsArtKind.CRITTER, KidsArtKind.from("CRITTER"))
        assertEquals(KidsArtKind.UNKNOWN, KidsArtKind.from(null))
    }
}
