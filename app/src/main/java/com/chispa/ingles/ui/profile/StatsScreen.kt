package com.chispa.ingles.ui.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.domain.ExerciseStats
import com.chispa.ingles.domain.TypeStat
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val stats: List<TypeStat> = emptyList(),
    val weak: List<TypeStat> = emptyList(),
    val overall: Int = 0,
    val summary: String = ""
)

class StatsViewModel(locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            locator.progressRepository.exerciseStats.collect { filas ->
                val stats = ExerciseStats.read(filas)
                _state.value = StatsUiState(
                    loading = false,
                    stats = stats,
                    weak = ExerciseStats.weakSpots(stats),
                    overall = ExerciseStats.overallAccuracy(stats),
                    summary = ExerciseStats.summary(stats)
                )
            }
        }
    }
}

/**
 * En qué falla el alumno.
 *
 * No es un marcador: es un diagnóstico. Por eso lo primero que se lee es una
 * frase en cristiano y no una tabla, y por eso los tipos flojos se enseñan con
 * lo que significan («entender de oído, sin ver el texto») y no solo con su
 * nombre. Saber que fallas en «listen_and_type» no ayuda; saber que se te
 * atraganta entender sin leer, sí.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onPractice: () -> Unit
) {
    val viewModel: StatsViewModel = chispaViewModel { StatsViewModel(it) }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors

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
            Text("En qué flojeas", style = MaterialTheme.typography.headlineSmall)
        }

        if (!state.loading && state.stats.isEmpty()) {
            EmptyState(
                mood = MascotMood.THINKING,
                title = "Todavía no hay datos",
                message = "Haz unas cuantas lecciones y aquí te diré en qué tipo de " +
                    "ejercicio se te está resistiendo el inglés.",
                modifier = Modifier.fillMaxSize()
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // -------- El diagnóstico, en una frase --------
            ChispaCard(borderColor = MaterialTheme.colorScheme.primary) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${state.overall}%",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "de aciertos a la primera",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(state.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // -------- Práctica dirigida --------
            if (state.weak.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val flojo = state.weak.first()
                ChispaCard(borderColor = colors.streak) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Lo que más te va a subir la media",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Has fallado ${flojo.wrong} de ${flojo.answered} en " +
                                "«${flojo.label.lowercase()}». Es donde tienes más margen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        ChispaButton(text = "Repasar mis fallos", onClick = onPractice)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Por tipo de ejercicio", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Solo cuenta el primer intento de cada ejercicio.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            state.stats.forEach { s ->
                TypeRow(stat = s, esFlojo = s in state.weak)
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TypeRow(stat: TypeStat, esFlojo: Boolean) {
    val colors = ChispaThemeTokens.colors
    val suficiente = stat.answered >= ExerciseStats.MIN_SAMPLE
    val color = when {
        !suficiente -> colors.locked
        esFlojo -> colors.wrong
        stat.accuracy >= 90 -> colors.correct
        else -> MaterialTheme.colorScheme.primary
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stat.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (esFlojo) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    stat.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (suficiente) "${stat.accuracy}%" else "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                Text(
                    "${stat.answered} resp.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ChispaProgressBar(
            progress = if (suficiente) stat.accuracy / 100f else 0f,
            height = 8.dp,
            color = color
        )
        if (!suficiente) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Te faltan ${ExerciseStats.MIN_SAMPLE - stat.answered} respuestas para medirlo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
