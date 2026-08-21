package com.chispa.ingles.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.PlacementQuestion
import com.chispa.ingles.domain.PlacementLadder
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.LevelChip
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlacementUiState(
    val loading: Boolean = true,
    val blocks: Map<CefrLevel, List<PlacementQuestion>> = emptyMap(),
    val level: CefrLevel = PlacementLadder.START,
    val direction: PlacementLadder.Direction = PlacementLadder.Direction.UP,
    val indexInBlock: Int = 0,
    val correctInBlock: Int = 0,
    val askedTotal: Int = 0,
    val bestPassed: CefrLevel? = null,
    val selected: String? = null,
    val finished: Boolean = false,
    val result: CefrLevel = CefrLevel.A1
) {
    val current: PlacementQuestion?
        get() = blocks[level]?.getOrNull(indexInBlock)

    /**
     * Progreso aproximado. El test es adaptativo, así que la longitud real no
     * se conoce de antemano: se estima sobre el máximo posible.
     */
    val progress: Float
        get() = (askedTotal.toFloat() / PlacementLadder.MAX_QUESTIONS).coerceIn(0f, 1f)
}

class PlacementViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(PlacementUiState())
    val state: StateFlow<PlacementUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val questions = locator.contentRepository.placementTest()
            val blocks = questions
                .groupBy { it.level }
                .mapValues { (_, qs) -> qs.take(PlacementLadder.BLOCK_SIZE) }

            // Si falta el bloque de arranque no hay test posible: se da por
            // hecho A1 y se deja al usuario continuar en vez de bloquearlo.
            val usable = blocks[PlacementLadder.START].orEmpty().isNotEmpty()

            _state.value = _state.value.copy(
                loading = false,
                blocks = blocks,
                finished = !usable,
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
        val acierto = state.selected == question.answer

        val correct = state.correctInBlock + if (acierto) 1 else 0
        val asked = state.askedTotal + 1
        val nextIndex = state.indexInBlock + 1
        val blockSize = state.blocks[state.level]?.size ?: PlacementLadder.BLOCK_SIZE

        // Aún quedan preguntas en este bloque.
        if (nextIndex < blockSize) {
            _state.value = state.copy(
                indexInBlock = nextIndex,
                correctInBlock = correct,
                askedTotal = asked,
                selected = null
            )
            return
        }

        // Bloque terminado: decidimos si subir, bajar o parar.
        val passed = correct >= PlacementLadder.PASS_THRESHOLD
        val best = if (passed) state.level else state.bestPassed

        when (val step = PlacementLadder.nextStep(
            level = state.level,
            passed = passed,
            direction = state.direction,
            bestPassed = state.bestPassed
        )) {
            is PlacementLadder.Step.Finish -> {
                _state.value = state.copy(
                    askedTotal = asked,
                    correctInBlock = correct,
                    bestPassed = best,
                    finished = true,
                    result = step.level,
                    selected = null
                )
            }

            is PlacementLadder.Step.Continue -> {
                // Si el nivel siguiente no tiene preguntas, cerramos con lo que hay.
                val hayBloque = state.blocks[step.level].orEmpty().isNotEmpty()
                if (!hayBloque) {
                    _state.value = state.copy(
                        askedTotal = asked,
                        bestPassed = best,
                        finished = true,
                        result = best ?: CefrLevel.A1,
                        selected = null
                    )
                } else {
                    _state.value = state.copy(
                        level = step.level,
                        direction = step.direction,
                        indexInBlock = 0,
                        correctInBlock = 0,
                        askedTotal = asked,
                        bestPassed = best,
                        selected = null
                    )
                }
            }
        }
    }

    /**
     * @param isRetake si el usuario está repitiendo el test desde Configuración.
     *   En ese caso el nivel solo puede subir (ver `retakePlacement`).
     */
    fun confirm(isRetake: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            if (isRetake) {
                locator.progressRepository.retakePlacement(_state.value.result)
            } else {
                locator.progressRepository.completePlacement(_state.value.result)
            }
            onDone()
        }
    }

    fun skip(isRetake: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            // Al repetir, saltarse el test no debe cambiar nada: el usuario ya
            // tenía un nivel asignado y abandonar a medias no es un resultado.
            if (!isRetake) locator.progressRepository.skipPlacement()
            onDone()
        }
    }
}

@Composable
fun PlacementScreen(
    onFinished: () -> Unit,
    isRetake: Boolean = false
) {
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
                asked = state.askedTotal,
                isRetake = isRetake,
                onContinue = { viewModel.confirm(isRetake, onFinished) }
            )
            return@Column
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ChispaProgressBar(progress = state.progress, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.skip(isRetake, onFinished) }) {
                Text(
                    if (isRetake) "Cancelar" else "Saltar",
                    style = MaterialTheme.typography.labelMedium
                )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelChip(
                    label = "Nivel ${state.level.label}",
                    color = levelTint(state.level)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Pregunta ${state.askedTotal + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.direction == PlacementLadder.Direction.UP && state.askedTotal > 0)
                    "Vas bien: subimos de nivel"
                else if (state.direction == PlacementLadder.Direction.DOWN)
                    "Ajustamos un poco hacia abajo"
                else
                    "El test se adapta a lo que respondas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
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
            text = "Siguiente",
            enabled = state.selected != null,
            onClick = viewModel::next
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun levelTint(level: CefrLevel): Color {
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
    asked: Int,
    onContinue: () -> Unit,
    isRetake: Boolean = false
) {
    val colors = ChispaThemeTokens.colors
    val avanzado = level.order >= CefrLevel.C1.order

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ChispaMascot(
            size = 150.dp,
            mood = if (avanzado) MascotMood.CELEBRATE else MascotMood.HAPPY
        )
        Spacer(Modifier.height(24.dp))
        Text(
            if (isRetake) "Tu nivel ahora" else "Tu punto de partida",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Nivel ${level.label}",
            style = MaterialTheme.typography.displaySmall,
            color = levelTint(level)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            PlacementLadder.describe(level),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        if (isRetake) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Tu progreso, tu racha y tu XP no se tocan. Y si esta vez sales " +
                    "por debajo de donde estabas, tu nivel se queda como estaba: " +
                    "nada de lo que ya tenías abierto se cierra.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        if (level.order > CefrLevel.A1.order) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.correctContainer)
                    .padding(16.dp)
            ) {
                Text(
                    "Todos los niveles por debajo quedan desbloqueados. Puedes " +
                        "repasarlos cuando quieras sin perder tu sitio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onCorrectContainer,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Test adaptativo · $asked preguntas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        ChispaButton(text = "Empezar a aprender", onClick = onContinue)
        Spacer(Modifier.height(16.dp))
    }
}
