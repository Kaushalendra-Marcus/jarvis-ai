package ai.lumo.app.service.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Jarvis voice in/out — fixed STT.
 *
 * Why voice-to-text failed before:
 * - A single shared SpeechRecognizer hit ERROR_RECOGNIZER_BUSY / ERROR_CLIENT and never recovered.
 * - Google-only ComponentName crashed on devices without the Google app (no fallback).
 * - NO_MATCH / SPEECH_TIMEOUT surfaced as dead "Missed that" with no retry path.
 * - No automatic fallback to the system Google voice Activity when inline recognition is unavailable.
 *
 * Fix:
 * - Fresh recognizer per session + forced destroy on every terminal callback.
 * - Google-first, system-default second, Activity-intent third (automatic via [fallbackRequest]).
 * - One silent auto-retry for transient NO_MATCH / SPEECH_TIMEOUT; then Activity fallback.
 * - Busy / server errors back off instead of looping.
 */
class VoiceManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var callback: ((String) -> Unit)? = null
    private var sessionActive = false
    private var autoRetried = false
    private var busyBackoffUntil = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    /** Incremented whenever the UI should launch the Google voice Activity instead. */
    private val _fallbackRequest = MutableStateFlow(0)
    val fallbackRequest: StateFlow<Int> = _fallbackRequest.asStateFlow()

    private var locale: Locale = Locale("en", "IN")

    init {
        tts = TextToSpeech(context.applicationContext, this).also { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _isSpeaking.value = true }
                override fun onDone(id: String?) { _isSpeaking.value = false }
                override fun onError(id: String?) { _isSpeaking.value = false }
            })
        }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun googleRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /** Fallback: full Google voice UI activity when inline recognizer fails. */
    fun googleVoiceIntent(prompt: String = "Speak now"): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    fun startListening(onResult: (String) -> Unit) {
        _voiceError.value = null
        if (!hasMicPermission()) {
            _voiceError.value = "Mic blocked — allow microphone to talk to Jarvis"
            return
        }
        if (!googleRecognitionAvailable()) {
            // No recognition service at all: go straight to the Activity / error.
            _voiceError.value = "No voice service — try Google voice"
            _fallbackRequest.value += 1
            callback = onResult
            return
        }
        if (System.currentTimeMillis() < busyBackoffUntil) {
            _voiceError.value = "Voice busy — one sec"
            _fallbackRequest.value += 1
            callback = onResult
            return
        }
        callback = onResult
        autoRetried = false
        _recognizedText.value = null
        _rmsLevel.value = 0f
        runCatching { SoundEffectPlayer.playStartListeningSound() }
        beginSession()
    }

    private fun beginSession() {
        destroyRecognizer()
        recognizer = createRecognizer()?.apply { setRecognitionListener(this@VoiceManager) }
            ?: run {
                _voiceError.value = "Voice busy — try Google voice"
                _fallbackRequest.value += 1
                return
            }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        sessionActive = true
        try {
            recognizer?.startListening(intent)
            _isListening.value = true
        } catch (_: SecurityException) {
            sessionActive = false
            _isListening.value = false
            _voiceError.value = "Mic blocked — allow microphone to talk to Jarvis"
        } catch (_: Exception) {
            sessionActive = false
            _isListening.value = false
            _voiceError.value = "Voice busy — try Google voice"
            _fallbackRequest.value += 1
        }
    }

    fun stopListening() {
        runCatching { if (sessionActive) SoundEffectPlayer.playStopListeningSound() }
        runCatching { recognizer?.stopListening() }
        sessionActive = false
        _isListening.value = false
    }

    fun cancelListening() {
        runCatching { recognizer?.cancel() }
        sessionActive = false
        _isListening.value = false
    }

    fun clearError() { _voiceError.value = null }

    fun speak(text: String) {
        val engine = tts ?: return
        val clean = text.replace(Regex("[*_`#>\\-]"), "").replace(Regex("\\s+"), " ").trim().take(320)
        if (clean.isEmpty()) return
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "JarvisUtterance")
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    fun shutdown() {
        destroyRecognizer()
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }

    // ---- internals ----

    private fun createRecognizer(): SpeechRecognizer? {
        // Google-first, but never crash when the Google app is missing.
        val googleCn = ComponentName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
        )
        val pm = context.packageManager
        val hasGoogle = runCatching { pm.getServiceInfo(googleCn, 0) }.getOrNull() != null
        return try {
            if (hasGoogle) SpeechRecognizer.createSpeechRecognizer(context, googleCn)
            else SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        }
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun requestActivityFallback(message: String) {
        _voiceError.value = message
        _fallbackRequest.value += 1
    }

    // RecognitionListener — all UI state funnels through here.
    override fun onReadyForSpeech(p: Bundle?) {}
    override fun onBeginningOfSpeech() { _rmsLevel.value = 0.15f }
    override fun onRmsChanged(rmsdB: Float) {
        _rmsLevel.value = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
    }
    override fun onBufferReceived(b: ByteArray?) {}
    override fun onEndOfSpeech() {
        sessionActive = false
        _isListening.value = false
        runCatching { SoundEffectPlayer.playStopListeningSound() }
    }

    override fun onError(code: Int) {
        sessionActive = false
        _isListening.value = false
        runCatching { SoundEffectPlayer.playStopListeningSound() }
        destroyRecognizer()
        when (code) {
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                requestActivityFallback("Offline — try Google voice")
            }
            SpeechRecognizer.ERROR_SERVER -> {
                busyBackoffUntil = System.currentTimeMillis() + 4_000
                requestActivityFallback("Google busy — try Google voice")
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                busyBackoffUntil = System.currentTimeMillis() + 2_000
                requestActivityFallback("Voice busy — try Google voice")
            }
            SpeechRecognizer.ERROR_CLIENT -> {
                // Our side restarted the session; one silent retry.
                if (!autoRetried) {
                    autoRetried = true
                    scope.launch {
                        delay(350)
                        if (callback != null) beginSession()
                    }
                } else {
                    requestActivityFallback("Try Google voice")
                }
                return
            }
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH -> {
                // Didn't catch it: one silent retry, then fall back with the partial text kept.
                if (!autoRetried) {
                    autoRetried = true
                    _voiceError.value = "Didn't catch — listening again"
                    scope.launch {
                        delay(400)
                        if (callback != null) beginSession()
                    }
                    return
                }
                requestActivityFallback("Missed that — try Google voice")
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                _voiceError.value = "Mic busy — close other recorders"
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                _voiceError.value = "Mic blocked — allow microphone to talk to Jarvis"
            }
            else -> {
                requestActivityFallback("Try Google voice")
            }
        }
    }

    override fun onResults(r: Bundle?) {
        sessionActive = false
        _isListening.value = false
        runCatching { SoundEffectPlayer.playStopListeningSound() }
        val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        destroyRecognizer()
        if (!text.isNullOrBlank()) {
            _recognizedText.value = text
            _voiceError.value = null
            callback?.invoke(text)
        } else if (!autoRetried) {
            autoRetried = true
            _voiceError.value = "Didn't catch — listening again"
            scope.launch {
                delay(400)
                if (callback != null) beginSession()
            }
            return
        } else {
            requestActivityFallback("Missed that — try Google voice")
        }
        callback = null
    }

    override fun onPartialResults(p: Bundle?) {
        val text = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        if (!text.isNullOrBlank()) _recognizedText.value = text
    }

    override fun onEvent(t: Int, p: Bundle?) {}

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        val candidates = listOf(Locale("en", "IN"), Locale("hi", "IN"), Locale.getDefault(), Locale.ENGLISH)
        for (loc in candidates) {
            val ok = engine.isLanguageAvailable(loc)
            if (ok == TextToSpeech.LANG_AVAILABLE || ok == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                ok == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                engine.language = loc
                locale = loc
                break
            }
        }
        engine.setSpeechRate(1.0f)
    }

    fun setVoiceLocale(tag: String) {
        val loc = Locale.forLanguageTag(tag)
        locale = loc
        tts?.let {
            if (it.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) it.language = loc
        }
    }
}
