package ai.lumo.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.lumo.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages encrypted key-value storage using Android Keystore and EncryptedSharedPreferences.
 * Sensitive data such as API keys and credentials are never stored in plaintext.
 */
class SecurePreferences(context: Context) {

    private val securePreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()
    private val preferences = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)

    val isSecureStorageAvailable: Boolean get() = securePreferences != null

    /** Key the user typed in Settings; empty when none was saved. */
    fun getUserGeminiApiKey(): String = getProviderApiKey("gemini")

    /** No bundled fallback — Req.9: never ship API keys in the APK. User must paste key in Settings. */
    val hasBuiltInGeminiKey: Boolean get() = false

    /** Key actually used for requests: user key only. */
    fun getGeminiApiKey(): String = getProviderApiKey("gemini")

    fun saveGeminiApiKey(apiKey: String) = saveProviderApiKey("gemini", apiKey)

    /** Selected LLM provider id (gemini, groq, deepseek, claude). */
    private val _llmProvider = MutableStateFlow(preferences.getString(KEY_LLM_PROVIDER, "gemini") ?: "gemini")
    val llmProvider: StateFlow<String> = _llmProvider.asStateFlow()

    fun setLlmProvider(id: String) {
        preferences.edit().putString(KEY_LLM_PROVIDER, id).apply()
        _llmProvider.value = id
        refreshLlmLabel()
    }

    /** Human label for the chat header, e.g. "Groq · llama-3.3-70b-versatile". */
    private val _llmLabel = MutableStateFlow(llmLabelText())
    val llmLabel: StateFlow<String> = _llmLabel.asStateFlow()

    fun getLlmProvider(): String = _llmProvider.value

    /** API key for one provider, stored encrypted. Empty when none was saved. */
    fun getProviderApiKey(providerId: String): String =
        securePreferences?.getString(KEY_PROVIDER_KEY_PREFIX + providerId, null)?.takeIf { it.isNotBlank() }.orEmpty()

    fun saveProviderApiKey(providerId: String, apiKey: String) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The API key was not saved." }
            .edit()
            .putString(KEY_PROVIDER_KEY_PREFIX + providerId, apiKey.trim())
            .apply()
    }

    /** Model override for one provider. Blank = provider default. */
    fun getProviderModel(providerId: String): String =
        securePreferences?.getString(KEY_PROVIDER_MODEL_PREFIX + providerId, null)?.takeIf { it.isNotBlank() }.orEmpty()

    fun saveProviderModel(providerId: String, model: String) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The model was not saved." }
            .edit()
            .putString(KEY_PROVIDER_MODEL_PREFIX + providerId, model.trim())
            .apply()
        refreshLlmLabel()
    }

    fun effectiveProviderModel(providerId: String, defaultModel: String): String =
        getProviderModel(providerId).ifBlank { defaultModel }

    private fun llmLabelText(): String {
        val id = _llmProvider.value
        val label = when (id) {
            "groq" -> "Groq"
            "deepseek" -> "DeepSeek"
            "claude" -> "Claude"
            else -> "Gemini"
        }
        val model = getProviderModel(id).ifBlank {
            when (id) {
                "groq" -> "llama-3.3-70b-versatile"
                "deepseek" -> "deepseek-chat"
                "claude" -> "claude-3-5-haiku-latest"
                else -> "gemini-3.5-flash-lite"
            }
        }
        return "$label · $model"
    }

    private fun refreshLlmLabel() {
        _llmLabel.value = llmLabelText()
    }

    /** Mem0 semantic memory key (Settings, Memory). Blank = Room-only offline mode. */
    fun getUserMem0ApiKey(): String =
        securePreferences?.getString(KEY_MEM0_API_KEY, null)?.takeIf { it.isNotBlank() }.orEmpty()

    fun getMem0ApiKey(): String = getUserMem0ApiKey().ifBlank { BuildConfig.MEM0_API_KEY_DEFAULT }

    /** True when smart recall (Mem0) can run — user key or bundled default. */
    private val _mem0Available = MutableStateFlow(getMem0ApiKey().isNotBlank())
    val mem0Available: StateFlow<Boolean> = _mem0Available.asStateFlow()

    fun saveMem0ApiKey(apiKey: String) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The Mem0 key was not saved." }
            .edit()
            .putString(KEY_MEM0_API_KEY, apiKey.trim())
            .apply()
        _mem0Available.value = getMem0ApiKey().isNotBlank()
    }

    fun getMem0UserId(): String = BuildConfig.MEM0_USER_ID_DEFAULT.ifBlank { "jarvis-android-user" }

    fun getDefaultEmail(): String {
        return securePreferences?.getString(KEY_DEFAULT_EMAIL, null)?.takeIf { it.isNotBlank() }.orEmpty()
    }

    fun saveDefaultEmail(email: String) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The email address was not saved." }
            .edit()
            .putString(KEY_DEFAULT_EMAIL, email.trim())
            .apply()
    }

    fun getBackendAccessToken(): String? = securePreferences?.getString(KEY_BACKEND_ACCESS_TOKEN, null)

    fun getBackendSessionExpiresAt(): Long = securePreferences?.getLong(KEY_BACKEND_SESSION_EXPIRES_AT, 0L) ?: 0L

    fun saveBackendSession(token: String, expiresAtEpochMillis: Long) {
        checkNotNull(securePreferences) { "Encrypted storage is unavailable. The backend session was not saved." }
            .edit()
            .putString(KEY_BACKEND_ACCESS_TOKEN, token)
            .putLong(KEY_BACKEND_SESSION_EXPIRES_AT, expiresAtEpochMillis)
            .apply()
    }

    fun clearBackendAccessToken() {
        securePreferences?.edit()
            ?.remove(KEY_BACKEND_ACCESS_TOKEN)
            ?.remove(KEY_BACKEND_SESSION_EXPIRES_AT)
            ?.apply()
    }

    fun isVoiceFeedbackEnabled(): Boolean {
        // Spoken replies are opt-in: the chat header toggle turns them on.
        return preferences.getBoolean(KEY_VOICE_FEEDBACK, false)
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_VOICE_FEEDBACK, enabled)
            .apply()
    }

    private val _themeMode = MutableStateFlow(preferences.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM")
    /** One of SYSTEM, LIGHT, DARK. */
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        preferences.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    /** Where mail comes from: OWN (the user's Gmail) or DEMO (sample mailbox). */
    private val _mailSource = MutableStateFlow(initialMailSource())
    val mailSource: StateFlow<String> = _mailSource.asStateFlow()

    fun isDemoMode(): Boolean = _mailSource.value == SOURCE_DEMO

    fun setMailSource(source: String) {
        require(source in setOf(SOURCE_DEMO, SOURCE_OWN))
        preferences.edit().putString(KEY_MAIL_SOURCE, source).apply()
        _mailSource.value = source
    }

    private fun initialMailSource(): String {
        // Older builds stored TEST/SHARED for the retired shared test account — migrate to OWN.
        when (preferences.getString(KEY_MAIL_SOURCE, null)) {
            SOURCE_OWN -> return SOURCE_OWN
            SOURCE_DEMO -> return SOURCE_DEMO
            "TEST", "SHARED" -> {
                preferences.edit().putString(KEY_MAIL_SOURCE, SOURCE_OWN).apply()
                return SOURCE_OWN
            }
        }
        // Older builds only stored a demo flag. A fresh install starts with the user's own Gmail.
        return when {
            preferences.getBoolean(KEY_DEMO_MODE, false) -> SOURCE_DEMO
            preferences.contains(KEY_DEMO_MODE) -> SOURCE_OWN
            else -> SOURCE_OWN
        }
    }

    fun isOnboardingDone(): Boolean = preferences.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone() {
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    companion object {
        const val SOURCE_DEMO = "DEMO"
        const val SOURCE_OWN = "OWN"
        private const val KEY_MAIL_SOURCE = "mail_source"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEMO_MODE = "demo_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val PREFS_FILENAME = "secure_agent_prefs"
        private const val KEY_PROVIDER_KEY_PREFIX = "llm_key_"
        private const val KEY_PROVIDER_MODEL_PREFIX = "llm_model_"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_MEM0_API_KEY = "mem0_api_key"
        private const val KEY_DEFAULT_EMAIL = "default_email"
        private const val KEY_BACKEND_ACCESS_TOKEN = "backend_access_token"
        private const val KEY_BACKEND_SESSION_EXPIRES_AT = "backend_session_expires_at"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback"
    }
}
