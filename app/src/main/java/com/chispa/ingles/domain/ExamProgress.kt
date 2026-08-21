package com.chispa.ingles.domain

/**
 * Serializa lo contestado en un simulacro para poder guardarlo y recuperarlo.
 *
 * Vive aquí y no dentro del ViewModel porque es la pieza de la que depende que
 * un examen de 115 minutos se pueda retomar: si al descodificar se pierde una
 * respuesta, el alumno la ve en blanco y no hay forma de que lo sepa. Es lógica
 * pura, así que se prueba.
 *
 * El formato es `id:opción` separado por comas. Nada de JSON: son 140 pares de
 * identificador y número, sin un solo carácter que escapar, y así la fila se
 * puede leer con sqlite3 al depurar.
 */
object ExamProgress {

    private const val SEPARADOR = ','
    private const val ASIGNACION = ':'

    fun encode(answers: Map<String, Int>): String =
        answers.entries.joinToString(SEPARADOR.toString()) { "${it.key}$ASIGNACION${it.value}" }

    /**
     * Lee lo guardado. Descarta en silencio lo que no cuadre.
     *
     * Ser tolerante es deliberado: ante una fila a medio escribir preferimos
     * devolver las 139 respuestas legibles que perder el examen entero por una.
     * Se busca el ÚLTIMO `:` porque un id podría llevar uno dentro.
     */
    fun decode(bruto: String): Map<String, Int> =
        bruto.split(SEPARADOR)
            .mapNotNull { par ->
                val i = par.lastIndexOf(ASIGNACION)
                if (i <= 0) return@mapNotNull null
                val opcion = par.substring(i + 1).toIntOrNull() ?: return@mapNotNull null
                if (opcion !in 0..3) return@mapNotNull null
                par.substring(0, i) to opcion
            }
            .toMap()

    fun encodeIds(ids: Set<String>): String = ids.joinToString(SEPARADOR.toString())

    fun decodeIds(bruto: String): Set<String> =
        bruto.split(SEPARADOR).filter { it.isNotBlank() }.toSet()
}
