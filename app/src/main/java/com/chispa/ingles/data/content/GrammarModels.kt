package com.chispa.ingles.data.content

import kotlinx.serialization.Serializable

/* =========================================================================
 *  Guía de gramática
 *
 *  No es un curso: es una referencia que se consulta. Por eso la explicación
 *  va en español y no se puntúa ni se bloquea nada. Se entra cuando surge la
 *  duda, se lee y se sale.
 *
 *  La parte que más rinde no es la explicación sino "errores típicos": los
 *  fallos concretos que comete quien piensa en español. Una regla abstracta
 *  se olvida; ver tu propio error escrito no.
 * ========================================================================= */

@Serializable
data class GrammarGuideJson(
    val id: String = "grammar",
    val title: String = "Gramática",
    val topics: List<GrammarTopicJson> = emptyList()
)

@Serializable
data class GrammarTopicJson(
    val id: String,
    val title: String,
    val level: String = "A1",
    /** Agrupador ancho: "Verbos", "Sustantivos", "Preposiciones"... */
    val area: String = "General",
    /** La duda real, escrita como la formularía el usuario. */
    val question: String = "",
    /** Explicación en español, en párrafos separados por línea en blanco. */
    val explanation: String = "",
    val forms: List<GrammarFormJson> = emptyList(),
    val examples: List<GrammarExampleJson> = emptyList(),
    val mistakes: List<GrammarMistakeJson> = emptyList(),
    /**
     * Sinónimos por los que alguien buscaría este tema aunque no aparezcan en
     * el título. El caso que lo motivó: el tema se llama "A, an y the" y nadie
     * lo encuentra escribiendo "artículos", que es como se llama en el colegio.
     */
    val keywords: List<String> = emptyList(),
    /** Ids de otros temas relacionados. Los que no existan se ignoran. */
    val related: List<String> = emptyList()
)

@Serializable
data class GrammarFormJson(
    val label: String,
    val pattern: String,
    val example: String = ""
)

@Serializable
data class GrammarExampleJson(
    val en: String,
    val es: String,
    val note: String = ""
)

@Serializable
data class GrammarMistakeJson(
    /** Lo que sale solo si traduces del español. */
    val wrong: String,
    val right: String,
    val why: String = ""
)

/* ---------------------------- Modelo de dominio ------------------------- */

data class GrammarGuide(val topics: List<GrammarTopic>) {
    val isEmpty: Boolean get() = topics.isEmpty()

    fun find(id: String): GrammarTopic? = topics.firstOrNull { it.id == id }

    fun byArea(): Map<String, List<GrammarTopic>> =
        topics.groupBy { it.area }

    /**
     * Busca por título, duda y área. Sin acentos y sin mayúsculas, porque
     * nadie escribe "artículo" con tilde en un buscador.
     */
    fun search(query: String): List<GrammarTopic> {
        val q = query.normalizeForSearch()
        if (q.isBlank()) return topics
        return topics.filter { it.searchBlob.contains(q) }
    }
}

data class GrammarTopic(
    val id: String,
    val title: String,
    val level: CefrLevel,
    val area: String,
    val question: String,
    val paragraphs: List<String>,
    val forms: List<GrammarForm>,
    val examples: List<GrammarExample>,
    val mistakes: List<GrammarMistake>,
    val keywords: List<String>,
    val related: List<String>
) {
    internal val searchBlob: String =
        listOf(
            title,
            question,
            area,
            keywords.joinToString(" "),
            examples.joinToString(" ") { it.en }
        ).joinToString(" ").normalizeForSearch()
}

data class GrammarForm(val label: String, val pattern: String, val example: String)
data class GrammarExample(val en: String, val es: String, val note: String)
data class GrammarMistake(val wrong: String, val right: String, val why: String)

/** Minúsculas y sin tildes, para que buscar "articulo" encuentre "artículo". */
internal fun String.normalizeForSearch(): String {
    val sb = StringBuilder(length)
    for (c in lowercase()) {
        sb.append(
            when (c) {
                'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'
                else -> c
            }
        )
    }
    return sb.toString()
}

/* ------------------------------ Conversión ------------------------------ */

fun GrammarTopicJson.toDomain(): GrammarTopic? {
    if (id.isBlank() || title.isBlank()) return null
    val cuerpo = explanation.split("\n\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    // Un tema sin explicación ni ejemplos no aporta nada; mejor no listarlo.
    if (cuerpo.isEmpty() && examples.isEmpty()) return null
    return GrammarTopic(
        id = id,
        title = title,
        level = CefrLevel.from(level),
        area = area.ifBlank { "General" },
        question = question,
        paragraphs = cuerpo,
        forms = forms.map { GrammarForm(it.label, it.pattern, it.example) },
        examples = examples.filter { it.en.isNotBlank() }
            .map { GrammarExample(it.en, it.es, it.note) },
        mistakes = mistakes.filter { it.wrong.isNotBlank() && it.right.isNotBlank() }
            .map { GrammarMistake(it.wrong, it.right, it.why) },
        keywords = keywords,
        related = related
    )
}
