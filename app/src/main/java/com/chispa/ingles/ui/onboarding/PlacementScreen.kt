package com.chispa.ingles.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.PlacementQuestion
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlacementUiState(
    val loading: Boolean = true,
    val questions: List<PlacementQuestion> = emptyList(),
    val index: Int = 0,
    val selected: String? = null,
    val correct: Int = 0,
    val finished: Boolean = false,
    val result: CefrLevel = CefrLevel.A1
) {
    val current: PlacementQuestion? get() = questions.getOrNull(index)
    val progress: Float
        get() = if (questions.isEmpty()) 0f else index.toFloat() / questions.size
}

class PlacementViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(PlacementUiState())
    val state: StateFlow<PlacementUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val questions = locator.contentRepository.placementTest()
            _state.value = _state.value.copy(
                loading = false,
                questions = questions,
                // Si el test no se pudo cargar, no dejamos al usuario atrapado en
                // una pantalla en blanco: se le asigna A1 y puede seguir.
                finished = questions.isEmpty(),
                result = CefrLevel.A1
            )
        }
    }

    fun select(option: String) {
        _state.value = _state.value.copy(selected = option)
    }

    fun next() {
        val state = _state.value
        val question = state.current ?: return
        val wasCorrect = state.selected == question.answer
        val correct = state.correct + if (wasCorrect) 1 else 0
        val nextIndex = state.index + 1

        if (nextIndex >= state.questions.size) {
            val level = levelFor(correct, state.questions.size)
            _state.value = state.copy(correct = correct, finished = true, result = level, selected = null)
        } else {
            _state.value = state.copy(index = nextIndex, correct = correct, selected = null)
        }
    }

    /**
     * Traduce aciertos a nivel de partida. Deliberadamente conservador: es mucho
     * peor empezar demasiado arriba y frustrarse que repetir contenido fácil.
     */
    private fun levelFor(correct: Int, total: Int): CefrLevel {
        if (total == 0) return CefrLevel.A1
        val ratio = correct.toFloat() / total
        return when {
            ratio >= 0.8f -> CefrLevel.B1
            ratio >= 0.5f -> CefrLevel.A2
            else -> CefrLevel.A1
        }
    }

    fun confirm(onDone: () -> Unit) {
        viewModelScope.launch {
            locator.progressRepository.completePlacement(_state.value.result)
            onDone()
        }
    }

    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            locator.progressRepository.skipPlacement()
            onDone()
        }
    }
}

@Composable
fun PlacementScreen(onFinished: () -> Unit) {
    val viewModel: PlacementViewModel = chispaViewModel { PlacementViewModel(it) }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        if (state.finished) {
            PlacementResult(
                level = state.result,
                correct = state.correct,
                total = state.questions.size,
                onContinue = { viewModel.confirm(onFinished) }
            )
            return@Column
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ChispaProgressBar(progress = state.progress, modifier = Modifier.weight(1f))
            Spacer(Modifier.height(0.dp))
            TextButton(onClick = { viewModel.skip(onFinished) }) {
                Text("Saltar", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(20.dp))

        val question = state.current
        if (state.loading || question == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChispaMascot(size = 120.dp, mood = MascotMood.THINKING)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Pregunta ${state.index + 1} de ${state.questions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(question.prompt, style = MaterialTheme.typography.headlineSmall)
            if (question.hint != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    question.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))

            question.options.forEach { option ->
                OptionRow(
                    text = option,
                    selected = state.selected == option,
                    onClick = { viewModel.select(option) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        ChispaButton(
            text = if (state.index == state.questions.lastIndex) "Ver resultado" else "Siguiente",
            enabled = state.selected != null,
            onClick = viewModel::next
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else colors.surfaceElevated
            )
            .border(
                2.dp,
                if (selected) MaterialTheme.colorScheme.primary else colors.cardStroke,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlacementResult(
    level: CefrLevel,
    correct: Int,
    total: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ChispaMascot(size = 160.dp, mood = MascotMood.CELEBRATE)
        Spacer(Modifier.height(24.dp))
        Text("Tu punto de partida", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nivel ${level.label}",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Acertaste $correct de $total. " + when (level) {
                CefrLevel.A1 -> "Empezamos desde el principio, con calma y bien hecho."
                CefrLevel.A2 -> "Ya tienes base. Te abro las unidades de A1 por si quieres repasar."
                else -> "Buen nivel. Te dejo A1 y A2 desbloqueados para repasar cuando quieras."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(32.dp))
        AnimatedVisibility(visible = true) {
            ChispaButton(text = "Empezar a aprender", onClick = onContinue)
        }
    }
}
