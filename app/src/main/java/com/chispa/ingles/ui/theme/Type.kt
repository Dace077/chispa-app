package com.chispa.ingles.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Tipografía basada en la familia del sistema (cero descargas, cero dependencias
 * de red) pero con escala y pesos propios: titulares muy marcados para la
 * sensación de "juego" y cuerpo cómodo para leer frases en inglés.
 */

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    spacing: Double = 0.0
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = spacing.sp,
    lineHeightStyle = lineHeightStyle
)

val ChispaTypography = Typography(
    displayLarge = style(44, 50, FontWeight.ExtraBold, -1.0),
    displayMedium = style(36, 42, FontWeight.ExtraBold, -0.8),
    displaySmall = style(30, 36, FontWeight.ExtraBold, -0.6),

    headlineLarge = style(28, 34, FontWeight.Bold, -0.4),
    headlineMedium = style(24, 30, FontWeight.Bold, -0.3),
    headlineSmall = style(21, 27, FontWeight.Bold, -0.2),

    titleLarge = style(19, 25, FontWeight.Bold),
    titleMedium = style(17, 23, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = style(17, 26, FontWeight.Normal),
    bodyMedium = style(15, 22, FontWeight.Normal),
    bodySmall = style(13, 19, FontWeight.Normal),

    labelLarge = style(15, 20, FontWeight.Bold, 0.4),
    labelMedium = style(13, 17, FontWeight.Bold, 0.5),
    labelSmall = style(11, 15, FontWeight.Bold, 0.6),
)
