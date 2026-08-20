package com.chispa.ingles.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppStage { LOADING, ONBOARDING, PLACEMENT, READY }

data class AppState(
    val stage: AppStage = AppStage.LOADING,
    /** Solo para saludar en la puerta de entrada. */
    val nombre: String = ""
)

/**
 * Decide en qué punto del embudo está el usuario: bienvenida, test de nivel o
 * app completa. Se consulta una sola vez al arrancar y tras cada paso.
 */
class AppViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        refresh()
        // Precalentamos el currículo en segundo plano: al llegar a la home ya
        // está parseado y la pantalla no parpadea.
        viewModelScope.launch {
            val curriculo = locator.contentRepository.curriculum()
            // Y de paso se saca del repaso lo que nunca debió entrar. Es
            // idempotente y solo borra por clave exacta, así que no toca el
            // vocabulario de verdad ni el progreso de nadie.
            val borradas = locator.progressRepository.purgeNonVocabCards(curriculo)
            if (borradas > 0) {
                Log.i("Chispa", "Repaso limpiado: $borradas tarjetas que no eran vocabulario")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = locator.progressRepository.ensureProfile()
            _state.value = AppState(
                stage = when {
                    !profile.onboardingDone -> AppStage.ONBOARDING
                    !profile.placementDone -> AppStage.PLACEMENT
                    else -> AppStage.READY
                },
                nombre = profile.studentName.trim()
            )
        }
    }
}
