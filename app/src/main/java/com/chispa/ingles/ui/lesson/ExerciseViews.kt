package com.chispa.ingles.ui.lesson

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.speech.SpeechState
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun ExerciseView(
    exercise: Exercise,
    state: LessonUiState,
    viewModel: LessonViewModel,
    onRequestMic: () -> Unit
) {
    when (exercise) {
        is Exercise.MultipleChoice -> MultipleChoiceView(exercise, state, viewModel)
        is Exercise.Translate -> TranslateView(exercise, state, viewModel)
        is Exercise.ListenAndType -> ListenAndTypeView(exercise, state, viewModel)
        is Exercise.WordOrder -> WordOrderView(exercise, state, viewModel)
        is Exercise.SpeakAndRepeat -> SpeakAndRepeatView(exercise, state, viewModel, onRequestMic)
        is Exercise.MatchingPairs -> MatchingPairsView(exercise, state, viewModel)
        is Exercise.FillInBlank -> FillInBlankView(exercise, state, viewModel)
        is Exercise.Tip -> TipView(exercise, viewModel)
        is Exercise.Reading -> ReadingView(exercise, viewModel)
        is Exercise.CultureNote -> CultureNoteView(exercise)
    }
}

/* =========================================================================
 *  1. Opción múltiple
 * ========================================================================= */

