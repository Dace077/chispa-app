package com.chispa.ingles.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.LessonKind
import com.chispa.ingles.domain.LessonNode
import com.chispa.ingles.domain.LessonState
import com.chispa.ingles.domain.Ranks
import com.chispa.ingles.domain.TrackNode
import com.chispa.ingles.domain.UnitNode
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.CrownBadge
import com.chispa.ingles.ui.components.HeartsRow
import com.chispa.ingles.ui.components.LevelChip
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.components.StatPill
import com.chispa.ingles.ui.components.StreakFlame
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun HomeScreen(
    onOpenLesson: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: HomeViewModel = chispaViewModel { HomeViewModel(it) }
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        HomeTopBar(state = state, onOpenSettings = onOpenSettings)

        if (state.contentEmpty && !state.loading) {
            com.chispa.ingles.ui.components.EmptyState(
                title = "No encuentro el contenido",
                message = "El currículo no se pudo cargar desde los archivos de la app. " +
                    "Reinstalar la app suele arreglarlo."
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { DailyGoalCard(state = state, onOpenReview = onOpenReview) }

            item { Spacer(Modifier.height(8.dp)) }

            state.coreTracks.forEach { track ->
                track.units.forEach { unitNode ->
                    item(key = "unit_${unitNode.unit.id}") {
                        UnitHeader(unitNode)
                    }
                    itemsIndexed(
                        items = unitNode.lessons,
                        key = { _, item -> "lesson_${item.lesson.id}" }
                    ) { index, lessonNode ->
                        LessonPathNode(
                            node = lessonNode,
                            index = index,
                            levelColor = levelColor(unitNode.unit.level),
                            onClick = { onOpenLesson(lessonNode.lesson.id) }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }

            if (state.extraTracks.isNotEmpty()) {
                item {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Text("Módulos extra", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Todo lo que no cabe en un curso normal: modismos, jerga, " +
                                "inglés de oficina, viajes, pronunciación e historias.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(state.extraTracks, key = { "track_${it.track.id}" }) { trackNode ->
                    ExtraTrackCard(
                        node = trackNode,
                        totalXp = state.profile.totalXp,
                        onOpenLesson = onOpenLesson
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/* =========================================================================
 *  Barra superior
 * ========================================================================= */

@Composable
private fun HomeTopBar(state: HomeUiState, onOpenSettings: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 8.dp, top = 48.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StreakFlame(streak = state.profile.currentStreak)
        StatPill(
            icon = Icons.Filled.Bolt,
            value = state.profile.totalXp.toString(),
            tint = colors.xp,
            contentDescription = "XP total"
        )
        Spacer(Modifier.weight(1f))
        HeartsRow(hearts = state.hearts)
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Configuración",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* =========================================================================
 *  Tarjeta de meta diaria
 * ========================================================================= */

@Composable
private fun DailyGoalCard(state: HomeUiState, onOpenReview: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    val rank = Ranks.current(state.profile.totalXp)
    val goalMet = state.today.xp >= state.profile.dailyGoalXp

    ChispaCard(
        modifier = Modifier.padding(vertical = 8.dp),
        borderColor = if (goalMet) colors.correct else colors.cardStroke
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChispaMascot(
                    size = 68.dp,
                    mood = when {
                        goalMet -> MascotMood.CELEBRATE
                        state.today.xp > 0 -> MascotMood.HAPPY
                        else -> MascotMood.NEUTRAL
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (goalMet) "¡Meta de hoy cumplida!" else "Meta de hoy",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${state.today.xp} / ${state.profile.dailyGoalXp} XP  ·  ${rank.emoji} ${rank.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            ChispaProgressBar(
                progress = state.dailyProgress,
                color = if (goalMet) colors.correct else MaterialTheme.colorScheme.primary
            )

            if (state.dueReviewCount > 0) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.xp.copy(alpha = 0.12f))
                        .clickable(onClick = onOpenReview)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = colors.xp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (state.dueReviewCount == 1) "1 palabra esperando repaso"
                        else "${state.dueReviewCount} palabras esperando repaso",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.xp
                    )
                }
            }

            if (state.hearts < 5 && state.minutesToNextHeart > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Próximo corazón en ${state.minutesToNextHeart} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* =========================================================================
 *  Árbol de lecciones
 * ========================================================================= */

@Composable
private fun UnitHeader(node: UnitNode) {
    val color = levelColor(node.unit.level)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelChip(label = node.unit.level.label, color = color)
            Spacer(Modifier.width(8.dp))
            Text(
                "${node.completedLessons}/${node.lessons.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(node.unit.title, style = MaterialTheme.typography.headlineSmall)
        if (node.unit.subtitle.isNotBlank()) {
            Text(
                node.unit.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        ChispaProgressBar(progress = node.progress, height = 8.dp, color = color)
    }
}

/**
 * Nodo del camino. Se desplaza en zigzag para que el recorrido se lea como un
 * sendero y no como una lista de ajustes.
 */
@Composable
private fun LessonPathNode(
    node: LessonNode,
    index: Int,
    levelColor: Color,
    onClick: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val offsets = listOf(0, 46, 66, 46, 0, -46, -66, -46)
    val horizontalOffset = offsets[index % offsets.size].dp

    val locked = node.state == LessonState.LOCKED
    val isCurrent = node.state == LessonState.CURRENT

    val transition = rememberInfiniteTransition(label = "node")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCurrent) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )

    // Un único Brush en ambos casos: `background` no acepta un if/else que mezcle
    // Color y Brush, y así el nodo bloqueado comparte exactamente la misma forma.
    val nodeBrush = Brush.verticalGradient(
        if (locked) listOf(colors.lockedContainer, colors.lockedContainer)
        else listOf(levelColor, levelColor.copy(alpha = 0.78f))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(x = horizontalOffset)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(nodeBrush)
                    .border(
                        width = if (isCurrent) 3.dp else 0.dp,
                        color = if (isCurrent) colors.xp else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable(enabled = !locked, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = nodeIcon(node),
                    contentDescription = node.lesson.title,
                    tint = if (locked) colors.locked else Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                node.lesson.title,
                style = MaterialTheme.typography.labelSmall,
                color = if (locked) colors.locked else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(120.dp)
            )
            if (node.crown > 0) {
                CrownBadge(crown = node.crown)
            }
        }
    }
}

private fun nodeIcon(node: LessonNode): ImageVector = when {
    node.state == LessonState.LOCKED -> Icons.Filled.Lock
    node.state == LessonState.MASTERED -> Icons.Filled.Star
    node.lesson.kind == LessonKind.STORY -> Icons.Filled.AutoStories
    node.crown > 0 -> Icons.Filled.Check
    else -> Icons.Filled.PlayArrow
}

/* =========================================================================
 *  Módulos extra
 * ========================================================================= */

@Composable
private fun ExtraTrackCard(
    node: TrackNode,
    totalXp: Int,
    onOpenLesson: (String) -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val locked = !node.isUnlocked

    ChispaCard(borderColor = if (locked) colors.cardStroke else colors.levelExtra.copy(alpha = 0.5f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.levelExtra.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(trackEmoji(node.track.icon), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.track.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (locked) "Se abre con ${node.xpRequired} XP (llevas $totalXp)"
                        else node.track.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (locked) {
                    Icon(Icons.Filled.Lock, null, tint = colors.locked, modifier = Modifier.size(20.dp))
                }
            }

            if (!locked) {
                Spacer(Modifier.height(12.dp))
                ChispaProgressBar(
                    progress = node.progress,
                    height = 8.dp,
                    color = colors.levelExtra
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(node.units.flatMap { it.lessons }) { lessonNode ->
                        ExtraLessonChip(
                            node = lessonNode,
                            onClick = { onOpenLesson(lessonNode.lesson.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraLessonChip(node: LessonNode, onClick: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    val locked = node.state == LessonState.LOCKED
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (locked) colors.lockedContainer
                else colors.levelExtra.copy(alpha = 0.14f)
            )
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (locked) {
            Icon(Icons.Filled.Lock, null, tint = colors.locked, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        } else if (node.crown > 0) {
            Icon(Icons.Filled.Check, null, tint = colors.levelExtra, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            node.lesson.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (locked) colors.locked else colors.levelExtra,
            maxLines = 1
        )
    }
}

private fun trackEmoji(icon: String): String = when (icon) {
    "idiom" -> "🎣"
    "slang" -> "😎"
    "business" -> "💼"
    "travel" -> "🧳"
    "pronunciation" -> "🗣️"
    "listening" -> "🎧"
    "story" -> "📖"
    "culture" -> "🌍"
    else -> "✨"
}

@Composable
private fun levelColor(level: CefrLevel): Color {
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
