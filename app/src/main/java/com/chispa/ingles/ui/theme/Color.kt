package com.chispa.ingles.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta original de Chispa.
 *
 * Eje principal índigo/violeta (confianza, noche, foco) con acentos coral y
 * ámbar (energía, celebración). El verde queda relegado a un uso mínimo y con
 * un tono teal claramente distinto, para no parecernos a nadie.
 */

// --- Índigo / violeta (columna vertebral de la marca) ---
val Indigo950 = Color(0xFF120C33)
val Indigo900 = Color(0xFF1A1145)
val Indigo800 = Color(0xFF241862)
val Indigo700 = Color(0xFF3A2BB5)
val Violet600 = Color(0xFF4B3AD6)
val Violet500 = Color(0xFF5B4BE8)
val Violet400 = Color(0xFF7C6FF0)
val Violet300 = Color(0xFFA79CF7)
val Violet200 = Color(0xFFC9C2FB)
val Violet100 = Color(0xFFE7E3FE)
val Violet50 = Color(0xFFF4F2FF)

// --- Coral (acento cálido, errores suaves, mascota) ---
val Coral700 = Color(0xFFC2402F)
val Coral500 = Color(0xFFFF6B5A)
val Coral400 = Color(0xFFFF8878)
val Coral200 = Color(0xFFFFC4BC)
val Coral50 = Color(0xFFFFF0EE)

// --- Ámbar (XP, racha, celebración) ---
val Amber600 = Color(0xFFE08A00)
val Amber500 = Color(0xFFFFB020)
val Amber400 = Color(0xFFFFC94D)
val Amber100 = Color(0xFFFFEFC7)

// --- Teal (acierto / correcto) ---
val Teal700 = Color(0xFF0A7A6B)
val Teal500 = Color(0xFF12B5A0)
val Teal400 = Color(0xFF3ED0BC)
val Teal100 = Color(0xFFD3F5F0)

// --- Rosa (error / vidas) ---
val Rose600 = Color(0xFFCC2F35)
val Rose500 = Color(0xFFE5484D)
val Rose400 = Color(0xFFF06A6E)
val Rose100 = Color(0xFFFDE0E1)

// --- Neutros ---
val Ink900 = Color(0xFF15132A)
val Ink700 = Color(0xFF3A3752)
val Ink500 = Color(0xFF6B6885)
val Ink300 = Color(0xFFA9A6BC)
val Ink100 = Color(0xFFE6E4EF)
val Paper = Color(0xFFFAF9FF)
val White = Color(0xFFFFFFFF)

/**
 * Colores semánticos que no encajan en el [androidx.compose.material3.ColorScheme]
 * estándar pero que la app usa por todos lados. Se exponen vía [LocalChispaColors].
 */
data class ChispaColors(
    val correct: Color,
    val correctContainer: Color,
    val onCorrectContainer: Color,
    val wrong: Color,
    val wrongContainer: Color,
    val onWrongContainer: Color,
    val streak: Color,
    val xp: Color,
    val heart: Color,
    val locked: Color,
    val lockedContainer: Color,
    val cardStroke: Color,
    val surfaceElevated: Color,
    val mascotBody: Color,
    val mascotWing: Color,
    val mascotBelly: Color,
    val mascotBeak: Color,
    val levelA1: Color,
    val levelA2: Color,
    val levelB1: Color,
    val levelB2: Color,
    val levelC1: Color,
    val levelC2: Color,
    val levelExtra: Color,
)

val LightChispaColors = ChispaColors(
    correct = Teal700,
    correctContainer = Teal100,
    onCorrectContainer = Color(0xFF04413A),
    wrong = Rose600,
    wrongContainer = Rose100,
    onWrongContainer = Color(0xFF6E1013),
    streak = Color(0xFFF2610C),
    xp = Amber600,
    heart = Rose500,
    locked = Ink300,
    lockedContainer = Ink100,
    cardStroke = Color(0xFFDDD9EC),
    surfaceElevated = White,
    mascotBody = Violet500,
    mascotWing = Teal500,
    mascotBelly = Amber400,
    mascotBeak = Coral500,
    levelA1 = Violet500,
    levelA2 = Teal500,
    levelB1 = Amber500,
    levelB2 = Coral500,
    // Los niveles C cierran la escala con los tonos más profundos: se ganan.
    levelC1 = Color(0xFFD1345B),
    levelC2 = Color(0xFF6D28D9),
    levelExtra = Color(0xFF9B51E0),
)

val DarkChispaColors = ChispaColors(
    correct = Teal400,
    correctContainer = Color(0xFF0B3A34),
    onCorrectContainer = Teal100,
    wrong = Rose400,
    wrongContainer = Color(0xFF4A1417),
    onWrongContainer = Rose100,
    streak = Color(0xFFFF8A3D),
    xp = Amber400,
    heart = Rose400,
    locked = Color(0xFF4A4664),
    lockedContainer = Color(0xFF272341),
    cardStroke = Color(0xFF332E52),
    surfaceElevated = Color(0xFF221D42),
    mascotBody = Violet400,
    mascotWing = Teal400,
    mascotBelly = Amber400,
    mascotBeak = Coral400,
    levelA1 = Violet400,
    levelA2 = Teal400,
    levelB1 = Amber400,
    levelB2 = Coral400,
    levelC1 = Color(0xFFFF7A9C),
    levelC2 = Color(0xFFA78BFA),
    levelExtra = Color(0xFFBB86FC),
)
