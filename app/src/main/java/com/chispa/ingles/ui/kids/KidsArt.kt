package com.chispa.ingles.ui.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chispa.ingles.domain.Avatar
import com.chispa.ingles.domain.KidsArtKind
import com.chispa.ingles.domain.KidsItem
import com.chispa.ingles.ui.components.AvatarView
import com.chispa.ingles.ui.components.MascotMood
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * El dibujo de cada palabra de Chispa Kids.
 *
 * Todo va en Canvas, como el resto de la app: ni un PNG. Además de mantener el
 * APK en dos megas, permite que cualquier figura se vea nítida al tamaño que
 * haga falta, y aquí hacen falta grandes — el dibujo **es** el enunciado,
 * porque el niño no puede leer la pregunta.
 *
 * Los animales no se redibujan: son los mismos avatares que ya tiene la app,
 * que además el niño reconoce si el adulto usa la aplicación.
 */
@Composable
fun KidsArt(item: KidsItem, size: Dp, modifier: Modifier = Modifier) {
    when (item.kind) {
        KidsArtKind.ANIMAL -> AvatarView(
            avatar = Avatar.from(item.art),
            size = size,
            mood = MascotMood.HAPPY,
            modifier = modifier
        )

        KidsArtKind.COLOR -> Box(modifier.size(size), Alignment.Center) {
            Canvas(Modifier.size(size)) { manchaDeColor(parseColor(item.art)) }
        }

        KidsArtKind.SHAPE -> Box(modifier.size(size), Alignment.Center) {
            Canvas(Modifier.size(size)) { figura(item.art) }
        }

        KidsArtKind.COUNT -> Box(modifier.size(size), Alignment.Center) {
            Canvas(Modifier.size(size)) { puntos(item.art.toIntOrNull() ?: 1) }
        }

        KidsArtKind.LETTER -> Box(modifier.size(size), Alignment.Center) {
            Letra(item.art, size)
        }

        KidsArtKind.CRITTER -> Box(modifier.size(size), Alignment.Center) {
            Canvas(Modifier.size(size)) { bicho(item.art) }
        }

        KidsArtKind.EMOJI -> Box(modifier.size(size), Alignment.Center) {
            Text(
                text = item.art,
                fontSize = with(LocalDensity.current) { (size * 0.72f).toSp() }
            )
        }

        KidsArtKind.UNKNOWN -> Unit
    }
}

/* ------------------------------------------------------------------ */
/*  Letras                                                             */
/* ------------------------------------------------------------------ */

/**
 * La letra, enorme y en mayuscula.
 *
 * Mayuscula porque es la que se aprende primero: la de los cubos, la de los
 * imanes de la nevera y la del nombre propio del nino. La minuscula viene
 * despues, cuando ya empieza a leer de verdad.
 */
@Composable
private fun Letra(letra: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Color(0xFF7C5CE6).copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letra.uppercase(),
            color = Color(0xFF5B3FD1),
            fontWeight = FontWeight.Black,
            fontSize = with(LocalDensity.current) { (size * 0.58f).toSp() }
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Colores                                                            */
/* ------------------------------------------------------------------ */

/**
 * Una mancha redonda, no un cuadrado perfecto.
 *
 * Se le da una silueta ligeramente irregular, como de pintura, para que el
 * color se lea como algo pintado y no como el fondo de un botón.
 */
private fun DrawScope.manchaDeColor(color: Color) {
    val r = min(size.width, size.height) / 2f * 0.86f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = color, radius = r, center = c)
    // Brillo, para que parezca volumen y no un disco plano.
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = r * 0.34f,
        center = Offset(c.x - r * 0.32f, c.y - r * 0.34f)
    )
}

private fun parseColor(hex: String): Color = runCatching {
    val limpio = hex.removePrefix("#")
    val v = limpio.toLong(16)
    if (limpio.length == 6) Color(0xFF000000 or v) else Color(v)
}.getOrElse { Color(0xFF7C5CE6) }

/* ------------------------------------------------------------------ */
/*  Formas                                                             */
/* ------------------------------------------------------------------ */

