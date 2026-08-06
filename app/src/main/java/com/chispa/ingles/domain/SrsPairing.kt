package com.chispa.ingles.domain

import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.data.content.VocabItem

/**
 * Decide si un ejercicio puede convertirse en tarjeta de repaso, y con qué par
 * inglés/español.
 *
 * Existe por un fallo real: hasta la 1.7.0 esta decisión se tomaba adivinando.
 * Se daba por hecho que todo ejercicio de opción múltiple era una traducción,
 * así que un ejercicio de gramática como
 *
 *     prompt  = "¿Cuál está bien escrito?"
 *     answer  = "two yellow bananas"
 *
 * producía la tarjeta en="two yellow bananas", es="¿Cuál está bien escrito?".
 * Después el repaso la mostraba como «¿Qué significa? two yellow bananas» y
 * daba por correcta la instrucción, con las traducciones de otros ejercicios
 * como distractores. Enseñaba algo falso.
 *
 * La regla ahora es no adivinar nunca: si no consta de dónde sale cada lado,
 * el ejercicio se corrige y suma, pero no genera tarjeta.
 */
object SrsPairing {

    /**
     * @param vocabLookup vocabulario declarado por el autor, que siempre manda.
     * @return el par (inglés, español) o null si el ejercicio no es traducible.
     */
    fun pairFor(
        exercise: Exercise,
        vocabLookup: Map<String, VocabItem> = emptyMap()
    ): Pair<String, String>? {
        vocabLookup[exercise.srsKey]?.let { return sanear(it.en to it.es) }

        val par = when (exercise) {
            // Una opción múltiple NUNCA genera tarjeta por sí sola.
            //
            // Se intentó deducirla del campo `direction`, y no vale: ese campo
            // solo decide si el enunciado se lee en voz alta, y en el contenido
            // real miente a menudo. Hay ejercicios marcados "en_es" con el
            // enunciado en español y ejercicios "es_en" cuya respuesta también
            // es española ("¿Cómo se pronuncia la letra E?" → "Como una 'i'").
            //
            // No se pierde nada: las 930 palabras del curso están declaradas en
            // el `vocab` de su lección y entran en el repaso al abrirla.
            is Exercise.MultipleChoice -> null
            is Exercise.Translate ->
                if (exercise.toEnglish) exercise.answer to exercise.prompt
                else exercise.prompt to exercise.answer
            is Exercise.ListenAndType -> exercise.translation?.let { exercise.answer to it }
            is Exercise.WordOrder -> exercise.answer to exercise.prompt
            is Exercise.SpeakAndRepeat -> exercise.translation?.let { exercise.phrase to it }
            is Exercise.FillInBlank -> exercise.translation?.let {
                exercise.sentence.replace(Exercise.FillInBlank.BLANK, exercise.answer) to it
            }
            // Tarjetas informativas y presentación de vocabulario: no se corrigen.
            else -> null
        }
        return sanear(par)
    }

    /** Una tarjeta a medias no enseña nada y ensucia la cola de repaso. */
    private fun sanear(par: Pair<String, String>?): Pair<String, String>? {
        val (en, es) = par ?: return null
        if (en.isBlank() || es.isBlank()) return null
        return en.trim() to es.trim()
    }
}
