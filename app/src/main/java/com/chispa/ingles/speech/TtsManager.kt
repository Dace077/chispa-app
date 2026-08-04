package com.chispa.ingles.speech

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.chispa.ingles.data.prefs.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Voz de la app: `android.speech.tts.TextToSpeech`, que viene en el propio
 * dispositivo. Cero red, cero claves de API, cero coste.
 *
 * Es un singleton porque instanciar TextToSpeech es caro (arranca un servicio)
 * y porque queremos una sola cola de reproducción en toda la app.
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** Idiomas realmente disponibles en este dispositivo, de los que nos interesan. */
    private val availableAccents = mutableSetOf<Accent>()

    private var engine: TextToSpeech? = null
    private var pendingRequest: SpeechRequest? = null
    private val utteranceCounter = AtomicInteger(0)

    private var currentAccent: Accent = Accent.US
    private var currentRate: Float = 0.9f

    init {
        engine = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureEngine()
                _state.value = if (availableAccents.isEmpty()) TtsState.NO_VOICE else TtsState.READY
                pendingRequest?.let { request ->
                    pendingRequest = null
                    speak(request.text, request.accent, request.rate)
                }
            } else {
                Log.w(TAG, "TextToSpeech no pudo inicializarse (status=$status)")
                _state.value = TtsState.UNAVAILABLE
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _speaking.value = true }
                override fun onDone(utteranceId: String?) { _speaking.value = false }
                @Deprecated("Requerido por la clase base")
                override fun onError(utteranceId: String?) { _speaking.value = false }
                override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
            })
        }
    }

    private fun configureEngine() {
        val tts = engine ?: return
        Accent.entries.forEach { accent ->
            val result = tts.isLanguageAvailable(Locale(accent.language, accent.country))
            if (result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                availableAccents += accent
            }
        }
        // Si no hay ninguna variante concreta, al menos probamos inglés genérico.
        if (availableAccents.isEmpty() && tts.isLanguageAvailable(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE) {
            availableAccents += Accent.US
        }
    }

    fun isAccentAvailable(accent: Accent): Boolean = accent in availableAccents

    /** Acentos con voz instalada; siempre devuelve al menos uno si hay TTS. */
    fun supportedAccents(): List<Accent> =
        Accent.entries.filter { it in availableAccents }.ifEmpty { listOf(Accent.US) }

    /**
     * Lee [text] en voz alta, cortando lo anterior.
     * Si el motor aún está arrancando, la petición se guarda y se reproduce al estar listo.
     */
    fun speak(text: String, accent: Accent = currentAccent, rate: Float = currentRate) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        val tts = engine
        if (tts == null || _state.value == TtsState.INITIALIZING) {
            pendingRequest = SpeechRequest(clean, accent, rate)
            return
        }
        if (_state.value == TtsState.UNAVAILABLE) return

        currentAccent = accent
        currentRate = rate

        val target = if (accent in availableAccents) {
            Locale(accent.language, accent.country)
        } else {
            Locale.ENGLISH
        }
        tts.language = target
        tts.setSpeechRate(rate.coerceIn(0.4f, 1.4f))
        tts.setPitch(1.0f)

        val id = "chispa-${utteranceCounter.incrementAndGet()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        } else {
            @Suppress("DEPRECATION")
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to id))
        }
    }

    /** Versión lenta, para cuando el usuario pulsa "más despacio". */
    fun speakSlowly(text: String, accent: Accent = currentAccent) {
        speak(text, accent, rate = (currentRate * 0.6f).coerceAtLeast(0.4f))
    }

    fun stop() {
        engine?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
        _state.value = TtsState.UNAVAILABLE
    }

    private data class SpeechRequest(val text: String, val accent: Accent, val rate: Float)

    companion object {
        private const val TAG = "TtsManager"
    }
}

enum class TtsState {
    INITIALIZING,
    READY,
    /** El motor arrancó pero no hay voz de inglés instalada. */
    NO_VOICE,
    UNAVAILABLE
}
