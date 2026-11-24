package ai.jarvis.app.service.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Jarvis voice in/out — no Google recognition services.
 *
 * Voice input is record → transcribe: the mic records AAC to a temp file
 * ([MediaRecorder], whose amplitude also drives the orb), then the audio is
 * transcribed via Groq Whisper when a Groq key exists, else via Gemini native
 * audio input. Nothing happens silently: every failure surfaces as guidance
 * naming the exact key to add in Settings → AI model.
 */
class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var callback: ((String) -> Unit)? = null
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recording = false
    private var recordJobId = 0L
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

    /** Transcription is possible when a Groq or Gemini key is saved. */
    private val _canTranscribe = MutableStateFlow(false)
    val canTranscribe: StateFlow<Boolean> = _canTranscribe.asStateFlow()

    private var locale: Locale = Locale("en", "IN")

    init {
        tts = TextToSpeech(context.applicationContext, this).also { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _isSpeaking.value = true }
                override fun onDone(id: String?) { _isSpeaking.value = false }
                override fun onError(id: String?) { _isSpeaking.value = false }
            })
        }
        refreshTranscribeState()
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun refreshTranscribeState() {
        val prefs = runCatching {
            ai.jarvis.app.core.security.SecurePreferences(context)
        }.getOrNull()
        _canTranscribe.value = prefs != null &&
            (prefs.getProviderApiKey("groq").isNotBlank() || prefs.getProviderApiKey("gemini").isNotBlank())
    }

    fun startListening(onResult: (String) -> Unit) {
        _voiceError.value = null
        if (!hasMicPermission()) {
            _voiceError.value = "Mic blocked — allow microphone to talk to Jarvis"
            return
        }
        refreshTranscribeState()
        if (!_canTranscribe.value) {
            _voiceError.value = "Add a Groq or Gemini key in Settings → AI model for voice input"
            return
        }
        if (recording) return
        callback = onResult
        _recognizedText.value = null
        _rmsLevel.value = 0f
        runCatching { SoundEffectPlayer.playStartListeningSound() }

        val file = File(context.cacheDir, "jarvis-voice-${System.currentTimeMillis()}.m4a")
        val rec = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(16_000)
            setOutputFile(file.absolutePath)
        }
        try {
            rec.prepare()
            rec.start()
        } catch (_: Exception) {
            runCatching { rec.release() }
            _voiceError.value = "Mic busy — close other recorders and try again"
            return
        }
        recorder = rec
        audioFile = file
        recording = true
        _isListening.value = true

        // Mic level for the orb.
        val myRun = ++recordJobId
        scope.launch {
            while (recording && myRun == recordJobId) {
                val amp = runCatching { rec.maxAmplitude }.getOrDefault(0)
                _rmsLevel.value = (amp / 32767f).coerceIn(0f, 1f).coerceAtLeast(if (recording) 0.08f else 0f)
                delay(100)
            }
        }
        // Hard cap: 60 seconds, then transcribe whatever we have.
        scope.launch {
            delay(60_000)
            if (recording && myRun == recordJobId) stopListening()
        }
    }

    fun stopListening() {
        if (!recording) return
        recording = false
        recordJobId++
        _isListening.value = false
        _rmsLevel.value = 0f
        val rec = recorder
        recorder = null
        try {
            rec?.stop()
        } catch (_: Exception) {
            runCatching { rec?.release() }
            discardAudio()
            _voiceError.value = "Too short — hold, speak, then stop"
            runCatching { SoundEffectPlayer.playStopListeningSound() }
            return
        } finally {
            runCatching { rec?.release() }
        }
        runCatching { SoundEffectPlayer.playStopListeningSound() }
        val file = audioFile
        audioFile = null
        if (file == null || !file.exists() || file.length() < 1024) {
            discardAudio(file)
            _voiceError.value = "Didn't catch that — try again"
            return
        }
        _recognizedText.value = "Writing down what you said…"
        scope.launch {
            val result = transcribeFile(file)
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { text ->
                        _recognizedText.value = text
                        _voiceError.value = null
                        callback?.invoke(text)
                        callback = null
                    }
                    .onFailure { error ->
                        _recognizedText.value = null
                        _voiceError.value = error.message ?: "Transcription failed — try again"
                    }
            }
        }
    }

    fun cancelListening() {
        recording = false
        recordJobId++
        _isListening.value = false
        _rmsLevel.value = 0f
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        discardAudio()
        callback = null
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
        cancelListening()
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }

    // ---- internals ----

    private suspend fun transcribeFile(file: File): Result<String> {
        val prefs = ai.jarvis.app.core.security.SecurePreferences(context)
        val groqKey = prefs.getProviderApiKey("groq").trim()
        if (groqKey.isNotBlank()) {
            return WhisperTranscriber.transcribe(
                file,
                WhisperTranscriber.Target.OpenAiCompatible(
                    baseUrl = "https://api.groq.com/openai/v1",
                    apiKey = groqKey,
                    model = "whisper-large-v3-turbo"
                )
            )
        }
        val geminiKey = prefs.getProviderApiKey("gemini").trim()
        if (geminiKey.isNotBlank()) {
            val model = prefs.getProviderModel("gemini").trim()
                .ifBlank { "gemini-3.5-flash-lite" }
            return WhisperTranscriber.transcribe(
                file,
                WhisperTranscriber.Target.Gemini(apiKey = geminiKey, model = model)
            )
        }
        runCatching { file.delete() }
        return Result.failure(IllegalStateException("Add a Groq or Gemini key in Settings → AI model for voice input"))
    }

    private fun discardAudio(file: File? = audioFile) {
        audioFile = null
        runCatching { file?.delete() }
    }

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
