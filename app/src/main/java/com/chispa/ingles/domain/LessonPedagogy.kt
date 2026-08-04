package com.chispa.ingles.domain

import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.data.content.VocabItem

/**
 * Cómo se ordena una lección.
 *
 * El principio es el de siempre en didáctica de idiomas: **primero entender,
 * después reconocer, después producir**. No se le puede pedir a alguien que
 * escriba "See you later" si nunca ha visto esas tres palabras juntas.
 *
 * La primera versión de la app servía los ejercicios en el orden en que
 * estaban escritos en el JSON, mezclando traducir y escribir al dictado con
 * ejercicios de reconocimiento. Para un usuario con base funcionaba; para uno
 * que parte de cero, era imposible.
 */
object LessonPedagogy {

    /**
     * Peso didáctico de cada tipo de ejercicio, de menor a mayor exigencia.
     *
     * 0-1  Exposición: se te enseña algo, no se te pide nada.
     * 2-3  Reconocimiento: eliges entre opciones que tienes delante.
     * 4-5  Producción guiada: construyes, pero con las piezas a la vista.
     * 6-8  Producción libre: lo escribes tú desde cero.
     * 9    Producción oral: lo dices en voz alta.
     */
    fun rank(exercise: Exercise): Int = when (exercise) {
        // --- Exposición ---
        is Exercise.Tip -> 0
        is Exercise.Reading -> 0          // en las historias, el texto va primero
        is Exercise.VocabIntro -> 1
        is Exercise.CultureNote -> 1

        // --- Reconocimiento ---
        is Exercise.MatchingPairs -> 2
        is Exercise.MultipleChoice -> 3

        // --- Producción guiada: las palabras están a la vista ---
        is Exercise.FillInBlank -> if (exercise.options.isNotEmpty()) 4 else 6
        is Exercise.WordOrder -> 5

        // --- Producción libre: hay que escribirlo de memoria ---
        is Exercise.ListenAndType -> 7
        is Exercise.Translate -> 8

        // --- Producción oral ---
        is Exercise.SpeakAndRepeat -> 9
    }

    /**
     * Ordena los ejercicios de menor a mayor exigencia manteniendo el orden
     * relativo dentro de cada grupo (`sortedBy` es estable en Kotlin), así que
     * la secuencia que escribió el autor se respeta dentro de cada escalón.
     */
    fun order(exercises: List<Exercise>): List<Exercise> = exercises.sortedBy(::rank)

    /**
     * Antepone la presentación del vocabulario si la lección tiene palabras y
     * todavía no incluye una tarjeta de presentación escrita a mano.
     *
     * Se limita a [MAX_INTRO_ITEMS] entradas: una tarjeta con veinte palabras
     * no se lee, se salta.
     */
    fun withVocabIntro(
        exercises: List<Exercise>,
        vocab: List<VocabItem>,
        lessonId: String,
        lessonTitle: String
    ): List<Exercise> {
        if (vocab.isEmpty()) return exercises
        if (exercises.any { it is Exercise.VocabIntro }) return exercises

        val intro = Exercise.VocabIntro(
            srsKey = "intro_$lessonId",
            title = lessonTitle,
            items = vocab.take(MAX_INTRO_ITEMS)
        )
        return listOf(intro) + exercises
    }

    /**
     * Prepara una lección entera: mete la presentación del vocabulario y luego
     * ordena todo por exigencia.
     *
     * El orden importa: primero se inserta la tarjeta y DESPUÉS se ordena, para
     * que caiga en el puesto que le asigna [rank] y no simplemente la primera.
     * Así un consejo de gramática (rango 0) sigue abriendo la lección si el
     * autor lo escribió, y el vocabulario viene justo detrás.
     */
    fun prepare(
        exercises: List<Exercise>,
        vocab: List<VocabItem>,
        lessonId: String,
        lessonTitle: String,
        respectAuthorOrder: Boolean
    ): List<Exercise> {
        val conIntro = withVocabIntro(exercises, vocab, lessonId, lessonTitle)
        return if (respectAuthorOrder) conIntro else order(conIntro)
    }

    const val MAX_INTRO_ITEMS = 12
}
