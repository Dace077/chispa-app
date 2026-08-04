package com.chispa.ingles.ui.grammar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.GrammarTopic
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.LevelChip
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.reader.tintForLevel
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun GrammarScreen(
    onOpenTopic: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: GrammarViewModel = chispaViewModel { GrammarViewModel(it) }
    val state by viewModel.state.collectAsState()

    if (state.vacia && !state.loading) {
        EmptyState(
            title = "No hay gramática",
            message = "No se pudo cargar la guía desde los archivos de la app.",
            mood = MascotMood.THINKING
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Gramática", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Para consultar cuando surja la duda. Cada tema termina con los " +
                    "errores que comete quien piensa en español: esa es la parte que más se pega.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::buscar,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar: pasado, the, phrasal...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.buscar("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.nivelesDisponibles, key = { it.name }) { nivel ->
                    NivelFiltro(
                        nivel = nivel,
                        activo = state.nivel == nivel,
                        onClick = { viewModel.filtrarNivel(nivel) }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (state.totalVisible == 0 && !state.loading) {
            item {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Nada coincide con esa búsqueda.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.porArea.forEach { (area, temas) ->
            item(key = "area_$area") {
                Text(
                    area.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
            }
            items(temas, key = { it.id }) { tema ->
                TopicCard(tema) { onOpenTopic(tema.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun NivelFiltro(nivel: CefrLevel, activo: Boolean, onClick: () -> Unit) {
    val color = tintForLevel(nivel)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (activo) color else color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            nivel.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (activo) MaterialTheme.colorScheme.onPrimary else color
        )
    }
}

@Composable
private fun TopicCard(topic: GrammarTopic, onClick: () -> Unit) {
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
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelChip(label = topic.level.label, color = tintForLevel(topic.level))
                Spacer(Modifier.width(8.dp))
                Text(topic.title, style = MaterialTheme.typography.titleMedium)
            }
            if (topic.question.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    topic.question,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (topic.mistakes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${topic.mistakes.size} errores típicos",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.wrong
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
