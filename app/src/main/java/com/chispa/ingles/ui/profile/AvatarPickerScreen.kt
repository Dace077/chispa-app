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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chispa.ingles.domain.Avatar
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.AvatarView
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

/**
 * Elección de avatar.
 *
 * Los bloqueados se enseñan igual, en gris y con el nivel que hace falta: saber
 * lo que viene es la mitad de la gracia. Ocultarlos hasta ganarlos no motiva a
 * nadie, porque nadie echa de menos lo que no sabe que existe.
 */
@Composable
fun AvatarPickerScreen(onBack: () -> Unit) {
    val viewModel: AvatarPickerViewModel = chispaViewModel { AvatarPickerViewModel(it) }
    val state by viewModel.state.collectAsState()

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
            Text("Tu avatar", style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // El elegido, en grande y animado.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarView(
                        avatar = state.selected,
                        size = 150.dp,
                        mood = MascotMood.HAPPY
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.selected.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        state.selected.species,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.selected.blurb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Se desbloquea uno por cada nivel que termines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Avatar.entries.chunked(3).forEach { fila ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    fila.forEach { avatar ->
                        AvatarCell(
                            avatar = avatar,
                            unlocked = avatar in state.unlocked,
                            selected = avatar == state.selected,
                            onClick = { viewModel.elegir(avatar) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AvatarCell(
    avatar: Avatar,
    unlocked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ChispaThemeTokens.colors

    ChispaCard(
        modifier = modifier,
        borderColor = if (selected) MaterialTheme.colorScheme.primary else null,
        onClick = if (unlocked) onClick else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Los bloqueados se ven, pero apagados: saber lo que viene motiva.
                AvatarView(
                    avatar = avatar,
                    size = 62.dp,
                    animate = unlocked && selected,
                    modifier = Modifier.alpha(if (unlocked) 1f else 0.25f)
                )
                if (!unlocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colors.locked
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Elegido",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (unlocked) avatar.displayName else "?",
                style = MaterialTheme.typography.labelLarge,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else colors.locked
            )
            Text(
                if (unlocked) avatar.species else "Termina ${avatar.unlockLevel?.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
