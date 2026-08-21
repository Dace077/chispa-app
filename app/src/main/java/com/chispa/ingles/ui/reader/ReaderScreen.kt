package com.chispa.ingles.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.Sentence
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ReaderScreen(readingId: String, onBack: () -> Unit) {
    val viewModel: ReaderViewModel = chispaViewModel(key = "reader_$readingId") {
        ReaderViewModel(it, readingId)
    }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors
    val listState = rememberLazyListState()

    // La frase que suena se mantiene a la vista sola, para poder leer sin tocar.
    LaunchedEffect(state.hablando) {
        state.hablando?.let { indice ->
            runCatching { listState.animateScrollToItem(indice, scrollOffset = -240) }
        }
    }

    // Se da por leída cuando la última frase llega a la pantalla.
    val ultima = state.reading?.sentences?.lastOrNull()?.index
    LaunchedEffect(ultima, listState) {
        if (ultima == null) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.key == ultima }
        }.collectLatest { visible ->
            // Un segundo y medio a la vista: abrir y salir de inmediato no cuenta.
            if (visible) {
                delay(1_500)
                viewModel.marcarLeida()
            }
        }
    }

    // Orden estable de aparición: así cada personaje conserva su color aunque
    // uno de ellos hable mucho más que el otro.
    val hablantes = androidx.compose.runtime.remember(state.reading) {
        state.reading?.sentences.orEmpty()
            .map { it.speaker }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    val reading = state.reading
    if (reading == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            ChispaMascot(size = 120.dp, mood = MascotMood.THINKING)
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---------------- Cabecera ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 44.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    reading.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
                Text(
                    "${reading.level.label} · ${reading.minutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = viewModel::alternarTodasLasTraducciones) {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = "Mostrar u ocultar todas las traducciones",
                    tint = if (state.mostrarTodasLasTraducciones) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------------- Texto ----------------
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp
            )
        ) {
            itemsIndexed(reading.sentences, key = { _, s -> s.index }) { indice, frase ->
                if (frase.startsParagraph && indice > 0) Spacer(Modifier.height(18.dp))

                // En un diálogo el nombre solo se repite cuando cambia el turno:
                // así se lee como un guion y no como una lista de etiquetas.
                val hablanteAnterior = reading.sentences.getOrNull(indice - 1)?.speaker.orEmpty()
                if (frase.speaker.isNotEmpty() && frase.speaker != hablanteAnterior) {
                    if (indice > 0) Spacer(Modifier.height(12.dp))
                    SpeakerLabel(
                        nombre = frase.speaker,
                        color = colorDeHablante(frase.speaker, hablantes)
                    )
                }

                SentenceBlock(
                    sentence = frase,
                    sonando = state.hablando == indice,
                    rangoPalabra = if (state.hablando == indice) state.palabra else null,
                    traduccionVisible = state.mostrarTodasLasTraducciones || indice in state.traducidas,
                    colorHablante = if (frase.speaker.isEmpty()) null
                    else colorDeHablante(frase.speaker, hablantes),
                    onTapFrase = { viewModel.alternarTraduccion(indice) },
                    onTapAltavoz = { viewModel.leerFrase(indice) },
                    onTapPalabra = viewModel::tocarPalabra
                )
                Spacer(Modifier.height(4.dp))
            }

            item {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.correctContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        "Toca una palabra para oírla y saber qué significa; si la guardas, " +
                            "entra en tu repaso espaciado. El icono 🔊 lee la frase y el " +
                            "icono de traducción la muestra en español." +
                            if (!state.sincronizaPalabras)
                                "\n\nTu motor de voz no informa palabra por palabra, así que " +
                                    "se resalta la frase entera. Todo lo demás funciona igual."
                            else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onCorrectContainer
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // ---------------- Tarjeta de palabra ----------------
        AnimatedVisibility(
            visible = state.palabraTocada != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            state.palabraTocada?.let { palabra ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(palabra.texto, style = MaterialTheme.typography.titleMedium)
                        Text(
                            palabra.traduccion ?: "No está en el diccionario de la app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    TextButton(onClick = viewModel::guardarPalabra, enabled = !palabra.yaGuardada) {
                        Icon(
                            if (palabra.yaGuardada) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (palabra.yaGuardada) "Guardada" else "Repasar")
                    }
                    IconButton(onClick = viewModel::cerrarPalabra) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ---------------- Controles ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::frasePrevia) {
                    Icon(Icons.Filled.SkipPrevious, "Frase anterior")
                }
                IconButton(onClick = viewModel::repetirFraseActual) {
                    Icon(Icons.Filled.Replay, "Repetir")
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = viewModel::alternarReproduccion),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.reproduciendo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.reproduciendo) "Pausar" else "Leer en voz alta",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = viewModel::fraseSiguiente) {
                    Icon(Icons.Filled.SkipNext, "Frase siguiente")
                }
                Text(
                    "%.1fx".format(state.velocidad),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = state.velocidad,
                onValueChange = viewModel::cambiarVelocidad,
                valueRange = 0.4f..1.3f,
                steps = 8
            )
        }
    }
}

