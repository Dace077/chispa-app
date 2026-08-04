package com.chispa.ingles.domain

import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.data.content.VocabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La secuencia didáctica de una lección.
 *
 * El fallo que motivó todo esto: la app examinaba de palabras que nunca había
 * enseñado, porque la lista `vocab` solo alimentaba la repetición espaciada y
 * no se mostraba jamás. Estos tests fijan que eso no pueda volver a pasar.
 */
class LessonPedagogyTest {

    private val vocab = listOf(
        VocabItem("hello", "hola", "/həˈloʊ/", null),
        VocabItem("goodbye", "adiós", null, null)
    )

    private fun mc(key: String) = Exercise.MultipleChoice(
        srsKey = key, prompt = key, subPrompt = null,
        options = listOf("a", "b"), answer = "a", speakPrompt = false
    )

    private fun translate(key: String) = Exercise.Translate(
        srsKey = key, prompt = key, answer = key,
        alternatives = emptyList(), toEnglish = true, hint = null
    )

    private fun listen(key: String) = Exercise.ListenAndType(
        srsKey = key, audioText = key, answer = key, translation = null
    )

    private fun pairs(key: String) = Exercise.MatchingPairs(
        srsKey = key, prompt = key, pairs = listOf("a" to "b", "c" to "d")
    )

    private fun speak(key: String) = Exercise.SpeakAndRepeat(
        srsKey = key, phrase = key, translation = null
    )

    private fun tip(key: String) = Exercise.Tip(
        srsKey = key, title = key, body = "cuerpo", examples = emptyList()
    )

    private fun prepare(exercises: List<Exercise>, conVocab: List<VocabItem> = vocab) =
        LessonPedagogy.prepare(
            exercises = exercises,
            vocab = conVocab,
            lessonId = "l1",
            lessonTitle = "Saludos",
            respectAuthorOrder = false
        )

    @Test
    fun `el vocabulario se presenta antes de cualquier ejercicio que lo examine`() {
        val resultado = prepare(listOf(translate("hello"), mc("goodbye")))

        val posicionIntro = resultado.indexOfFirst { it is Exercise.VocabIntro }
        assertTrue("Debe existir una tarjeta de vocabulario", posicionIntro >= 0)

        val primerExamen = resultado.indexOfFirst { it.isGraded }
        assertTrue(
            "La presentación debe ir antes del primer ejercicio puntuable",
            posicionIntro < primerExamen
        )
    }

    @Test
    fun `se reconoce antes de producir`() {
        val desordenados = listOf(
            translate("t"), speak("s"), listen("l"), pairs("p"), mc("m")
        )
        val orden = prepare(desordenados).map { LessonPedagogy.rank(it) }
        assertEquals(
            "Los ejercicios deben ir de menor a mayor exigencia",
            orden.sorted(), orden
        )
    }

    @Test
    fun `hablar en voz alta va siempre al final`() {
        val resultado = prepare(listOf(speak("s"), mc("m"), translate("t")))
        assertTrue(
            "El ejercicio de habla debe cerrar la lección",
            resultado.last() is Exercise.SpeakAndRepeat
        )
    }

    @Test
    fun `un consejo de gramatica sigue abriendo la leccion`() {
        val resultado = prepare(listOf(mc("m"), tip("regla")))
        assertTrue("El consejo va primero", resultado.first() is Exercise.Tip)
        assertTrue("Y el vocabulario justo detrás", resultado[1] is Exercise.VocabIntro)
    }

    @Test
    fun `sin vocabulario no se inventa una tarjeta vacia`() {
        val resultado = prepare(listOf(mc("m")), conVocab = emptyList())
        assertTrue(resultado.none { it is Exercise.VocabIntro })
    }

    @Test
    fun `no se duplica la tarjeta si ya se preparo antes`() {
        val unaVez = prepare(listOf(mc("m")))
        val dosVeces = LessonPedagogy.prepare(
            exercises = unaVez, vocab = vocab,
            lessonId = "l1", lessonTitle = "Saludos", respectAuthorOrder = false
        )
        assertEquals(1, dosVeces.count { it is Exercise.VocabIntro })
    }

    @Test
    fun `respetar el orden del autor desactiva la reordenacion`() {
        val original = listOf(translate("t"), mc("m"))
        val resultado = LessonPedagogy.prepare(
            exercises = original, vocab = vocab,
            lessonId = "l1", lessonTitle = "Saludos", respectAuthorOrder = true
        )
        // La tarjeta se añade igual, pero el resto conserva su orden.
        assertTrue(resultado.first() is Exercise.VocabIntro)
        assertTrue(resultado[1] is Exercise.Translate)
        assertTrue(resultado[2] is Exercise.MultipleChoice)
    }

    @Test
    fun `la tarjeta no se desborda con lecciones de mucho vocabulario`() {
        val muchas = (1..30).map { VocabItem("w$it", "p$it", null, null) }
        val intro = prepare(listOf(mc("m")), conVocab = muchas)
            .filterIsInstance<Exercise.VocabIntro>().first()
        assertTrue(
            "Una tarjeta con 30 palabras no se lee, se salta",
            intro.items.size <= LessonPedagogy.MAX_INTRO_ITEMS
        )
    }

    @Test
    fun `la presentacion no cuenta como ejercicio puntuable`() {
        val intro = prepare(listOf(mc("m"))).filterIsInstance<Exercise.VocabIntro>().first()
        assertTrue("No debe restar corazones ni contar para la nota", !intro.isGraded)
    }
}
