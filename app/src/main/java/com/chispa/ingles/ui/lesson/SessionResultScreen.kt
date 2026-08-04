package com.chispa.ingles.ui.lesson

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chispa.ingles.domain.Achievement
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.delay

@Composable
fun SessionResultScreen(
    state: LessonUiState,
    onDone: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val outcome = state.outcome
    val accuracy = state.accuracy

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(220)
        revealed = true
    }

    val xpCounter by animateFloatAsState(
        targetValue = if (revealed) (outcome?.xpEarned ?: 0).toFloat() else 0f,
        animationSpec = tween(900),
        label = "xpCount"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            AnimatedVisibility(visible = revealed, enter = scaleIn() + fadeIn()) {
                ChispaMascot(
                    size = 150.dp,
                    mood = if (accuracy >= 80) MascotMood.CELEBRATE else MascotMood.HAPPY
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = headline(state),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultTile(
                    icon = Icons.Filled.Bolt,
                    label = "XP ganada",
                    value = "+${xpCounter.toInt()}",
                    tint = colors.xp,
                    modifier = Modifier.weight(1f)
                )
                ResultTile(
                    icon = Icons.Filled.Percent,
                    label = "Aciertos",
                    value = "$accuracy%",
                    tint = if (accuracy >= 80) colors.correct else colors.wrong,
                    modifier = Modifier.weight(1f)
                )
                ResultTile(
                    icon = Icons.Filled.LocalFireDepartment,
                    label = "Racha",
                    value = (outcome?.streak ?: 0).toString(),
                    tint = colors.streak,
                    modifier = Modifier.weight(1f)
                )
            }

            if (outcome != null) {
                Spacer(Modifier.height(20.dp))
                DailyGoalProgress(outcome.dayXp, outcome.dailyGoalXp)

                if (outcome.streakIncreased) {
                    Spacer(Modifier.height(16.dp))
                    HighlightBanner(
                        emoji = "🔥",
                        title = if (outcome.streak == 1) "¡Racha iniciada!"
                        else "Racha de ${outcome.streak} días",
                        body = if (outcome.streak == 1) "Día uno. Mañana toca el dos."
                        else "Un día más. Así se construye esto."
                    )
                }

                if (outcome.goalJustMet) {
                    Spacer(Modifier.height(12.dp))
                    HighlightBanner(
                        emoji = "🎯",
                        title = "Meta diaria cumplida",
                        body = "Todo lo que hagas a partir de aquí es propina."
                    )
                }

                outcome.rankUp?.let { rank ->
                    Spacer(Modifier.height(12.dp))
                    HighlightBanner(
                        emoji = rank.emoji,
                        title = "Nuevo rango: ${rank.name}",
                        body = "Tu XP total te ha subido de categoría."
                    )
                }

                if (outcome.perfect) {
                    Spacer(Modifier.height(12.dp))
                    HighlightBanner(
                        emoji = "💎",
                        title = "Lección perfecta",
                        body = "Ni un fallo. Impecable."
                    )
                }

                if (outcome.newAchievements.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Logros desbloqueados",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    outcome.newAchievements.forEach { achievement ->
                        AchievementRow(achievement)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            ChispaButton(text = "Continuar", onClick = onDone)
        }
    }
}

private fun headline(state: LessonUiState): String = when {
    state.accuracy == 100 -> "¡Perfecto!"
    state.accuracy >= 80 -> "¡Muy bien!"
    state.accuracy >= 50 -> "¡Sesión completada!"
    else -> "Hecho. Y eso ya vale"
}

private fun subtitle(state: LessonUiState): String = when (state.mode) {
    SessionMode.LESSON -> "Acertaste ${state.correctCount} de ${state.gradedTotal} a la primera."
    SessionMode.REVIEW -> "Repasaste ${state.gradedTotal} palabras. Tu memoria te lo agradece."
    SessionMode.SPEAKING -> "Practicaste ${state.gradedTotal} frases en voz alta."
}

@Composable
private fun ResultTile(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = tint)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DailyGoalProgress(dayXp: Int, goalXp: Int) {
    val colors = ChispaThemeTokens.colors
    val met = dayXp >= goalXp
    ChispaCard {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Meta de hoy", style = MaterialTheme.typography.titleSmall)
                Text(
                    "$dayXp / $goalXp XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (met) colors.correct else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            ChispaProgressBar(
                progress = if (goalXp == 0) 1f else dayXp.toFloat() / goalXp,
                color = if (met) colors.correct else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HighlightBanner(emoji: String, title: String, body: String) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.xp.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.levelExtra.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.levelExtra.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(achievement.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(achievement.title, style = MaterialTheme.typography.titleSmall)
            Text(
                achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
