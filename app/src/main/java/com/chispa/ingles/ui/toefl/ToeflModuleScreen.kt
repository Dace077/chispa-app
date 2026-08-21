package com.chispa.ingles.ui.toefl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

/**
 * Un tema del material de apoyo.
 *
 * Tres bloques con papeles distintos: el texto explica, las **claves** son lo
 * que hay que llevarse sí o sí, y las **trampas** enseñan el error concreto al
 * lado de su corrección. Un alumno que solo tiene diez minutos lee las claves y
 * ya se ha llevado lo importante.
 */
@Composable
fun ToeflModuleScreen(moduleId: String, onBack: () -> Unit) {
    val viewModel: ToeflViewModel = chispaViewModel(key = "toefl") { ToeflViewModel(it) }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors
    val modulo = state.guide.find(moduleId)

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
        }

        if (state.loading) return@Column

        if (modulo == null) {
            EmptyState(
                title = "Tema no encontrado",
                message = "Este apartado ya no existe.",
                mood = MascotMood.THINKING,
                modifier = Modifier.fillMaxSize()
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "${modulo.section.emoji}  ${modulo.section.label} · ${modulo.minutes} min",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(modulo.title, style = MaterialTheme.typography.headlineMedium)
            if (modulo.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    modulo.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))
            modulo.body.forEach { parrafo ->
                Text(parrafo, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
            }

            // -------- Claves --------
            if (modulo.keys.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Lo que hay que llevarse", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                modulo.keys.forEach { clave ->
                    ChispaCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = .35f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                clave.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(clave.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            // -------- Ejemplos --------
            if (modulo.examples.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Ejemplos", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                modulo.examples.forEach { ej ->
                    ChispaCard {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                ej.en,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (ej.es.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    ej.es,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (ej.note.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    ej.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            // -------- Trampas --------
            if (modulo.traps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Dónde te van a hacer caer", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                modulo.traps.forEach { trampa ->
                    ChispaCard(borderColor = colors.wrong.copy(alpha = .4f)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Incorrecto",
                                    tint = colors.wrong,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(trampa.wrong, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Done,
                                    contentDescription = "Correcto",
                                    tint = colors.correct,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    trampa.right,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                trampa.why,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
