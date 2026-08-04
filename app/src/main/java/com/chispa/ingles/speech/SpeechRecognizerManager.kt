package com.chispa.ingles.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.chispa.ingles.data.prefs.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reconocimiento de voz con `android.speech.SpeechRecognizer`.
 *
 * En Android 12+ se pide explícitamente el reconocimiento **on-device**
 * (`EXTRA_PREFER_OFFLINE`), coherente con la promesa de que la app no usa red.
 * Si el dispositivo no trae modelo offline, el sistema puede caer a su propio
 * servicio; en ese caso avisamos al usuario en la UI en vez de fingir que todo
 * va bien.
 *
 * Debe usarse siempre desde el hilo principal: es requisito de SpeechRecognizer.
 */
class SpeechRecognizerManager(private val context: Context) {

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    /** Nivel de voz 0f..1f para animar el micrófono. */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(accent: Accent) {
        if (!isAvailable()) {
            _state.value = SpeechState.Unavailable
            return
        }
        if (!hasPermission()) {
            _state.value = SpeechState.PermissionNeeded
            return
        }
        stop()

        val engine = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechState.Listening()
            }

            override fun onBeginningOfSpeech() {
                _state.value = SpeechState.Listening()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB llega aproximadamente en -2..10; lo normalizamos para la animación.
                _amplitude.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                _amplitude.value = 0f
                _state.value = SpeechState.Processing
            }

            override fun onError(error: Int) {
                listening = false
                _amplitude.value = 0f
                _state.value = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechState.NoMatch
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechState.PermissionNeeded
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechState.Idle
                    else -> SpeechState.Error(describe(error))
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                _amplitude.value = 0f
                val hypotheses = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                _state.value = if (hypotheses.isEmpty()) {
                    SpeechState.NoMatch
                } else {
                    SpeechState.Result(hypotheses)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                if (partial != null && _state.value is SpeechState.Listening) {
                    _state.value = SpeechState.Listening(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "${accent.language}-${accent.country}")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        listening = true
        _state.value = SpeechState.Listening()
        runCatching { engine.startListening(intent) }
            .onFailure {
                listening = false
                _state.value = SpeechState.Error("No se pudo abrir el micrófono")
            }
    }

    fun stop() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
        listening = false
        _amplitude.value = 0f
    }

    fun reset() {
        stop()
        _state.value = SpeechState.Idle
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Problema con el micrófono"
        SpeechRecognizer.ERROR_CLIENT -> "El reconocedor se cerró solo"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Tu dispositivo pidió internet para reconocer voz. Descarga el idioma inglés para uso sin conexión."
        SpeechRecognizer.ERROR_SERVER -> "El motor de voz del sistema falló"
        else -> "No se pudo reconocer el audio"
    }
}

sealed interface SpeechState {
    data object Idle : SpeechState
    data class Listening(val partial: String? = null) : SpeechState
    data object Processing : SpeechState
    data class Result(val hypotheses: List<String>) : SpeechState
    data object NoMatch : SpeechState
    data object PermissionNeeded : SpeechState
    data object Unavailable : SpeechState
    data class Error(val message: String) : SpeechState
}
