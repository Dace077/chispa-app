package com.chispa.ingles.ui.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.prefs.Accent
import com.chispa.ingles.domain.KidsItem
import com.chispa.ingles.domain.KidsMode
import com.chispa.ingles.domain.KidsRonda
import com.chispa.ingles.domain.KidsRules
import com.chispa.ingles.domain.KidsWorld
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class KidsStage { CARGANDO, VACIO, MUNDOS, EXPLORAR, JUGAR, CELEBRAR }

data class KidsUiState(
    val stage: KidsStage = KidsStage.CARGANDO,
    val worlds: List<KidsWorld> = emptyList(),
    val world: KidsWorld? = null,
    val mode: KidsMode = KidsMode.EXPLORAR,
    val ronda: KidsRonda? = null,
    val numeroRonda: Int = 0,
    val aciertos: Int = 0,
    /** Id del dibujo que acaba de acertarse, para celebrarlo. */
    val acertado: String? = null,
    /** Id del dibujo tocado por error: se sacude y vuelve a sonar. */
    val fallado: String? = null,
    /** Id que está sonando ahora, para animarlo mientras habla. */
    val sonando: String? = null
)

/**
 * El motor de Chispa Kids.
 *
 * Aquí no hay puntuación que perder, ni corazones, ni reloj. Un fallo no resta
 * nada: se sacude el dibujo, se repite la palabra y se sigue esperando. A los
 * tres años el castigo no corrige, solo hace que el niño deje de tocar.
 *
 * Tampoco escribe nada en el progreso del curso: la racha, la XP y el SRS son
 * de quien estudia A1→C2. Si un niño juega media hora, el adulto no debe
 * encontrarse la racha alterada ni el repaso lleno de palabras que él no vio.
 */
class KidsViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(KidsUiState())
    val state: StateFlow<KidsUiState> = _state.asStateFlow()

    private val preguntados = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val mundos = locator.contentRepository.kidsWorlds()
            _state.value = _state.value.copy(
                stage = if (mundos.isEmpty()) KidsStage.VACIO else KidsStage.MUNDOS,
                worlds = mundos
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Navegación                                                         */
    /* ------------------------------------------------------------------ */

    fun abrirMundo(world: KidsWorld, mode: KidsMode) {
        preguntados.clear()
        _state.value = _state.value.copy(
            world = world,
            mode = mode,
            aciertos = 0,
            numeroRonda = 0,
            acertado = null,
            fallado = null
        )
        if (mode == KidsMode.EXPLORAR) {
            _state.value = _state.value.copy(stage = KidsStage.EXPLORAR)
        } else {
            siguienteRonda()
        }
    }

    fun volverAMundos() {
        locator.tts.stop()
        _state.value = _state.value.copy(
            stage = KidsStage.MUNDOS, world = null, ronda = null,
            acertado = null, fallado = null, sonando = null
        )
    }

    /* ------------------------------------------------------------------ */
    /*  Explorar: tocar y oír, sin aciertos ni fallos                      */
    /* ------------------------------------------------------------------ */

    fun decir(item: KidsItem) {
        _state.value = _state.value.copy(sonando = item.id)
        hablar(item)
        viewModelScope.launch {
            delay(900)
            if (_state.value.sonando == item.id) {
                _state.value = _state.value.copy(sonando = null)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Jugar: suena una palabra y hay que tocarla                         */
    /* ------------------------------------------------------------------ */

    private fun siguienteRonda() {
        val mundo = _state.value.world ?: return
        val n = _state.value.numeroRonda

        if (n >= KidsRules.RONDAS) {
            _state.value = _state.value.copy(stage = KidsStage.CELEBRAR, ronda = null)
            celebrar()
            return
        }

        val ronda = KidsRules.ronda(mundo.items, n, preguntados) ?: return
        preguntados += ronda.correcto.id
        _state.value = _state.value.copy(
            stage = KidsStage.JUGAR,
            ronda = ronda,
            acertado = null,
            fallado = null
        )
        // Un respiro antes de hablar: si la voz sale a la vez que la pantalla,
        // el niño todavía no está mirando.
        viewModelScope.launch {
            delay(450)
            hablar(ronda.correcto)
        }
    }

    /** Repite la palabra. Siempre disponible: preguntar dos veces no es hacer trampa. */
    fun repetir() {
        _state.value.ronda?.let { hablar(it.correcto) }
    }

    fun tocar(item: KidsItem) {
        val ronda = _state.value.ronda ?: return
        if (_state.value.acertado != null) return   // ya acertó, se ignora

        if (item.id == ronda.correcto.id) {
            _state.value = _state.value.copy(
                acertado = item.id,
                fallado = null,
                aciertos = _state.value.aciertos + 1
            )
            hablar(item)
            viewModelScope.launch {
                delay(1300)
                _state.value = _state.value.copy(numeroRonda = _state.value.numeroRonda + 1)
                siguienteRonda()
            }
        } else {
            // Ni se resta ni se avanza: se marca un instante y se repite la
            // palabra buena para que la vuelva a oír.
            _state.value = _state.value.copy(fallado = item.id)
            viewModelScope.launch {
                delay(700)
                if (_state.value.fallado == item.id) {
                    _state.value = _state.value.copy(fallado = null)
                }
                hablar(ronda.correcto)
            }
        }
    }

    fun otraVez() {
        val mundo = _state.value.world ?: return
        abrirMundo(mundo, KidsMode.ENCONTRAR)
    }

    /* ------------------------------------------------------------------ */

    /**
     * Dice la palabra en inglés, despacio.
     *
     * Más lento que en el curso de adultos a propósito: es la primera vez que
     * el niño oye ese sonido y lo va a imitar.
     */
    private fun hablar(item: KidsItem) {
        locator.tts.speak(item.spoken, accent = Accent.US, rate = 0.75f)
    }

    private fun celebrar() {
        viewModelScope.launch {
            delay(350)
            locator.tts.speak("Great job!", accent = Accent.US, rate = 0.85f)
        }
    }

    override fun onCleared() {
        locator.tts.stop()
        super.onCleared()
    }
}
