package com.chispa.ingles.domain

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Corrección de respuestas escritas y habladas.
 *
 * Filosofía: el usuario está aprendiendo un idioma, no mecanografía. Una tilde
 * de más, un punto final o una `s` bailando no deberían costarle un corazón.
 * Por eso normalizamos agresivamente y toleramos un error de tecleo, pero
 * avisando de cuál era la forma correcta.
 */
object AnswerChecker {

    sealed interface Result {
        val isAccepted: Boolean

        data object Correct : Result {
            override val isAccepted get() = true
        }

        /** Aceptada, pero había una errata. Se muestra la forma correcta. */
        data class Typo(val correct: String) : Result {
            override val isAccepted get() = true
        }

        data class Wrong(val correct: String) : Result {
            override val isAccepted get() = false
        }
    }

    /** Contracciones inglesas comunes: se aceptan ambas formas indistintamente. */
    private val CONTRACTIONS = listOf(
        "i am" to "im", "i'm" to "im",
        "you are" to "youre", "you're" to "youre",
        "he is" to "hes", "he's" to "hes",
        "she is" to "shes", "she's" to "shes",
        "it is" to "its", "it's" to "its",
        "we are" to "were", "we're" to "were",
        "they are" to "theyre", "they're" to "theyre",
        "do not" to "dont", "don't" to "dont",
        "does not" to "doesnt", "doesn't" to "doesnt",
        "did not" to "didnt", "didn't" to "didnt",
        "is not" to "isnt", "isn't" to "isnt",
        "are not" to "arent", "aren't" to "arent",
        "was not" to "wasnt", "wasn't" to "wasnt",
        "were not" to "werent", "weren't" to "werent",
        "cannot" to "cant", "can not" to "cant", "can't" to "cant",
        "will not" to "wont", "won't" to "wont",
        "would not" to "wouldnt", "wouldn't" to "wouldnt",
        "i will" to "ill", "i'll" to "ill",
        "i have" to "ive", "i've" to "ive",
        "i would" to "id", "i'd" to "id",
        "let us" to "lets", "let's" to "lets",
        "there is" to "theres", "there's" to "theres",
        "that is" to "thats", "that's" to "thats",
        "what is" to "whats", "what's" to "whats",
        "have not" to "havent", "haven't" to "havent",
        "has not" to "hasnt", "hasn't" to "hasnt"
    )

    /**
     * Variantes británica y americana de la misma palabra.
     *
     * Se unifican al escribir para que ninguna de las dos se marque mal. Sin
     * esto "centre" contaba como fallo frente a "center" (dos letras de
     * diferencia, presupuesto de una) y "colour" pasaba pero avisando de una
     * errata que no existe. Las dos son inglés correcto y la propia app lo
     * enseña en A1, así que corregirlas era contradecirse.
     *
     * Es una lista cerrada y no una regla general a propósito: una regla
     * "-our → -or" convertiría "four" en "for" y "hour" en "hor".
     */
    private val SPELLING_VARIANTS: Map<String, String> = buildMap {
        // -our / -or
        listOf(
            "colour" to "color", "colours" to "colors", "coloured" to "colored",
            "favour" to "favor", "favours" to "favors", "favourite" to "favorite",
            "behaviour" to "behavior", "neighbour" to "neighbor",
            "neighbours" to "neighbors", "neighbourhood" to "neighborhood",
            "honour" to "honor", "humour" to "humor", "labour" to "labor",
            "flavour" to "flavor", "flavours" to "flavors", "rumour" to "rumor",
            "harbour" to "harbor", "odour" to "odor", "savour" to "savor",
            // -re / -er
            "centre" to "center", "centres" to "centers", "theatre" to "theater",
            "metre" to "meter", "metres" to "meters", "litre" to "liter",
            "litres" to "liters", "fibre" to "fiber", "calibre" to "caliber",
            // -ise / -ize
            "realise" to "realize", "realised" to "realized", "realises" to "realizes",
            "organise" to "organize", "organised" to "organized",
            "apologise" to "apologize", "apologised" to "apologized",
            "recognise" to "recognize", "recognised" to "recognized",
            "analyse" to "analyze", "analysed" to "analyzed",
            "criticise" to "criticize", "memorise" to "memorize",
            "specialise" to "specialize", "summarise" to "summarize",
            "apologising" to "apologizing", "organisation" to "organization",
            // dobles consonantes
            "travelling" to "traveling", "travelled" to "traveled",
            "traveller" to "traveler", "cancelled" to "canceled",
            "cancelling" to "canceling", "modelling" to "modeling",
            "labelled" to "labeled",
            // -ce / -se y sueltas
            "defence" to "defense", "offence" to "offense", "licence" to "license",
            "practise" to "practice", "practising" to "practicing",
            "programme" to "program", "programmes" to "programs",
            "grey" to "gray", "tyre" to "tire", "cheque" to "check",
            "plough" to "plow", "aluminium" to "aluminum", "maths" to "math",
            "storey" to "story", "kerb" to "curb", "pyjamas" to "pajamas",
            "moustache" to "mustache", "jewellery" to "jewelry",
            // pasados irregulares que conviven
            "learnt" to "learned", "spelt" to "spelled", "dreamt" to "dreamed",
            "burnt" to "burned", "spoilt" to "spoiled", "leapt" to "leaped",
            "smelt" to "smelled"
        ).forEach { (br, am) -> put(br, am) }
    }

