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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.GrammarExample
import com.chispa.ingles.data.content.GrammarForm
import com.chispa.ingles.data.content.GrammarMistake
import com.chispa.ingles.data.content.GrammarTopic
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.LevelChip
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.reader.tintForLevel
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun GrammarTopicScreen(
    topicId: String,
    onOpenTopic: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: GrammarTopicViewModel =
        chispaViewModel(key = "grammar-$topicId") { GrammarTopicViewModel(it, topicId) }
    val state by viewModel.state.collectAsState()
    val topic = state.topic

    if (topic == null) {
        if (!state.loading) {
            EmptyState(
                title = "Tema no encontrado",
                message = "Puede que se haya renombrado en una versión más nueva.",
                mood = MascotMood.THINKING
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp)
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelChip(label = topic.level.label, color = tintForLevel(topic.level))
                Spacer(Modifier.width(8.dp))
                Text(
                    topic.area,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(topic.title, style = MaterialTheme.typography.headlineMedium)
            if (topic.question.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    topic.question,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        items(topic.paragraphs, key = { it.take(40) }) { parrafo ->
            Text(
                parrafo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
        }

        if (topic.forms.isNotEmpty()) {
            item { Cabecera("Cómo se forma") }
            items(topic.forms, key = { "f_${it.label}" }) { forma ->
                FormRow(forma)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (topic.examples.isNotEmpty()) {
            item { Cabecera("Ejemplos") }
            items(topic.examples, key = { "e_${it.en}" }) { ejemplo ->
                ExampleRow(ejemplo) { viewModel.escuchar(ejemplo.en) }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (topic.mistakes.isNotEmpty()) {
            item {
                Cabecera("Errores típicos")
                Text(
                    "Lo que sale solo si traduces del español.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }
            items(topic.mistakes, key = { "m_${it.wrong}" }) { fallo ->
                MistakeRow(fallo) { viewModel.escuchar(fallo.right) }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (state.relacionados.isNotEmpty()) {
            item { Cabecera("Relacionado") }
            items(state.relacionados, key = { "r_${it.id}" }) { otro ->
                RelatedRow(otro) { onOpenTopic(otro.id) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Cabecera(texto: String) {
    Spacer(Modifier.height(14.dp))
    Text(texto, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun FormRow(form: GrammarForm) {
    val colors = ChispaThemeTokens.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .padding(14.dp)
    ) {
        Text(
            form.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            form.pattern,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
        if (form.example.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                form.example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ExampleRow(example: GrammarExample, onListen: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onListen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(example.en, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text(
                example.es,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (example.note.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    example.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Filled.VolumeUp,
            contentDescription = "Escuchar",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MistakeRow(mistake: GrammarMistake, onListen: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Close, null,
                tint = colors.wrong,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                mistake.wrong,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.wrong,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Check, null,
                tint = colors.correct,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                mistake.right,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.correct,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onListen)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Escuchar la forma correcta",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (mistake.why.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                mistake.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RelatedRow(topic: GrammarTopic, onClick: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LevelChip(label = topic.level.label, color = tintForLevel(topic.level))
        Text(topic.title, style = MaterialTheme.typography.bodyLarge)
    }
}
