package com.example.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

sealed interface SpeechListenState {
    data object Idle : SpeechListenState
    data object Listening : SpeechListenState
    data class Error(val message: String) : SpeechListenState
}

interface SpeechInputService {
    val state: StateFlow<SpeechListenState>
    val partialText: StateFlow<String>
    val rmsLevel: StateFlow<Float>
    fun isAvailable(): Boolean
    fun startListening(onFinalResult: (String) -> Unit)
    fun stopListening()
}

interface SpeechOutputService {
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String, pitch: Float = 1.0f, rate: Float = 1.0f, onComplete: (() -> Unit)? = null)
    fun stop()
    fun shutdown()
}

class AndroidSpeechInputService(private val context: Context) : SpeechInputService {

    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechListenState>(SpeechListenState.Idle)
    override val state: StateFlow<SpeechListenState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(onFinalResult: (String) -> Unit) {
        if (!isAvailable()) {
            _state.value = SpeechListenState.Error("Speech recognition service is unavailable on this device.")
            return
        }
        stopListening()
        _partialText.value = ""
        _state.value = SpeechListenState.Listening

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = SpeechListenState.Listening
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    _rmsLevel.value = 0f
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the orb to try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Voice recognition interrupted (code $error)."
                    }
                    _state.value = SpeechListenState.Error(msg)
                }

                override fun onResults(results: Bundle?) {
                    _rmsLevel.value = 0f
                    _state.value = SpeechListenState.Idle
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) {
                        onFinalResult(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialText.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            startListening(intent)
        }
    }

    override fun stopListening() {
        _rmsLevel.value = 0f
        recognizer?.runCatching {
            stopListening()
            destroy()
        }
        recognizer = null
        if (_state.value is SpeechListenState.Listening) {
            _state.value = SpeechListenState.Idle
        }
    }
}

class AndroidSpeechOutputService(context: Context) : SpeechOutputService {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isReady = true
            }
        }
    }

    override fun speak(text: String, pitch: Float, rate: Float, onComplete: (() -> Unit)?) {
        if (!isReady || text.isBlank()) {
            onComplete?.invoke()
            return
        }
        val clean = text.replace("**", "").replace("*", "").replace("•", "")
        tts?.setPitch(pitch.coerceIn(0.6f, 1.5f))
        tts?.setSpeechRate(rate.coerceIn(0.6f, 1.6f))
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onComplete?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onComplete?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                onComplete?.invoke()
            }
        })
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        _isSpeaking.value = false
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
