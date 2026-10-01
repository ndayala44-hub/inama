package rw.inama.app.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rw.inama.app.domain.model.AppLanguage

/**
 * Voice questions — Android port of AgriAI's useSpeechRecognition hook, built on the platform
 * SpeechRecognizer so the app can show its own listening screen (live transcript + level meter).
 * Must be used from the main thread. Needs the RECORD_AUDIO permission.
 *
 * Kinyarwanda speech-to-text is not available on most phones; in the next version this class is
 * the place to stream audio to a Kinyarwanda STT service instead.
 */
class SpeechInput(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String, val level: Float) : State
        data class Result(val text: String) : State
        data class Failed(val reason: Reason) : State
    }

    enum class Reason { NOT_AVAILABLE, NO_MATCH, NO_PERMISSION, NETWORK, OTHER }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: AppLanguage) {
        if (!isAvailable()) {
            _state.value = State.Failed(Reason.NOT_AVAILABLE)
            return
        }
        destroyRecognizer()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        _state.value = State.Listening("", 0f)
        r.startListening(intent)
    }

    /** Stops listening; the final result arrives through [state]. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        _state.value = State.Idle
    }

    fun reset() {
        _state.value = State.Idle
    }

    fun release() {
        destroyRecognizer()
        _state.value = State.Idle
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value
            if (current is State.Listening) {
                _state.value = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            _state.value = State.Failed(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Reason.NO_MATCH
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Reason.NO_PERMISSION
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Reason.NETWORK
                    else -> Reason.OTHER
                },
            )
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            _state.value = if (text.isBlank()) State.Failed(Reason.NO_MATCH) else State.Result(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val current = _state.value
            if (current is State.Listening && text.isNotBlank()) _state.value = current.copy(partial = text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
