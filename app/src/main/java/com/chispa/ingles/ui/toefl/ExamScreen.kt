package com.chispa.ingles.ui.toefl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chispa.ingles.data.content.ExamPartKind
import com.chispa.ingles.data.content.ExamQuestion
import com.chispa.ingles.domain.ToeflItp
import com.chispa.ingles.domain.ToeflSection
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaOutlinedButton
import com.chispa.ingles.ui.components.ChispaProgressBar
import com.chispa.ingles.ui.components.EmptyState
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens

/**
 * Un simulacro completo, con las reglas del examen real.
 *
 * Lo importante aquí no es la corrección —eso es trivial— sino reproducir la
 * presión: reloj por sección, sin volver atrás y audio que suena una vez. Un
 * simulacro sin esas tres cosas mide otra habilidad distinta de la que se
 * examina el día del examen.
 */
@Composable
fun ExamScreen(
    examId: String,
    onExit: () -> Unit
) {
    val viewModel: ExamViewModel = chispaViewModel(key = examId) { ExamViewModel(it, examId) }
    val state by viewModel.state.collectAsState()
    val contexto = LocalContext.current
    var confirmarSalida by remember { mutableStateOf(false) }

    when (state.phase) {
        ExamPhase.LOADING -> Box(Modifier.fillMaxSize())

        ExamPhase.MISSING -> EmptyState(
            title = "Simulacro no disponible",
            message = "Este examen todavía no está listo o le faltan preguntas. " +
                "Un examen incompleto daría un puntaje falso, así que preferimos no servirlo.",
            mood = MascotMood.THINKING,
            modifier = Modifier.fillMaxSize(),
            action = { ChispaButton(text = "Volver", onClick = onExit) }
        )

        ExamPhase.INTRO -> ExamIntro(
            titulo = state.exam?.title.orEmpty(),
            onStart = viewModel::begin,
            onReviewPrevious = if (state.revisionPrevia) viewModel::openPreviousReview else null,
            onExit = onExit
        )

        ExamPhase.SECTION_INTRO -> SectionIntro(
            section = state.section ?: ToeflSection.LISTENING,
            onStart = viewModel::startSection
        )

        ExamPhase.SECTION_DONE -> SectionDone(
            section = state.section ?: ToeflSection.LISTENING,
            blancos = state.blanksInSection,
            onContinue = viewModel::nextSection
        )

        ExamPhase.FINISHED -> ExamResult(
            result = state.result,
            onReview = viewModel::review,
            onExport = { viewModel.exportarInforme(contexto) },
            onExit = onExit
        )

        ExamPhase.REVIEW -> ExamReview(
            items = viewModel.revision(),
            filtro = state.reviewFilter,
            onFiltro = viewModel::setReviewFilter,
            onBack = viewModel::backToResult
        )

        ExamPhase.RESUME_ASK -> ResumeAsk(
            onResume = viewModel::resume,
            onRestart = viewModel::discardAndRestart
        )

        ExamPhase.ANSWERING -> AnsweringPane(
            state = state,
            viewModel = viewModel,
            onRequestExit = { confirmarSalida = true }
        )
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text("¿Salir del simulacro?") },
            text = {
                Text(
                    "Se guarda donde vas y podrás seguir después. Eso sí, el " +
                        "reloj de la sección se queda como está: en el examen " +
                        "real no hay pausa."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.abandon()
                    onExit()
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalida = false }) { Text("Seguir") }
            }
        )
    }
}

/* ------------------------------- Portadas ------------------------------- */

/**
 * Se encontró un examen a medias.
 *
 * Se pregunta en vez de retomarlo solo porque el reloj sigue donde estaba: si
 * lo dejaste con dos minutos en Reading, retomar significa dos minutos. Quien
 * prefiera empezar limpio debe poder decirlo.
 */
@Composable
private fun ResumeAsk(onResume: () -> Unit, onRestart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Tienes un simulacro a medias",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Se guardó todo lo que llevabas contestado y el tiempo que te " +
                "quedaba en la sección. Puedes seguir donde lo dejaste.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        ChispaButton(text = "Seguir donde lo dejé", onClick = onResume)
        Spacer(Modifier.height(10.dp))
        ChispaOutlinedButton(text = "Empezar de nuevo", onClick = onRestart)
    }
}

