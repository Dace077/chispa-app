package com.chispa.ingles.ui.lesson

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.HeartsRow
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun LessonScreen(
    lessonId: String,
    mode: SessionMode,
    onExit: () -> Unit
) {
    val viewModel: LessonViewModel = chispaViewModel(key = "$mode-$lessonId") {
        LessonViewModel(it, lessonId, mode)
    }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors
    val keyboard = LocalSoftwareKeyboardController.current

    var showQuitDialog by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startListening() }

    BackHandler(enabled = state.phase != SessionPhase.FINISHED) {
        if (state.index == 0 && state.correctCount == 0) onExit() else showQuitDialog = true
    }

    when (state.phase) {
        SessionPhase.LOADING -> LoadingPane()

        SessionPhase.EMPTY -> EmptySessionPane(mode = mode, onExit = onExit)

        SessionPhase.FINISHED -> SessionResultScreen(
            state = state,
            onDone = onExit
        )

        SessionPhase.OUT_OF_HEARTS -> OutOfHeartsPane(
            onRecover = viewModel::recoverHearts,
            onExit = onExit
        )

        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .imePadding()
            ) {
                // -------- Cabecera --------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 44.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (state.index == 0 && state.correctCount == 0) onExit() else showQuitDialog = true
                    }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Salir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ChispaProgressBar(
                        progress = state.progress,
                        modifier = Modifier.weight(1f),
                        color = colors.correct
                    )
                    if (state.heartsEnabled) {
                        Spacer(Modifier.width(12.dp))
                        HeartsRow(hearts = state.hearts)
                    }
                }

                // -------- Ejercicio --------
                val exercise = state.current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    if (exercise != null) {
                        Text(
                            exercise.instruction(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(20.dp))
                        ExerciseView(
                            exercise = exercise,
                            state = state,
                            viewModel = viewModel,
                            onRequestMic = {
                                if (viewModel.speechRecognizer.hasPermission()) {
                                    viewModel.startListening()
                                } else {
                                    micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // -------- Feedback y acción --------
                FeedbackFooter(
                    state = state,
                    onPrimaryAction = {
                        keyboard?.hide()
                        when {
                            state.phase == SessionPhase.FEEDBACK -> viewModel.advance()
                            // En pronunciación el botón principal es "saltar":
                            // la respuesta correcta llega por el micrófono, no por aquí.
                            state.current is Exercise.SpeakAndRepeat -> viewModel.skipSpeaking()
                            else -> viewModel.submit()
                        }
                    }
                )
            }
        }
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("¿Seguro que quieres salir?") },
            text = { Text("Perderás el progreso de esta sesión. Ya casi lo tienes.") },
            confirmButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Sigo practicando") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false; onExit() }) { Text("Salir") }
            }
        )
    }

    LaunchedEffect(state.phase) {
        if (state.phase == SessionPhase.ANSWERING) keyboard?.hide()
    }
}

/* =========================================================================
 *  Pie con feedback y botón principal
 * ========================================================================= */

@Composable
private fun FeedbackFooter(
    state: LessonUiState,
    onPrimaryAction: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val feedback = state.feedback
    val showingFeedback = state.phase == SessionPhase.FEEDBACK && feedback != null

    val background = when {
        !showingFeedback -> MaterialTheme.colorScheme.background
        feedback!!.correct -> colors.correctContainer
        else -> colors.wrongContainer
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        AnimatedVisibility(
            visible = showingFeedback,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut()
        ) {
            if (feedback != null) {
                Row(
                    modifier = Modifier.padding(bottom = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (feedback.correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = if (feedback.correct) colors.correct else colors.wrong,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            feedback.headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (feedback.correct) colors.onCorrectContainer else colors.onWrongContainer
                        )
                        if (!feedback.correct && feedback.correctAnswer != null) {
                            Text(
                                feedback.correctAnswer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (feedback.correct) colors.onCorrectContainer else colors.onWrongContainer
                            )
                        }
                        if (feedback.note != null) {
                            Text(
                                feedback.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = (if (feedback.correct) colors.onCorrectContainer else colors.onWrongContainer)
                                    .copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        ChispaButton(
            text = primaryLabel(state),
            enabled = canSubmit(state),
            container = when {
                !showingFeedback -> MaterialTheme.colorScheme.primary
                feedback!!.correct -> colors.correct
                else -> colors.wrong
            },
            onClick = onPrimaryAction
        )
    }
}

private fun primaryLabel(state: LessonUiState): String = when {
    state.phase == SessionPhase.FEEDBACK -> "Continuar"
    state.current is Exercise.Tip ||
        state.current is Exercise.Reading ||
        state.current is Exercise.CultureNote -> "Entendido"
    state.current is Exercise.SpeakAndRepeat -> "Saltar este"
    else -> "Comprobar"
}

private fun canSubmit(state: LessonUiState): Boolean {
    if (state.phase == SessionPhase.FEEDBACK) return true
    return when (val exercise = state.current) {
        null -> false
        is Exercise.MultipleChoice -> state.selectedOption != null
        is Exercise.Translate, is Exercise.ListenAndType -> state.textInput.isNotBlank()
        is Exercise.FillInBlank ->
            if (exercise.options.isNotEmpty()) state.selectedOption != null
            else state.textInput.isNotBlank()
        is Exercise.WordOrder -> state.builtWords.isNotEmpty()
        is Exercise.MatchingPairs -> state.matchState?.isComplete == true
        is Exercise.SpeakAndRepeat -> true
        else -> true
    }
}

/* =========================================================================
 *  Paneles auxiliares
 * ========================================================================= */

@Composable
private fun LoadingPane() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        ChispaMascot(size = 120.dp, mood = MascotMood.THINKING)
    }
}

@Composable
private fun EmptySessionPane(mode: SessionMode, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ChispaMascot(size = 140.dp, mood = MascotMood.SLEEPY)
        Spacer(Modifier.height(20.dp))
        Text(
            when (mode) {
                SessionMode.REVIEW -> "Nada que repasar todavía"
                SessionMode.SPEAKING -> "Aún no hay frases que practicar"
                SessionMode.LESSON -> "Esta lección está vacía"
            },
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (mode) {
                SessionMode.REVIEW -> "Haz un par de lecciones y volveré con palabras para reforzar."
                SessionMode.SPEAKING -> "Completa alguna lección y aquí aparecerán frases para decir en voz alta."
                SessionMode.LESSON -> "Prueba con otra lección mientras lo revisamos."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        ChispaButton(text = "Volver", onClick = onExit)
    }
}

@Composable
private fun OutOfHeartsPane(onRecover: () -> Unit, onExit: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ChispaMascot(size = 140.dp, mood = MascotMood.SAD)
        Spacer(Modifier.height(20.dp))
        Text("Te quedaste sin corazones", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Se recuperan solos con el tiempo (uno cada 4 horas). " +
                "O los recuperas todos ahora mismo y sigues practicando: aquí nadie te cobra nada.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.heart.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Text(
                "Los corazones existen para que vayas despacio y con atención, " +
                    "no para venderte nada.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.heart
            )
        }
        Spacer(Modifier.height(24.dp))
        ChispaButton(text = "Recuperar corazones", onClick = onRecover)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("Salir de la lección")
        }
    }
}
