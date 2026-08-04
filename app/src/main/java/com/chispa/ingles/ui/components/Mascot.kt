package com.chispa.ingles.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estados de ánimo de Chispa, el colibrí.
 *
 * La mascota se dibuja entera con Canvas (nada de PNGs): pesa cero, escala a
 * cualquier tamaño sin pixelarse y se adapta sola al tema claro/oscuro.
 */
enum class MascotMood {
    NEUTRAL,
    HAPPY,
    CELEBRATE,
    SAD,
    THINKING,
    SLEEPY
}

/**
 * Chispa: colibrí de plumaje violeta iridiscente, alas turquesa, pecho ámbar y
 * pico coral. Diseño 100% original.
 */
@Composable
fun ChispaMascot(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    mood: MascotMood = MascotMood.NEUTRAL,
    animate: Boolean = true
) {
    val colors = ChispaThemeTokens.colors

    val transition = rememberInfiniteTransition(label = "chispa")

    // El aleteo del colibrí: rápido y de recorrido corto.
    val wingBeat by transition.animateFloat(
        initialValue = -18f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mood == MascotMood.SLEEPY) 1400 else 260),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wing"
    )

    // Flotación vertical, para que no parezca una pegatina.
    val hover by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hover"
    )

    val celebrationSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(2600), repeatMode = RepeatMode.Restart),
        label = "spin"
    )

    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 100f
        val bobbing = if (animate) hover * unit else 0f

        translate(top = bobbing) {
            drawChispa(
                unit = unit,
                mood = mood,
                wingAngle = if (animate) wingBeat else 6f,
                sparkPhase = if (animate) celebrationSpin else 0f,
                body = colors.mascotBody,
                wing = colors.mascotWing,
                belly = colors.mascotBelly,
                beak = colors.mascotBeak,
                accent = colors.xp
            )
        }
    }
}

private fun DrawScope.drawChispa(
    unit: Float,
    mood: MascotMood,
    wingAngle: Float,
    sparkPhase: Float,
    body: Color,
    wing: Color,
    belly: Color,
    beak: Color,
    accent: Color
) {
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun u(v: Float) = v * unit

    // El dibujo se compone de atrás hacia delante: cola, cuerpo, cabeza, ala y ojo.
    // Todas las piezas nacen dentro de la silueta del cuerpo para que el conjunto
    // se lea como un solo animal y no como formas sueltas.

    // ---- Cola: dos plumas que salen de dentro del cuerpo ----
    val tail = Path().apply {
        moveTo(u(32f), u(60f))
        lineTo(u(5f), u(74f))
        lineTo(u(17f), u(73f))
        lineTo(u(10f), u(85f))
        lineTo(u(34f), u(71f))
        close()
    }
    drawPath(tail, color = body.copy(alpha = 0.75f))

    // ---- Cuerpo ----
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(body, body.copy(alpha = 0.88f)),
            start = p(24f, 42f),
            end = p(70f, 78f)
        ),
        topLeft = p(24f, 42f),
        size = Size(u(44f), u(34f))
    )

    // ---- Pecho ámbar ----
    drawOval(
        color = belly,
        topLeft = p(34f, 50f),
        size = Size(u(26f), u(22f))
    )

    // ---- Cabeza (solapa con el cuerpo para que no haya costura) ----
    drawCircle(color = body, radius = u(15f), center = p(64f, 40f))

    // ---- Garganta iridiscente, la marca de un colibrí ----
    drawOval(
        color = beak.copy(alpha = 0.88f),
        topLeft = p(56f, 46f),
        size = Size(u(19f), u(10f))
    )

    // ---- Pico largo y fino ----
    val beakPath = Path().apply {
        moveTo(u(74f), u(35f))
        lineTo(u(98f), u(39f))
        lineTo(u(74f), u(43f))
        close()
    }
    drawPath(beakPath, color = beak)

    // ---- Cresta: una chispita apoyada en la cabeza ----
    drawSpark(center = p(59f, 23f), radius = u(6.5f), color = accent)

    // ---- Ala: nace sobre el lomo y bate desde ese punto ----
    rotate(degrees = wingAngle, pivot = p(48f, 52f)) {
        val wingPath = Path().apply {
            moveTo(u(48f), u(53f))
            cubicTo(u(42f), u(36f), u(30f), u(28f), u(19f), u(32f))
            cubicTo(u(22f), u(45f), u(34f), u(55f), u(48f), u(53f))
            close()
        }
        drawPath(
            wingPath,
            brush = Brush.linearGradient(
                colors = listOf(wing, wing.copy(alpha = 0.75f)),
                start = p(19f, 28f),
                end = p(48f, 55f)
            )
        )
        drawPath(wingPath, color = Color.Black.copy(alpha = 0.07f), style = Stroke(width = u(1f)))
    }

    // ---- Ojo, que es donde vive la expresión ----
    drawEye(mood = mood, unit = unit)

    // ---- Extras según el ánimo ----
    when (mood) {
        MascotMood.CELEBRATE -> {
            repeat(5) { index ->
                val angle = Math.toRadians((sparkPhase + index * 72f).toDouble())
                val radius = u(46f)
                val center = Offset(
                    u(56f) + (cos(angle) * radius).toFloat(),
                    u(40f) + (sin(angle) * radius * 0.7f).toFloat()
                )
                drawSpark(center = center, radius = u(5f), color = accent.copy(alpha = 0.85f))
            }
        }

        MascotMood.THINKING -> {
            // Burbujas de pensamiento saliendo de la cabeza.
            drawCircle(color = body.copy(alpha = 0.3f), radius = u(2.5f), center = p(80f, 18f))
            drawCircle(color = body.copy(alpha = 0.45f), radius = u(4f), center = p(88f, 10f))
        }

        MascotMood.SLEEPY -> {
            drawSpark(center = p(84f, 16f), radius = u(3.5f), color = body.copy(alpha = 0.4f))
        }

        else -> Unit
    }
}