    /**
     * Quita tildes, signos, mayúsculas y espacios de más, y unifica contracciones.
     * "¿Cómo estás?" y "como estas" acaban siendo la misma cadena.
     */
    fun normalize(input: String): String {
        var text = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")          // marcas diacríticas
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("[¿?¡!.,;:\"“”()\\[\\]…]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        CONTRACTIONS.forEach { (long, short) ->
            text = text.replace(Regex("\\b${Regex.escape(long)}\\b"), short)
        }
        // Tras normalizar, los apóstrofos ya no aportan nada.
        text = text.replace("'", "").replace(Regex("\\s+"), " ").trim()

        // Y por último, británico y americano se unifican palabra a palabra.
        if (text.isEmpty()) return text
        return text.split(' ').joinToString(" ") { palabra ->
            SPELLING_VARIANTS[palabra] ?: palabra
        }
    }

    /**
     * @param typed lo que escribió el usuario
     * @param expected la respuesta canónica (la que se le mostrará si falla)
     * @param alternatives otras formas igualmente válidas
     * @param allowTypos si false, exige coincidencia exacta tras normalizar
     */
    fun check(
        typed: String,
        expected: String,
        alternatives: List<String> = emptyList(),
        allowTypos: Boolean = true
    ): Result {
        val user = normalize(typed)
        if (user.isEmpty()) return Result.Wrong(expected)

        val candidates = (listOf(expected) + alternatives).map { normalize(it) }.filter { it.isNotEmpty() }
        if (user in candidates) return Result.Correct

        if (allowTypos) {
            val tolerant = candidates.any { candidate ->
                val distance = levenshtein(user, candidate)
                distance <= typoBudget(candidate)
            }
            if (tolerant) return Result.Typo(expected)
        }

        return Result.Wrong(expected)
    }

    /** Cuántas erratas se perdonan según la longitud de la respuesta esperada. */
    private fun typoBudget(expected: String): Int = when {
        expected.length <= 3 -> 0
        expected.length <= 8 -> 1
        expected.length <= 20 -> 2
        else -> 3
    }

    /**
     * Corrección de reordenar palabras: solo importa la secuencia final.
     */
    fun checkWordOrder(built: List<String>, expected: String): Result {
        val user = normalize(built.joinToString(" "))
        return if (user == normalize(expected)) Result.Correct else Result.Wrong(expected)
    }

    /**
     * Evalúa lo que devolvió el reconocedor de voz.
     *
     * El motor on-device de Android devuelve varias hipótesis; basta con que una
     * se parezca lo bastante. Aquí somos más permisivos que al escribir: el
     * reconocedor introduce sus propios errores y no es justo cobrárselos al usuario.
     *
     * @return similitud 0f..1f de la mejor hipótesis
     */
    fun speechSimilarity(hypotheses: List<String>, expected: String): Float {
        val target = normalize(expected)
        if (target.isEmpty()) return 0f
        return hypotheses.maxOfOrNull { hypothesis ->
            val said = normalize(hypothesis)
            if (said.isEmpty()) return@maxOfOrNull 0f
            val distance = levenshtein(said, target)
            1f - distance.toFloat() / max(said.length, target.length)
        } ?: 0f
    }

    /** Umbral a partir del cual damos por buena una pronunciación. */
    const val SPEECH_PASS_THRESHOLD = 0.72f

    /** Distancia de edición clásica, con una sola fila de trabajo. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
