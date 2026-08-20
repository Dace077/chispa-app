package com.chispa.ingles.domain

import com.chispa.ingles.data.db.ExerciseStatEntity

/**
 * Lectura de las estadísticas por tipo de ejercicio.
 *
 * La pregunta que responde no es "cuántas has acertado" sino **"¿en qué fallas
 * sistemáticamente?"**. Alguien con un 90 % global que baja al 55 % en
 * `listen_and_type` no tiene un problema de inglés: tiene un problema de oído, y
 * merece que se lo digan en vez de dejar que lo achaque a que no vale.
 */
data class TypeStat(
    val type: String,
    val label: String,
    val hint: String,
    val answered: Int,
    val correct: Int
) {
    val accuracy: Int get() = if (answered == 0) 0 else (correct * 100) / answered
    val wrong: Int get() = answered - correct
}

object ExerciseStats {

    /** Respuestas mínimas antes de opinar. Con cuatro datos no se juzga a nadie. */
    const val MIN_SAMPLE = 12

    /** Cuántos puntos por debajo de tu media hacen que un tipo sea "flojo". */
    const val GAP_THRESHOLD = 12

    /** Por debajo de esto es flojo aunque tu media también lo sea. */
    const val ABSOLUTE_FLOOR = 65

    private data class Meta(val label: String, val hint: String)

    private val META = mapOf(
        "multiple_choice" to Meta("Elegir opción", "Reconocer la respuesta entre varias"),
        "translate_to_en" to Meta("Traducir al inglés", "Producir la frase tú, de memoria"),
        "translate_to_es" to Meta("Traducir al español", "Entender lo que lees en inglés"),
        "listen_and_type" to Meta("Escuchar y escribir", "Entender de oído, sin ver el texto"),
        "word_order" to Meta("Ordenar palabras", "El orden de la frase en inglés"),
        "speak_and_repeat" to Meta("Pronunciar", "Decirlo en voz alta"),
        "matching_pairs" to Meta("Unir parejas", "Asociar palabra y significado"),
        "fill_in_blank" to Meta("Completar huecos", "La palabra exacta en su sitio")
    )

    fun read(entities: List<ExerciseStatEntity>): List<TypeStat> =
        entities
            .filter { it.type in META }
            .map {
                val m = META.getValue(it.type)
                TypeStat(it.type, m.label, m.hint, it.answered, it.correct)
            }
            .sortedByDescending { it.answered }

    /** Precisión global, que es la vara con la que se mide cada tipo. */
    fun overallAccuracy(stats: List<TypeStat>): Int {
        val total = stats.sumOf { it.answered }
        if (total == 0) return 0
        return stats.sumOf { it.correct } * 100 / total
    }

    /**
     * Los tipos donde el usuario flojea de verdad, del peor al menos malo.
     *
     * Se exige muestra suficiente Y que la diferencia sea grande: señalar un
     * 88 % frente a un 92 % de media no ayuda a nadie, solo genera ansiedad.
     */
    fun weakSpots(stats: List<TypeStat>): List<TypeStat> {
        val media = overallAccuracy(stats)
        return stats
            .filter { it.answered >= MIN_SAMPLE }
            .filter { it.accuracy <= ABSOLUTE_FLOOR || media - it.accuracy >= GAP_THRESHOLD }
            .sortedBy { it.accuracy }
    }

    /** El punto fuerte, para no dar solo malas noticias. */
    fun strongest(stats: List<TypeStat>): TypeStat? =
        stats.filter { it.answered >= MIN_SAMPLE }.maxByOrNull { it.accuracy }

    /**
     * Frase que resume la situación. Es lo que se enseña arriba del todo.
     */
    fun summary(stats: List<TypeStat>): String {
        val conMuestra = stats.filter { it.answered >= MIN_SAMPLE }
        if (conMuestra.isEmpty()) {
            return "Practica un poco más y aquí te diré en qué estás flojeando."
        }
        val flojos = weakSpots(stats)
        val fuerte = strongest(stats)

        return when {
            flojos.isEmpty() && fuerte != null ->
                "Vas parejo en todo, sin puntos débiles claros. Lo mejor lo llevas en " +
                    "«${fuerte.label.lowercase()}», con un ${fuerte.accuracy} %."

            flojos.size == 1 ->
                "Donde más se te atraganta es «${flojos[0].label.lowercase()}»: " +
                    "${flojos[0].accuracy} % frente a tu ${overallAccuracy(stats)} % de media."

            else ->
                "Se te atragantan sobre todo «${flojos[0].label.lowercase()}» y " +
                    "«${flojos[1].label.lowercase()}». Ahí es donde más vas a ganar."
        }
    }
}
