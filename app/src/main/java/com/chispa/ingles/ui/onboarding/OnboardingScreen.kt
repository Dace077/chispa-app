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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.domain.DailyGoal
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.launch

/** Motivos de aprendizaje. Solo afectan al tono de los mensajes, nunca al contenido. */
enum class Motive(val id: String, val label: String, val emoji: String, val blurb: String) {
    TRAVEL("travel", "Viajar", "✈️", "Moverte por el mundo sin señas ni traductor"),
    WORK("work", "Trabajo", "💼", "Reuniones, correos y entrevistas en inglés"),
    STUDY("study", "Estudios", "🎓", "Exámenes, carrera o una beca fuera"),
    MEDIA("media", "Series y música", "🎬", "Entender sin subtítulos, por fin"),
    PEOPLE("people", "Personas", "💬", "Hablar con gente de otros países"),
    FUN("fun", "Por gusto", "✨", "Porque aprender idiomas engancha")
}

class OnboardingViewModel(private val locator: ServiceLocator) : ViewModel() {
    fun finish(motive: Motive, goal: DailyGoal, onDone: () -> Unit) {
        viewModelScope.launch {
            locator.progressRepository.completeOnboarding(motive.id, goal.xp)
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val viewModel: OnboardingViewModel = chispaViewModel { OnboardingViewModel(it) }
    var step by remember { mutableIntStateOf(0) }
    var motive by remember { mutableStateOf<Motive?>(null) }
    var goal by remember { mutableStateOf(DailyGoal.REGULAR) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        StepDots(current = step, total = 3, modifier = Modifier.padding(top = 24.dp))
        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboarding"
        ) { currentStep ->
            when (currentStep) {
                0 -> WelcomeStep()
                1 -> MotiveStep(selected = motive, onSelect = { motive = it })
                else -> GoalStep(selected = goal, onSelect = { goal = it })
            }
        }

        ChispaButton(
            text = when (step) {
                0 -> "Empezar"
                1 -> "Continuar"
                else -> "¡Vamos allá!"
            },
            enabled = step != 1 || motive != null,
            onClick = {
                when (step) {
                    0 -> step = 1
                    1 -> step = 2
                    else -> {
                        onRequestNotificationPermission()
                        viewModel.finish(motive ?: Motive.FUN, goal, onFinished)
                    }
                }
            }
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ChispaMascot(size = 180.dp, mood = MascotMood.HAPPY)
        Spacer(Modifier.height(28.dp))
        Text(
            "Hola, soy Chispa",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Voy a acompañarte de cero hasta hablar inglés de verdad. " +
                "Sin anuncios, sin pagos y sin necesitar internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureChip("🎧", "Escucha")
            FeatureChip("🎤", "Habla")
            FeatureChip("🧠", "Repasa")
        }
    }
}

@Composable
private fun FeatureChip(emoji: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MotiveStep(selected: Motive?, onSelect: (Motive) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(12.dp))
        Text("¿Para qué quieres el inglés?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Con esto adapto los mensajes que te mando. El contenido lo tendrás todo igual.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Motive.entries.forEach { option ->
            SelectableRow(
                emoji = option.emoji,
                title = option.label,
                subtitle = option.blurb,
                selected = selected == option,
                onClick = { onSelect(option) }
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GoalStep(selected: DailyGoal, onSelect: (DailyGoal) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(12.dp))
        Text("¿Cuánto quieres practicar al día?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Elige algo que puedas cumplir un martes cualquiera con sueño. " +
                "Podrás cambiarlo cuando quieras.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        DailyGoal.entries.forEach { option ->
            SelectableRow(
                emoji = "${option.xp}",
                title = option.label,
                subtitle = option.description,
                selected = selected == option,
                onClick = { onSelect(option) },
                emojiIsText = true
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SelectableRow(
    emoji: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    emojiIsText: Boolean = false
) {
    val colors = ChispaThemeTokens.colors
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else colors.cardStroke

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else colors.surfaceElevated
            )
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                emoji,
                style = if (emojiIsText) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.headlineSmall,
                color = if (emojiIsText) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(6.dp)
                    .width(if (index == current) 28.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else ChispaThemeTokens.colors.lockedContainer
                    )
            )
        }
    }
}
