package com.chispa.ingles.data.content

import kotlinx.serialization.Serializable

/**
 * Banco de frases para practicar en voz alta.
 *
 * La sección de Hablar tenía nueve sonidos con tres o cuatro ejemplos cada uno.
 * Eso sirve para afinar la boca —la diferencia entre *ship* y *sheep*— pero no
 * para hablar: nadie sale a la calle a decir vocales sueltas. Esto son frases
 * enteras de situaciones que se viven de verdad.
 *
 * Va en JSON y no en Kotlin, como el resto del contenido, para poder ampliarlo
 * sin tocar código. `speaking.json` se carga aparte y **no va en `index.json`**:
 * no es un track del curso ni desbloquea nada.
 */
@Serializable
data class SpeakingFileJson(
    val version: Int = 1,
    val categories: List<SpeakingCategoryJson> = emptyList()
)

@Serializable
data class SpeakingCategoryJson(
    val id: String = "",
    val title: String = "",
    val emoji: String = "",
    val level: String = "",
    val phrases: List<SpeakingPhraseJson> = emptyList()
)

@Serializable
data class SpeakingPhraseJson(
    val en: String = "",
    val es: String = "",
    val note: String = ""
)

/* ------------------------------ dominio ------------------------------- */

data class SpeakingPhrase(
    val en: String,
    val es: String,
    /** Apunte breve: por qué se dice así y no de la otra forma. */
    val note: String = ""
)

data class SpeakingCategory(
    val id: String,
    val title: String,
    val emoji: String,
    val level: String,
    val phrases: List<SpeakingPhrase>
)

fun SpeakingCategoryJson.toDomain(): SpeakingCategory? {
    val frases = phrases.mapNotNull { p ->
        // Sin inglés no hay nada que practicar; sin español el alumno no sabe
        // qué está diciendo, que es peor que no decir nada.
        if (p.en.isBlank() || p.es.isBlank()) null
        else SpeakingPhrase(p.en.trim(), p.es.trim(), p.note.trim())
    }
    if (id.isBlank() || frases.isEmpty()) return null
    return SpeakingCategory(
        id = id,
        title = title.ifBlank { "Frases" },
        emoji = emoji.ifBlank { "💬" },
        level = level,
        phrases = frases
    )
}