private val TINTA_FORMA = Color(0xFF7C5CE6)

private fun DrawScope.figura(nombre: String) {
    val lado = min(size.width, size.height)
    val r = lado / 2f * 0.82f
    val c = Offset(size.width / 2f, size.height / 2f)

    when (nombre.lowercase()) {
        "circle" -> drawCircle(TINTA_FORMA, r, c)

        "square" -> drawRect(
            color = TINTA_FORMA,
            topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f),
            size = Size(r * 1.72f, r * 1.72f)
        )

        "triangle" -> drawPath(
            Path().apply {
                moveTo(c.x, c.y - r)
                lineTo(c.x + r * 0.92f, c.y + r * 0.76f)
                lineTo(c.x - r * 0.92f, c.y + r * 0.76f)
                close()
            },
            TINTA_FORMA
        )

        "star" -> drawPath(estrella(c, r), Color(0xFFF5C518))

        "heart" -> drawPath(corazon(c, r), Color(0xFFE5556F))
    }
}

/** Estrella de cinco puntas: se alternan radio grande y pequeño. */
private fun estrella(c: Offset, r: Float): Path = Path().apply {
    val puntas = 5
    val interior = r * 0.45f
    for (i in 0 until puntas * 2) {
        val radio = if (i % 2 == 0) r else interior
        // -90° para que la primera punta mire hacia arriba.
        val ang = Math.toRadians((i * 180.0 / puntas) - 90.0)
        val x = c.x + (radio * cos(ang)).toFloat()
        val y = c.y + (radio * sin(ang)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Corazón: dos lóbulos arriba y una punta abajo. */
private fun corazon(c: Offset, r: Float): Path = Path().apply {
    val top = c.y - r * 0.42f
    moveTo(c.x, c.y + r * 0.86f)
    cubicTo(
        c.x - r * 1.35f, c.y + r * 0.05f,
        c.x - r * 0.78f, top - r * 0.72f,
        c.x, top - r * 0.02f
    )
    cubicTo(
        c.x + r * 0.78f, top - r * 0.72f,
        c.x + r * 1.35f, c.y + r * 0.05f,
        c.x, c.y + r * 0.86f
    )
    close()
}

/* ------------------------------------------------------------------ */
/*  Números                                                            */
/* ------------------------------------------------------------------ */

/**
 * Puntos gordos para contar, no la cifra.
 *
 * A los tres años el símbolo «4» todavía no significa nada, pero cuatro puntos
 * sí se cuentan con el dedo. Se colocan en las formas del dado, que es como
 * los ve en los juegos de mesa.
 */
private fun DrawScope.puntos(n: Int) {
    val lado = min(size.width, size.height)
    val radio = lado * 0.115f
    val c = Offset(size.width / 2f, size.height / 2f)
    val d = lado * 0.26f
    val color = Color(0xFF3B6FD4)

    val posiciones: List<Offset> = when (n.coerceIn(1, 5)) {
        1 -> listOf(c)
        2 -> listOf(Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d))
        3 -> listOf(Offset(c.x - d, c.y - d), c, Offset(c.x + d, c.y + d))
        4 -> listOf(
            Offset(c.x - d, c.y - d), Offset(c.x + d, c.y - d),
            Offset(c.x - d, c.y + d), Offset(c.x + d, c.y + d)
        )
        else -> listOf(
            Offset(c.x - d, c.y - d), Offset(c.x + d, c.y - d), c,
            Offset(c.x - d, c.y + d), Offset(c.x + d, c.y + d)
        )
    }
    posiciones.forEach { drawCircle(color, radio, it) }
}


/* ------------------------------------------------------------------ */
/*  Animales que no son avatares                                       */
/* ------------------------------------------------------------------ */

/**
 * Los bichos de la granja y del mar.
 *
 * Se dibujan con el mismo esqueleto que los avatares —cabeza redonda, ojos
 * cerrados y contentos— para que toda la app parezca de la misma mano. Lo que
 * cambia de uno a otro son las orejas, el hocico y el color, que es justo por
 * donde un nino los distingue.
 */
private fun DrawScope.bicho(nombre: String) {
    val u = min(size.width, size.height) / 100f
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun p(x: Float, y: Float) = Offset(cx + x * u, cy + y * u)

    when (nombre.lowercase()) {
        "dog" -> {
            val cuerpo = Color(0xFFC98A4B)
            val claro = Color(0xFFF0D6B4)
            // Orejas caidas: es lo que dice «perro» a esta edad.
            drawOval(cuerpo, topLeft = p(-42f, -22f), size = Size(24f * u, 44f * u))
            drawOval(cuerpo, topLeft = p(18f, -22f), size = Size(24f * u, 44f * u))
            drawCircle(cuerpo, 30f * u, p(0f, 0f))
            drawOval(claro, topLeft = p(-16f, 2f), size = Size(32f * u, 24f * u))
            drawCircle(Color(0xFF2B2B33), 5.5f * u, p(0f, 6f))
            ojosFelices(u, cx, cy, 12f, -10f)
        }

        "cow" -> {
            val blanco = Color(0xFFF7F3EC)
            val mancha = Color(0xFF3B3B44)
            // Las manchas van ARRIBA del todo y a los lados. En la primera
            // versión caían justo donde van los ojos y la vaca parecía tener
            // cuatro: dos manchas enormes y dos ojos perdidos debajo.
            drawOval(blanco, topLeft = p(-48f, -8f), size = Size(24f * u, 28f * u))
            drawOval(blanco, topLeft = p(24f, -8f), size = Size(24f * u, 28f * u))
            drawCircle(blanco, 31f * u, p(0f, 2f))
            drawOval(mancha, topLeft = p(-26f, -28f), size = Size(22f * u, 16f * u))
            drawOval(mancha, topLeft = p(10f, -26f), size = Size(15f * u, 11f * u))
            // Cuernos pequeños: rematan la silueta como vaca y no como perro.
            drawOval(Color(0xFFE8D9B5), topLeft = p(-30f, -34f), size = Size(13f * u, 10f * u))
            drawOval(Color(0xFFE8D9B5), topLeft = p(17f, -34f), size = Size(13f * u, 10f * u))
            drawOval(Color(0xFFEFB8C8), topLeft = p(-18f, 10f), size = Size(36f * u, 24f * u))
            drawCircle(Color(0xFF9E6B7C), 3.6f * u, p(-8f, 20f))
            drawCircle(Color(0xFF9E6B7C), 3.6f * u, p(8f, 20f))
            ojosFelices(u, cx, cy, 13f, -2f)
        }

        "duck" -> {
            val amarillo = Color(0xFFF7D046)
            drawCircle(amarillo, 30f * u, p(0f, 0f))
            // Pico ancho y naranja: la senal inconfundible del pato.
            drawOval(Color(0xFFF08A1E), topLeft = p(-15f, 6f), size = Size(30f * u, 16f * u))
            ojosFelices(u, cx, cy, 11f, -12f)
            drawCircle(amarillo, 9f * u, p(0f, -32f))
        }

        "frog" -> {
            val verde = Color(0xFF4CAF50)
            drawCircle(verde, 15f * u, p(-17f, -22f))
            drawCircle(verde, 15f * u, p(17f, -22f))
            drawCircle(Color.White, 8f * u, p(-17f, -24f))
            drawCircle(Color.White, 8f * u, p(17f, -24f))
            drawCircle(Color(0xFF2B2B33), 4f * u, p(-17f, -23f))
            drawCircle(Color(0xFF2B2B33), 4f * u, p(17f, -23f))
            drawCircle(verde, 30f * u, p(0f, 4f))
            drawArc(
                Color(0xFF1B5E20), 20f, 140f, false,
                topLeft = p(-16f, -2f), size = Size(32f * u, 22f * u),
                style = Stroke(2.6f * u)
            )
        }

        "fish" -> {
            val naranja = Color(0xFFF57C33)
            drawPath(
                Path().apply {
                    moveTo(p(14f, 0f).x, p(14f, 0f).y)
                    lineTo(p(40f, -18f).x, p(40f, -18f).y)
                    lineTo(p(40f, 18f).x, p(40f, 18f).y)
                    close()
                },
                naranja.copy(alpha = 0.85f)
            )
            drawOval(naranja, topLeft = p(-38f, -22f), size = Size(56f * u, 44f * u))
            drawCircle(Color.White, 7f * u, p(-18f, -6f))
            drawCircle(Color(0xFF2B2B33), 3.6f * u, p(-19f, -6f))
        }

        "bee" -> {
            val amarillo = Color(0xFFF7C948)
            val negro = Color(0xFF2B2B33)
            // Alas detrás, con borde: en blanco sobre blanco no se veían.
            listOf(-24f, 24f).forEach { x ->
                drawOval(
                    Color(0xFFDCEBFF).copy(alpha = 0.9f),
                    topLeft = p(x - 13f, -30f), size = Size(26f * u, 20f * u)
                )
                drawOval(
                    Color(0xFF9FC0E8),
                    topLeft = p(x - 13f, -30f), size = Size(26f * u, 20f * u),
                    style = Stroke(1.6f * u)
                )
            }
            // La cara va limpia arriba y las rayas solo en el abdomen: antes
            // cruzaban toda la cabeza y tapaban los ojos.
            drawOval(amarillo, topLeft = p(-24f, -4f), size = Size(48f * u, 40f * u))
            listOf(6f to 44f, 17f to 34f).forEach { (y, ancho) ->
                drawRect(negro, topLeft = p(-ancho / 2f, y), size = Size(ancho * u, 6f * u))
            }
            drawCircle(amarillo, 22f * u, p(0f, -14f))
            // Antenas.
            listOf(-9f, 9f).forEach { x ->
                drawLine(
                    negro, p(x, -32f), p(x * 1.5f, -44f), strokeWidth = 2.2f * u
                )
                drawCircle(negro, 3f * u, p(x * 1.5f, -45f))
            }
            ojosFelices(u, cx, cy, 9f, -20f)
        }

        "elephant" -> {
            val gris = Color(0xFF9AA5B1)
            drawOval(gris.copy(alpha = 0.9f), topLeft = p(-54f, -22f), size = Size(32f * u, 46f * u))
            drawOval(gris.copy(alpha = 0.9f), topLeft = p(22f, -22f), size = Size(32f * u, 46f * u))
            drawCircle(gris, 30f * u, p(0f, -2f))
            // La trompa: con eso solo ya se reconoce.
            drawOval(gris, topLeft = p(-7f, 14f), size = Size(14f * u, 36f * u))
            ojosFelices(u, cx, cy, 12f, -12f)
        }

        "horse" -> {
            val cafe = Color(0xFF8B5A2B)
            val crin = Color(0xFF4A2F17)
            drawOval(crin, topLeft = p(-32f, -42f), size = Size(22f * u, 30f * u))
            drawOval(cafe, topLeft = p(-30f, -36f), size = Size(14f * u, 24f * u))
            drawOval(cafe, topLeft = p(16f, -36f), size = Size(14f * u, 24f * u))
            drawCircle(cafe, 28f * u, p(0f, -2f))
            drawOval(Color(0xFFD9AE7E), topLeft = p(-14f, 8f), size = Size(28f * u, 26f * u))
            drawCircle(Color(0xFF5C3A1A), 3.2f * u, p(-6f, 19f))
            drawCircle(Color(0xFF5C3A1A), 3.2f * u, p(6f, 19f))
            ojosFelices(u, cx, cy, 11f, -10f)
        }
    }
}

/** Los mismos ojos cerrados y contentos que llevan los avatares. */
private fun DrawScope.ojosFelices(u: Float, cx: Float, cy: Float, dx: Float, dy: Float) {
    listOf(-dx, dx).forEach { x ->
        drawArc(
            color = Color(0xFF2B2B33),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx + (x - 6f) * u, cy + dy * u),
            size = Size(12f * u, 10f * u),
            style = Stroke(2.6f * u)
        )
    }
}
