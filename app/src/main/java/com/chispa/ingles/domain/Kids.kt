package com.chispa.ingles.domain

/**
 * Chispa Kids: la etapa para niños de 2 a 5 años.
 *
 * **La regla que manda sobre todas: aquí no se lee.** Un niño de tres años no
 * sabe leer ni en español, así que ninguna instrucción puede ser un texto. Lo
 * que hay es un dibujo grande y una voz que dice la palabra. Eso obliga a
 * romper con casi todo el motor del curso de adultos, donde `fill_in_blank`,
 * `translate` o `word_order` dan por hecho que se lee y se escribe.
 *
 * Las otras reglas salen de mirar cómo lo hacen las apps que funcionan a esta
 * edad (Studycat, Lingokids, Duolingo ABC):
 *
 * - **Sin reloj.** Studycat quitó los cronómetros de sus juegos a propósito: a
 *   esta edad la prisa no motiva, bloquea.
 * - **Sin corazones ni vidas.** Perder por fallar no enseña a un niño de tres
 *   años; solo le enseña a dejar de tocar.
 * - **El audio se repite siempre.** Se practica sin sentirse examinado.
 * - **Un toque, grande.** Nada de arrastrar, escribir ni escoger entre seis.
 * - **Sesiones cortísimas.** Seis rondas y a celebrar.
 */

/** Qué dibuja la app para un elemento. El arte va en Canvas, no en PNG. */
enum class KidsArtKind {
    /** Uno de los avatares que ya existen (`Avatar.id`). */
    ANIMAL,

    /** Una mancha de color liso. */
    COLOR,

    /** Una figura geométrica. */
    SHAPE,

    /** Un grupo de puntos para contar. */
    COUNT,

    /** Una letra del abecedario, bien grande. */
    LETTER,

    /** Un animal dibujado aquí, de los que no son avatares. */
    CRITTER,

    /**
     * Una imagen del sistema (emoji), a tamaño grande.
     *
     * Los avatares y las figuras se dibujan a mano, pero una etapa infantil
     * necesita cientos de objetos —comida, ropa, casa, transporte— y dibujar
     * cada uno en Canvas costaría meses. El repertorio de Android está hecho
     * por ilustradores, es coherente entre sí, escala sin pixelarse y el niño
     * lo reconoce. Se eligen emoji antiguos y comunes, que existen desde
     * Android 7 (el mínimo de la app).
     */
    EMOJI,

    /** Desconocido: el elemento se descarta en vez de pintar un hueco. */
    UNKNOWN;

    companion object {
        fun from(raw: String?): KidsArtKind = when (raw?.trim()?.lowercase()) {
            "animal" -> ANIMAL
            "color" -> COLOR
            "shape" -> SHAPE
            "count" -> COUNT
            "letter" -> LETTER
            "critter" -> CRITTER
            "emoji" -> EMOJI
            else -> UNKNOWN
        }
    }
}

/**
 * Una palabra que se aprende: su dibujo, cómo se dice y cómo se llama en
 * español para que el adulto que acompaña sepa qué está sonando.
 */
data class KidsItem(
    val id: String,
    val en: String,
    val es: String,
    val kind: KidsArtKind,
    /** Qué pintar: id de avatar, color en hex, nombre de figura, número o letra. */
    val art: String,
    /**
     * Lo que se manda al motor de voz, si no basta con [en].
     *
     * Hace falta para las letras: escribir «B» a secas hace que el TTS diga su
     * nombre («bi») y punto, cuando lo que enseña de verdad es el sonido con
     * una palabra detrás: «B... ball». Vacío significa «di [en] tal cual».
     */
    val say: String = ""
) {
    /** El texto que se pronuncia. */
    val spoken: String get() = say.ifBlank { en }
}

/** Un mundo: animales, colores, formas, números. */
data class KidsWorld(
    val id: String,
    val titleEs: String,
    val emoji: String,
    val colorHex: String,
    val items: List<KidsItem>
) {
    /**
     * Hacen falta al menos dos para poder preguntar «¿cuál es el gato?»: con
     * uno solo no hay elección posible y el juego no existe.
     */
    val jugable: Boolean get() = items.size >= 2
}

/** Lo que el niño está haciendo ahora mismo. */
enum class KidsMode {
    /** Toca lo que quieras y suena. Sin acierto ni error. */
    EXPLORAR,

    /** Suena una palabra y hay que tocar el dibujo correcto. */
    ENCONTRAR
}

object KidsRules {

    /** Rondas de una partida. Corta a propósito: la atención a esta edad lo es. */
    const val RONDAS = 6

    /**
     * Cuántos dibujos se enseñan a la vez.
     *
     * Empieza en dos y sube a tres a mitad de partida. Cuatro opciones a los
     * tres años es una pantalla llena de ruido, no un reto.
     */
    fun opcionesEnRonda(ronda: Int): Int = if (ronda < 3) 2 else 3

    /**
     * Elige el elemento a preguntar y con quién compite.
     *
     * El correcto va en una posición que rota, para que no se aprenda a tocar
     * siempre el mismo sitio; a esta edad eso pasa enseguida y deja de
     * aprenderse la palabra. Es el mismo problema que tenía el test de nivel de
     * los adultos, donde la respuesta caía casi siempre en la primera opción.
     */
    fun ronda(
        items: List<KidsItem>,
        ronda: Int,
        yaPreguntados: Set<String>
    ): KidsRonda? {
        if (items.size < 2) return null

        val frescos = items.filter { it.id !in yaPreguntados }
        val correcto = (frescos.ifEmpty { items }).random()

        val cuantos = opcionesEnRonda(ronda).coerceAtMost(items.size)
        val distractores = items.filter { it.id != correcto.id }.shuffled().take(cuantos - 1)

        val posicion = ronda % cuantos
        val opciones = distractores.toMutableList().apply { add(posicion, correcto) }

        return KidsRonda(correcto = correcto, opciones = opciones)
    }
}

data class KidsRonda(
    val correcto: KidsItem,
    val opciones: List<KidsItem>
)
