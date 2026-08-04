package com.chispa.ingles.ui.grammar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.GrammarGuide
import com.chispa.ingles.data.content.GrammarTopic
import com.chispa.ingles.data.prefs.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* =========================================================================
 *  Índice de la guía
 *
 *  Se puede filtrar por nivel y buscar por texto. Los dos filtros se aplican
 *  a la vez, porque quien busca "pasado" y está en A2 quiere las dos cosas.
 * ========================================================================= */

data class GrammarUiState(
    val loading: Boolean = true,
    val vacia: Boolean = false,
    val query: String = "",
    val nivel: CefrLevel? = null,
    /** Niveles que realmente tienen temas, para no pintar filtros vacíos. */
    val nivelesDisponibles: List<CefrLevel> = emptyList(),
    /** Ya filtrado y agrupado por área, en el orden en que se escribió. */
    val porArea: Map<String, List<GrammarTopic>> = emptyMap(),
    val totalVisible: Int = 0,
    val totalGuia: Int = 0
)

class GrammarViewModel(locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(GrammarUiState())
    val state: StateFlow<GrammarUiState> = _state.asStateFlow()

    private var guia: GrammarGuide = GrammarGuide(emptyList())

    init {
        viewModelScope.launch {
            guia = locator.contentRepository.grammar()
            _state.value = _state.value.copy(
                loading = false,
                vacia = guia.isEmpty,
                totalGuia = guia.topics.size,
                nivelesDisponibles = guia.topics.map { it.level }
                    .distinct()
                    .sortedBy { it.order }
            )
            recalcular()
        }
    }

    fun buscar(texto: String) {
        _state.value = _state.value.copy(query = texto)
        recalcular()
    }

    /** Volver a pulsar el nivel ya activo lo quita: es el gesto que espera todo el mundo. */
    fun filtrarNivel(nivel: CefrLevel?) {
        val actual = _state.value.nivel
        _state.value = _state.value.copy(nivel = if (nivel == actual) null else nivel)
        recalcular()
    }

    private fun recalcular() {
        val s = _state.value
        val visibles = guia.search(s.query)
            .filter { s.nivel == null || it.level == s.nivel }
        _state.value = s.copy(
            porArea = visibles.groupBy { it.area },
            totalVisible = visibles.size
        )
    }
}

/* =========================================================================
 *  Ficha de un tema
 * ========================================================================= */

data class GrammarTopicUiState(
    val loading: Boolean = true,
    val topic: GrammarTopic? = null,
    /** Temas relacionados que existen de verdad, ya resueltos a objeto. */
    val relacionados: List<GrammarTopic> = emptyList(),
    val accent: Accent = Accent.US,
    val velocidad: Float = 0.9f
)

class GrammarTopicViewModel(
    locator: ServiceLocator,
    private val topicId: String
) : ViewModel() {

    private val _state = MutableStateFlow(GrammarTopicUiState())
    val state: StateFlow<GrammarTopicUiState> = _state.asStateFlow()

    private val tts = locator.tts

    init {
        viewModelScope.launch {
            val ajustes = locator.settingsStore.current()
            val guia = locator.contentRepository.grammar()
            val tema = guia.find(topicId)
            _state.value = GrammarTopicUiState(
                loading = false,
                topic = tema,
                relacionados = tema?.related.orEmpty().mapNotNull { guia.find(it) },
                accent = ajustes.accent,
                velocidad = ajustes.speechRate
            )
        }
    }

    /** Escuchar una frase de ejemplo. Sin seguimiento de palabra: aquí no hace falta. */
    fun escuchar(texto: String) {
        val s = _state.value
        tts.speak(texto, s.accent, s.velocidad)
    }

    override fun onCleared() {
        tts.stop()
        super.onCleared()
    }
}
