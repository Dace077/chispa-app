package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel

/**
 * Escalera adaptativa del test de nivel.
 *
 * Un test plano no sirve cuando hay seis niveles: o es larguísimo para todos,
 * o es tan corto que no distingue un B2 de un C2. Este funciona por bloques.
 *
 * Se empieza por B1, que es el centro de la escala. Si apruebas el bloque
 * subes al siguiente nivel; si no, bajas. Se para en cuanto un bloque falla
 * (subiendo) o aprueba (bajando).
 *
 * Coste real: un principiante contesta 6 preguntas, alguien de C2 contesta 12.
 * Un test plano equivalente necesitaría 18 para todos.
 */
object PlacementLadder {

    /** Niveles evaluables, de menor a mayor. EXTRA no es un nivel del camino. */
    val LEVELS = listOf(
        CefrLevel.A1, CefrLevel.A2, CefrLevel.B1,
        CefrLevel.B2, CefrLevel.C1, CefrLevel.C2
    )

    /** Nivel por el que arranca el test. */
    val START: CefrLevel = CefrLevel.B1

    /** Preguntas por bloque. */
    const val BLOCK_SIZE = 3

    /** Aciertos necesarios dentro de un bloque para darlo por superado. */
    const val PASS_THRESHOLD = 2

    /** Máximo de preguntas que puede llegar a ver alguien (caso C2). */
    const val MAX_QUESTIONS = 12

    enum class Direction { UP, DOWN }

    /**
     * Nivel resultante de **repetir** el test: nunca baja.
     *
     * `UnlockRules` abre los niveles por debajo del asignado, así que bajar el
     * nivel cerraría contenido que el usuario ya tenía abierto, y a lo mejor
     * llevaba semanas usando. Quien repite el test quiere comprobar si ha
     * mejorado, no arriesgarse a perder terreno por un mal día.
     *
     * No afecta al test de la primera vez, donde no hay nada que proteger.
     */
    fun afterRetake(actual: CefrLevel, nuevo: CefrLevel): CefrLevel =
        if (nuevo.order > actual.order) nuevo else actual

    /**
     * Qué hacer cuando termina un bloque.
     *
     * @param level nivel del bloque recién terminado
     * @param passed si se superó
     * @param direction hacia dónde íbamos
     * @param bestPassed el nivel más alto superado hasta ahora, si hay alguno
     */
    fun nextStep(
        level: CefrLevel,
        passed: Boolean,
        direction: Direction,
        bestPassed: CefrLevel?
    ): Step {
        val index = LEVELS.indexOf(level)

        return when {
            // Subiendo y aprobado: probamos el siguiente nivel si queda alguno.
            direction == Direction.UP && passed -> {
                val next = LEVELS.getOrNull(index + 1)
                if (next == null) Step.Finish(level) else Step.Continue(next, Direction.UP)
            }

            // Subiendo y suspendido: nos quedamos con el último aprobado.
            // Si no aprobamos ninguno (falla el primer bloque, B1), bajamos.
            direction == Direction.UP && !passed ->
                if (bestPassed != null) Step.Finish(bestPassed) else stepDown(index)

            // Bajando y aprobado: este es su nivel, no hace falta seguir.
            direction == Direction.DOWN && passed -> Step.Finish(level)

            // Bajando y suspendido: seguimos bajando hasta tocar suelo.
            else -> stepDown(index)
        }
    }

    /**
     * Baja un peldaño. Si el siguiente ya es el nivel más bajo, se termina ahí
     * directamente: por debajo no hay nada que distinguir, y hacerle a un
     * principiante tres preguntas más cuyo resultado da igual es la mejor
     * manera de que abandone el test.
     */
    private fun stepDown(index: Int): Step {
        val lower = LEVELS.getOrNull(index - 1) ?: return Step.Finish(LEVELS.first())
        return if (lower == LEVELS.first()) Step.Finish(lower)
        else Step.Continue(lower, Direction.DOWN)
    }

    sealed interface Step {
        data class Continue(val level: CefrLevel, val direction: Direction) : Step
        data class Finish(val level: CefrLevel) : Step
    }

    /** Frase que explica el resultado sin sonar a boletín de notas. */
    fun describe(level: CefrLevel): String = when (level) {
        CefrLevel.A1 -> "Empezamos desde el principio, con calma y bien hecho."
        CefrLevel.A2 -> "Ya tienes base. Te dejo A1 abierto por si quieres repasar."
        CefrLevel.B1 -> "Te defiendes. Abro A1 y A2 para que repases lo que quieras."
        CefrLevel.B2 -> "Buen nivel. Tienes desbloqueado todo lo anterior para repasar."
        CefrLevel.C1 -> "Nivel alto. Vas directo a lo fino: matiz, registro y precisión."
        CefrLevel.C2 -> "Nivel de maestría. Empiezas por lo que casi nadie llega a estudiar."
        CefrLevel.EXTRA -> "Empezamos por el principio."
    }
}
