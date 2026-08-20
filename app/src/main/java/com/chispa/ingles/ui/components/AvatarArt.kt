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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chispa.ingles.domain.Avatar

/**
 * Los avatares, dibujados con Canvas igual que Chispa: ni un PNG, peso cero y
 * escalan a cualquier tamaño sin pixelarse.
 *
 * **Todos van de frente**, y Chispa de perfil. No es un descuido: un avatar es
 * una foto de perfil, se mira de cara, y de frente los seis comparten el mismo
 * esqueleto —cabeza grande, cuerpecito debajo, ojos en el mismo sitio— así que
 * se leen como una familia. Chispa se queda de perfil porque no es un avatar de
 * jugador: es la mascota que acompaña y explica, y ese papel es otro.
 *
 * El esqueleto compartido está en [drawAvatarBase] y [drawAvatarEyes]. Cada
 * animal solo añade lo suyo: orejas, hocico, lana, branquias. Si algún día hay
 * que retocar la proporción de todos, se toca ahí y punto.
 */

private val INK = Color(0xFF241A17)

/** Paleta de cada bicho. Fija, no depende del tema: un personaje no cambia de color. */
private data class Pelaje(
    val cuerpo: Color,
    val sombra: Color,
    val claro: Color,
    val detalle: Color,
    val acento: Color = detalle
)

private val PELAJES = mapOf(
    Avatar.TRUFA to Pelaje(
        cuerpo = Color(0xFFF7A8B8), sombra = Color(0xFFE38DA0),
        claro = Color(0xFFFFD3DC), detalle = Color(0xFFE8768F)
    ),
    Avatar.NUBE to Pelaje(
        cuerpo = Color(0xFFFAF6EE), sombra = Color(0xFFE3DCCE),
        claro = Color(0xFFFFFDF8), detalle = Color(0xFF4A4038), acento = Color(0xFF6E6055)
    ),
    Avatar.MICHI to Pelaje(
        cuerpo = Color(0xFFF4A855), sombra = Color(0xFFDB8C36),
        claro = Color(0xFFFFE0BC), detalle = Color(0xFFE8768F)
    ),
    Avatar.BRASA to Pelaje(
        cuerpo = Color(0xFFD9A36B), sombra = Color(0xFFBF8850),
        claro = Color(0xFFF3DEC2), detalle = Color(0xFF7A5230)
    ),
    Avatar.FLECHA to Pelaje(
        cuerpo = Color(0xFF7C8AA3), sombra = Color(0xFF5D6B85),
        claro = Color(0xFFE7ECF4), detalle = Color(0xFFFFB020)
    ),
    Avatar.XOLOTL to Pelaje(
        cuerpo = Color(0xFFF3A9D4), sombra = Color(0xFFDD8DBF),
        claro = Color(0xFFFFD6EE), detalle = Color(0xFFE0559F)
    )
)

/**
 * Pinta el avatar elegido.
 *
 * Chispa delega en [ChispaMascot], que ya existía y va de perfil; el resto usan
 * el esqueleto frontal de este archivo.
 */
@Composable
fun AvatarView(
    avatar: Avatar,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    mood: MascotMood = MascotMood.NEUTRAL,
    animate: Boolean = true
) {
    if (avatar == Avatar.CHISPA) {
        ChispaMascot(modifier = modifier, size = size, mood = mood, animate = animate)
        return
    }

    val pelaje = PELAJES[avatar] ?: return
    val transition = rememberInfiniteTransition(label = "avatar")

    val flote by transition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(animation = tween(2000), repeatMode = RepeatMode.Reverse),
        label = "flote"
    )
    val meneo by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(animation = tween(2600), repeatMode = RepeatMode.Reverse),
        label = "meneo"
    )

    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 100f
        translate(top = if (animate) flote * u else 0f) {
            dibujar(avatar, pelaje, u, mood, if (animate) meneo else 0f)
        }
    }
}

