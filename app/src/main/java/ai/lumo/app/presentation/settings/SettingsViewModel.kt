package ai.lumo.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ai.lumo.app.core.security.SecurePreferences
import ai.lumo.app.data.mail.MailSourceManager
import ai.lumo.app.data.remote.BackendAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import ai.lumo.app.data.local.metrics.RoomPerformanceRecorder
import ai.lumo.app.domain.metrics.MetricSummary

class SettingsViewModel(
    private val securePreferences: SecurePreferences,
    private val backendAuthManager: BackendAuthManager,
    private val performanceRecorder: RoomPerformanceRecorder? = null,
    private val mailSourceManager: MailSourceManager? = null
) : ViewModel() {

    val mailSource: StateFlow<String> = securePreferences.mailSource
    fun selectMailSource(source: String) {
        viewModelScope.launch { mailSourceManager?.select(source) }
    }

    /** Measured on-device latency (median / p95) per metric, newest 1000 samples. */
    val performance: StateFlow<List<MetricSummary>> =
        (performanceRecorder?.summaries() ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearPerformanceSamples() {
        viewModelScope.launch { performanceRecorder?.clear() }
    }

    val gmailConnectionState: StateFlow<String> = backendAuthManager.connectionState

    init {
        viewModelScope.launch { backendAuthManager.refreshConnectionState() }
    }

    fun refreshGmailStatus() {
        viewModelScope.launch { backendAuthManager.refreshConnectionState() }
    }

    fun disconnectGmail() {
        viewModelScope.launch { backendAuthManager.disconnect() }
    }

    val themeMode: StateFlow<String> = securePreferences.themeMode
    val usesBuiltInKey: Boolean get() = securePreferences.hasBuiltInGeminiKey

    fun setThemeMode(mode: String) = securePreferences.setThemeMode(mode)

    // ---- LLM provider switching (Gemini, Groq, DeepSeek, Claude) ----
    val llmProvider: StateFlow<String> = securePreferences.llmProvider

    fun setLlmProvider(id: String) = securePreferences.setLlmProvider(id)

    fun providerKey(providerId: String): String = securePreferences.getProviderApiKey(providerId)

    fun updateProviderKey(providerId: String, key: String) {
        try {
            securePreferences.saveProviderApiKey(providerId, key)
            _storageMessage.value = "API key saved in encrypted storage."
        } catch (error: Exception) {
            _storageMessage.value = error.message ?: "Encrypted storage is unavailable; the key was not saved."
        }
    }

    fun providerModel(providerId: String): String = securePreferences.getProviderModel(providerId)

    fun updateProviderModel(providerId: String, model: String) {
        try {
            securePreferences.saveProviderModel(providerId, model)
            _storageMessage.value = "Model saved."
        } catch (error: Exception) {
            _storageMessage.value = error.message ?: "Encrypted storage is unavailable; the model was not saved."
        }
    }

    // ---- Smart recall (Mem0) ----
    val mem0Available: StateFlow<Boolean> = securePreferences.mem0Available

    private val _mem0Key = MutableStateFlow(securePreferences.getUserMem0ApiKey())
    val mem0Key: StateFlow<String> = _mem0Key.asStateFlow()

    fun updateMem0Key(key: String) {
        try {
            securePreferences.saveMem0ApiKey(key)
            _mem0Key.value = key.trim()
            _storageMessage.value = "Smart recall key saved in encrypted storage."
        } catch (error: Exception) {
            _storageMessage.value = error.message ?: "Encrypted storage is unavailable; the key was not saved."
        }
    }

    private val _apiKey = MutableStateFlow(securePreferences.getUserGeminiApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _defaultEmail = MutableStateFlow(securePreferences.getDefaultEmail())
    val defaultEmail: StateFlow<String> = _defaultEmail.asStateFlow()

    private val _voiceFeedback = MutableStateFlow(securePreferences.isVoiceFeedbackEnabled())
    val voiceFeedback: StateFlow<Boolean> = _voiceFeedback.asStateFlow()

    private val _storageMessage = MutableStateFlow(
        if (securePreferences.isSecureStorageAvailable) null
        else "Encrypted storage is unavailable. Secrets and email addresses cannot be saved."
    )
    val storageMessage: StateFlow<String?> = _storageMessage.asStateFlow()

    fun updateApiKey(key: String) {
        try {
            securePreferences.saveGeminiApiKey(key)
            _apiKey.value = key
            _storageMessage.value = "API key saved in encrypted storage."
        } catch (error: Exception) {
            _storageMessage.value = error.message ?: "Encrypted storage is unavailable; the key was not saved."
        }
    }

    fun updateDefaultEmail(email: String) {
        try {
            securePreferences.saveDefaultEmail(email)
            _defaultEmail.value = email
            _storageMessage.value = "Default email saved in encrypted storage."
        } catch (error: Exception) {
            _storageMessage.value = error.message ?: "Encrypted storage is unavailable; the email address was not saved."
        }
    }

    fun setVoiceFeedback(enabled: Boolean) {
        _voiceFeedback.value = enabled
        securePreferences.setVoiceFeedbackEnabled(enabled)
    }
}
