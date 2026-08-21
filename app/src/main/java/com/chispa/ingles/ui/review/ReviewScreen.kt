package com.chispa.ingles.ui.review

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.db.SrsCardEntity
import com.chispa.ingles.domain.Srs
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ReviewUiState(
    val loading: Boolean = true,
    /** true cuando la lista no son fallos sino simplemente lo menos asentado. */
    val soloFlojas: Boolean = false,
    val dueCount: Int = 0,
    val seenCount: Int = 0,
    val masteredCount: Int = 0,
    val hardest: List<SrsCardEntity> = emptyList()
)

class ReviewViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                locator.progressRepository.vocabSeenCount,
                locator.progressRepository.vocabMasteredCount
            ) { seen, mastered -> seen to mastered }
                .collect { (seen, mastered) ->
                    // Si todavía no ha fallado nada, se enseñan las más flojas:
                    // la lista nunca queda vacía teniendo vocabulario.
                    val falladas = locator.progressRepository.hardestCards(8)
                    _state.value = ReviewUiState(
                        loading = false,
                        dueCount = locator.progressRepository.dueCount(),
                        seenCount = seen,
                        masteredCount = mastered,
                        hardest = falladas.ifEmpty { locator.progressRepository.weakestCards(8) },
                        soloFlojas = falladas.isEmpty()
                    )
                }
        }
    }
}

@Composable
fun ReviewScreen(
    onStartReview: () -> Unit,
    onOpenVocabulary: () -> Unit
) {
    val viewModel: ReviewViewModel = chispaViewModel { ReviewViewModel(it) }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 52.dp, bottom = 28.dp
        )
    ) {
        item {
            Text("Repaso inteligente", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Chispa recuerda cuándo estás a punto de olvidar cada palabra y te la " +
                    "devuelve justo a tiempo. Es repetición espaciada, y funciona.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }

        item {
            ChispaCard(borderColor = if (state.dueCount > 0) colors.xp else colors.cardStroke) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChispaMascot(
                            size = 72.dp,
                            mood = if (state.dueCount > 0) MascotMood.THINKING else MascotMood.HAPPY
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.dueCount > 0) "${state.dueCount} listas para repasar"
                                else "Todo al día",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                when {
                                    state.dueCount > 0 ->
                                        "Unos ${(state.dueCount / 3).coerceAtLeast(1)} minutos"
                                    // Sin fallos todavía: no hay "lo difícil" que prometer.
                                    state.soloFlojas -> "Vuelve más tarde y afianza lo aprendido"
                                    else -> "Vuelve más tarde o repasa lo que más se te resiste"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    ChispaButton(
                        text = when {
                            state.dueCount > 0 -> "Empezar repaso"
                            state.soloFlojas -> "Repasar de todos modos"
                            else -> "Repasar lo difícil"
                        },
                        enabled = state.seenCount > 0,
                        container = if (state.dueCount > 0) colors.xp else MaterialTheme.colorScheme.primary,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                        onClick = onStartReview
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    value = state.seenCount.toString(),
                    label = "Palabras vistas",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    value = state.masteredCount.toString(),
                    label = "Dominadas",
                    tint = colors.correct,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    value = state.dueCount.toString(),
                    label = "Pendientes",
                    tint = colors.xp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.seenCount > 0) {
                ChispaProgressBar(
                    progress = state.masteredCount.toFloat() / state.seenCount.coerceAtLeast(1),
                    color = colors.correct
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dominas ${state.masteredCount} de ${state.seenCount} palabras vistas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (state.hardest.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // El título cede el espacio que haga falta; sin el weight,
                    // con la letra grande se comía la fila y «Ver todo» acababa
                    // partido en cuatro líneas de una letra.
                    Text(
                        if (state.soloFlojas) "Lo que llevas menos asentado"
                        else "Las que más se te resisten",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenVocabulary) {
                        Text("Ver todo", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            items(state.hardest, key = { it.cardKey }) { card ->
                HardWordRow(card)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.seenCount == 0 && !state.loading) {
            item {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Haz tu primera lección y aquí empezarán a aparecer palabras para repasar.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    value: String,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HardWordRow(card: SrsCardEntity) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(strengthColor(card.strength).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                card.strength.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = strengthColor(card.strength)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.en, style = MaterialTheme.typography.titleSmall)
            Text(
                card.es,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Srs.strengthLabel(card.strength),
                style = MaterialTheme.typography.labelSmall,
                color = strengthColor(card.strength)
            )
            if (card.lapses > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Bolt,
                        null,
                        tint = colors.wrong,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        if (card.lapses == 1) "1 fallo" else "${card.lapses} fallos",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.wrong
                    )
                }
            }
        }
    }
}

@Composable
private fun strengthColor(strength: Int): androidx.compose.ui.graphics.Color {
    val colors = ChispaThemeTokens.colors
    return when (strength) {
        0, 1 -> colors.wrong
        2, 3 -> colors.xp
        else -> colors.correct
    }
}
