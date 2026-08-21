package com.chispa.ingles.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.db.AchievementEntity
import com.chispa.ingles.data.db.DailyActivityEntity
import com.chispa.ingles.data.db.SrsCardEntity
import com.chispa.ingles.data.db.UserProfileEntity
import com.chispa.ingles.domain.Avatar
import com.chispa.ingles.domain.AvatarRules
import com.chispa.ingles.domain.CertificateRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: UserProfileEntity = UserProfileEntity(),
    /** Ya resuelto contra los niveles terminados, nunca uno sin desbloquear. */
    val avatar: Avatar = Avatar.DEFAULT,
    val activity: List<DailyActivityEntity> = emptyList(),
    val unlockedAchievements: Set<String> = emptySet(),
    val vocabSeen: Int = 0,
    val vocabMastered: Int = 0,
    val lessonsCompleted: Int = 0,
    val thisWeekXp: Int = 0,
    val lastWeekXp: Int = 0,
    val bestWeekXp: Int = 0
)

class ProfileViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** Últimas 15 semanas: suficiente para ver un patrón sin saturar la pantalla. */
    private val historyStartDay = com.chispa.ingles.core.Time.todayEpochDay() - 105

    init {
        viewModelScope.launch {
            val curriculum = locator.contentRepository.curriculum()

            val base = combine(
                locator.progressRepository.profile,
                locator.progressRepository.activitySince(historyStartDay),
                locator.progressRepository.achievements,
                locator.progressRepository.vocabSeenCount,
                locator.progressRepository.vocabMasteredCount
            ) { profile, activity, achievements, seen, mastered ->
                ProfileUiState(
                    loading = false,
                    profile = profile,
                    activity = activity,
                    unlockedAchievements = achievements.map(AchievementEntity::achievementId).toSet(),
                    vocabSeen = seen,
                    vocabMastered = mastered
                )
            }

            // El avatar se resuelve aparte porque necesita el progreso por
            // lección: son los niveles COMPLETOS los que lo desbloquean, la
            // misma fuente que emite los certificados.
            combine(base, locator.progressRepository.lessonProgress) { estado, progress ->
                val completos = CertificateRules.earnedLevels(curriculum, progress)
                    .map { it.level }
                    .toSet()
                estado.copy(avatar = AvatarRules.resolve(estado.profile.avatarId, completos))
            }.collect { base ->
                val thisWeek = locator.progressRepository.weeklyXp(0)
                val lastWeek = locator.progressRepository.weeklyXp(1)
                val best = (0..12).maxOf { locator.progressRepository.weeklyXp(it) }
                _state.value = base.copy(
                    thisWeekXp = thisWeek,
                    lastWeekXp = lastWeek,
                    bestWeekXp = best,
                    lessonsCompleted = locator.database.lessonProgressDao().completedCount()
                )
            }
        }
    }
}

data class VocabularyUiState(
    val loading: Boolean = true,
    val cards: List<SrsCardEntity> = emptyList()
)

class VocabularyViewModel(locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(VocabularyUiState())
    val state: StateFlow<VocabularyUiState> = _state.asStateFlow()

    private val tts = locator.tts

    init {
        viewModelScope.launch {
            locator.progressRepository.observeVocabulary().collect { cards ->
                _state.value = VocabularyUiState(loading = false, cards = cards)
            }
        }
    }

    fun speak(text: String) = tts.speak(text)
}
