package rw.inama.app.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rw.inama.app.domain.model.AppLanguage
import java.util.Locale

/**
 * "Listen" on every piece of advice — Android port of AgriAI's useSpeechSynthesis hook.
 *
 * The voice follows the app language. Phones rarely ship a Kinyarwanda TTS voice, so when the
 * requested language is unavailable this falls back to English and reports it via
 * [lastLanguageSupported]; the next version can plug a server-side Kinyarwanda TTS in here.
 */
class SpeechOutput(context: Context) {
    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    @Volatile
    private var ready = false
    var lastLanguageSupported: Boolean = true
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.also { engine ->
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speakingId.value = utteranceId
            }

            override fun onDone(utteranceId: String?) {
                if (_speakingId.value == utteranceId) _speakingId.value = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (_speakingId.value == utteranceId) _speakingId.value = null
            }
        })
    }

    val isReady: Boolean get() = ready

    /** Speaks [text]; returns false if the phone has no text-to-speech engine ready. */
    fun speak(id: String, text: String, language: AppLanguage, rate: Float): Boolean {
        if (!ready || text.isBlank()) return false
        val wanted = Locale.forLanguageTag(language.tag)
        lastLanguageSupported = tts.isLanguageAvailable(wanted) >= TextToSpeech.LANG_AVAILABLE
        tts.language = if (lastLanguageSupported) wanted else Locale.ENGLISH
        tts.setSpeechRate(rate)
        _speakingId.value = id
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
    }

    fun toggle(id: String, text: String, language: AppLanguage, rate: Float): Boolean =
        if (_speakingId.value == id) {
            stop()
            true
        } else {
            speak(id, text, language, rate)
        }

    fun stop() {
        tts.stop()
        _speakingId.value = null
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
