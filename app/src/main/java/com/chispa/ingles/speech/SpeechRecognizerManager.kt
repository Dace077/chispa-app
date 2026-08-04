package com.chispa.ingles.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.chispa.ingles.data.prefs.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reconocimiento de voz con `android.speech.SpeechRecognizer`.
 *
 * Historia de este archivo: la primera versión pedía siempre reconocimiento
 * **sin conexión** (`EXTRA_PREFER_OFFLINE`). En los móviles que no traen
 * descargado el modelo de inglés, el servicio del sistema no responde nunca:
 * ni resultado, ni error. La pantalla se quedaba escuchando eternamente.
 *
 * Ahora se intenta primero sin conexión y, si falla o no contesta, se
 * reintenta automáticamente con el modo normal. Además hay un perro guardián
 * que corta a los 12 segundos, para que un motor mudo nunca deje al usuario
 * atrapado mirando un micrófono que no hace nada.
 *
 * Nota de privacidad: el reconocimiento lo ejecuta otro proceso del sistema
 * (normalmente la app de Google), no Chispa. Esta app sigue sin declarar el
 * permiso de INTERNET y sigue sin poder enviar nada a ninguna parte.
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
    private val handler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null

    /** Acento del intento en curso, para poder reintentar sin él. */
    private var currentAccent: Accent = Accent.US
    /** true si el intento actual pidió modo sin conexión y aún no se ha reintentado. */
    private var triedOffline = false

    fun isAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /* ------------------------------------------------------------------ */
    /*  Arranque                                                           */
    /* ------------------------------------------------------------------ */

    fun start(accent: Accent) {
        currentAccent = accent
        triedOffline = false
        launchRecognition(accent, preferOffline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }

    private fun launchRecognition(accent: Accent, preferOffline: Boolean) {
        if (!isAvailable()) {
            _state.value = SpeechState.Unavailable
            return
        }
        if (!hasPermission()) {
            _state.value = SpeechState.PermissionNeeded
            return
        }

        cancelInternal()
        triedOffline = preferOffline

        val engine = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (engine == null) {
            _state.value = SpeechState.Error("No se pudo iniciar el motor de voz de tu dispositivo")
            return
        }
        recognizer = engine
        engine.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "${accent.language}-${accent.country}")
            // Idiomas de reserva: si el acento exacto no está, que use inglés genérico
            // en vez de rendirse.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (preferOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        _state.value = SpeechState.Listening()
        armWatchdog(WATCHDOG_START_MS)

        runCatching { engine.startListening(intent) }
            .onFailure { error ->
                Log.e(TAG, "startListening falló", error)
                clearWatchdog()
                _state.value = SpeechState.Error("No se pudo abrir el micrófono")
            }
    }

    /* ------------------------------------------------------------------ */
    /*  Perro guardián: ningún motor mudo puede dejar la pantalla colgada  */
    /* ------------------------------------------------------------------ */

    private fun armWatchdog(millis: Long) {
        clearWatchdog()
        val task = Runnable {
            Log.w(TAG, "El motor de voz no respondió en ${millis}ms")
            // Si pedimos modo sin conexión, el silencio casi siempre significa
            // que falta el modelo de idioma: reintentamos en modo normal.
            if (triedOffline) {
                Log.i(TAG, "Reintentando sin exigir modo offline")
                releaseEngine()
                launchRecognition(currentAccent, preferOffline = false)
            } else {
                cancelInternal()
                _state.value = SpeechState.Error(
                    "Tu motor de voz no respondió. Comprueba que tienes instalado " +
                        "el reconocimiento de voz en inglés."
                )
            }
        }
        watchdog = task
        handler.postDelayed(task, millis)
    }

    private fun clearWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    /* ------------------------------------------------------------------ */
    /*  Escucha de eventos                                                 */
    /* ------------------------------------------------------------------ */

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            // El motor está vivo: ampliamos el margen para que el usuario hable.
            armWatchdog(WATCHDOG_SPEAKING_MS)
            _state.value = SpeechState.Listening()
        }

        override fun onBeginningOfSpeech() {
            armWatchdog(WATCHDOG_SPEAKING_MS)
            _state.value = SpeechState.Listening()
        }

        override fun onRmsChanged(rmsdB: Float) {
            _amplitude.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _amplitude.value = 0f
            // Ya dejó de hablar: solo falta que el motor procese.
            armWatchdog(WATCHDOG_PROCESSING_MS)
            _state.value = SpeechState.Processing
        }

        override fun onError(error: Int) {
            _amplitude.value = 0f
            clearWatchdog()

            // Estos errores significan "no tengo ese idioma disponible aquí".
            // Si veníamos de exigir modo sin conexión, reintentamos con el normal.
            val esFaltaDeIdioma = error in LANGUAGE_ERRORS ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT

            if (esFaltaDeIdioma && triedOffline) {
                Log.i(TAG, "Error $error en modo offline; reintento en modo normal")
                // El motor ya ha fallado: soltarlo sin `cancel()`. Llamar a cancel
                // sobre un servicio caído solo genera "not connected" en el log.
                releaseEngine()
                launchRecognition(currentAccent, preferOffline = false)
                return
            }

            cancelInternal()
            _state.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechState.NoMatch
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechState.PermissionNeeded
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    SpeechState.Error("El micrófono lo está usando otra app. Ciérrala e inténtalo otra vez.")
                else -> SpeechState.Error(describe(error))
            }
        }

        override fun onResults(results: Bundle?) {
            clearWatchdog()
            _amplitude.value = 0f
            val hypotheses = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            releaseEngine()
            _state.value = if (hypotheses.isEmpty()) SpeechState.NoMatch
            else SpeechState.Result(hypotheses)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
            if (partial != null && _state.value is SpeechState.Listening) {
                armWatchdog(WATCHDOG_SPEAKING_MS)
                _state.value = SpeechState.Listening(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /* ------------------------------------------------------------------ */
    /*  Parada                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * El usuario ha terminado de hablar y pulsa para evaluar.
     *
     * Ojo: aquí solo se llama a `stopListening()`. La versión anterior
     * encadenaba `cancel()` y `destroy()` justo después, lo que mataba el
     * reconocimiento antes de que llegara `onResults` — pulsar para parar
     * nunca daba resultado.
     */
    fun stopAndEvaluate() {
        if (recognizer == null) return
        _state.value = SpeechState.Processing
        armWatchdog(WATCHDOG_PROCESSING_MS)
        runCatching { recognizer?.stopListening() }
    }

    /** Cancela del todo y deja el micrófono libre. */
    fun stop() {
        cancelInternal()
        if (_state.value is SpeechState.Listening || _state.value is SpeechState.Processing) {
            _state.value = SpeechState.Idle
        }
    }

    fun reset() {
        cancelInternal()
        _state.value = SpeechState.Idle
    }

    private fun cancelInternal() {
        clearWatchdog()
        runCatching { recognizer?.cancel() }
        releaseEngine()
        _amplitude.value = 0f
    }

    private fun releaseEngine() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Problema al leer el micrófono"
        SpeechRecognizer.ERROR_CLIENT -> "El motor de voz se cerró solo"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Tu motor de voz necesita conexión y no la tiene. Descarga el inglés " +
                "para uso sin conexión en los ajustes de voz de Android."
        SpeechRecognizer.ERROR_SERVER -> "El motor de voz del sistema falló"
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            "Tu dispositivo no tiene el idioma inglés para reconocimiento de voz."
        else -> "No se pudo reconocer el audio (código $error)"
    }

    companion object {
        private const val TAG = "SpeechRecognizer"

        /** Margen para que el motor dé señales de vida tras arrancar. */
        private const val WATCHDOG_START_MS = 7_000L
        /** Margen mientras el usuario habla. */
        private const val WATCHDOG_SPEAKING_MS = 15_000L
        /** Margen para que devuelva resultados tras dejar de hablar. */
        private const val WATCHDOG_PROCESSING_MS = 10_000L

        // Constantes de API 33+ escritas a mano para no romper minSdk 24.
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CANNOT_CHECK_SUPPORT = 14

        private val LANGUAGE_ERRORS = setOf(
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE,
            ERROR_CANNOT_CHECK_SUPPORT
        )
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
