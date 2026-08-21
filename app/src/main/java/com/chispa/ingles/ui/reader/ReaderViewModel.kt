package com.chispa.ingles.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.Reading
import com.chispa.ingles.data.content.ReadingLibrary
import com.chispa.ingles.data.prefs.Accent
import com.chispa.ingles.domain.DialoguePitch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* =========================================================================
 *  Índice de la biblioteca
 * ========================================================================= */

data class LibraryUiState(
    val loading: Boolean = true,
    val porNivel: Map<CefrLevel, List<Reading>> = emptyMap(),
    /** Ids ya terminados, para no releer sin querer lo mismo. */
    val leidas: Set<String> = emptySet(),
    val vacia: Boolean = false
)

class LibraryViewModel(locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val biblioteca: ReadingLibrary = locator.contentRepository.library()
            _state.value = _state.value.copy(
                loading = false,
                porNivel = biblioteca.byLevel().toSortedMap(compareBy { it.order }),
                vacia = biblioteca.isEmpty
            )
        }
        viewModelScope.launch {
            locator.settingsStore.settings.collect { ajustes ->
                _state.value = _state.value.copy(leidas = ajustes.readingsRead)
            }
        }
    }
}

/* =========================================================================
 *  El lector
 * ========================================================================= */

data class ReaderUiState(
    val loading: Boolean = true,
    val reading: Reading? = null,
    /** Frase que se está leyendo en voz alta, o null si no suena nada. */
    val hablando: Int? = null,
    /** Trozo de esa frase que suena ahora, en índices de carácter. */
    val palabra: IntRange? = null,
    /** Frases cuya traducción está desplegada. */
    val traducidas: Set<Int> = emptySet(),
    val reproduciendo: Boolean = false,
    val velocidad: Float = 0.9f,
    val accent: Accent = Accent.US,
    val mostrarTodasLasTraducciones: Boolean = false,
    /** Palabra tocada, para la tarjeta de significado. */
    val palabraTocada: PalabraSeleccionada? = null,
    val guardadas: Set<String> = emptySet(),
    val sincronizaPalabras: Boolean = false
)

data class PalabraSeleccionada(
    val texto: String,
    val traduccion: String?,
    val yaGuardada: Boolean
)