private fun DrawScope.dibujar(
    avatar: Avatar,
    pel: Pelaje,
    u: Float,
    mood: MascotMood,
    meneo: Float
) {
    when (avatar) {
        Avatar.TRUFA -> drawTrufa(u, pel, mood, meneo)
        Avatar.NUBE -> drawNube(u, pel, mood)
        Avatar.MICHI -> drawMichi(u, pel, mood, meneo)
        Avatar.BRASA -> drawBrasa(u, pel, mood)
        Avatar.FLECHA -> drawFlecha(u, pel, mood)
        Avatar.XOLOTL -> drawXolotl(u, pel, mood, meneo)
        Avatar.CHISPA -> Unit
    }
}

/**
 * Dibuja un avatar quieto sobre cualquier [DrawScope].
 *
 * Existe para poder renderizarlos fuera de Compose y **mirarlos**: un avatar
 * que compila no es un avatar que se parezca a un cerdo. Lo usa
 * `AvatarArtTest`, que los vuelca a PNG en el dispositivo.
 */
internal fun DrawScope.drawAvatarStatic(avatar: Avatar, sizePx: Float, mood: MascotMood) {
    val pel = PELAJES[avatar] ?: return
    dibujar(avatar, pel, sizePx / 100f, mood, meneo = 0f)
}

/* =========================================================================
 *  Esqueleto compartido
 * ========================================================================= */

private fun DrawScope.p(x: Float, y: Float, u: Float) = Offset(x * u, y * u)

/** Cuerpo y cabeza. Todos los avatares nacen de aquí. */
private fun DrawScope.drawAvatarBase(u: Float, pel: Pelaje, cabezaR: Float = 29f) {
    // Cuerpecito, apenas asomando: en un avatar manda la cabeza.
    drawOval(
        color = pel.sombra,
        topLeft = p(29f, 66f, u),
        size = Size(42f * u, 34f * u)
    )
    drawOval(
        color = pel.claro,
        topLeft = p(38f, 76f, u),
        size = Size(24f * u, 24f * u)
    )
    drawCircle(color = pel.cuerpo, radius = cabezaR * u, center = p(50f, 44f, u))
}

/**
 * Los ojos, en el mismo sitio en todos.
 *
 * Es lo que más hace que se lean como una familia: si cada bicho tiene los ojos
 * a su altura, el conjunto parece de siete autores distintos.
 */
private fun DrawScope.drawAvatarEyes(
    u: Float,
    mood: MascotMood,
    separacion: Float = 12f,
    altura: Float = 42f,
    radio: Float = 6.2f
) {
    val izq = p(50f - separacion, altura, u)
    val der = p(50f + separacion, altura, u)

    when (mood) {
        MascotMood.HAPPY, MascotMood.CELEBRATE -> {
            listOf(izq, der).forEach { c ->
                val arco = Path().apply {
                    moveTo(c.x - radio * u, c.y + 1.5f * u)
                    quadraticBezierTo(c.x, c.y - radio * 1.2f * u, c.x + radio * u, c.y + 1.5f * u)
                }
                drawPath(arco, color = INK, style = Stroke(width = 2.6f * u))
            }
        }

        MascotMood.SLEEPY -> listOf(izq, der).forEach { c ->
            drawLine(INK, Offset(c.x - radio * u, c.y), Offset(c.x + radio * u, c.y), 2.4f * u)
        }

        else -> {
            listOf(izq, der).forEach { c ->
                drawCircle(Color.White, radio * u, c)
                val mirada = when (mood) {
                    MascotMood.THINKING -> Offset(c.x + 1.6f * u, c.y - 1.6f * u)
                    MascotMood.SAD -> Offset(c.x, c.y + 1.4f * u)
                    else -> Offset(c.x + 0.6f * u, c.y + 0.4f * u)
                }
                drawCircle(INK, radio * 0.55f * u, mirada)
                drawCircle(Color.White, radio * 0.2f * u, Offset(mirada.x + 1.2f * u, mirada.y - 1.2f * u))
            }
            if (mood == MascotMood.SAD) {
                drawLine(INK.copy(alpha = .8f), p(33f, 32f, u), p(43f, 35f, u), 2.2f * u)
                drawLine(INK.copy(alpha = .8f), p(67f, 32f, u), p(57f, 35f, u), 2.2f * u)
            }
        }
    }
}

