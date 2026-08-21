package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.Curriculum
import com.chispa.ingles.data.db.LessonProgressEntity
import kotlin.math.abs

/**
 * Certificados de nivel.
 *
 * Chispa emite una **constancia propia**: acredita que esta persona completó
 * este nivel de este curso, y nada más. No es un título oficial ni sustituye a
 * uno, y el PDF lo dice con todas las letras. Hacerlo pasar por una acreditación
 * de un organismo real sería falsificar un documento, y además tumbaría la app
 * de Google Play el día que alguien lo denunciara.
 *
 * Dicho eso, una constancia honesta no vale menos: para mucha gente va a ser el
 * primer papel que diga su nombre junto a la palabra "inglés", y ese papel se
 * enseña en casa y se pega en la pared.
 */

/** Estado de un nivel de cara al certificado. */
data class LevelCompletion(
    val level: CefrLevel,
    val totalLessons: Int,
    val completedLessons: Int,
    /** Media de la mejor precisión obtenida en las lecciones hechas, 0-100. */
    val accuracy: Int,
    val xpEarned: Int
) {
    val isComplete: Boolean get() = totalLessons > 0 && completedLessons >= totalLessons

    val progress: Float
        get() = if (totalLessons == 0) 0f else completedLessons.toFloat() / totalLessons

    val remaining: Int get() = (totalLessons - completedLessons).coerceAtLeast(0)
}

object CertificateRules {

    /**
     * Estado de cada nivel del camino principal, en orden A1 → C2.
     *
     * Solo cuentan los niveles del camino: los módulos extra (modismos, viajes,
     * business…) no son un nivel del marco europeo y no certifican nada.
     */
    fun levelStatus(
        curriculum: Curriculum,
        progress: Map<String, LessonProgressEntity>
    ): List<LevelCompletion> {
        val porNivel = curriculum.coreUnits.groupBy { it.level }

        return CefrLevel.entries
            .filter { it != CefrLevel.EXTRA }
            .sortedBy { it.order }
            .map { level ->
                val lecciones = porNivel[level].orEmpty().flatMap { it.lessons }
                val hechas = lecciones.mapNotNull { progress[it.id] }
                    .filter { it.timesCompleted > 0 }

                LevelCompletion(
                    level = level,
                    totalLessons = lecciones.size,
                    completedLessons = hechas.size,
                    accuracy = if (hechas.isEmpty()) 0 else hechas.sumOf { it.bestAccuracy } / hechas.size,
                    xpEarned = hechas.sumOf { it.xpEarned }
                )
            }
    }

    /** Niveles terminados del todo, que son los que dan derecho a certificado. */
    fun earnedLevels(
        curriculum: Curriculum,
        progress: Map<String, LessonProgressEntity>
    ): List<LevelCompletion> = levelStatus(curriculum, progress).filter { it.isComplete }

    /**
     * Folio del certificado.
     *
     * No es un número de serie de nada oficial: es un identificador estable para
     * que dos constancias no se confundan y para que el usuario pueda decir "es
     * la CH-B2-…-4F2A". Se calcula del nivel, el día y el nombre, así que el
     * mismo certificado siempre tiene el mismo folio aunque se regenere el PDF.
     */
    fun folio(level: CefrLevel, studentName: String, issuedAtEpochDay: Long): String {
        val semilla = "${level.label}|${studentName.trim().lowercase()}|$issuedAtEpochDay"
        val corto = abs(semilla.hashCode()).toString(16).uppercase().padStart(4, '0').takeLast(4)
        return "CH-${level.label}-$issuedAtEpochDay-$corto"
    }

