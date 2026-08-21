package com.chispa.ingles.ui.profile

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chispa.ingles.certificates.CertificatePdf
import com.chispa.ingles.certificates.CertificateSharing
import com.chispa.ingles.core.ServiceLocator
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.db.CertificateEntity
import com.chispa.ingles.domain.CertificateRules
import com.chispa.ingles.domain.LevelCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CertificatesUiState(
    val loading: Boolean = true,
    val hasName: Boolean = false,
    /** Ciudad del alumno, para la línea de lugar y fecha del PDF. */
    val city: String = "",
    val levels: List<LevelCompletion> = emptyList(),
    /** Certificados ya emitidos, indexados por etiqueta de nivel. */
    val issued: Map<String, CertificateEntity> = emptyMap()
)

class CertificatesViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(CertificatesUiState())
    val state: StateFlow<CertificatesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val curriculum = locator.contentRepository.curriculum()
            combine(
                locator.progressRepository.profile,
                locator.progressRepository.lessonProgress,
                locator.progressRepository.certificates
            ) { profile, progress, certificates ->
                CertificatesUiState(
                    loading = false,
                    hasName = profile.canReceiveCertificate,
                    city = profile.studentCity,
                    levels = CertificateRules.levelStatus(curriculum, progress),
                    issued = certificates.associateBy { it.level }
                )
            }.collect { _state.value = it }
        }
    }

    /**
     * Emite el certificado si hacía falta, genera el PDF y lo abre.
     *
     * Si el teléfono no tiene visor de PDF —que pasa— se cae de vuelta al
     * diálogo de compartir, que siempre existe. Quedarse sin hacer nada tras
     * pulsar un botón es la peor de las opciones.
     */
    fun generar(context: Context, level: CefrLevel) {
        viewModelScope.launch {
            val archivo = prepararPdf(context, level) ?: return@launch
            if (!CertificateSharing.abrir(context, archivo)) {
                CertificateSharing.compartir(context, archivo, tituloDe(level))
            }
        }
    }

    fun compartir(context: Context, level: CefrLevel) {
        viewModelScope.launch {
            val archivo = prepararPdf(context, level) ?: return@launch
            CertificateSharing.compartir(context, archivo, tituloDe(level))
        }
    }

    private suspend fun prepararPdf(context: Context, level: CefrLevel): File? {
        val completion = _state.value.levels.firstOrNull { it.level == level }
        if (completion == null || !completion.isComplete) return null

        val certificado = locator.progressRepository.issueCertificate(level, completion)
        if (certificado == null) {
            Toast.makeText(
                context,
                "Pon tu nombre en «Tus datos» para poder emitir el certificado",
                Toast.LENGTH_LONG
            ).show()
            return null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                CertificatePdf.render(
                    context = context,
                    data = CertificatePdf.Data(
                        studentName = certificado.studentName,
                        level = level,
                        folio = certificado.folio,
                        issuedAt = certificado.issuedAt,
                        city = _state.value.city,
                        lessonsCompleted = certificado.lessonsCompleted,
                        accuracy = certificado.accuracy,
                        totalXp = certificado.totalXp
                    )
                )
            }.getOrNull()
        }
    }

    private fun tituloDe(level: CefrLevel) = "Certificado de inglés — Nivel ${level.label}"
}
