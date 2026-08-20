package com.chispa.ingles.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chispa.ingles.domain.Avatar
import com.chispa.ingles.ui.components.AvatarView
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

/**
 * La puerta de entrada: quién va a usar la app ahora.
 *
 * Sale cada vez que se abre y **no recuerda la última elección** a propósito.
 * El teléfono es de un adulto, pero quien lo agarra a veces es un niño de tres
 * años; si la app recordara «la última vez entró el papá», el niño se
 * encontraría el curso de adultos, y al revés el papá tendría que salir del
 * modo infantil cada mañana. Un toque de más al abrir es un precio pequeño por
 * no equivocarse nunca de persona.
 *
 * Las dos puertas se ven distintas a posta: la de adultos con la mascota y
 * texto normal; la del niño enorme, con animales y una sola palabra.
 */
@Composable
fun ModePickerScreen(
    nombre: String,
    onNormal: () -> Unit,
    onKids: () -> Unit
) {
    val colors = ChispaThemeTokens.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (nombre.isBlank()) "¿Quién va a practicar?" else "Hola, $nombre",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        // Sin nombre, el título ya hace esta pregunta: repetirla debajo sobra.
        if (nombre.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "¿Quién va a practicar hoy?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))

        // --- Adulto -----------------------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceElevated)
                .border(2.dp, colors.cardStroke, RoundedCornerShape(24.dp))
                .clickable(onClick = onNormal)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChispaMascot(size = 64.dp, mood = MascotMood.HAPPY)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Chispa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "El curso de inglés, de A1 a C2",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- Niño -------------------------------------------------------
        // Más grande y con dibujos: si el niño llega solo a esta pantalla,
        // tiene que poder distinguir su puerta sin leer una palabra.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(KIDS.copy(alpha = 0.12f))
                .border(3.dp, KIDS.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .clickable(onClick = onKids)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Avatar.MICHI, Avatar.TRUFA, Avatar.NUBE).forEach {
                    AvatarView(avatar = it, size = 62.dp, mood = MascotMood.HAPPY)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Chispa Kids",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = KIDS
            )
            Text(
                "Para niños de 2 a 5 años, sin leer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(KIDS)
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text(
                    "▶  Jugar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
    }
}

private val KIDS = Color(0xFF7C5CE6)