    /**
     * Lo que el nivel significa en la práctica, en cristiano.
     *
     * Va impreso bajo el nombre. Un certificado que solo dice "nivel B1" no le
     * dice nada a quien lo recibe ni a quien se lo enseña; esto sí.
     */
    fun descripcion(level: CefrLevel): String = when (level) {
        CefrLevel.A1 -> "Comprende y utiliza expresiones cotidianas de uso frecuente, se presenta " +
            "y pide información básica sobre sí mismo y su entorno."
        CefrLevel.A2 -> "Se comunica en situaciones sencillas y habituales, describe en pasado su " +
            "entorno, su rutina y sus necesidades inmediatas."
        CefrLevel.B1 -> "Se desenvuelve con soltura en la mayoría de situaciones de viaje y trabajo, " +
            "narra experiencias y justifica brevemente sus opiniones."
        CefrLevel.B2 -> "Comprende textos complejos, argumenta con claridad y se relaciona con " +
            "hablantes nativos con un grado de fluidez que no supone esfuerzo para ninguna de las partes."
        CefrLevel.C1 -> "Se expresa de forma fluida y espontánea, utiliza el idioma con flexibilidad " +
            "en contextos sociales, académicos y profesionales."
        CefrLevel.C2 -> "Comprende con facilidad prácticamente todo lo que oye o lee, y se expresa " +
            "con precisión, matizando incluso en situaciones de mayor complejidad."
        CefrLevel.EXTRA -> ""
    }

    /**
     * Frase de ánimo, con el nombre de la persona dentro.
     *
     * Se elige por nivel y de forma estable (mismo folio → misma frase), porque
     * un certificado que cambia de texto cada vez que lo abres se siente falso.
     */
    fun frase(level: CefrLevel, nombre: String, folio: String): String {
        val pila = FRASES[level].orEmpty()
        if (pila.isEmpty()) return ""
        val indice = abs(folio.hashCode()) % pila.size
        val primerNombre = nombre.trim().split(" ").firstOrNull().orEmpty()
        return pila[indice].replace("{n}", primerNombre)
    }

    private val FRASES: Map<CefrLevel, List<String>> = mapOf(
        CefrLevel.A1 to listOf(
            "{n}, empezar es la parte que casi nadie hace. Tú ya la hiciste.",
            "Hace unas semanas esto era un idioma ajeno, {n}. Ya no lo es.",
            "{n}: el primer escalón es el más difícil, y lo tienes detrás."
        ),
        CefrLevel.A2 to listOf(
            "{n}, ya no solo entiendes: ya cuentas cosas. Sigue.",
            "Aquí es donde muchos lo dejan, {n}. Tú vas a seguir.",
            "{n}, ya puedes defenderte. Ahora vamos a por soltura."
        ),
        CefrLevel.B1 to listOf(
            "{n}, con este nivel ya se viaja y se trabaja. Y todavía queda cuerda.",
            "B1 es el nivel en el que el inglés deja de dar miedo, {n}. Enhorabuena.",
            "{n}, ya te entiendes con el mundo. Ahora afinemos."
        ),
        CefrLevel.B2 to listOf(
            "{n}, este es el nivel que piden en la mayoría de las empresas. Lo tienes.",
            "B2, {n}: ya puedes discutir, negociar y defender una idea en inglés.",
            "{n}, has llegado donde muchos querían llegar. Y puedes seguir."
        ),
        CefrLevel.C1 to listOf(
            "{n}, a este nivel el idioma ya es una herramienta tuya, no un obstáculo.",
            "C1, {n}. Ya no traduces: piensas en inglés.",
            "{n}, muy poca gente llega aquí. Tú sí."
        ),
        CefrLevel.C2 to listOf(
            "{n}, has llegado al techo del marco europeo. No hay nivel por encima.",
            "C2, {n}: dominio. Empezaste de cero y estás aquí.",
            "{n}, esto ya no es aprender inglés. Esto es saber inglés."
        )
    )

    /** La línea legal. Va en el PDF en pequeño, pero va. */
    const val AVISO_LEGAL =
        "Esta constancia acredita la finalización del nivel correspondiente dentro del curso de " +
            "la aplicación Chispa. No constituye una certificación oficial ni sustituye a los " +
            "exámenes de organismos acreditados."
}
