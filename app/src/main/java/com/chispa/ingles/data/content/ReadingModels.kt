package com.chispa.ingles.data.content

import kotlinx.serialization.Serializable

/* =========================================================================
 *  Biblioteca de lecturas bilingües
 *
 *  El alineado es por FRASE, no por párrafo: cada oración inglesa lleva su
 *  traducción al lado. Eso es lo que permite tocar una frase y ver qué
 *  significa exactamente, en vez de buscarla dentro de un bloque traducido.
 * ========================================================================= */

@Serializable
data class ReadingLibraryJson(
    val id: String = "library",
    val title: String = "Biblioteca",
    val readings: List<ReadingJson> = emptyList()
)

@Serializable
data class ReadingJson(
    val id: String,
    val title: String,
    val level: String = "A1",
    val category: String = "story",
    val summary: String = "",
    /** Minutos aproximados de lectura. Se calcula solo si no viene puesto. */
    val minutes: Int = 0,
    val sentences: List<SentenceJson> = emptyList(),
    /** Palabras que merece la pena llevarse al repaso espaciado. */
    val glossary: List<VocabJson> = emptyList()
)

@Serializable
data class SentenceJson(
    val en: String,
    val es: String,
    /** true para cortar párrafo antes de esta frase. */
    val paragraph: Boolean = false
)

/* ---------------------------- Modelo de dominio ------------------------- */

data class ReadingLibrary(val readings: List<Reading>) {
    fun byLevel(): Map<CefrLevel, List<Reading>> = readings.groupBy { it.level }
    fun find(id: String): Reading? = readings.firstOrNull { it.id == id }
    val isEmpty: Boolean get() = readings.isEmpty()
}

data class Reading(
    val id: String,
    val title: String,
    val level: CefrLevel,
    val category: ReadingCategory,
    val summary: String,
    val minutes: Int,
    val sentences: List<Sentence>,
    val glossary: List<VocabItem>
) {
    val wordCount: Int = sentences.sumOf { it.en.split(" ").size }
}

data class Sentence(
    val index: Int,
    val en: String,
    val es: String,
    val startsParagraph: Boolean
)

enum class ReadingCategory(val label: String, val emoji: String) {
    STORY("Relato", "📖"),
    ARTICLE("Artículo", "📰"),
    DIALOGUE("Diálogo", "💬"),
    LETTER("Carta", "✉️");

    companion object {
        fun from(raw: String?): ReadingCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: STORY
    }
}
