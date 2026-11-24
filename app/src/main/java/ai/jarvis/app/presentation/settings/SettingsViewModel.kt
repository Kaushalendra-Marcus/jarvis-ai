package ai.jarvis.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ai.jarvis.app.core.security.SecurePreferences
import ai.jarvis.app.data.mail.MailSourceManager
import ai.jarvis.app.data.remote.BackendAuthManager
import ai.jarvis.app.data.remote.UserAccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import ai.jarvis.app.data.local.metrics.RoomPerformanceRecorder
import ai.jarvis.app.domain.metrics.MetricSummary

class SettingsViewModel(
    private val securePreferences: SecurePreferences,
    private val backendAuthManager: BackendAuthManager,
    private val userAccountManager: UserAccountManager,
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
        viewModelScope.launch {
            backendAuthManager.disconnect()
            // Server disconnect revokes every session of the Google account, so any
            // linked app identity on this device is dead too. Stay truthful in UI.
            userAccountManager.signOutLocally()
        }
    }

    // ---- App account (email + password signup / login / logout) ----
    val accountEmail: StateFlow<String> = userAccountManager.userEmail
    val accountBusy: StateFlow<Boolean> = userAccountManager.busy
    val accountError: StateFlow<String?> = userAccountManager.error

    fun clearAccountError() = userAccountManager.clearError()

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            if (userAccountManager.signUp(email, password)) backendAuthManager.refreshConnectionState()
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (userAccountManager.login(email, password)) backendAuthManager.refreshConnectionState()
        }
    }

    fun logout() {
        viewModelScope.launch {
            userAccountManager.logout()
            backendAuthManager.refreshConnectionState()
        }
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
    // Backend-managed: no key UI here. Status surfaces in Memory → About you.

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
