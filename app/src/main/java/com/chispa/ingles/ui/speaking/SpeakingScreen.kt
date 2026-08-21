package com.chispa.ingles.ui.speaking

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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.data.content.SpeakingCategory
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.prefs.Accent
import com.chispa.ingles.speech.SpeechRecognizerManager
import com.chispa.ingles.speech.TtsState
import com.chispa.ingles.ui.chispaViewModel
import com.chispa.ingles.ui.components.ChispaButton
import com.chispa.ingles.ui.components.ChispaCard
import com.chispa.ingles.ui.components.ChispaMascot
import com.chispa.ingles.ui.components.MascotMood
import com.chispa.ingles.ui.theme.ChispaThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sonidos que más cuestan a un hispanohablante. No es una lista genérica: son
 * exactamente los contrastes que el español no tiene y que el oído no distingue
 * hasta que alguien te los señala.
 */
private data class SoundDrill(
    val symbol: String,
    val title: String,
    val explanation: String,
    val examples: List<Pair<String, String>>
)

private val SOUND_DRILLS = listOf(
    SoundDrill(
        symbol = "/θ/",
        title = "La 'th' sorda",
        explanation = "Saca la punta de la lengua entre los dientes y sopla. No es una 's' ni una 't'. " +
            "En España se parece a la 'z' de 'zapato'; en Latinoamérica no existe, hay que fabricarla.",
        examples = listOf("think" to "pensar", "three" to "tres", "mouth" to "boca", "birthday" to "cumpleaños")
    ),
    SoundDrill(
        symbol = "/ð/",
        title = "La 'th' sonora",
        explanation = "Misma posición que la anterior, pero vibrando las cuerdas vocales. " +
            "Es la diferencia entre 'thin' y 'this'.",
        examples = listOf("this" to "esto", "mother" to "madre", "they" to "ellos", "weather" to "clima")
    ),
    SoundDrill(
        symbol = "/ɪ/ vs /iː/",
        title = "Vocal corta vs larga",
        explanation = "En español solo hay una 'i'. En inglés son dos sonidos distintos y cambian el " +
            "significado: 'ship' (barco) no es 'sheep' (oveja).",
        examples = listOf("ship / sheep" to "barco / oveja", "bit / beat" to "trozo / latido", "live / leave" to "vivir / irse")
    ),
    SoundDrill(
        symbol = "/æ/",
        title = "La 'a' abierta",
        explanation = "Entre la 'a' y la 'e' españolas, con la boca bien abierta. " +
            "Sin ella, 'cat' suena a 'cot'.",
        examples = listOf("cat" to "gato", "map" to "mapa", "bad" to "malo", "hand" to "mano")
    ),
    SoundDrill(
        symbol = "/ə/",
        title = "El schwa",
        explanation = "El sonido más común del inglés: una vocal relajada y débil en las sílabas sin acento. " +
            "Pronunciar todas las vocales con fuerza es lo que más marca el acento español.",
        examples = listOf("about" to "sobre", "problem" to "problema", "banana" to "plátano", "computer" to "computadora")
    ),
    SoundDrill(
        symbol = "sp- / st- / sk-",
        title = "Sin 'e' delante",
        explanation = "En español decimos 'España', 'estrés'. En inglés la palabra empieza directamente " +
            "por la 's'. Nada de 'esspain'.",
        examples = listOf("Spain" to "España", "student" to "estudiante", "school" to "escuela", "special" to "especial")
    ),
    SoundDrill(
        symbol = "/v/ vs /b/",
        title = "V labiodental",
        explanation = "En español 'b' y 'v' suenan igual. En inglés la 'v' se hace con los dientes " +
            "superiores tocando el labio inferior.",
        examples = listOf("very" to "muy", "vote / boat" to "votar / barco", "live" to "vivir")
    ),
    SoundDrill(
        symbol = "-ed",
        title = "Los tres finales de pasado",
        explanation = "'-ed' suena /t/, /d/ o /ɪd/ según la letra anterior. Nunca se pronuncia 'ed' entero " +
            "salvo tras 't' o 'd'.",
        examples = listOf("worked" to "trabajó (suena 'workt')", "played" to "jugó (suena 'playd')", "wanted" to "quiso (suena 'wantid')")
    )
)

data class SpeakingUiState(
    val recognitionAvailable: Boolean = true,
    val ttsState: TtsState = TtsState.INITIALIZING,
    val accent: Accent = Accent.US,
    val practicedCount: Int = 0,
    val hasMaterial: Boolean = false,
    val categorias: List<SpeakingCategory> = emptyList(),
    /** Categoria abierta. Solo una a la vez: 134 frases de golpe no se leen. */
    val abierta: String? = null
)

class SpeakingViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val recognizer = SpeechRecognizerManager(locator.appContext)

    private val _state = MutableStateFlow(SpeakingUiState())
    val state: StateFlow<SpeakingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = locator.settingsStore.current()
            _state.value = SpeakingUiState(
                recognitionAvailable = recognizer.isAvailable(),
                accent = settings.accent,
                practicedCount = settings.speakingExercises,
                hasMaterial = locator.progressRepository.dueCount() > 0 ||
                    locator.progressRepository.hardestCards(1).isNotEmpty(),
                categorias = locator.contentRepository.speakingPhrases()
            )
        }
        viewModelScope.launch {
            locator.tts.state.collect { ttsState ->
                _state.value = _state.value.copy(ttsState = ttsState)
            }
        }
    }

    fun speak(text: String) {
        locator.tts.speak(text, _state.value.accent)
    }

    /** Abre una categoria y cierra la que estuviera abierta. */
    fun alternar(id: String) {
        _state.value = _state.value.copy(
            abierta = if (_state.value.abierta == id) null else id
        )
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.stop()
    }
}

@Composable
fun SpeakingScreen(onStartSession: () -> Unit) {
    val viewModel: SpeakingViewModel = chispaViewModel { SpeakingViewModel(it) }
    val state by viewModel.state.collectAsState()
    val colors = ChispaThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(52.dp))
        Text("Habla en voz alta", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "El reconocimiento de voz es el del propio teléfono: no sale nada de aquí " +
                "y no cuesta nada.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(22.dp))

        ChispaCard {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChispaMascot(size = 72.dp, mood = MascotMood.HAPPY)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sesión de pronunciación", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "10 frases que ya conoces, para decirlas en voz alta y ver qué tal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ChispaButton(
                    text = "Empezar",
                    enabled = state.recognitionAvailable,
                    onClick = onStartSession
                )
                if (!state.recognitionAvailable) {
                    Spacer(Modifier.height(10.dp))
                    WarningRow(
                        "Tu dispositivo no trae reconocimiento de voz. " +
                            "Puedes instalar el servicio de voz de Google o practicar escuchando."
                    )
                }
                if (state.ttsState == TtsState.NO_VOICE || state.ttsState == TtsState.UNAVAILABLE) {
                    Spacer(Modifier.height(10.dp))
                    WarningRow(
                        "No encuentro una voz en inglés instalada. Ve a Ajustes de Android → " +
                            "Idiomas → Salida de texto a voz e instala el idioma inglés."
                    )
                }
                if (state.practicedCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Llevas ${state.practicedCount} frases practicadas en voz alta 🎤",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.correct
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        val totalFrases = state.categorias.sumOf { it.phrases.size }
        if (totalFrases > 0) {
            Text("Frases para decir en voz alta", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "$totalFrases frases de situaciones reales. Toca una para oírla y repítela.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            state.categorias.forEach { categoria ->
                CategoriaFrases(
                    categoria = categoria,
                    abierta = state.abierta == categoria.id,
                    onAlternar = { viewModel.alternar(categoria.id) },
                    onSpeak = viewModel::speak
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(28.dp))
        }

        Text("Sonidos que cuestan en español", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Toca cualquier ejemplo para escucharlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        SOUND_DRILLS.forEach { drill ->
            SoundDrillCard(drill = drill, onSpeak = viewModel::speak)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Una categoría de frases, plegable.
 *
 * Plegada de inicio y solo una abierta a la vez: 134 frases desplegadas de
 * golpe son un muro de texto que nadie recorre. Así se elige el momento
 * («voy al aeropuerto») y se practica solo eso.
 */
@Composable
private fun CategoriaFrases(
    categoria: SpeakingCategory,
    abierta: Boolean,
    onAlternar: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val colors = ChispaThemeTokens.colors
    ChispaCard {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAlternar)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(categoria.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(categoria.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${categoria.phrases.size} frases  ·  ${categoria.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (abierta) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (abierta) "Cerrar" else "Abrir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (abierta) {
                categoria.phrases.forEach { frase ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSpeak(frase.en) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(frase.en, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                frase.es,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (frase.note.isNotBlank()) {
                                Text(
                                    frase.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.xp
                                )
                            }
                        }
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = "Escuchar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SoundDrillCard(drill: SoundDrill, onSpeak: (String) -> Unit) {
    val colors = ChispaThemeTokens.colors
    ChispaCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        drill.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(drill.title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                drill.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            drill.examples.forEach { (english, spanish) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(english, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            spanish,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { onSpeak(english.substringBefore(" /")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = "Escuchar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningRow(message: String) {
    val colors = ChispaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.xp.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.Info, null, tint = colors.xp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = colors.xp)
    }
}
