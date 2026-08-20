package com.chispa.ingles.ui.toefl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.ToeflGuide
import com.chispa.ingles.domain.CertificateRules
import com.chispa.ingles.domain.LevelCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Un simulacro, tal y como se lista en la portada. */
data class ExamEntry(val id: String, val titulo: String, val mejorPuntaje: Int?)

data class ToeflUiState(
    val loading: Boolean = true,
    val guide: ToeflGuide = ToeflGuide("", "", emptyList(), emptyList(), emptyList()),
    /** Los simulacros se abren al terminar B2. */
    val unlocked: Boolean = false,
    val b2Progress: Float = 0f,
    val b2Remaining: Int = 0,
    val exams: List<ExamEntry> = emptyList(),
    val bestScore: Int? = null
)

class ToeflViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(ToeflUiState())
    val state: StateFlow<ToeflUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val guia = locator.contentRepository.toefl()
            val curriculum = locator.contentRepository.curriculum()
            val examenes = locator.contentRepository.toeflExamIds()

            locator.progressRepository.lessonProgress.collect { progreso ->
                val b2: LevelCompletion? = CertificateRules
                    .levelStatus(curriculum, progreso)
                    .firstOrNull { it.level == CefrLevel.B2 }

                _state.value = ToeflUiState(
                    loading = false,
                    guide = guia,
                    unlocked = b2?.isComplete == true,
                    b2Progress = b2?.progress ?: 0f,
                    b2Remaining = b2?.remaining ?: 0,
                    exams = examenes.mapIndexed { i, id ->
                        ExamEntry(
                            id = id,
                            titulo = "Simulacro ${i + 1}",
                            mejorPuntaje = locator.database.examAttemptDao().bestFor(id)?.scaledScore
                        )
                    },
                    bestScore = locator.database.examAttemptDao().bestScore()
                )
            }
        }
    }
}
