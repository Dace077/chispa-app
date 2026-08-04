package com.chispa.ingles.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La guía de gramática se carga sin validación previa: cualquier tema mal
 * formado se descartaría en silencio. Estos tests fijan qué se descarta y qué
 * no, para que ampliar el JSON no haga desaparecer temas sin avisar.
 */
class GrammarModelsTest {

    private fun tema(
        id: String = "g_test",
        title: String = "Tema",
        level: String = "A1",
        area: String = "Verbos",
        question: String = "¿Duda?",
        explanation: String = "Primer párrafo.\n\nSegundo párrafo.",
        examples: List<GrammarExampleJson> = emptyList(),
        mistakes: List<GrammarMistakeJson> = emptyList(),
        keywords: List<String> = emptyList(),
        related: List<String> = emptyList()
    ) = GrammarTopicJson(
        id = id, title = title, level = level, area = area, question = question,
        explanation = explanation, examples = examples, mistakes = mistakes,
        keywords = keywords, related = related
    )

    @Test
    fun `la explicacion se parte en parrafos por linea en blanco`() {
        val t = tema().toDomain()!!
        assertEquals(listOf("Primer párrafo.", "Segundo párrafo."), t.paragraphs)
    }

    @Test
    fun `un tema sin id o sin titulo se descarta`() {
        assertNull(tema(id = "").toDomain())
        assertNull(tema(title = "").toDomain())
    }

    @Test
    fun `un tema sin explicacion pero con ejemplos sigue valiendo`() {
        val t = tema(
            explanation = "",
            examples = listOf(GrammarExampleJson(en = "I am here.", es = "Estoy aquí."))
        ).toDomain()
        assertNotNull(t)
        assertEquals(1, t!!.examples.size)
    }

    @Test
    fun `un tema completamente vacio se descarta`() {
        assertNull(tema(explanation = "", examples = emptyList()).toDomain())
    }

    @Test
    fun `un error tipico sin la forma correcta no se muestra`() {
        val t = tema(
            mistakes = listOf(
                GrammarMistakeJson(wrong = "He work.", right = "He works.", why = "Falta la -s."),
                GrammarMistakeJson(wrong = "Algo mal", right = "")
            )
        ).toDomain()!!
        assertEquals(1, t.mistakes.size)
        assertEquals("He works.", t.mistakes.first().right)
    }

    @Test
    fun `el area vacia cae a General en vez de crear un grupo sin nombre`() {
        assertEquals("General", tema(area = "").toDomain()!!.area)
    }

    /* ------------------------------ Búsqueda ------------------------------ */

    private fun guia(vararg temas: GrammarTopicJson) =
        GrammarGuide(temas.mapNotNull { it.toDomain() })

    @Test
    fun `buscar ignora tildes y mayusculas`() {
        val g = guia(tema(id = "a", title = "Artículos y el artículo cero"))
        assertEquals(1, g.search("articulo").size)
        assertEquals(1, g.search("ARTÍCULO").size)
        assertEquals(1, g.search("Articulos").size)
    }

    @Test
    fun `buscar tambien mira dentro de los ejemplos en ingles`() {
        val g = guia(
            tema(
                id = "a", title = "Presente simple",
                examples = listOf(GrammarExampleJson("She works here.", "Trabaja aquí."))
            ),
            tema(id = "b", title = "Pasado simple", explanation = "Otra cosa.")
        )
        val resultado = g.search("works")
        assertEquals(1, resultado.size)
        assertEquals("a", resultado.first().id)
    }

    @Test
    fun `se encuentra un tema por un sinonimo que no esta en el titulo`() {
        // El caso real: el tema se llama "A, an y the" y el usuario escribe
        // "artículos", que es la palabra que aprendió en el colegio.
        val g = guia(
            tema(
                id = "g_articles", title = "A, an y the",
                question = "¿Cuándo pongo the?",
                keywords = listOf("articulos", "determinantes")
            )
        )
        assertEquals(1, g.search("articulos").size)
        assertEquals(1, g.search("artículos").size)
        assertEquals(1, g.search("determinantes").size)
    }

    @Test
    fun `una busqueda vacia devuelve todo`() {
        val g = guia(tema(id = "a"), tema(id = "b"))
        assertEquals(2, g.search("").size)
        assertEquals(2, g.search("   ").size)
    }

    @Test
    fun `los relacionados que no existen no rompen nada`() {
        val g = guia(
            tema(id = "a", related = listOf("b", "no_existe")),
            tema(id = "b")
        )
        val resueltos = g.find("a")!!.related.mapNotNull { g.find(it) }
        assertEquals(1, resueltos.size)
        assertEquals("b", resueltos.first().id)
    }

    @Test
    fun `agrupar por area no pierde ningun tema`() {
        val g = guia(
            tema(id = "a", area = "Verbos"),
            tema(id = "b", area = "Verbos"),
            tema(id = "c", area = "Sustantivos")
        )
        val agrupado = g.byArea()
        assertEquals(2, agrupado.size)
        assertEquals(3, agrupado.values.sumOf { it.size })
        assertTrue(agrupado.getValue("Verbos").map { it.id }.containsAll(listOf("a", "b")))
    }
}
