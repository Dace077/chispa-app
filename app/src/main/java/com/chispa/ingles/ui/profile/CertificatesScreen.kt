package com.chispa.ingles.ui.profile

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chispa.ingles.certificates.CertificateSharing
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.domain.LevelCompletion
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaOutlinedButton
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

@Composable
fun CertificatesScreen(
    onBack: () -> Unit,
    onOpenStudentData: () -> Unit
) {
    val viewModel: CertificatesViewModel = chispaViewModel { CertificatesViewModel(it) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

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
            Text("Tus certificados", style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (state.loading) {
                Spacer(Modifier.height(40.dp))
                return@Column
            }

            Text(
                "Al terminar todas las lecciones de un nivel recibes una constancia " +
                    "en PDF con tu nombre, lista para guardar, imprimir o enviar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (!state.hasName) {
                FaltaNombre(onOpenStudentData)
                Spacer(Modifier.height(16.dp))
            }

            state.levels.forEach { nivel ->
                NivelCard(
                    completion = nivel,
                    hasName = state.hasName,
                    emitido = state.issued[nivel.level.label] != null,
                    onEmitir = { viewModel.generar(context, nivel.level) },
                    onCompartir = { viewModel.compartir(context, nivel.level) },
                    onPonerNombre = onOpenStudentData
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Esta constancia acredita que completaste el nivel dentro del curso de " +
                    "Chispa. No es una certificación oficial: para eso están exámenes como " +
                    "el TOEFL, que preparamos en su propio módulo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FaltaNombre(onOpenStudentData: () -> Unit) {
    ChispaCard(borderColor = ChispaThemeTokens.colors.streak) {
        Column(Modifier.padding(16.dp)) {
            Text("Te falta poner tu nombre", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Un certificado sin nombre no le sirve a nadie. Tarda diez segundos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            ChispaOutlinedButton(text = "Poner mi nombre", onClick = onOpenStudentData)
        }
    }
}

@Composable
private fun NivelCard(
    completion: LevelCompletion,
    hasName: Boolean,
    emitido: Boolean,
    onEmitir: () -> Unit,
    onCompartir: () -> Unit,
    onPonerNombre: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val listo = completion.isComplete

    ChispaCard(borderColor = if (listo) MaterialTheme.colorScheme.primary else null) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (listo) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else colors.lockedContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (listo) Icons.Filled.WorkspacePremium else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (listo) MaterialTheme.colorScheme.primary else colors.locked
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nivel ${completion.level.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            listo -> "Completado · ${completion.accuracy}% de precisión"
                            completion.completedLessons == 0 -> "${completion.totalLessons} lecciones"
                            else -> "Te faltan ${completion.remaining} de ${completion.totalLessons}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!listo) {
                Spacer(Modifier.height(12.dp))
                ChispaProgressBar(progress = completion.progress)
            } else {
                Spacer(Modifier.height(14.dp))
                if (!hasName) {
                    ChispaOutlinedButton(text = "Poner mi nombre", onClick = onPonerNombre)
                } else {
                    ChispaButton(
                        text = if (emitido) "Ver certificado" else "Generar certificado",
                        onClick = onEmitir
                    )
                    Spacer(Modifier.height(8.dp))
                    ChispaOutlinedButton(text = "Compartir", onClick = onCompartir)
                }
            }
        }
    }
}
