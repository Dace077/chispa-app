package com.chispa.ingles.ui.kids

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chispa.ingles.domain.KidsItem
import com.chispa.ingles.domain.KidsMode
import com.chispa.ingles.domain.KidsRules
import com.chispa.ingles.domain.KidsWorld
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.MascotMood

/**
 * Chispa Kids: la etapa para los que todavía no leen.
 *
 * **Ni una sola instrucción escrita.** Las palabras que se ven aquí son para el
 * adulto que acompaña (el nombre del mundo, la palabra bajo el dibujo); todo lo
 * que el niño necesita para jugar entra por el oído y por el dedo. Si se
 * quitaran todos los textos de esta pantalla, seguiría siendo jugable — esa es
 * la prueba de que está bien hecha.
 */
@Composable
fun KidsScreen(onExit: () -> Unit) {
    val viewModel: KidsViewModel = chispaViewModel { KidsViewModel(it) }
    val state by viewModel.state.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state.stage) {
            KidsStage.CARGANDO -> Unit

            KidsStage.VACIO -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChispaMascot(size = 120.dp, mood = MascotMood.THINKING)
                Spacer(Modifier.height(16.dp))
                Text(
                    "No se pudieron cargar los juegos",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }

            KidsStage.MUNDOS -> Mundos(
                worlds = state.worlds,
                onExplorar = { viewModel.abrirMundo(it, KidsMode.EXPLORAR) },
                onJugar = { viewModel.abrirMundo(it, KidsMode.ENCONTRAR) },
                onExit = onExit
            )

            KidsStage.EXPLORAR -> Explorar(
                world = state.world,
                sonando = state.sonando,
                onTocar = viewModel::decir,
                onVolver = viewModel::volverAMundos
            )

            KidsStage.JUGAR -> Jugar(
                state = state,
                onTocar = viewModel::tocar,
                onRepetir = viewModel::repetir,
                onVolver = viewModel::volverAMundos
            )

            KidsStage.CELEBRAR -> Celebrar(
                aciertos = state.aciertos,
                onOtraVez = viewModel::otraVez,
                onVolver = viewModel::volverAMundos
            )
        }
    }
}

/* ------------------------------- Mundos -------------------------------- */

@Composable
private fun Mundos(
    worlds: List<KidsWorld>,
    onExplorar: (KidsWorld) -> Unit,
    onJugar: (KidsWorld) -> Unit,
    onExit: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "Salir de Chispa Kids")
            }
            Column(Modifier.weight(1f)) {
                Text("Chispa Kids", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Para los que todavía no leen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(worlds, key = { it.id }) { mundo ->
                MundoCard(mundo, onExplorar = { onExplorar(mundo) }, onJugar = { onJugar(mundo) })
            }
        }
    }
}

@Composable
private fun MundoCard(world: KidsWorld, onExplorar: () -> Unit, onJugar: () -> Unit) {
    val color = colorDe(world.colorHex)
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.12f))
            .border(3.dp, color.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(world.emoji, fontSize = 48.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            world.titleEs,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "${world.items.size} palabras",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        // Dos botonazos: oír y jugar. Los iconos hacen el trabajo; el texto es
        // para el adulto que se lo lee al niño la primera vez.
        BotonGrande("👂", "Oír", color.copy(alpha = 0.28f), onExplorar)
        Spacer(Modifier.height(8.dp))
        BotonGrande("🎯", "Jugar", color, onJugar, contenidoBlanco = true)
    }
}

@Composable
private fun BotonGrande(
    emoji: String,
    texto: String,
    fondo: Color,
    onClick: () -> Unit,
    contenidoBlanco: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.size(8.dp))
        Text(
            texto,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (contenidoBlanco) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/* ------------------------------ Explorar ------------------------------- */

@Composable
private fun Explorar(
    world: KidsWorld?,
    sonando: String?,
    onTocar: (KidsItem) -> Unit,
    onVolver: () -> Unit
) {
    if (world == null) return
    Column(Modifier.fillMaxSize()) {
        CabeceraKids(world.titleEs, onVolver)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(world.items, key = { it.id }) { item ->
                val escala by animateFloatAsState(
                    targetValue = if (sonando == item.id) 1.12f else 1f,
                    animationSpec = tween(180),
                    label = "sonando"
                )
                Column(
                    Modifier
                        .scale(escala)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            3.dp,
                            if (sonando == item.id) colorDe(world.colorHex)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(24.dp)
                        )
                        .clickable { onTocar(item) }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KidsArt(item, size = 96.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.en,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        item.es,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* -------------------------------- Jugar -------------------------------- */

@Composable
private fun Jugar(
    state: KidsUiState,
    onTocar: (KidsItem) -> Unit,
    onRepetir: () -> Unit,
    onVolver: () -> Unit
) {
    val ronda = state.ronda ?: return
    val world = state.world ?: return
    val color = colorDe(world.colorHex)

    Column(Modifier.fillMaxSize()) {
        CabeceraKids(world.titleEs, onVolver)

        // Cuántas rondas van, en bolitas: contar puntos sí sabe un niño de tres.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(KidsRules.RONDAS) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < state.aciertos) color
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        // Todo lo que se toca va centrado en la pantalla y no pegado arriba:
        // el móvil lo sujeta un niño, y arriba del todo no llega su pulgar.
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // El altavoz es el enunciado: se puede tocar tantas veces como quiera.
            Box(
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(onClick = onRepetir),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Escuchar otra vez",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ronda.opciones.forEach { item ->
                    val acertado = state.acertado == item.id
                    val fallado = state.fallado == item.id
                    val escala by animateFloatAsState(
                        targetValue = when {
                            acertado -> 1.14f
                            fallado -> 0.92f
                            else -> 1f
                        },
                        animationSpec = tween(160),
                        label = "toque"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .scale(escala)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                if (acertado) Color(0xFF3AA84C).copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                4.dp,
                                when {
                                    acertado -> Color(0xFF3AA84C)
                                    fallado -> Color(0xFFE5A02D)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                RoundedCornerShape(28.dp)
                            )
                            .clickable { onTocar(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        KidsArt(item, size = if (ronda.opciones.size <= 2) 132.dp else 88.dp)
                    }
                }
            }
        }
    }
}

/* ------------------------------ Celebrar ------------------------------- */

@Composable
private fun Celebrar(aciertos: Int, onOtraVez: () -> Unit, onVolver: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChispaMascot(size = 150.dp, mood = MascotMood.CELEBRATE)
        Spacer(Modifier.height(16.dp))

        // Estrellas, una por acierto. No hay nota ni porcentaje: a esta edad
        // un 4/6 no significa nada, pero cuatro estrellas se ven.
        Row {
            repeat(aciertos.coerceAtLeast(1)) {
                Text("⭐", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        BotonGrande("🔁", "Otra vez", Color(0xFF7C5CE6), onOtraVez, contenidoBlanco = true)
        Spacer(Modifier.height(10.dp))
        BotonGrande("🏠", "Volver", Color(0xFF7C5CE6).copy(alpha = 0.22f), onVolver)
    }
}

/* ------------------------------- Comunes ------------------------------- */

@Composable
private fun CabeceraKids(titulo: String, onVolver: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onVolver) {
            Icon(Icons.Filled.Close, contentDescription = "Volver a los mundos")
        }
        Text(titulo, style = MaterialTheme.typography.titleLarge)
    }
}

private fun colorDe(hex: String): Color = runCatching {
    Color(0xFF000000 or hex.removePrefix("#").toLong(16))
}.getOrElse { Color(0xFF7C5CE6) }