/** Mofletes. Un toque de color que sienta bien a casi todos. */
private fun DrawScope.drawMofletes(u: Float, color: Color, y: Float = 53f, x: Float = 26f) {
    drawOval(color.copy(alpha = .45f), p(50f - x - 6f, y, u), Size(12f * u, 7f * u))
    drawOval(color.copy(alpha = .45f), p(50f + x - 6f, y, u), Size(12f * u, 7f * u))
}

/** Boca sencilla, la misma curva para todos los que la llevan. */
private fun DrawScope.drawBoca(u: Float, y: Float, ancho: Float = 8f) {
    val boca = Path().apply {
        moveTo((50f - ancho) * u, y * u)
        quadraticBezierTo(50f * u, (y + 4f) * u, (50f + ancho) * u, y * u)
    }
    drawPath(boca, color = INK, style = Stroke(width = 2.2f * u))
}

/* =========================================================================
 *  Trufa, la cerdita
 * ========================================================================= */
private fun DrawScope.drawTrufa(u: Float, pel: Pelaje, mood: MascotMood, meneo: Float) {
    // Orejas caídas hacia fuera, que es lo que distingue a un cerdo de un ratón.
    listOf(-1f, 1f).forEach { lado ->
        rotate(degrees = meneo * lado, pivot = p(50f + lado * 22f, 26f, u)) {
            val oreja = Path().apply {
                moveTo((50f + lado * 14f) * u, 26f * u)
                lineTo((50f + lado * 32f) * u, 12f * u)
                lineTo((50f + lado * 33f) * u, 34f * u)
                close()
            }
            drawPath(oreja, color = pel.sombra)
        }
    }

    drawAvatarBase(u, pel)
    drawAvatarEyes(u, mood)
    drawMofletes(u, pel.detalle)

    // Hocico: el rasgo que lo dice todo.
    drawOval(pel.detalle, p(38f, 52f, u), Size(24f * u, 17f * u))
    drawOval(INK.copy(alpha = .75f), p(44f, 57f, u), Size(4f * u, 6f * u))
    drawOval(INK.copy(alpha = .75f), p(52f, 57f, u), Size(4f * u, 6f * u))
}

/* =========================================================================
 *  Nube, la oveja
 * ========================================================================= */
private fun DrawScope.drawNube(u: Float, pel: Pelaje, mood: MascotMood) {
    // Lana: círculos solapados alrededor de la cabeza. Se dibujan primero para
    // que la cara quede limpia encima.
    val rizos = listOf(
        Triple(26f, 30f, 15f), Triple(50f, 18f, 17f), Triple(74f, 30f, 15f),
        Triple(20f, 50f, 13f), Triple(80f, 50f, 13f), Triple(50f, 66f, 14f)
    )
    rizos.forEach { (x, y, r) -> drawCircle(pel.sombra, r * u, p(x, y, u)) }
    rizos.forEach { (x, y, r) -> drawCircle(pel.claro, (r - 2.5f) * u, p(x, y - 1.5f, u)) }

    // Orejas, asomando por debajo de la lana.
    listOf(-1f, 1f).forEach { lado ->
        drawOval(
            pel.acento,
            p(50f + lado * 30f - 9f, 44f, u),
            Size(18f * u, 10f * u)
        )
    }

    // Cara oscura, como las ovejas de verdad.
    drawOval(pel.detalle, p(31f, 34f, u), Size(38f * u, 40f * u))
    // Flequillo de lana sobre la frente.
    drawCircle(pel.claro, 13f * u, p(50f, 30f, u))
    drawCircle(pel.claro, 9f * u, p(36f, 34f, u))
    drawCircle(pel.claro, 9f * u, p(64f, 34f, u))

    drawAvatarEyes(u, mood, separacion = 9f, altura = 48f, radio = 5.4f)
    drawOval(pel.claro.copy(alpha = .9f), p(46f, 58f, u), Size(8f * u, 6f * u))
}

/* =========================================================================
 *  Michi, el gatito
 * ========================================================================= */
