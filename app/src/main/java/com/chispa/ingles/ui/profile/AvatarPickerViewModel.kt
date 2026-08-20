package com.chispa.ingles.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.domain.Avatar
import com.chispa.ingles.domain.AvatarRules
import com.chispa.ingles.domain.CertificateRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AvatarPickerUiState(
    val selected: Avatar = Avatar.DEFAULT,
    val unlocked: List<Avatar> = listOf(Avatar.DEFAULT),
    val completedLevels: Set<CefrLevel> = emptySet()
)

class AvatarPickerViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(AvatarPickerUiState())
    val state: StateFlow<AvatarPickerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val curriculum = locator.contentRepository.curriculum()
            combine(
                locator.progressRepository.profile,
                locator.progressRepository.lessonProgress
            ) { profile, progress ->
                // Los niveles completos salen de la MISMA fuente que decide los
                // certificados. Dos criterios distintos para "terminaste B1"
                // acabarían discrepando, y el usuario vería el diploma pero no
                // el avatar, o al revés.
                val completos = CertificateRules.earnedLevels(curriculum, progress)
                    .map { it.level }
                    .toSet()

                AvatarPickerUiState(
                    selected = AvatarRules.resolve(profile.avatarId, completos),
                    unlocked = AvatarRules.unlocked(completos),
                    completedLevels = completos
                )
            }.collect { _state.value = it }
        }
    }

    /** Ignora los bloqueados en silencio: la pantalla ya no deja pulsarlos. */
    fun elegir(avatar: Avatar) {
        if (!AvatarRules.isUnlocked(avatar, _state.value.completedLevels)) return
        _state.value = _state.value.copy(selected = avatar)
        viewModelScope.launch { locator.progressRepository.setAvatar(avatar.id) }
    }
}
