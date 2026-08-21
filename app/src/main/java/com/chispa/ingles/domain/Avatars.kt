package com.chispa.ingles.domain

import com.chispa.ingles.data.content.CefrLevel

/**
 * Avatares del alumno.
 *
 * Chispa es la mascota de la app: la que acompaña, explica y celebra. El avatar
 * es otra cosa: **eres tú**. Por eso se desbloquean avanzando, uno por nivel, y
 * por eso el usuario elige cuál lleva puesto.
 *
 * Todos se dibujan con Canvas y comparten esqueleto (mismo cuerpo, misma
 * cabeza, mismos ojos), así que se leen como una familia y no como siete
 * dibujos sueltos. Ver `AvatarArt.kt`.
 */
enum class Avatar(
    val id: String,
    val displayName: String,
    val species: String,
    /** Nivel que hay que COMPLETAR para desbloquearlo. null = disponible ya. */
    val unlockLevel: CefrLevel?,
    val blurb: String
) {
    CHISPA(
        id = "chispa",
        displayName = "Chispa",
        species = "Colibrí",
        unlockLevel = null,
        blurb = "El de siempre. Pequeño, rapidísimo y no se está quieto."
    ),
    TRUFA(
        id = "trufa",
        displayName = "Trufa",
        species = "Cerdita",
        unlockLevel = CefrLevel.A1,
        blurb = "Tiene mejor olfato que nadie. Encuentra lo que busca aunque esté enterrado."
    ),
    NUBE(
        id = "nube",
        displayName = "Nube",
        species = "Oveja",
        unlockLevel = CefrLevel.A2,
        blurb = "Va a su ritmo y llega igual. No hay prisa que le quite el sueño."
    ),
    MICHI(
        id = "michi",
        displayName = "Michi",
        species = "Gatito",
        unlockLevel = CefrLevel.B1,
        blurb = "Curioso hasta el problema. Si hay algo nuevo, ya está encima."
    ),
    BRASA(
        id = "brasa",
        displayName = "Brasa",
        species = "Llama",
        unlockLevel = CefrLevel.B2,
        blurb = "Aguanta lo que le echen y sigue subiendo. Escupe solo si hace falta."
    ),
    FLECHA(
        id = "flecha",
        displayName = "Flecha",
        species = "Halcón",
        unlockLevel = CefrLevel.C1,
        blurb = "Ve el detalle desde lejos. Cuando se lanza, no falla."
    ),
    XOLOTL(
        id = "xolotl",
        displayName = "Xólotl",
        species = "Ajolote",
        unlockLevel = CefrLevel.C2,
        blurb = "Mexicano, raro y capaz de regenerar lo que pierda. Nunca deja de aprender."
    );

    companion object {
        val DEFAULT = CHISPA

        fun from(id: String?): Avatar =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

object AvatarRules {

    /**
     * Avatares disponibles según los niveles ya terminados.
     *
     * Se apoya en [CertificateRules.earnedLevels], que es la misma fuente que
     * decide los certificados: si te dieron el diploma de B1, tienes a Michi.
     * Dos criterios distintos para "terminaste B1" acabarían discrepando.
     */
    fun unlocked(completedLevels: Set<CefrLevel>): List<Avatar> =
        Avatar.entries.filter { it.unlockLevel == null || it.unlockLevel in completedLevels }

    fun isUnlocked(avatar: Avatar, completedLevels: Set<CefrLevel>): Boolean =
        avatar.unlockLevel == null || avatar.unlockLevel in completedLevels

    /**
     * El siguiente por conseguir, para poder enseñarlo bloqueado como zanahoria.
     * Devuelve null cuando ya están todos.
     */
    fun next(completedLevels: Set<CefrLevel>): Avatar? =
        Avatar.entries.firstOrNull { !isUnlocked(it, completedLevels) }

    /**
     * Si el avatar guardado ya no vale, se cae a Chispa.
     *
     * Hace falta porque el progreso se puede reiniciar desde Configuración: sin
     * esto, alguien que borra su progreso se queda con un halcón que ya no le
     * corresponde y la pantalla de selección se contradice a sí misma.
     */
    fun resolve(savedId: String?, completedLevels: Set<CefrLevel>): Avatar {
        val avatar = Avatar.from(savedId)
        return if (isUnlocked(avatar, completedLevels)) avatar else Avatar.DEFAULT
    }
}