private fun DrawScope.drawMichi(u: Float, pel: Pelaje, mood: MascotMood, meneo: Float) {
    listOf(-1f, 1f).forEach { lado ->
        rotate(degrees = meneo * 0.5f * lado, pivot = p(50f + lado * 18f, 24f, u)) {
            val oreja = Path().apply {
                moveTo((50f + lado * 8f) * u, 24f * u)
                lineTo((50f + lado * 26f) * u, 4f * u)
                lineTo((50f + lado * 30f) * u, 28f * u)
                close()
            }
            drawPath(oreja, color = pel.cuerpo)
            val dentro = Path().apply {
                moveTo((50f + lado * 13f) * u, 24f * u)
                lineTo((50f + lado * 24f) * u, 11f * u)
                lineTo((50f + lado * 26f) * u, 26f * u)
                close()
            }
            drawPath(dentro, color = pel.detalle.copy(alpha = .55f))
        }
    }

    drawAvatarBase(u, pel)
    // Rayas de la frente.
    listOf(-8f, 0f, 8f).forEach { dx ->
        drawLine(pel.sombra, p(50f + dx, 20f, u), p(50f + dx * 1.4f, 30f, u), 3f * u)
    }

    drawAvatarEyes(u, mood)
    drawMofletes(u, pel.detalle, y = 52f)

    // Hocico y bigotes.
    drawOval(pel.claro, p(38f, 50f, u), Size(24f * u, 16f * u))
    val nariz = Path().apply {
        moveTo(46f * u, 54f * u); lineTo(54f * u, 54f * u); lineTo(50f * u, 58f * u); close()
    }
    drawPath(nariz, color = pel.detalle)
    drawBoca(u, y = 60f, ancho = 6f)
    listOf(-1f, 1f).forEach { lado ->
        listOf(-3f, 1f).forEach { dy ->
            drawLine(
                INK.copy(alpha = .35f),
                p(50f + lado * 14f, 56f + dy, u),
                p(50f + lado * 30f, 54f + dy * 1.6f, u),
                1.6f * u
            )
        }
    }
}

/* =========================================================================
 *  Brasa, la llama
 * ========================================================================= */
private fun DrawScope.drawBrasa(u: Float, pel: Pelaje, mood: MascotMood) {
    // Orejas de plátano: estrechas y MUY altas. La primera versión las hizo
    // anchas y cortas, y la cabeza se las comía: salía un burro.
    listOf(-1f, 1f).forEach { lado ->
        val oreja = Path().apply {
            moveTo((50f + lado * 11f) * u, 24f * u)
            cubicTo(
                (50f + lado * 9f) * u, -4f * u,
                (50f + lado * 19f) * u, -6f * u,
                (50f + lado * 19f) * u, 20f * u
            )
            close()
        }
        drawPath(oreja, color = pel.cuerpo)
        drawPath(
            Path().apply {
                moveTo((50f + lado * 13f) * u, 20f * u)
                cubicTo(
                    (50f + lado * 12f) * u, 3f * u,
                    (50f + lado * 16.5f) * u, 2f * u,
                    (50f + lado * 16.5f) * u, 18f * u
                )
                close()
            },
            color = pel.detalle.copy(alpha = .3f)
        )
    }

    drawAvatarBase(u, pel, cabezaR = 24f)
    // Flequillo, que las llamas siempre lo llevan.
    drawCircle(pel.claro, 10f * u, p(43f, 26f, u))
    drawCircle(pel.claro, 8.5f * u, p(56f, 24f, u))

    drawAvatarEyes(u, mood, separacion = 10.5f, altura = 40f, radio = 5.8f)

    // Morro largo, que es la otra mitad de la firma de una llama.
    drawOval(pel.claro, p(37f, 48f, u), Size(26f * u, 28f * u))
    drawOval(INK.copy(alpha = .7f), p(43.5f, 56f, u), Size(4f * u, 3f * u))
    drawOval(INK.copy(alpha = .7f), p(52.5f, 56f, u), Size(4f * u, 3f * u))
    drawBoca(u, y = 65f, ancho = 6f)
}

/* =========================================================================
 *  Flecha, el halcón
 * ========================================================================= */
