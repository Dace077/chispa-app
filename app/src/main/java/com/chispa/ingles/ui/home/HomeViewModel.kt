package com.chispa.ingles.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.db.DailyActivityEntity
import com.chispa.ingles.data.db.UserProfileEntity
import com.chispa.ingles.domain.LessonNode
import com.chispa.ingles.domain.TrackNode
import com.chispa.ingles.domain.UnlockRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val profile: UserProfileEntity = UserProfileEntity(),
    val today: DailyActivityEntity = DailyActivityEntity(epochDay = 0L),
    val coreTracks: List<TrackNode> = emptyList(),
    val extraTracks: List<TrackNode> = emptyList(),
    val dueReviewCount: Int = 0,
    val hearts: Int = UserProfileEntity.MAX_HEARTS,
    val minutesToNextHeart: Long = 0L,
    val contentEmpty: Boolean = false
) {
    val dailyProgress: Float
        get() = if (profile.dailyGoalXp <= 0) 1f
        else (today.xp.toFloat() / profile.dailyGoalXp).coerceIn(0f, 1f)

    /** La lección que la home destaca con el botón grande. */
    val nextLesson: LessonNode?
        get() = coreTracks.asSequence()
            .flatMap { it.units.asSequence() }
            .flatMap { it.lessons.asSequence() }
            .firstOrNull { it.state == com.chispa.ingles.domain.LessonState.CURRENT }
}

class HomeViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val curriculum = locator.contentRepository.curriculum()

            combine(
                locator.progressRepository.profile,
                locator.progressRepository.lessonProgress,
                locator.progressRepository.todayActivity(),
                refreshTrigger
            ) { profile, progress, today, _ ->
                val placement = CefrLevel.from(profile.placementLevel)

                // El camino principal es una única secuencia A1 → B2; los módulos
                // extra son independientes entre sí y solo dependen de la XP.
                val core = UnlockRules.buildCorePath(
                    tracks = curriculum.coreTracks,
                    progress = progress,
                    totalXp = profile.totalXp,
                    placementLevel = placement
                )
                val extra = curriculum.extraTracks.map { track ->
                    UnlockRules.buildTrack(track, progress, profile.totalXp, placement)
                }

                HomeUiState(
                    loading = false,
                    profile = profile,
                    today = today,
                    coreTracks = core,
                    extraTracks = extra,
                    hearts = profile.hearts,
                    contentEmpty = curriculum.totalLessons == 0
                )
            }.collect { base ->
                // Estos dos datos son consultas puntuales, no flujos: se refrescan
                // cada vez que cambia algo del progreso, que es justo cuando importan.
                _state.value = base.copy(
                    dueReviewCount = locator.progressRepository.dueCount(),
                    hearts = locator.progressRepository.refreshHearts(),
                    minutesToNextHeart = locator.progressRepository.millisUntilNextHeart() / 60_000L
                )
            }
        }
    }

    fun refresh() {
        refreshTrigger.value += 1
    }
}