/** Ojo del colibrí. Cada ánimo cambia forma de párpado y ceja. */
private fun DrawScope.drawEye(mood: MascotMood, unit: Float) {
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun u(v: Float) = v * unit

    val eyeCenter = p(69f, 36f)
    val ink = Color(0xFF1A1145)

    when (mood) {
        MascotMood.HAPPY, MascotMood.CELEBRATE -> {
            // Ojo cerrado en arco hacia arriba: la sonrisa de un pájaro.
            val arc = Path().apply {
                moveTo(u(64f), u(37f))
                quadraticBezierTo(u(69f), u(30f), u(74f), u(37f))
            }
            drawPath(arc, color = ink, style = Stroke(width = u(2.6f)))
        }

        MascotMood.SLEEPY -> {
            drawLine(
                color = ink,
                start = p(64f, 36f),
                end = p(74f, 36f),
                strokeWidth = u(2.4f)
            )
        }

        MascotMood.SAD -> {
            drawCircle(color = Color.White, radius = u(5f), center = eyeCenter)
            drawCircle(color = ink, radius = u(2.6f), center = p(68f, 38f))
            // Ceja caída hacia fuera: la señal universal de desánimo.
            drawLine(
                color = ink.copy(alpha = 0.8f),
                start = p(63f, 28f),
                end = p(74f, 31f),
                strokeWidth = u(2.2f)
            )
        }

        MascotMood.THINKING -> {
            drawCircle(color = Color.White, radius = u(5f), center = eyeCenter)
            drawCircle(color = ink, radius = u(2.6f), center = p(71f, 34f))
            drawLine(
                color = ink.copy(alpha = 0.8f),
                start = p(63f, 29f),
                end = p(75f, 26f),
                strokeWidth = u(2.2f)
            )
        }

        MascotMood.NEUTRAL -> {
            drawCircle(color = Color.White, radius = u(5.5f), center = eyeCenter)
            drawCircle(color = ink, radius = u(3f), center = p(70f, 36f))
            drawCircle(color = Color.White, radius = u(1.1f), center = p(71.5f, 34.5f))
        }
    }
}

/** La chispa de cuatro puntas del logo, reutilizable como partícula. */
private fun DrawScope.drawSpark(center: Offset, radius: Float, color: Color) {
    val inner = radius * 0.34f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticBezierTo(center.x + inner, center.y - inner, center.x + radius, center.y)
        quadraticBezierTo(center.x + inner, center.y + inner, center.x, center.y + radius)
        quadraticBezierTo(center.x - inner, center.y + inner, center.x - radius, center.y)
        quadraticBezierTo(center.x - inner, center.y - inner, center.x, center.y - radius)
        close()
    }
    drawPath(path, color = color)
}
