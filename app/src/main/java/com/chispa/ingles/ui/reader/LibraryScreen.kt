package com.chispa.ingles.ui.reader

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.Reading
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.LevelChip
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun LibraryScreen(onOpenReading: (String) -> Unit) {
    val viewModel: LibraryViewModel = chispaViewModel { LibraryViewModel(it) }
    val state by viewModel.state.collectAsState()

    if (state.vacia && !state.loading) {
        EmptyState(
            title = "La biblioteca está vacía",
            message = "No se pudieron cargar las lecturas desde los archivos de la app.",
            mood = MascotMood.THINKING
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 52.dp, bottom = 28.dp
        )
    ) {
        item {
            Text("Leer", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Textos con la traducción al lado y voz que va señalando cada palabra. " +
                    "Toca una frase para verla en español; toca una palabra para saber qué significa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
        }

        state.porNivel.forEach { (nivel, lecturas) ->
            item(key = "cab_${nivel.name}") {
                Row(
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LevelChip(label = nivel.label, color = tintForLevel(nivel))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${lecturas.size} ${if (lecturas.size == 1) "lectura" else "lecturas"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(lecturas, key = { it.id }) { lectura ->
                ReadingCard(lectura) { onOpenReading(lectura.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ReadingCard(reading: Reading, onClick: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tintForLevel(reading.level).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(reading.category.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(reading.title, style = MaterialTheme.typography.titleMedium)
            if (reading.summary.isNotBlank()) {
                Text(
                    reading.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${reading.minutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${reading.wordCount} palabras",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun tintForLevel(level: CefrLevel): Color {
    val colors = ChispaThemeTokens.colors
    return when (level) {
        CefrLevel.A1 -> colors.levelA1
        CefrLevel.A2 -> colors.levelA2
        CefrLevel.B1 -> colors.levelB1
        CefrLevel.B2 -> colors.levelB2
        CefrLevel.C1 -> colors.levelC1
        CefrLevel.C2 -> colors.levelC2
        CefrLevel.EXTRA -> colors.levelExtra
    }
}
