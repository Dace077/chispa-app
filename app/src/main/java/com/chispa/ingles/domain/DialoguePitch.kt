package com.chispa.ingles.domain

/**
 * Reparte tonos de voz entre los personajes de un diálogo.
 *
 * El motor de voz de Android tiene una sola voz, y hasta ahora las
 * conversaciones del examen se leían enteras del tirón con una pausa entre
 * turnos. El problema no es de realismo sino de examen: media sección de
 * Listening pregunta *what does the **woman** mean*, y si los dos hablantes
 * suenan idénticos hay que deducir quién habla por el contenido, que es una
 * dificultad que el examen real no tiene.
 *
 * Cambiar el tono no da dos voces de verdad, pero sí separa los turnos con
 * claridad y es gratis: el mismo motor, sin descargas ni permisos.
 */
object DialoguePitch {

    const val NEUTRO = 1.0f
    private const val GRAVE = 0.85f
    private const val AGUDO = 1.18f
    /** Para un tercer personaje, si lo hubiera. */
    private const val MEDIO = 1.05f

    private val ESCALA = listOf(GRAVE, AGUDO, MEDIO)

    /**
     * Un tono por personaje, en el orden en que aparecen.
     *
     * Cuando el guion dice «Man» y «Woman» se respeta lo evidente en vez de ir
     * por orden: el alumno oye lo que la pregunta le va a preguntar. Con roles
     * («Student», «Librarian») no hay forma de saber el género, y tampoco hace
     * falta: basta con que se distingan.
     *
     * Con un solo personaje se devuelve el tono normal. Una charla académica no
     * gana nada leída con voz grave, y cambiarla solo la haría rara.
     */
    fun forSpeakers(speakers: List<String>): Map<String, Float> {
        val orden = speakers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (orden.size <= 1) return orden.associateWith { NEUTRO }

        val explicitos = orden.mapNotNull { nombre ->
            porGenero(nombre)?.let { nombre to it }
        }.toMap()

        // Los que no declaran género se reparten los tonos que queden libres.
        val libres = ESCALA.filter { it !in explicitos.values }.toMutableList()
        return orden.associateWith { nombre ->
            explicitos[nombre] ?: libres.removeFirstOrNull() ?: NEUTRO
        }
    }

    /**
     * «woman» contiene «man», así que el orden de estas comprobaciones no es
     * cosmético: al revés, toda mujer sonaría grave.
     */
    private fun porGenero(nombre: String): Float? {
        val n = nombre.lowercase()
        return when {
            "woman" in n || "female" in n || "girl" in n -> AGUDO
            "man" in n || "male" in n || "boy" in n -> GRAVE
            else -> null
        }
    }
}
