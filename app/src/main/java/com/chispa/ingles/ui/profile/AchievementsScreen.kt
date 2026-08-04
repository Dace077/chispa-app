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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chispa.ingles.domain.Achievement
import com.chispa.ingles.domain.AchievementKind
import com.chispa.ingles.domain.Achievements
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val viewModel: ProfileViewModel = chispaViewModel { ProfileViewModel(it) }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors

    val unlocked = state.unlockedAchievements
    val grouped = Achievements.ALL.groupBy { it.kind }

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
            Text("Logros", style = MaterialTheme.typography.headlineMedium)
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp
            )
        ) {
            item {
                Column {
                    Text(
                        "${unlocked.size} de ${Achievements.ALL.size} desbloqueados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    ChispaProgressBar(
                        progress = unlocked.size.toFloat() / Achievements.ALL.size,
                        color = colors.xp
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            grouped.forEach { (kind, achievements) ->
                item {
                    Text(
                        kindLabel(kind),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
                    )
                }
                items(achievements, key = { it.id }) { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        unlocked = achievement.id in unlocked
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun kindLabel(kind: AchievementKind): String = when (kind) {
    AchievementKind.LESSONS -> "Lecciones"
    AchievementKind.STREAK -> "Rachas"
    AchievementKind.XP -> "Experiencia"
    AchievementKind.VOCAB -> "Vocabulario"
    AchievementKind.SPEAKING -> "Pronunciación"
    AchievementKind.REVIEW -> "Repaso"
    AchievementKind.PERFECT -> "Perfección"
    AchievementKind.SPECIAL -> "Especiales"
}

@Composable
private fun AchievementCard(achievement: Achievement, unlocked: Boolean) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (unlocked) colors.xp.copy(alpha = 0.12f) else colors.surfaceElevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (unlocked) colors.xp.copy(alpha = 0.2f) else colors.lockedContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            if (unlocked) {
                Text(achievement.emoji, style = MaterialTheme.typography.headlineSmall)
            } else {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Bloqueado",
                    tint = colors.locked,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (unlocked) 1f else 0.6f)
        ) {
            Text(achievement.title, style = MaterialTheme.typography.titleSmall)
            Text(
                achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (unlocked) {
            Text("✓", style = MaterialTheme.typography.titleLarge, color = colors.correct)
        }
    }
}