@Composable
private fun ExamIntro(
    titulo: String,
    onStart: () -> Unit,
    onReviewPrevious: (() -> Unit)?,
    onExit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text(titulo, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Antes de empezar, léelo:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        listOf(
            "Son ${ToeflSection.TOTAL_QUESTIONS} preguntas y ${ToeflSection.TOTAL_MINUTES} minutos, sin pausas.",
            "Cada sección tiene su propio reloj. Cuando se acaba, se cierra sola.",
            "No se puede volver a una sección terminada.",
            "Los audios de Listening suenan una sola vez.",
            "Fallar no resta. Nunca dejes una pregunta en blanco."
        ).forEach { linea ->
            Row(Modifier.padding(bottom = 10.dp)) {
                Text("•  ", style = MaterialTheme.typography.bodyLarge)
                Text(linea, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(12.dp))
        // Nombre a la izquierda y cifras a la derecha se lee muy bien... hasta
        // que la letra crece. Con la fuente del sistema muy grande no caben las
        // dos columnas y Compose parte «Comprensión» por la mitad, así que a
        // partir de ahí se apilan.
        val apilado = LocalDensity.current.fontScale > 1.3f
        ChispaCard {
            Column(Modifier.padding(16.dp)) {
                ToeflSection.ORDER.forEach { s ->
                    if (apilado) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(s.subtitle, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${s.questions} preg · ${s.minutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                s.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${s.questions} preg · ${s.minutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        ChispaButton(text = "Empezar el simulacro", onClick = onStart)
        if (onReviewPrevious != null) {
            Spacer(Modifier.height(8.dp))
            ChispaOutlinedButton(
                text = "Ver mis respuestas del último intento",
                onClick = onReviewPrevious
            )
        }
        Spacer(Modifier.height(8.dp))
        ChispaOutlinedButton(text = "Ahora no", onClick = onExit)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionIntro(section: ToeflSection, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Sección ${ToeflSection.ORDER.indexOf(section) + 1} de 3",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            section.label,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            section.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "${section.questions} preguntas · ${section.minutes} minutos",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "El reloj arranca al pulsar. No se detiene.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        ChispaButton(text = "Empezar sección", onClick = onStart)
    }
}

@Composable
private fun SectionDone(section: ToeflSection, blancos: Int, onContinue: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sección terminada", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            section.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        if (blancos > 0) {
            Text(
                "Dejaste $blancos en blanco. En el examen real eso son $blancos ceros: " +
                    "fallar no resta, así que la próxima vez marca algo aunque dudes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.wrong,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Contestaste todas. Bien hecho.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.correct
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "No puedes volver a esta sección.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        ChispaButton(text = "Siguiente sección", onClick = onContinue)
    }
}

/* ------------------------------ Respondiendo ---------------------------- */

@Composable
private fun AnsweringPane(
    state: ExamUiState,
    viewModel: ExamViewModel,
    onRequestExit: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val pregunta = state.current ?: return
    val parte = state.part

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // -------- Cabecera: reloj y progreso --------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 44.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRequestExit) {
                Icon(Icons.Filled.Close, contentDescription = "Salir")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.questionIndex + 1} / ${state.questions.size}",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                ChispaProgressBar(
                    progress = (state.questionIndex + 1).toFloat() / state.questions.size,
                    height = 6.dp
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = if (state.tiempoBajo) colors.wrong else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    // Locale explícito: con el del sistema, en árabe o hindi el
                    // reloj saldría con otros dígitos y el minutero deja de leerse.
                    String.format(java.util.Locale.US, "%d:%02d", state.minutes, state.seconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.tiempoBajo) colors.wrong else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            parte?.let { p ->
                if (p.instructions.isNotBlank()) {
                    Text(
                        p.instructions,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (p.passage.isNotBlank()) {
                    ChispaCard {
                        Text(
                            p.passage,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (pregunta.kind.isAudio) {
                AudioBlock(
                    yaSono = pregunta.id in state.played,
                    onPlay = { viewModel.play(pregunta) }
                )
                Spacer(Modifier.height(14.dp))
            }

            if (pregunta.stem.isNotBlank()) {
                Text(pregunta.stem, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
            }

            val elegida = state.answers[pregunta.id]
            pregunta.options.forEachIndexed { i, opcion ->
                OptionRow(
                    texto = opcion,
                    letra = ('A' + i).toString(),
                    seleccionada = elegida == i,
                    esErrorId = pregunta.isErrorId,
                    onClick = { viewModel.answer(pregunta.id, i) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))
        }

        // -------- Navegación --------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChispaOutlinedButton(
                text = "Anterior",
                enabled = state.questionIndex > 0,
                onClick = viewModel::previous,
                modifier = Modifier.weight(1f)
            )
            if (state.questionIndex == state.questions.size - 1) {
                ChispaButton(
                    text = "Terminar sección",
                    onClick = viewModel::closeSection,
                    modifier = Modifier.weight(1f)
                )
            } else {
                ChispaButton(
                    text = "Siguiente",
                    onClick = viewModel::next,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AudioBlock(yaSono: Boolean, onPlay: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    ChispaCard(borderColor = if (yaSono) colors.cardStroke else MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (yaSono) colors.locked else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (yaSono) "Ya reproducido" else "Toca para escuchar",
                    style = MaterialTheme.typography.bodyMedium
                )
                // El recordatorio de que suena una vez es un apunte: con la letra
                // muy grande roba sitio al botón, y el botón es lo que se usa.
                if (LocalDensity.current.fontScale <= 1.3f) {
                    Text(
                        "Como en el examen real: suena una sola vez.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!yaSono) {
                TextButton(onClick = onPlay) { Text("Reproducir", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun OptionRow(
    texto: String,
    letra: String,
    seleccionada: Boolean,
    esErrorId: Boolean,
    onClick: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val borde = if (seleccionada) MaterialTheme.colorScheme.primary else colors.cardStroke

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (seleccionada) MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                else colors.surfaceElevated
            )
            .border(2.dp, borde, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sin ancho fijo: 24.dp valen para la letra normal, pero el texto crece
        // con la fuente del sistema y los dp no, así que a tamaño grande la
        // «A» se cortaba por la mitad.
        Text(
            letra,
            style = MaterialTheme.typography.titleSmall,
            color = if (seleccionada) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 24.dp).padding(end = 8.dp)
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (esErrorId) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/* -------------------------------- Resultado ----------------------------- */

@Composable
private fun ExamResult(
    result: com.chispa.ingles.domain.ToeflResult?,
    onReview: () -> Unit,
    onExport: () -> Unit,
    onExit: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    if (result == null) return

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Puntaje estimado", style = MaterialTheme.typography.titleMedium)
        Text(
            "${result.total}",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "de ${ToeflItp.MIN_TOTAL} a ${ToeflItp.MAX_TOTAL}  ·  nivel ${ToeflItp.nivelAproximado(result.total)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        ChispaCard {
            Column(Modifier.padding(16.dp)) {
                SeccionResultado("Listening", result.listeningRaw, ToeflSection.LISTENING.questions, result.listeningScaled)
                Spacer(Modifier.height(10.dp))
                SeccionResultado("Structure", result.structureRaw, ToeflSection.STRUCTURE.questions, result.structureScaled)
                Spacer(Modifier.height(10.dp))
                SeccionResultado("Reading", result.readingRaw, ToeflSection.READING.questions, result.readingScaled)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            ToeflItp.resumen(result),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
        Text(
            ToeflItp.AVISO_ESTIMACION,
            style = MaterialTheme.typography.labelSmall,
            color = colors.locked,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))
        // La revisión va antes que "Terminar" a propósito: el puntaje no enseña
        // nada, y quien sale de aquí sin mirar sus fallos ha perdido el examen.
        ChispaButton(text = "Revisar mis respuestas", onClick = onReview)
        Spacer(Modifier.height(10.dp))
        ChispaOutlinedButton(text = "Guardar el informe en PDF", onClick = onExport)
        Spacer(Modifier.height(10.dp))
        ChispaOutlinedButton(text = "Terminar", onClick = onExit)
        Spacer(Modifier.height(32.dp))
    }
}

/* -------------------------------- Revisión ------------------------------ */

/**
 * Las 140 preguntas con la respuesta buena y por qué lo es.
 *
 * Aquí sí se enseña el guion del Listening y el texto del Reading: el examen ya
 * terminó y esconderlos solo impediría entender el fallo. Arranca filtrado por
 * las falladas, que es lo que casi nadie repasaría si tuviera que buscarlas
 * entre las 140.
 */
@Composable
private fun ExamReview(
    items: List<ReviewItem>,
    filtro: ReviewFilter,
    onFiltro: (ReviewFilter) -> Unit,
    onBack: () -> Unit
) {
    val colors = ChispaThemeTokens.colors
    val falladas = items.count { !it.correcta && !it.enBlanco }
    val blancos = items.count { it.enBlanco }

    val visibles = when (filtro) {
        ReviewFilter.FALLADAS -> items.filter { !it.correcta && !it.enBlanco }
        ReviewFilter.BLANCO -> items.filter { it.enBlanco }
        ReviewFilter.TODAS -> items
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 40.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Volver al puntaje")
            }
            Text("Revisión", style = MaterialTheme.typography.titleLarge)
        }

        Row(
            Modifier
                .fillMaxWidth()
                // Tres chips no caben en una fila con la letra grande: se
                // desplazan en vez de aplastarse unos a otros.
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FiltroChip("Falladas ($falladas)", filtro == ReviewFilter.FALLADAS) {
                onFiltro(ReviewFilter.FALLADAS)
            }
            FiltroChip("En blanco ($blancos)", filtro == ReviewFilter.BLANCO) {
                onFiltro(ReviewFilter.BLANCO)
            }
            FiltroChip("Todas (${items.size})", filtro == ReviewFilter.TODAS) {
                onFiltro(ReviewFilter.TODAS)
            }
        }

        if (visibles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when (filtro) {
                        ReviewFilter.FALLADAS -> "Ninguna fallada. Enhorabuena."
                        ReviewFilter.BLANCO -> "No dejaste ninguna en blanco."
                        ReviewFilter.TODAS -> "No hay preguntas que mostrar."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Column
        }

        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp
            )
        ) {
            items(visibles.size) { i ->
                RevisionCard(visibles[i], colors)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FiltroChip(texto: String, activo: Boolean, onClick: () -> Unit) {
    val colors = ChispaThemeTokens.colors
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = if (activo) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (activo) MaterialTheme.colorScheme.primary else colors.surfaceElevated
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun RevisionCard(item: ReviewItem, colors: com.chispa.ingles.ui.theme.ChispaColors) {
    var abierto by remember { mutableStateOf(false) }
    val pregunta = item.question
    val tieneApoyo = item.script.isNotEmpty() || item.passage.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${item.section.shortLabel} ${item.numero}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    item.enBlanco -> "En blanco"
                    item.correcta -> "Acertada"
                    else -> "Fallada"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    item.enBlanco -> colors.locked
                    item.correcta -> colors.correct
                    else -> colors.wrong
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(pregunta.stem, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))

        // Lo que marcó, solo si marcó algo y no era la buena.
        if (item.given != null && !item.correcta) {
            RespuestaLinea(
                etiqueta = "Marcaste",
                texto = pregunta.options.getOrElse(item.given) { "" },
                letra = LETRAS.getOrElse(item.given) { "" },
                color = colors.wrong
            )
            Spacer(Modifier.height(6.dp))
        }
        RespuestaLinea(
            etiqueta = if (pregunta.isErrorId) "El error está en" else "Respuesta correcta",
            texto = pregunta.correctOption,
            letra = LETRAS.getOrElse(pregunta.answer) { "" },
            color = colors.correct
        )

        if (pregunta.explanation.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                pregunta.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (tieneApoyo) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { abierto = !abierto }) {
                Text(
                    if (abierto) "Ocultar"
                    else if (item.script.isNotEmpty()) "Ver lo que se dijo"
                    else "Ver el texto"
                )
            }
            if (abierto) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                ) {
                    item.script.forEach { linea ->
                        Text(
                            "${linea.speaker}: ${linea.text}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (item.passage.isNotBlank()) {
                        Text(item.passage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RespuestaLinea(etiqueta: String, texto: String, letra: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("$letra.  $texto", style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private val LETRAS = listOf("A", "B", "C", "D")

@Composable
private fun SeccionResultado(nombre: String, aciertos: Int, total: Int, puntos: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(nombre, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$aciertos de $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "$puntos",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
