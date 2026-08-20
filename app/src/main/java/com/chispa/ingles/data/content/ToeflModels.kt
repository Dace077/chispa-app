package com.chispa.ingles.data.content

import kotlinx.serialization.Serializable

/* =========================================================================
 *  Material de apoyo del TOEFL ITP
 *
 *  Va aparte de `grammar.json` porque no es lo mismo: la guía de gramática
 *  explica el idioma, y esto explica un EXAMEN. Cambian las preguntas que
 *  responde ("¿cuánto dura?", "¿qué me van a preguntar?", "¿dónde me van a
 *  hacer caer?") y cambia el formato que hace falta para responderlas.
 *
 *  Como todo el contenido, se carga aparte y NO va en index.json.
 * ========================================================================= */

@Serializable
data class ToeflGuideJson(
    val id: String = "toefl_itp",
    val title: String = "",
    val subtitle: String = "",
    /** Párrafos de portada: qué es esto y para qué sirve. */
    val intro: List<String> = emptyList(),
    /** Datos duros del examen, para la tarjeta de resumen. */
    val facts: List<ToeflFactJson> = emptyList(),
    val modules: List<ToeflModuleJson> = emptyList()
)

@Serializable
data class ToeflFactJson(val label: String, val value: String)

@Serializable
data class ToeflModuleJson(
    val id: String,
    val title: String,
    val subtitle: String = "",
    /** listening | structure | reading | general */
    val section: String = "general",
    /** Minutos de lectura aproximados. */
    val minutes: Int = 0,
    val body: List<String> = emptyList(),
    /** Las ideas que hay que llevarse sí o sí. */
    val keys: List<ToeflKeyJson> = emptyList(),
    val examples: List<ToeflExampleJson> = emptyList(),
    /** Trampas típicas: lo que el examen hace para que falles. */
    val traps: List<ToeflTrapJson> = emptyList()
)

@Serializable
data class ToeflKeyJson(val title: String, val text: String)

@Serializable
data class ToeflExampleJson(val en: String, val es: String = "", val note: String = "")

@Serializable
data class ToeflTrapJson(val wrong: String, val right: String, val why: String)

/* ---------------------------- Modelo de dominio ------------------------- */

data class ToeflGuide(
    val title: String,
    val subtitle: String,
    val intro: List<String>,
    val facts: List<ToeflFact>,
    val modules: List<ToeflModule>
) {
    val isEmpty: Boolean get() = modules.isEmpty()
    fun find(id: String): ToeflModule? = modules.firstOrNull { it.id == id }
    val totalMinutes: Int get() = modules.sumOf { it.minutes }
}

data class ToeflFact(val label: String, val value: String)

data class ToeflModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val section: ToeflGuideSection,
    val minutes: Int,
    val body: List<String>,
    val keys: List<ToeflKey>,
    val examples: List<ToeflExample>,
    val traps: List<ToeflTrap>
)

data class ToeflKey(val title: String, val text: String)
data class ToeflExample(val en: String, val es: String, val note: String)
data class ToeflTrap(val wrong: String, val right: String, val why: String)

enum class ToeflGuideSection(val label: String, val emoji: String) {
    GENERAL("El examen", "📋"),
    LISTENING("Listening", "🎧"),
    STRUCTURE("Structure", "🔧"),
    READING("Reading", "📖");

    companion object {
        fun from(raw: String?): ToeflGuideSection =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GENERAL
    }
}

/* ------------------------------ Conversión ------------------------------ */

fun ToeflGuideJson.toDomain(): ToeflGuide = ToeflGuide(
    title = title.ifBlank { "Preparación TOEFL ITP" },
    subtitle = subtitle,
    intro = intro,
    facts = facts.map { ToeflFact(it.label, it.value) },
    modules = modules.map { m ->
        ToeflModule(
            id = m.id,
            title = m.title,
            subtitle = m.subtitle,
            section = ToeflGuideSection.from(m.section),
            // Si el autor no lo pone, se estima: ~180 palabras por minuto de
            // lectura en un idioma que no es el tuyo, redondeando hacia arriba.
            minutes = if (m.minutes > 0) m.minutes else estimarMinutos(m),
            body = m.body,
            keys = m.keys.map { ToeflKey(it.title, it.text) },
            examples = m.examples.map { ToeflExample(it.en, it.es, it.note) },
            traps = m.traps.map { ToeflTrap(it.wrong, it.right, it.why) }
        )
    }
)

private fun estimarMinutos(m: ToeflModuleJson): Int {
    val palabras = m.body.sumOf { it.split(" ").size } +
        m.keys.sumOf { it.text.split(" ").size } +
        m.traps.sumOf { it.why.split(" ").size }
    return (palabras / 180 + 1).coerceIn(1, 20)
}