private fun DrawScope.drawFlecha(u: Float, pel: Pelaje, mood: MascotMood) {
    drawAvatarBase(u, pel)

    // Cabeza entera oscura: es la capucha del halcón peregrino.
    drawCircle(pel.sombra, 29f * u, p(50f, 44f, u))

    // DOS mejillas claras, no una cara blanca.
    //
    // Aquí estaba el error de las dos primeras versiones: un óvalo claro grande
    // sobre cabeza oscura con pico naranja es, exactamente, un pingüino. Lo que
    // hace a una rapaz son dos parches separados por la franja oscura de la
    // bigotera, y un borde superior en ángulo que hace de ceja.
    listOf(-1f, 1f).forEach { lado ->
        val mejilla = Path().apply {
            moveTo((50f + lado * 20f) * u, 36f * u)
            lineTo((50f + lado * 5f) * u, 44f * u)
            lineTo((50f + lado * 5f) * u, 62f * u)
            cubicTo(
                (50f + lado * 12f) * u, 64f * u,
                (50f + lado * 19f) * u, 58f * u,
                (50f + lado * 21f) * u, 48f * u
            )
            close()
        }
        drawPath(mejilla, color = pel.claro)
    }

    drawAvatarEyes(u, mood, separacion = 12f, altura = 45f, radio = 6f)

    // Pico ganchudo, estrecho y con la punta caída.
    val pico = Path().apply {
        moveTo(45f * u, 46f * u)
        lineTo(55f * u, 46f * u)
        cubicTo(54.5f * u, 55f * u, 52.5f * u, 60f * u, 50f * u, 63f * u)
        cubicTo(47.5f * u, 60f * u, 45.5f * u, 55f * u, 45f * u, 46f * u)
        close()
    }
    drawPath(pico, color = pel.detalle)
    drawLine(INK.copy(alpha = .3f), p(46f, 50f, u), p(54f, 50f, u), 1.3f * u)
}

/* =========================================================================
 *  Xólotl, el ajolote
 * ========================================================================= */
private fun DrawScope.drawXolotl(u: Float, pel: Pelaje, mood: MascotMood, meneo: Float) {
    // Branquias externas: tres penachos a cada lado. Es EL rasgo del ajolote.
    //
    // Van como lóbulos gordos y desiguales colgando del tallo. La primera
    // versión los puso como bolitas iguales en fila y parecían un ábaco: unas
    // branquias son plumosas y blandas, no un mecanismo.
    listOf(-1f, 1f).forEach { lado ->
        listOf(Triple(-12f, 26f, 1.0f), Triple(2f, 30f, 1.15f), Triple(16f, 26f, .95f))
            .forEachIndexed { i, (dy, largo, escala) ->
                val base = p(50f + lado * 22f, 40f + dy, u)
                rotate(degrees = meneo * lado * (0.7f + i * 0.25f), pivot = base) {
                    val punta = Offset(base.x + lado * largo * u, base.y - 4f * u)
                    drawLine(pel.sombra, base, punta, 3f * u)
                    // Lóbulos de tamaño decreciente hacia la punta.
                    listOf(0.30f to 7.5f, 0.60f to 6.5f, 0.88f to 5f).forEach { (t, r) ->
                        val cx = base.x + (punta.x - base.x) * t
                        val cy = base.y + (punta.y - base.y) * t
                        drawCircle(pel.detalle, r * escala * u, Offset(cx, cy - r * .55f * u))
                        drawCircle(pel.detalle, r * .82f * escala * u, Offset(cx, cy + r * .6f * u))
                    }
                }
            }
    }

    drawAvatarBase(u, pel, cabezaR = 28f)
    drawAvatarEyes(u, mood, separacion = 13f, altura = 40f, radio = 4.6f)
    drawMofletes(u, pel.detalle, y = 48f, x = 22f)

    // La sonrisa ancha y plácida del ajolote, que es media gracia del bicho.
    val sonrisa = Path().apply {
        moveTo(36f * u, 52f * u)
        quadraticBezierTo(50f * u, 66f * u, 64f * u, 52f * u)
    }
    drawPath(sonrisa, color = INK.copy(alpha = .85f), style = Stroke(width = 2.6f * u))
}
