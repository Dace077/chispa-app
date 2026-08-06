package com.chispa.ingles.domain

import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.data.content.VocabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de regresión del fallo que reportó el usuario: el repaso preguntaba
 * «¿Qué significa? two yellow bananas» y daba por correcta la respuesta
 * «¿Cuál está bien escrito?», que era el enunciado de ese mismo ejercicio.
 */
class SrsPairingTest {

    private fun mc(
        prompt: String,
        answer: String,
        options: List<String> = listOf(answer, "otra"),
        speakPrompt: Boolean = false,
        key: String = answer.lowercase()
    ) = Exercise.MultipleChoice(
        srsKey = key, prompt = prompt, subPrompt = null,
        options = options, answer = answer,
        speakPrompt = speakPrompt
    )

    /* --------------------- El fallo que hay que impedir -------------------- */

    @Test
    fun `un ejercicio de gramatica no genera tarjeta de vocabulario`() {
        val ejercicio = mc(
            prompt = "¿Cuál está bien escrito?",
            answer = "two yellow bananas",
            options = listOf(
                "two yellow bananas", "two yellows bananas",
                "two bananas yellow", "two banana yellows"
            )
        )
        assertNull(SrsPairing.pairFor(ejercicio))
    }

    @Test
    fun `nunca se guarda una instruccion en español como lado ingles`() {
        // La otra forma del mismo fallo: la respuesta es española y acababa
        // guardada en el campo "en", que luego se lee con voz inglesa.
        val ejercicio = mc(
            prompt = "Tu jefe dice \"I wonder if we might reconsider\". ¿Qué significa?",
            answer = "Cambia el enfoque"
        )
        assertNull(SrsPairing.pairFor(ejercicio))
    }

    @Test
    fun `una opcion multiple nunca genera tarjeta, aunque parezca traducible`() {
        assertNull(SrsPairing.pairFor(mc("¿Cómo se dice 'Soy médico'?", "I am a doctor")))
        assertNull(SrsPairing.pairFor(mc("¿Cuál es el plural de 'city'?", "cities")))
    }

    @Test
    fun `ni una opcion multiple que parece traduccion crea tarjeta`() {
        // `direction` no es de fiar: solo decide si el enunciado se lee en voz
        // alta. En el contenido real hay ejercicios marcados "en_es" con el
        // enunciado en español y "es_en" con la respuesta en español, así que
        // de ese campo no se puede deducir qué lado es cuál.
        assertNull(SrsPairing.pairFor(mc("the key", "la llave", speakPrompt = true)))
        assertNull(SrsPairing.pairFor(mc("la llave", "the key", speakPrompt = false)))
    }

    /* ------------------------ Lo que sí debe pasar ------------------------ */

    @Test
    fun `el vocabulario declarado por el autor manda sobre cualquier deduccion`() {
        val ejercicio = mc("¿Cuál está bien escrito?", "two yellow bananas", key = "banana")
        val vocab = mapOf("banana" to VocabItem(en = "banana", es = "plátano", ipa = null, note = null))
        assertEquals("banana" to "plátano", SrsPairing.pairFor(ejercicio, vocab))
    }

    @Test
    fun `traducir mantiene el ingles en su lado segun la direccion`() {
        val aIngles = Exercise.Translate(
            srsKey = "k", prompt = "la casa es grande", answer = "the house is big",
            alternatives = emptyList(), toEnglish = true, hint = null
        )
        assertEquals("the house is big" to "la casa es grande", SrsPairing.pairFor(aIngles))

        val aEspanol = aIngles.copy(
            prompt = "the house is big", answer = "la casa es grande", toEnglish = false
        )
        assertEquals("the house is big" to "la casa es grande", SrsPairing.pairFor(aEspanol))
    }

    @Test
    fun `sin traduccion no se crea una tarjeta a medias`() {
        val sinTraduccion = Exercise.SpeakAndRepeat(
            srsKey = "k", phrase = "Nice to meet you", translation = null
        )
        assertNull(SrsPairing.pairFor(sinTraduccion))

        val vacia = Exercise.ListenAndType(
            srsKey = "k", audioText = "Good morning",
            answer = "Good morning", translation = "   "
        )
        assertNull(SrsPairing.pairFor(vacia))
    }

    @Test
    fun `las tarjetas informativas no entran nunca en el repaso`() {
        val tip = Exercise.Tip(srsKey = "t", title = "Ojo", body = "Algo importante", examples = emptyList())
        assertNull(SrsPairing.pairFor(tip))
    }
}