/**
 * Una frase del texto.
 *
 * El resaltado de palabra usa los índices de carácter que manda el motor de
 * voz, así que va exactamente al ritmo de lo que se oye. Cada palabra es
 * pulsable por separado para consultar su significado.
 */
@Composable
private fun SentenceBlock(
    sentence: Sentence,
    sonando: Boolean,
    rangoPalabra: IntRange?,
    traduccionVisible: Boolean,
    colorHablante: Color?,
    onTapFrase: () -> Unit,
    onTapAltavoz: () -> Unit,
    onTapPalabra: (String) -> Unit
) {
    val colors = ChispaThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (sonando) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            // En los diálogos, una barra del color de quien habla marca el turno
            // sin robar espacio al texto.
            .then(
                if (colorHablante != null) Modifier.drawBehind {
                    drawRect(
                        color = colorHablante.copy(alpha = 0.55f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                    )
                } else Modifier
            )
            .padding(
                start = if (colorHablante != null) 14.dp else 8.dp,
                end = 8.dp, top = 6.dp, bottom = 6.dp
            )
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                PalabrasPulsables(
                    texto = sentence.en,
                    rangoResaltado = rangoPalabra,
                    sonando = sonando,
                    onTapPalabra = onTapPalabra,
                    onTapFondo = onTapFrase
                )
            }
            // Dos acciones explícitas por frase. Antes la traducción se abría
            // tocando el hueco entre palabras, que es imposible de adivinar y
            // difícil de acertar con el dedo. En fila y no en columna: apiladas
            // estiraban cada frase y cabía la mitad de texto en pantalla.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTapAltavoz, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = "Leer esta frase",
                        tint = if (sonando) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onTapFrase, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.Translate,
                        contentDescription = "Ver la traducción de esta frase",
                        tint = if (traduccionVisible) colors.correct
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = traduccionVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.padding(top = 4.dp, end = 32.dp)) {
                Text(
                    sentence.es,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.correct
                )
                // La nota va junto a la traducción y no siempre visible: se pide
                // cuando no entiendes algo, que es justo cuando hace falta.
                if (sentence.note.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "💡 ${sentence.note}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakerLabel(nombre: String, color: Color) {
    Row(
        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            nombre.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Color estable por personaje. Si hay más hablantes que colores, se repiten:
 * con tres o cuatro turnos alternos ya se distinguen por posición y nombre.
 */
@Composable
private fun colorDeHablante(nombre: String, orden: List<String>): Color {
    val colors = ChispaThemeTokens.colors
    val paleta = listOf(colors.levelB1, colors.levelA2, colors.levelC1, colors.levelB2)
    val posicion = orden.indexOf(nombre).coerceAtLeast(0)
    return paleta[posicion % paleta.size]
}

/**
 * Parte la frase en palabras pulsables y resalta la que suena.
 *
 * Se usa un Row con envoltura manual en vez de un ClickableText porque hace
 * falta que cada palabra sea su propio objetivo táctil: un dedo sobre texto
 * corrido falla demasiado.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PalabrasPulsables(
    texto: String,
    rangoResaltado: IntRange?,
    sonando: Boolean,
    onTapPalabra: (String) -> Unit,
    onTapFondo: () -> Unit
) {
    val colors = ChispaThemeTokens.colors

    // Trocea conservando la posición de cada palabra en el texto original,
    // que es lo que permite casarla con los índices del motor de voz.
    val trozos = remberTokens(texto)

    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.clickable(onClick = onTapFondo)
    ) {
        trozos.forEach { token ->
            val resaltada = rangoResaltado != null &&
                token.inicio < rangoResaltado.last + 1 &&
                token.fin > rangoResaltado.first

            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = if (resaltada) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                resaltada -> MaterialTheme.colorScheme.primary
                                sonando -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) { append(token.texto) }
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (resaltada) colors.xp.copy(alpha = 0.28f) else Color.Transparent
                    )
                    .clickable { onTapPalabra(token.texto) }
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            )
            Text(" ", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private data class Token(val texto: String, val inicio: Int, val fin: Int)

@Composable
private fun remberTokens(texto: String): List<Token> =
    androidx.compose.runtime.remember(texto) {
        val resultado = mutableListOf<Token>()
        var i = 0
        while (i < texto.length) {
            while (i < texto.length && texto[i].isWhitespace()) i++
            if (i >= texto.length) break
            val inicio = i
            while (i < texto.length && !texto[i].isWhitespace()) i++
            resultado += Token(texto.substring(inicio, i), inicio, i)
        }
        resultado
    }
