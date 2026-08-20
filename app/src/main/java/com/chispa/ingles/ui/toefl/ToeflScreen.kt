package com.chispa.ingles.ui.toefl

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
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.ToeflGuideSection
import com.chispa.ingles.data.content.ToeflModule
import com.chispa.ingles.domain.ToeflItp
import com.chispa.ingles.domain.ToeflSection
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

/**
 * Portada del módulo de certificación.
 *
 * Se abre al terminar B2, que es el nivel a partir del cual presentarse al
 * examen tiene sentido. Antes se enseña bloqueado con lo que falta: saber que
 * existe una meta al final del camino es parte de la motivación.
 */
@Composable
fun ToeflScreen(
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    onStartExam: (String) -> Unit
) {
    val viewModel: ToeflViewModel = chispaViewModel { ToeflViewModel(it) }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors

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
            Text("Certificación", style = MaterialTheme.typography.headlineSmall)
        }

        if (state.loading) return@Column

        if (state.guide.isEmpty) {
            EmptyState(
                title = "Material no disponible",
                message = "No se pudo cargar la guía de preparación.",
                mood = MascotMood.SAD,
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
            Text(state.guide.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                state.guide.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (!state.unlocked) {
                BloqueoCard(state.b2Progress, state.b2Remaining)
                Spacer(Modifier.height(16.dp))
            }

            // -------- Datos del examen --------
            ChispaCard {
                Column(Modifier.padding(16.dp)) {
                    state.guide.facts.forEachIndexed { i, f ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                f.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                f.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            state.guide.intro.forEach { parrafo ->
                Text(
                    parrafo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }

            // -------- Mejor puntaje --------
            state.bestScore?.let { mejor ->
                Spacer(Modifier.height(8.dp))
                ChispaCard(borderColor = MaterialTheme.colorScheme.primary) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Tu mejor simulacro",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$mejor",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val umbral = ToeflItp.umbralAlcanzado(mejor)
                        Text(
                            umbral?.para ?: "Sigue practicando para llegar a los 400.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            ToeflItp.AVISO_ESTIMACION,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.locked
                        )
                    }
                }
            }

            // -------- Material por secciones --------
            Spacer(Modifier.height(24.dp))
            Text("Material de apoyo", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.guide.modules.size} temas · unos ${state.guide.totalMinutes} minutos de lectura",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            ToeflGuideSection.entries.forEach { seccion ->
                val modulos = state.guide.modules.filter { it.section == seccion }
                if (modulos.isEmpty()) return@forEach

                Spacer(Modifier.height(8.dp))
                Text(
                    "${seccion.emoji}  ${seccion.label}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                modulos.forEach { m ->
                    ModuleRow(m) { onOpenModule(m.id) }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // -------- Simulacros --------
            Spacer(Modifier.height(24.dp))
            Text("Simulacros", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "${ToeflSection.TOTAL_QUESTIONS} preguntas y ${ToeflSection.TOTAL_MINUTES} " +
                    "minutos, igual que el examen real.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (state.exams.isEmpty()) {
                ChispaCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Todavía no hay simulacros", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "El material de apoyo ya está completo. Los simulacros llegan " +
                                "en la próxima actualización.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                state.exams.forEach { examen ->
                    ExamRow(
                        titulo = examen.titulo,
                        mejor = examen.mejorPuntaje,
                        habilitado = state.unlocked,
                        onClick = { onStartExam(examen.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BloqueoCard(progreso: Float, faltan: Int) {
    val colors = ChispaThemeTokens.colors
    ChispaCard(borderColor = colors.streak) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.streak)
                Spacer(Modifier.width(10.dp))
                Text("Los simulacros se abren al terminar B2", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (faltan > 0) {
                    "Te faltan $faltan lecciones de B2. Presentarse al examen antes de " +
                        "ese nivel casi siempre sale caro: el puntaje no alcanza y la cuota no se devuelve."
                } else {
                    "Ya puedes empezar."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            ChispaProgressBar(progress = progreso, color = colors.streak)
            Spacer(Modifier.height(8.dp))
            Text(
                "Mientras tanto, el material de apoyo está abierto. Léelo con calma.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModuleRow(module: ToeflModule, onClick: () -> Unit) {
    ChispaCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(module.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    module.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${module.minutes} min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExamRow(titulo: String, mejor: Int?, habilitado: Boolean, onClick: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    ChispaCard(onClick = if (habilitado) onClick else null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (habilitado) MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                        else colors.lockedContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!habilitado) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.locked)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.bodyLarge)
                Text(
                    mejor?.let { "Tu mejor puntaje: $it" } ?: "Sin intentar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (habilitado) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
