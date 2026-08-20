package com.chispa.ingles.data.content

import com.chispa.ingles.domain.KidsArtKind
import com.chispa.ingles.domain.KidsItem
import com.chispa.ingles.domain.KidsWorld
import kotlinx.serialization.Serializable

/**
 * Contenido de la etapa infantil.
 *
 * `kids.json` se carga aparte y **no va en `index.json`**, igual que
 * `readings.json`, `grammar.json` y `placement.json`: no es un track del curso
 * y no entra en la ruta de desbloqueo A1→C2.
 */
@Serializable
data class KidsFileJson(
    val version: Int = 1,
    val worlds: List<KidsWorldJson> = emptyList()
)

@Serializable
data class KidsWorldJson(
    val id: String = "",
    val titleEs: String = "",
    val emoji: String = "",
    val color: String = "#7C5CE6",
    val items: List<KidsItemJson> = emptyList()
)

@Serializable
data class KidsItemJson(
    val id: String = "",
    val en: String = "",
    val es: String = "",
    val kind: String = "",
    val art: String = "",
    val say: String = ""
) {
    /**
     * Un elemento sin dibujo o sin palabra se descarta.
     *
     * Aquí no hay texto de apoyo que salve la papeleta: si no se puede pintar,
     * el niño vería un hueco en blanco y no habría forma de contestar.
     */
    fun toDomain(): KidsItem? {
        val kind = KidsArtKind.from(kind)
        if (id.isBlank() || en.isBlank() || art.isBlank()) return null
        if (kind == KidsArtKind.UNKNOWN) return null
        return KidsItem(
            id = id, en = en.trim(), es = es.trim(),
            kind = kind, art = art.trim(), say = say.trim()
        )
    }
}

fun KidsWorldJson.toDomain(): KidsWorld? {
    val elementos = items.mapNotNull { it.toDomain() }
    if (id.isBlank() || elementos.size < 2) return null
    return KidsWorld(
        id = id,
        titleEs = titleEs.ifBlank { "Mundo" },
        emoji = emoji.ifBlank { "⭐" },
        colorHex = color,
        items = elementos
    )
}
