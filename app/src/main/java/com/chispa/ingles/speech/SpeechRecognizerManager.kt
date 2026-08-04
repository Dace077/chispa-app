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
    /** Evita reintentar en bucle si el servicio se cae una y otra vez. */
    private var retriedAfterDisconnect = false

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
        retriedAfterDisconnect = false
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

        clearWatchdog()
        _amplitude.value = 0f
        triedOffline = preferOffline

        // Una sola instancia para toda la vida del gestor. Crear y destruir un
        // SpeechRecognizer en cada intento es lo que provoca el ERROR_SERVER_DISCONNECTED
        // (código 11) en muchos móviles: el servicio no da abasto a reconectarse.
        val engine = ensureRecognizer()
        if (engine == null) {
            _state.value = SpeechState.Error("No se pudo iniciar el motor de voz de tu dispositivo")
            return
        }
        runCatching { engine.cancel() }

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

            // Código 11: el servicio de voz se cayó. La instancia actual ya no
            // vale; hay que tirarla y crear otra, pero dándole un respiro al
            // sistema para relevantar el servicio. Sin esa pausa vuelve a fallar.
            if (error == ERROR_SERVER_DISCONNECTED && !retriedAfterDisconnect) {
                Log.i(TAG, "Servicio de voz desconectado; reintento tras una pausa")
                retriedAfterDisconnect = true
                releaseEngine()
                handler.postDelayed(
                    { launchRecognition(currentAccent, preferOffline = false) },
                    RECONNECT_DELAY_MS
                )
                return
            }

            // Estos errores significan "no tengo ese idioma disponible aquí".
            // Si veníamos de exigir modo sin conexión, reintentamos con el normal.
            val esFaltaDeIdioma = error in LANGUAGE_ERRORS ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT

            if (esFaltaDeIdioma && triedOffline) {
                Log.i(TAG, "Error $error en modo offline; reintento en modo normal")
                launchRecognition(currentAccent, preferOffline = false)
                return
            }

            cancelInternal()
            _state.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechState.NoMatch
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechState.PermissionNeeded
                else -> SpeechState.Error(
                    message = describe(error),
                    suggestSystemDialog = error in ACTIVITY_FALLBACK_ERRORS
                )
            }
        }

        override fun onResults(results: Bundle?) {
            clearWatchdog()
            _amplitude.value = 0f
            val hypotheses = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            // No se destruye el motor: se reutiliza en el siguiente ejercicio.
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

    /**
     * Crea el reconocedor la primera vez y lo reutiliza siempre. Solo se
     * destruye al cerrar la pantalla.
     */
    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val engine = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            ?: return null
        engine.setRecognitionListener(listener)
        recognizer = engine
        return engine
    }

    private fun cancelInternal() {
        clearWatchdog()
        runCatching { recognizer?.cancel() }
        _amplitude.value = 0f
    }

    /**
     * Tira la instancia actual. Solo se usa cuando el servicio ya se ha caído
     * y hay que empezar de cero, o al destruir la pantalla.
     */
    private fun releaseEngine() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    /** Suelta el motor del todo. Llamar al salir de la pantalla. */
    fun release() {
        clearWatchdog()
        runCatching { recognizer?.cancel() }
        releaseEngine()
        _amplitude.value = 0f
    }

    /**
     * Mensaje para cada código de error del estándar. Se listan todos a
     * propósito: un "no se pudo reconocer el audio" genérico no le dice nada
     * al usuario ni sirve para diagnosticar nada.
     */
    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_NETWORK ->
            "Tu motor de voz quiere conexión y no la tiene. Instala el inglés " +
                "para uso sin conexión en los ajustes de voz de Android."
        SpeechRecognizer.ERROR_AUDIO ->
            "No se pudo leer el micrófono. Comprueba que ninguna otra app lo esté usando."
        SpeechRecognizer.ERROR_SERVER ->
            "El motor de voz del sistema devolvió un error."
        SpeechRecognizer.ERROR_CLIENT ->
            "El motor de voz se cerró solo. Prueba con el asistente de voz del sistema."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "El micrófono lo está usando otra app. Ciérrala e inténtalo otra vez."
        ERROR_TOO_MANY_REQUESTS ->
            "Demasiados intentos seguidos. Espera unos segundos y vuelve a probar."
        ERROR_SERVER_DISCONNECTED ->
            "El servicio de voz de tu móvil se desconectó. Prueba con el asistente del sistema."
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            "Tu dispositivo no tiene instalado el inglés para reconocimiento de voz."
        ERROR_CANNOT_CHECK_SUPPORT ->
            "Tu móvil no pudo comprobar qué idiomas tiene disponibles."
        ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
            "Tu móvil está descargando el idioma. Espera un momento y reinténtalo."
        else -> "El motor de voz falló (código $error)"
    }

    companion object {
        private const val TAG = "SpeechRecognizer"

        /** Margen para que el motor dé señales de vida tras arrancar. */
        private const val WATCHDOG_START_MS = 7_000L
        /** Margen mientras el usuario habla. */
        private const val WATCHDOG_SPEAKING_MS = 15_000L
        /** Margen para que devuelva resultados tras dejar de hablar. */
        private const val WATCHDOG_PROCESSING_MS = 10_000L
        /** Pausa antes de reconectar cuando el servicio se cae (código 11). */
        private const val RECONNECT_DELAY_MS = 500L

        // Constantes de API 31/33+ escritas a mano para no romper minSdk 24.
        private const val ERROR_TOO_MANY_REQUESTS = 10
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CANNOT_CHECK_SUPPORT = 14
        private const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15

        private val LANGUAGE_ERRORS = setOf(
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE,
            ERROR_CANNOT_CHECK_SUPPORT,
            ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
        )

        /**
         * Errores en los que el servicio enlazado no sirve, pero el asistente
         * de voz del sistema (una Activity aparte) suele funcionar igualmente.
         */
        val ACTIVITY_FALLBACK_ERRORS = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER,
            ERROR_SERVER_DISCONNECTED,
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE,
            ERROR_CANNOT_CHECK_SUPPORT
        )

        /** Intent para abrir el diálogo de voz del sistema, como plan B. */
        fun systemDialogIntent(accent: Accent, prompt: String): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "${accent.language}-${accent.country}")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            }
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

    /**
     * @param suggestSystemDialog true si merece la pena ofrecer el asistente de
     *   voz del sistema: el servicio enlazado no sirve en este móvil, pero la
     *   Activity de reconocimiento casi siempre sí.
     */
    data class Error(
        val message: String,
        val suggestSystemDialog: Boolean = true
    ) : SpeechState
}