@Composable
private fun MultipleChoiceView(
    exercise: Exercise.MultipleChoice,
    state: LessonUiState,
    viewModel: LessonViewModel
) {
    Column {
        PromptBubble(
            text = exercise.prompt,
            speakable = exercise.speakPrompt,
            onSpeak = { viewModel.speak(exercise.prompt) },
            onSpeakSlow = { viewModel.speakSlowly(exercise.prompt) }
        )
        Spacer(Modifier.height(20.dp))
        exercise.options.forEach { option ->
            ChoiceRow(
                text = option,
                selected = state.selectedOption == option,
                locked = state.phase == SessionPhase.FEEDBACK,
                isCorrectAnswer = option == exercise.answer,
                revealAnswer = state.phase == SessionPhase.FEEDBACK,
                onClick = { viewModel.selectOption(option) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/* =========================================================================
 *  2. Traducir
 * ========================================================================= */

@Composable
private fun TranslateView(
    exercise: Exercise.Translate,
    state: LessonUiState,
    viewModel: LessonViewModel
) {
    Column {
        PromptBubble(
            text = exercise.prompt,
            speakable = !exercise.toEnglish,
            onSpeak = { viewModel.speak(exercise.prompt) },
            onSpeakSlow = { viewModel.speakSlowly(exercise.prompt) }
        )
        if (exercise.hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                exercise.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(20.dp))
        AnswerField(
            value = state.textInput,
            onValueChange = viewModel::updateText,
            enabled = state.phase == SessionPhase.ANSWERING,
            placeholder = if (exercise.toEnglish) "Escribe en inglés…" else "Escribe en español…",
            onSubmit = viewModel::submit
        )
    }
}

/* =========================================================================
 *  3. Escuchar y escribir
 * ========================================================================= */

@Composable
private fun ListenAndTypeView(
    exercise: Exercise.ListenAndType,
    state: LessonUiState,
    viewModel: LessonViewModel
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioButton(
                icon = Icons.Filled.VolumeUp,
                size = 92.dp,
                description = "Reproducir audio",
                onClick = { viewModel.speak(exercise.audioText) }
            )
            AudioButton(
                icon = Icons.Filled.SlowMotionVideo,
                size = 64.dp,
                description = "Reproducir despacio",
                container = MaterialTheme.colorScheme.tertiary,
                onClick = { viewModel.speakSlowly(exercise.audioText) }
            )
        }
        Spacer(Modifier.height(24.dp))
        AnswerField(
            value = state.textInput,
            onValueChange = viewModel::updateText,
            enabled = state.phase == SessionPhase.ANSWERING,
            placeholder = "Escribe lo que escuchaste",
            onSubmit = viewModel::submit
        )
        if (state.phase == SessionPhase.FEEDBACK && exercise.translation != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                exercise.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* =========================================================================
 *  4. Ordenar palabras
 * ========================================================================= */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordOrderView(
    exercise: Exercise.WordOrder,
    state: LessonUiState,
    viewModel: LessonViewModel
) {
    val colors = ChispaThemeTokens.colors
    Column {
        if (exercise.prompt.isNotBlank()) {
            PromptBubble(
                text = exercise.prompt,
                speakable = false,
                onSpeak = {},
                onSpeakSlow = {}
            )
            Spacer(Modifier.height(20.dp))
        }

        // Zona de construcción
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            if (state.builtWords.isEmpty()) {
                Text(
                    "Toca las palabras en el orden correcto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.builtWords.forEach { token ->
                    WordChip(
                        text = token.text,
                        onClick = { viewModel.unpickWord(token) },
                        enabled = state.phase == SessionPhase.ANSWERING
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Banco de palabras
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.wordPool.forEach { token ->
                WordChip(
                    text = token.text,
                    onClick = { viewModel.pickWord(token) },
                    enabled = state.phase == SessionPhase.ANSWERING,
                    container = colors.surfaceElevated
                )
            }
        }
    }
}

@Composable
private fun WordChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color = MaterialTheme.colorScheme.surface
) {
    val colors = ChispaThemeTokens.colors
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(2.dp, colors.cardStroke, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/* =========================================================================
 *  5. Hablar y repetir
 * ========================================================================= */

@Composable
private fun SpeakAndRepeatView(
    exercise: Exercise.SpeakAndRepeat,
    state: LessonUiState,
    viewModel: LessonViewModel,
    onRequestMic: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val amplitude by viewModel.speechRecognizer.amplitude.collectAsState()

    val listening = state.speech is SpeechState.Listening
    val transition = rememberInfiniteTransition(label = "mic")
    val idlePulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "micPulse"
    )
    val micScale by animateFloatAsState(
        targetValue = if (listening) 1f + amplitude * 0.35f else idlePulse,
        label = "micScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        PromptBubble(
            text = exercise.phrase,
            speakable = true,
            onSpeak = { viewModel.speak(exercise.phrase) },
            onSpeakSlow = { viewModel.speakSlowly(exercise.phrase) }
        )

        if (exercise.translation != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                exercise.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(micScale)
                .clip(CircleShape)
                .background(if (listening) colors.wrong else MaterialTheme.colorScheme.primary)
                .clickable(enabled = state.phase == SessionPhase.ANSWERING) {
                    if (listening) viewModel.stopListening() else onRequestMic()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = if (listening) "Detener" else "Grabar",
                tint = Color.White,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = when (val speech = state.speech) {
                is SpeechState.Listening -> speech.partial ?: "Escuchando… habla ahora"
                SpeechState.Processing -> "Analizando lo que dijiste…"
                SpeechState.NoMatch -> "No te escuché bien. Prueba otra vez"
                SpeechState.PermissionNeeded -> "Necesito permiso para usar el micrófono"
                SpeechState.Unavailable ->
                    "Tu dispositivo no tiene reconocimiento de voz. Puedes saltar este ejercicio."
                is SpeechState.Error -> speech.message
                is SpeechState.Result -> speech.hypotheses.firstOrNull().orEmpty()
                SpeechState.Idle -> "Pulsa el micrófono y repite la frase"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (state.speechScore != null) {
            Spacer(Modifier.height(12.dp))
            val score = state.speechScore
            Text(
                "Coincidencia: ${(score * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = if (score >= 0.72f) colors.correct else colors.wrong
            )
        }
    }
}

/* =========================================================================
 *  6. Unir parejas
 * ========================================================================= */

@Composable
private fun MatchingPairsView(
    exercise: Exercise.MatchingPairs,
    state: LessonUiState,
    viewModel: LessonViewModel
) {
    val match = state.matchState ?: return
    val colors = ChispaThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            match.left.forEach { item ->
                val solved = item in match.solved
                MatchCell(
                    text = item,
                    selected = match.selectedLeft == item,
                    solved = solved,
                    wrong = match.wrongPair?.first == item,
                    onClick = {
                        viewModel.selectMatchLeft(item)
                        viewModel.speak(item)
                    }
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            match.right.forEach { item ->
                val solved = exercise.pairs.any { it.second == item && it.first in match.solved }
                MatchCell(
                    text = item,
                    selected = match.selectedRight == item,
                    solved = solved,
                    wrong = match.wrongPair?.second == item,
                    onClick = { viewModel.selectMatchRight(item) }
                )
            }
        }
    }

    if (match.mistakes > 0) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Fallos: ${match.mistakes}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.wrong
        )
    }
}

@Composable
private fun MatchCell(
    text: String,
    selected: Boolean,
    solved: Boolean,
    wrong: Boolean,
    onClick: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val borderColor by animateColorAsState(
        targetValue = when {
            solved -> colors.correct
            wrong -> colors.wrong
            selected -> MaterialTheme.colorScheme.primary
            else -> colors.cardStroke
        },
        label = "matchBorder"
    )
    val background = when {
        solved -> colors.correctContainer
        wrong -> colors.wrongContainer
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> colors.surfaceElevated
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = !solved, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

/* =========================================================================
 *  7. Rellenar el hueco
 * ========================================================================= */

@Composable
private fun FillInBlankView(
    exercise: Exercise.FillInBlank,
    state: LessonUiState,
    viewModel: LessonViewModel
) {
    val colors = ChispaThemeTokens.colors
    val filled = state.selectedOption ?: state.textInput.ifBlank { null }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated)
                .border(2.dp, colors.cardStroke, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(
                text = exercise.sentence.replace(
                    Exercise.FillInBlank.BLANK,
                    filled ?: "______"
                ),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = if (filled != null) FontWeight.Bold else FontWeight.Normal
                )
            )
        }

        if (exercise.translation != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                exercise.translation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))

        if (exercise.options.isNotEmpty()) {
            exercise.options.forEach { option ->
                ChoiceRow(
                    text = option,
                    selected = state.selectedOption == option,
                    locked = state.phase == SessionPhase.FEEDBACK,
                    isCorrectAnswer = option == exercise.answer,
                    revealAnswer = state.phase == SessionPhase.FEEDBACK,
                    onClick = { viewModel.selectOption(option) }
                )
                Spacer(Modifier.height(10.dp))
            }
        } else {
            AnswerField(
                value = state.textInput,
                onValueChange = viewModel::updateText,
                enabled = state.phase == SessionPhase.ANSWERING,
                placeholder = "Escribe la palabra que falta",
                onSubmit = viewModel::submit
            )
        }
    }
}

/* =========================================================================
 *  8-10. Tarjetas informativas
 * ========================================================================= */

@Composable
private fun TipView(exercise: Exercise.Tip, viewModel: LessonViewModel) {
    val colors = ChispaThemeTokens.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChispaMascot(size = 64.dp, mood = MascotMood.THINKING)
            Spacer(Modifier.width(10.dp))
            Text(exercise.title, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .padding(18.dp)
        ) {
            Text(exercise.body, style = MaterialTheme.typography.bodyLarge)
        }

        if (exercise.examples.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("Ejemplos", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            exercise.examples.forEach { example ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceElevated)
                        .border(2.dp, colors.cardStroke, RoundedCornerShape(14.dp))
                        .clickable { viewModel.speak(example.en) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(example.en, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            example.es,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (example.ipa != null) {
                            Text(
                                example.ipa,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = "Escuchar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingView(exercise: Exercise.Reading, viewModel: LessonViewModel) {
    val colors = ChispaThemeTokens.colors
    Column {
        Text(exercise.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.speak(exercise.body) }) {
                Icon(Icons.Filled.VolumeUp, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Escuchar la historia")
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated)
                .border(2.dp, colors.cardStroke, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(exercise.body, style = MaterialTheme.typography.bodyLarge)
        }
        if (exercise.translation != null) {
            Spacer(Modifier.height(14.dp))
            Text("Traducción", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                exercise.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CultureNoteView(exercise: Exercise.CultureNote) {
    val colors = ChispaThemeTokens.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Public,
                contentDescription = null,
                tint = colors.levelExtra,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(exercise.title, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.levelExtra.copy(alpha = 0.1f))
                .padding(18.dp)
        ) {
            Text(exercise.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/* =========================================================================
 *  Piezas compartidas
 * ========================================================================= */

/** Bocadillo con la frase a trabajar, con Chispa al lado y botones de audio. */
@Composable
private fun PromptBubble(
    text: String,
    speakable: Boolean,
    onSpeak: () -> Unit,
    onSpeakSlow: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    Row(verticalAlignment = Alignment.Bottom) {
        ChispaMascot(size = 68.dp, mood = MascotMood.NEUTRAL)
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated)
                .border(2.dp, colors.cardStroke, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(text, style = MaterialTheme.typography.headlineSmall)
            if (speakable) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAudioAction(Icons.Filled.VolumeUp, "Escuchar", onSpeak)
                    SmallAudioAction(Icons.Filled.SlowMotionVideo, "Despacio", onSpeakSlow)
                }
            }
        }
    }
}

@Composable
private fun SmallAudioAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AudioButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    description: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(size / 2.4f)
        )
    }
}

@Composable
private fun ChoiceRow(
    text: String,
    selected: Boolean,
    locked: Boolean,
    isCorrectAnswer: Boolean,
    revealAnswer: Boolean,
    onClick: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val borderColor by animateColorAsState(
        targetValue = when {
            revealAnswer && isCorrectAnswer -> colors.correct
            revealAnswer && selected -> colors.wrong
            selected -> MaterialTheme.colorScheme.primary
            else -> colors.cardStroke
        },
        label = "choiceBorder"
    )
    val background = when {
        revealAnswer && isCorrectAnswer -> colors.correctContainer
        revealAnswer && selected -> colors.wrongContainer
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> colors.surfaceElevated
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onSubmit() }
        ),
        // Al corregir, el campo se deshabilita: sin esto Material lo pintaría casi
        // gris y el usuario no podría releer lo que había escrito.
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = ChispaThemeTokens.colors.cardStroke,
            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        minLines = 2
    )
}
