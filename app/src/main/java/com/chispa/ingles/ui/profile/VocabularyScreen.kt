package com.chispa.ingles.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.db.SrsCardEntity
import com.chispa.ingles.domain.Srs
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun VocabularyScreen(onBack: () -> Unit) {
    val viewModel: VocabularyViewModel = chispaViewModel { VocabularyViewModel(it) }
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 44.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column {
                Text("Mi vocabulario", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${state.cards.size} palabras · ordenadas por dominio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.cards.isEmpty() && !state.loading) {
            EmptyState(
                title = "Todavía no hay nada aquí",
                message = "En cuanto empieces una lección, cada palabra nueva aparecerá en " +
                    "esta lista con su nivel de dominio.",
                mood = MascotMood.SLEEPY
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.cards, key = { it.cardKey }) { card ->
                VocabRow(card = card, onSpeak = { viewModel.speak(card.en) })
            }
        }
    }
}

@Composable
private fun VocabRow(card: SrsCardEntity, onSpeak: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    val tint = strengthTint(card.strength)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onSpeak)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(card.en, style = MaterialTheme.typography.titleSmall)
            Text(
                card.es,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(Srs.MAX_STRENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(width = 18.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index < card.strength) tint else colors.lockedContainer)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    Srs.strengthLabel(card.strength),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.VolumeUp,
                contentDescription = "Escuchar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun strengthTint(strength: Int): Color {
    val colors = ChispaThemeTokens.colors
    return when (strength) {
        0, 1 -> colors.wrong
        2, 3 -> colors.xp
        else -> colors.correct
    }
}
