package com.chispa.ingles.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chispa.ingles.ui.theme.ChispaThemeTokens

/* =========================================================================
 *  Botones
 * ========================================================================= */

/**
 * Botón principal con "profundidad": una sombra sólida debajo que desaparece al
 * pulsar, imitando una tecla física. Es lo que hace que la app se sienta un
 * juego y no un formulario.
 */
@Composable
fun ChispaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    icon: ImageVector? = null,
    height: Dp = 56.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 4.dp
    val offset by animateFloatAsState(
        targetValue = if (pressed || !enabled) 0f else 1f,
        animationSpec = spring(stiffness = 900f),
        label = "buttonDepth"
    )

    val effectiveContainer = if (enabled) container else ChispaThemeTokens.colors.lockedContainer
    val effectiveContent = if (enabled) contentColor else ChispaThemeTokens.colors.locked

    Box(
        modifier = modifier
            .height(height + depth)
            .fillMaxWidth()
    ) {
        // Capa de "sombra" sólida
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(18.dp))
                .background(darken(effectiveContainer))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .align(Alignment.TopCenter)
                .padding(top = depth * (1f - offset))
                .clip(RoundedCornerShape(18.dp))
                .background(effectiveContainer)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = effectiveContent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = effectiveContent,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ChispaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, ChispaThemeTokens.colors.cardStroke)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) contentColor else ChispaThemeTokens.colors.locked
            )
        }
    }
}

private fun darken(color: Color, factor: Float = 0.72f): Color =
    Color(
        red = color.red * factor,
        green = color.green * factor,
        blue = color.blue * factor,
        alpha = color.alpha
    )

/* =========================================================================
 *  Indicadores de estado
 * ========================================================================= */

@Composable
fun StatPill(
    icon: ImageVector,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

@Composable
fun HeartsRow(
    hearts: Int,
    max: Int = 5,
    modifier: Modifier = Modifier
) {
    val colors = ChispaThemeTokens.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { index ->
            val filled = index < hearts
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.82f,
                animationSpec = spring(),
                label = "heart$index"
            )
            Icon(
                imageVector = if (filled) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (filled) colors.heart else colors.locked,
                modifier = Modifier.size(20.dp).scale(scale).padding(horizontal = 1.dp)
            )
        }
    }
}

/** Barra de progreso redondeada con animación suave. */
@Composable
fun ChispaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = ChispaThemeTokens.colors.lockedContainer
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(420),
        label = "progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(listOf(color, lighten(color)))
                )
        )
        // Brillo superior: detalle barato que hace que la barra parezca de vidrio.
        if (animated > 0.04f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(height / 3)
                    .padding(horizontal = height / 3)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            )
        }
    }
}

private fun lighten(color: Color, amount: Float = 0.22f): Color =
    Color(
        red = color.red + (1f - color.red) * amount,
        green = color.green + (1f - color.green) * amount,
        blue = color.blue + (1f - color.blue) * amount,
        alpha = color.alpha
    )

@Composable
fun CrownBadge(
    crown: Int,
    max: Int = 5,
    modifier: Modifier = Modifier
) {
    val colors = ChispaThemeTokens.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = "Nivel de dominio",
            tint = if (crown > 0) colors.xp else colors.locked,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$crown/$max",
            style = MaterialTheme.typography.labelSmall,
            color = if (crown > 0) colors.xp else colors.locked
        )
    }
}

@Composable
fun StreakFlame(streak: Int, modifier: Modifier = Modifier) {
    StatPill(
        icon = Icons.Filled.LocalFireDepartment,
        value = streak.toString(),
        tint = ChispaThemeTokens.colors.streak,
        modifier = modifier,
        contentDescription = "Racha de $streak días"
    )
}

/* =========================================================================
 *  Contenedores
 * ========================================================================= */

@Composable
fun ChispaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    background: Color = ChispaThemeTokens.colors.surfaceElevated,
    content: @Composable () -> Unit
) {
    val stroke = borderColor ?: ChispaThemeTokens.colors.cardStroke
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(background)
        .border(2.dp, stroke, RoundedCornerShape(20.dp))

    Box(
        modifier = modifier
            .then(base)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun LockedOverlayIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Icon(
        Icons.Filled.Lock,
        contentDescription = "Bloqueado",
        tint = ChispaThemeTokens.colors.locked,
        modifier = modifier.size(size)
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.THINKING,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ChispaMascot(size = 130.dp, mood = mood)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/** Etiqueta pequeña de nivel (A1, B2, Extra…). */
@Composable
fun LevelChip(label: String, color: Color, modifier: Modifier = Modifier) {
    val animatedColor by animateColorAsState(color, label = "levelChip")
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(animatedColor.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = animatedColor)
    }
}

@Composable
fun DimmedIf(condition: Boolean, content: @Composable () -> Unit) {
    Box(modifier = if (condition) Modifier.alpha(0.45f) else Modifier) { content() }
}
