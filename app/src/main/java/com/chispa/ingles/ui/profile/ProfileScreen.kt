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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chispa.ingles.core.Time
import com.chispa.ingles.data.db.DailyActivityEntity
import com.chispa.ingles.domain.Achievements
import com.chispa.ingles.domain.Ranks
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenVocabulary: () -> Unit
) {
    val viewModel: ProfileViewModel = chispaViewModel { ProfileViewModel(it) }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors

    val rank = Ranks.current(state.profile.totalXp)
    val nextRank = Ranks.next(state.profile.totalXp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(52.dp))

        // -------- Cabecera de rango --------
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChispaMascot(
                size = 92.dp,
                mood = if (state.profile.currentStreak > 0) MascotMood.HAPPY else MascotMood.NEUTRAL
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${rank.emoji} ${rank.name}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${state.profile.totalXp} XP en total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        ChispaProgressBar(progress = Ranks.progress(state.profile.totalXp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = nextRank?.let {
                "Te faltan ${it.minXp - state.profile.totalXp} XP para ${it.name} ${it.emoji}"
            } ?: "Has llegado al rango máximo. Eso es constancia.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // -------- Métricas --------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("🔥", state.profile.currentStreak.toString(), "Racha actual", Modifier.weight(1f))
            StatBox("🏅", state.profile.longestStreak.toString(), "Racha récord", Modifier.weight(1f))
            StatBox("❄️", state.profile.streakFreezes.toString(), "Comodines", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("📚", state.lessonsCompleted.toString(), "Lecciones", Modifier.weight(1f))
            StatBox("🔤", state.vocabSeen.toString(), "Palabras", Modifier.weight(1f))
            StatBox("💎", state.vocabMastered.toString(), "Dominadas", Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        // -------- Liga personal --------
        PersonalLeagueCard(
            thisWeek = state.thisWeekXp,
            lastWeek = state.lastWeekXp,
            best = state.bestWeekXp
        )

        Spacer(Modifier.height(24.dp))

        // -------- Calendario de actividad --------
        Text("Tu actividad", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cada cuadrito es un día. Cuanto más intenso, más practicaste.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        ActivityHeatmap(activity = state.activity, goalXp = state.profile.dailyGoalXp)

        Spacer(Modifier.height(28.dp))

        // -------- Accesos --------
        NavRow(
            icon = Icons.Filled.EmojiEvents,
            title = "Logros",
            subtitle = "${state.unlockedAchievements.size} de ${Achievements.ALL.size} desbloqueados",
            onClick = onOpenAchievements
        )
        Spacer(Modifier.height(10.dp))
        NavRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "Mi vocabulario",
            subtitle = "${state.vocabSeen} palabras aprendidas",
            onClick = onOpenVocabulary
        )
        Spacer(Modifier.height(10.dp))
        NavRow(
            icon = Icons.Filled.Settings,
            title = "Configuración",
            subtitle = "Recordatorios, voz, tema y datos",
            onClick = onOpenSettings
        )

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.correctContainer)
                .padding(16.dp)
        ) {
            Text(
                "Chispa es gratis y siempre lo será: sin anuncios, sin suscripciones y sin " +
                    "enviar tus datos a ningún sitio. Todo lo que ves aquí vive en tu teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onCorrectContainer
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/* =========================================================================
 *  Piezas
 * ========================================================================= */

@Composable
private fun StatBox(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    val colors = ChispaThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * "Liga" sin servidor: compites contra tú mismo de la semana pasada. Es la única
 * comparación honesta cuando no hay backend, y además es la que de verdad importa.
 */
@Composable
private fun PersonalLeagueCard(thisWeek: Int, lastWeek: Int, best: Int) {
    val colors = ChispaThemeTokens.colors
    val diff = thisWeek - lastWeek

    ChispaCard {
        Column(Modifier.padding(18.dp)) {
            Text("Tu liga personal", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sin rivales inventados: compites contra tu semana pasada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            WeekBar("Esta semana", thisWeek, best, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            WeekBar("Semana pasada", lastWeek, best, colors.locked)
            Spacer(Modifier.height(10.dp))
            WeekBar("Tu récord", best, best, colors.xp)

            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    lastWeek == 0 && thisWeek == 0 -> "Empieza a sumar XP y aquí verás tu evolución."
                    diff > 0 -> "Vas $diff XP por encima de la semana pasada. 🔥"
                    diff == 0 -> "Empate técnico con la semana pasada."
                    else -> "Vas ${-diff} XP por debajo. Todavía hay tiempo."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (diff >= 0) colors.correct else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekBar(label: String, value: Int, max: Int, color: Color) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("$value XP", style = MaterialTheme.typography.labelMedium, color = color)
        }
        Spacer(Modifier.height(4.dp))
        ChispaProgressBar(
            progress = if (max <= 0) 0f else value.toFloat() / max,
            height = 10.dp,
            color = color
        )
    }
}

/** Mapa de calor de actividad, estilo contribuciones: 15 semanas hacia atrás. */
@Composable
private fun ActivityHeatmap(activity: List<DailyActivityEntity>, goalXp: Int) {
    val colors = ChispaThemeTokens.colors
    val byDay = activity.associateBy { it.epochDay }
    val today = Time.today()
    // Arrancamos en el lunes de hace 14 semanas para que las columnas cuadren.
    val start = Time.startOfWeek(today).minusWeeks(14)
    val weeks = 15

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(weeks) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { dayOfWeek ->
                        val date = start.plusWeeks(week.toLong()).plusDays(dayOfWeek.toLong())
                        val future = date.isAfter(today)
                        val xp = byDay[date.toEpochDay()]?.xp ?: 0
                        val intensity = when {
                            future -> -1f
                            xp <= 0 -> 0f
                            goalXp <= 0 -> 1f
                            else -> (xp.toFloat() / goalXp).coerceIn(0.25f, 1f)
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        intensity < 0f -> Color.Transparent
                                        intensity == 0f -> colors.lockedContainer
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = intensity)
                                    }
                                )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Menos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            listOf(0f, 0.3f, 0.6f, 1f).forEach { level ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (level == 0f) colors.lockedContainer
                            else MaterialTheme.colorScheme.primary.copy(alpha = level)
                        )
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "Más",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
