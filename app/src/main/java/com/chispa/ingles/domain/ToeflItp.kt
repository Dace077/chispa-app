package com.chispa.ingles.domain

import kotlin.math.roundToInt

/**
 * Reglas del examen TOEFL ITP, que es el que piden en México para titulación,
 * para posgrados nacionales y en bastantes empresas.
 *
 * **Por qué el ITP y no el iBT**: el ITP es 100 % opción múltiple. Sin writing
 * ni speaking, no hace falta un corrector humano, así que Chispa puede simularlo
 * de verdad con el motor de ejercicios que ya tiene y con el TTS para la sección
 * de listening. El iBT sería imposible sin alguien calificando redacciones.
 *
 * ---
 *
 * **AVISO IMPORTANTE SOBRE EL PUNTAJE.** La tabla oficial de conversión de
 * aciertos a puntos es de ETS y no es pública. Lo que hay aquí es una
 * **estimación** construida sobre los tres datos que sí son públicos: el número
 * de preguntas por sección, el rango de cada sección y la fórmula del total.
 *
 * Sirve para saber por dónde andas y para ver si mejoras entre simulacros. NO
 * sirve para predecir tu puntaje real al punto. La app lo dice así, con esas
 * palabras, y no debe dejar de decirlo.
 */
enum class ToeflSection(
    val id: String,
    val label: String,
    val subtitle: String,
    val questions: Int,
    val minutes: Int,
    val minScaled: Int,
    val maxScaled: Int
) {
    LISTENING(
        id = "listening",
        label = "Listening Comprehension",
        subtitle = "Comprensión auditiva",
        questions = 50,
        minutes = 35,
        minScaled = 31,
        maxScaled = 68
    ),
    STRUCTURE(
        id = "structure",
        label = "Structure and Written Expression",
        subtitle = "Estructura y expresión escrita",
        questions = 40,
        minutes = 25,
        minScaled = 31,
        maxScaled = 68
    ),
    READING(
        id = "reading",
        label = "Reading Comprehension",
        subtitle = "Comprensión de lectura",
        questions = 50,
        minutes = 55,
        minScaled = 31,
        maxScaled = 67
    );

    /** Solo el nombre, para numerar preguntas: «Structure 12». */
    val shortLabel: String get() = label.substringBefore(' ')

    companion object {
        val ORDER = listOf(LISTENING, STRUCTURE, READING)
        val TOTAL_QUESTIONS = ORDER.sumOf { it.questions }      // 140
        val TOTAL_MINUTES = ORDER.sumOf { it.minutes }          // 115
    }
}

/** Lo que se saca de un simulacro terminado. */
data class ToeflResult(
    val listeningRaw: Int,
    val structureRaw: Int,
    val readingRaw: Int,
    val listeningScaled: Int,
    val structureScaled: Int,
    val readingScaled: Int,
    val total: Int
) {
    val totalRaw: Int get() = listeningRaw + structureRaw + readingRaw
    val accuracy: Int get() = totalRaw * 100 / ToeflSection.TOTAL_QUESTIONS
}

object ToeflItp {

    const val MIN_TOTAL = 310
    const val MAX_TOTAL = 677

    /**
     * El aviso que acompaña a cualquier puntaje que enseñe la app.
     * No es decorativo: prometer un puntaje exacto sería mentir.
     */
    const val AVISO_ESTIMACION =
        "Puntaje estimado. La tabla oficial de conversión es de ETS y no es pública, " +
            "así que esta cifra sirve para medir tu progreso entre simulacros, no para " +
            "predecir tu resultado real."

    /**
     * Convierte aciertos en puntos de sección.
     *
     * La curva no es recta a propósito: en los exámenes reales cuesta mucho más
     * subir del 85 % al 95 % que del 40 % al 50 %, y una recta premiaría por
     * igual los dos tramos. Se usa una potencia suave sobre la proporción de
     * aciertos, que reproduce esa forma sin inventarse una tabla que no tenemos.
     */
    fun scaled(section: ToeflSection, correct: Int): Int {
        val aciertos = correct.coerceIn(0, section.questions)
        val proporcion = aciertos.toFloat() / section.questions
        val curva = Math.pow(proporcion.toDouble(), 1.18).toFloat()
        val rango = (section.maxScaled - section.minScaled).toFloat()
        return (section.minScaled + curva * rango).roundToInt()
            .coerceIn(section.minScaled, section.maxScaled)
    }