class ReaderViewModel(
    private val locator: ServiceLocator,
    private val readingId: String
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val tts = locator.tts

    /** Diccionario para resolver el significado de una palabra al tocarla. */
    private var indiceVocabulario: Map<String, String> = emptyMap()

    /**
     * Un tono por personaje. En las lecturas con diálogo —pedir en un café,
     * hablar con el casero— oír dos voces distintas es la mitad de la
     * comprensión; con una sola hay que reconstruir quién habla leyendo.
     */
    private var tonos: Map<String, Float> = emptyMap()

    init {
        viewModelScope.launch {
            val ajustes = locator.settingsStore.current()
            val lectura = locator.contentRepository.library().find(readingId)
            val curriculo = locator.contentRepository.curriculum()

            // El glosario de la lectura manda; si una palabra no está, se busca
            // en todo el vocabulario del curso antes de rendirse.
            indiceVocabulario = buildMap {
                curriculo.vocabIndex.forEach { (clave, item) -> put(clave, item.es) }
                lectura?.glossary?.forEach { put(it.srsKey, it.es) }
            }

            tonos = DialoguePitch.forSpeakers(
                lectura?.sentences.orEmpty().map { it.speaker }
            )

            _state.value = _state.value.copy(
                loading = false,
                reading = lectura,
                velocidad = ajustes.speechRate,
                accent = ajustes.accent
            )
        }

        // Seguimiento de la palabra que suena
        viewModelScope.launch {
            tts.spokenRange.collect { rango ->
                if (rango == null) {
                    _state.value = _state.value.copy(palabra = null)
                    return@collect
                }
                val indice = rango.utteranceId.removePrefix(PREFIJO).toIntOrNull() ?: return@collect
                if (indice == _state.value.hablando) {
                    _state.value = _state.value.copy(palabra = rango.start until rango.end)
                }
            }
        }

        viewModelScope.launch {
            tts.supportsWordSync.collect { soporta ->
                _state.value = _state.value.copy(sincronizaPalabras = soporta)
            }
        }

        // Encadenado de frases: al terminar una, va la siguiente.
        viewModelScope.launch {
            tts.finishedUtterance.collect { id ->
                if (id == null || !id.startsWith(PREFIJO)) return@collect
                tts.consumeFinished()
                val terminada = id.removePrefix(PREFIJO).toIntOrNull() ?: return@collect
                val estado = _state.value
                if (!estado.reproduciendo || estado.hablando != terminada) return@collect

                val siguiente = terminada + 1
                val total = estado.reading?.sentences?.size ?: 0
                if (siguiente < total) {
                    hablarFrase(siguiente)
                } else {
                    _state.value = estado.copy(reproduciendo = false, hablando = null, palabra = null)
                }
            }
        }
    }

    /**
     * La da por leída. Lo llama la pantalla cuando la última frase entra en
     * pantalla: abrirla y salir a los dos segundos no cuenta como haberla leído.
     */
    fun marcarLeida() {
        if (leidaYa) return
        leidaYa = true
        viewModelScope.launch { locator.settingsStore.markReadingRead(readingId) }
    }

    private var leidaYa = false

    /* ------------------------------------------------------------------ */
    /*  Reproducción                                                       */
    /* ------------------------------------------------------------------ */

    private fun hablarFrase(indice: Int) {
        val frase = _state.value.reading?.sentences?.getOrNull(indice) ?: return
        _state.value = _state.value.copy(hablando = indice, palabra = null)
        tts.speakTracked(
            utteranceId = "$PREFIJO$indice",
            text = frase.en,
            accent = _state.value.accent,
            rate = _state.value.velocidad,
            pitch = tonos[frase.speaker.trim()] ?: DialoguePitch.NEUTRO
        )
    }

    /** Play/pausa desde donde estuviera. */
    fun alternarReproduccion() {
        val estado = _state.value
        if (estado.reproduciendo) {
            tts.stop()
            _state.value = estado.copy(reproduciendo = false, palabra = null)
        } else {
            val desde = estado.hablando ?: 0
            _state.value = estado.copy(reproduciendo = true)
            hablarFrase(desde)
        }
    }

    /** Lee una frase suelta sin encadenar con las siguientes. */
    fun leerFrase(indice: Int) {
        tts.stop()
        _state.value = _state.value.copy(reproduciendo = false)
        hablarFrase(indice)
    }

    fun repetirFraseActual() {
        _state.value.hablando?.let { hablarFrase(it) }
    }

    fun frasePrevia() {
        val actual = _state.value.hablando ?: 0
        if (actual > 0) hablarFrase(actual - 1)
    }

    fun fraseSiguiente() {
        val actual = _state.value.hablando ?: -1
        val total = _state.value.reading?.sentences?.size ?: 0
        if (actual + 1 < total) hablarFrase(actual + 1)
    }

    fun cambiarVelocidad(valor: Float) {
        _state.value = _state.value.copy(velocidad = valor.coerceIn(0.4f, 1.3f))
        viewModelScope.launch { locator.settingsStore.setSpeechRate(valor) }
    }

    /* ------------------------------------------------------------------ */
    /*  Traducción y vocabulario                                           */
    /* ------------------------------------------------------------------ */

    fun alternarTraduccion(indice: Int) {
        val actuales = _state.value.traducidas
        _state.value = _state.value.copy(
            traducidas = if (indice in actuales) actuales - indice else actuales + indice
        )
    }

    fun alternarTodasLasTraducciones() {
        val nuevo = !_state.value.mostrarTodasLasTraducciones
        _state.value = _state.value.copy(
            mostrarTodasLasTraducciones = nuevo,
            traducidas = if (nuevo) emptySet() else _state.value.traducidas
        )
    }

    /** Al tocar una palabra suelta: la lee y busca su significado. */
    fun tocarPalabra(bruta: String) {
        val limpia = bruta.trim().trim('.', ',', '!', '?', ';', ':', '"', '“', '”', '—', '(', ')')
        if (limpia.isEmpty()) return
        val clave = limpia.lowercase()

        tts.speak(limpia, _state.value.accent, _state.value.velocidad)
        _state.value = _state.value.copy(
            palabraTocada = PalabraSeleccionada(
                texto = limpia,
                traduccion = indiceVocabulario[clave],
                yaGuardada = clave in _state.value.guardadas
            )
        )
    }

    fun cerrarPalabra() {
        _state.value = _state.value.copy(palabraTocada = null)
    }

    /** Manda la palabra al repaso espaciado, que es donde de verdad se aprende. */
    fun guardarPalabra() {
        val seleccion = _state.value.palabraTocada ?: return
        val clave = seleccion.texto.lowercase()
        viewModelScope.launch {
            locator.progressRepository.recordAnswer(
                cardKey = clave,
                correct = false,          // recién descubierta: que vuelva pronto
                en = seleccion.texto,
                es = seleccion.traduccion ?: "",
                lesson = null
            )
            _state.value = _state.value.copy(
                guardadas = _state.value.guardadas + clave,
                palabraTocada = seleccion.copy(yaGuardada = true)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts.stop()
    }

    companion object {
        private const val PREFIJO = "read_"
    }
}