    /**
     * Puntaje total en la escala 310-677.
     *
     * La fórmula sí es la oficial y es pública: se suman los tres puntajes de
     * sección, se multiplica por 10 y se divide entre 3.
     */
    fun total(listening: Int, structure: Int, reading: Int): Int =
        ((listening + structure + reading) * 10 / 3.0).roundToInt()
            .coerceIn(MIN_TOTAL, MAX_TOTAL)

    fun evaluate(listeningRaw: Int, structureRaw: Int, readingRaw: Int): ToeflResult {
        val l = scaled(ToeflSection.LISTENING, listeningRaw)
        val s = scaled(ToeflSection.STRUCTURE, structureRaw)
        val r = scaled(ToeflSection.READING, readingRaw)
        return ToeflResult(
            listeningRaw = listeningRaw.coerceIn(0, ToeflSection.LISTENING.questions),
            structureRaw = structureRaw.coerceIn(0, ToeflSection.STRUCTURE.questions),
            readingRaw = readingRaw.coerceIn(0, ToeflSection.READING.questions),
            listeningScaled = l,
            structureScaled = s,
            readingScaled = r,
            total = total(l, s, r)
        )
    }

    /**
     * Para qué te da ese puntaje.
     *
     * Los umbrales son los que piden habitualmente las universidades mexicanas
     * para titulación y posgrado. Cada institución fija el suyo, así que se
     * enseñan como referencia y no como promesa.
     */
    data class Umbral(val puntos: Int, val para: String)

    val UMBRALES = listOf(
        Umbral(400, "Nivel intermedio. Muchas licenciaturas piden a partir de aquí."),
        Umbral(450, "Titulación en bastantes universidades públicas."),
        Umbral(500, "Titulación y posgrados nacionales. Es el umbral más pedido."),
        Umbral(550, "Posgrados exigentes y algunas becas."),
        Umbral(600, "Nivel alto. Cubre prácticamente cualquier requisito nacional.")
    )

    /** El umbral más alto que ya cubre este puntaje, o null si no llega al primero. */
    fun umbralAlcanzado(total: Int): Umbral? =
        UMBRALES.lastOrNull { total >= it.puntos }

    /** El siguiente objetivo, para tener adónde apuntar. */
    fun siguienteUmbral(total: Int): Umbral? =
        UMBRALES.firstOrNull { total < it.puntos }

    /** Equivalencia orientativa con el marco europeo, para situar al alumno. */
    fun nivelAproximado(total: Int): String = when {
        total >= 600 -> "C1"
        total >= 543 -> "B2 alto"
        total >= 500 -> "B2"
        total >= 460 -> "B1 alto"
        total >= 400 -> "B1"
        else -> "A2"
    }

    /**
     * Frase de resultado. Dice la verdad incluso cuando no es agradable, pero
     * siempre con lo siguiente que hacer.
     */
    fun resumen(result: ToeflResult): String {
        val siguiente = siguienteUmbral(result.total)
        val flojo = ToeflSection.ORDER.minByOrNull { seccion ->
            when (seccion) {
                ToeflSection.LISTENING -> result.listeningScaled
                ToeflSection.STRUCTURE -> result.structureScaled
                ToeflSection.READING -> result.readingScaled
            }
        }
        val base = "Has acertado ${result.totalRaw} de ${ToeflSection.TOTAL_QUESTIONS}."
        val objetivo = siguiente?.let {
            // Solo la inicial: `lowercase()` entero se llevaba por delante la
            // mayúscula de después del punto («intermedio. muchas licenciaturas»).
            val descripcion = it.para.replaceFirstChar { c -> c.lowercaseChar() }
            " Te faltan ${it.puntos - result.total} puntos para ${it.puntos}: $descripcion"
        } ?: " Estás por encima de todos los umbrales de referencia."
        val consejo = flojo?.let { " Donde más margen tienes es en ${it.subtitle.lowercase()}." } ?: ""
        return base + objetivo + consejo
    }
}
