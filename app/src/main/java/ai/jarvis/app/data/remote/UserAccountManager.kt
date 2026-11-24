package ai.jarvis.app.data.remote

import ai.jarvis.app.core.security.SecurePreferences
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * App-level user accounts (email + password) on the Jarvis backend.
 *
 * Guest flows are untouched: a device with no account email keeps working exactly
 * as before (demo mailbox, Google-only Gmail sessions). Logging in swaps the device
 * to a user session; a later Gmail OAuth links Google into that same session.
 * Logging out revokes this device's session server-side and clears local identity.
 */
class UserAccountManager(
    private val api: BackendApiClient,
    private val securePreferences: SecurePreferences
) {
    private val _userEmail = MutableStateFlow(securePreferences.getUserEmail())
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun isLoggedIn(): Boolean = _userEmail.value.isNotBlank()

    fun clearError() {
        _error.value = null
    }

    suspend fun signUp(emailInput: String, password: String): Boolean = withContext(Dispatchers.IO) {
        authenticate(emailInput, password, ::apiCallSignUp)
    }

    suspend fun login(emailInput: String, password: String): Boolean = withContext(Dispatchers.IO) {
        authenticate(emailInput, password, ::apiCallLogin)
    }

    private suspend fun apiCallSignUp(email: String, password: String) = api.signUp(email, password)
    private suspend fun apiCallLogin(email: String, password: String) = api.login(email, password)

    private suspend fun authenticate(
        emailInput: String,
        password: String,
        call: suspend (String, String) -> BackendApiClient.UserSession
    ): Boolean {
        _busy.value = true
        _error.value = null
        try {
            val email = AuthValidation.cleanEmail(emailInput)
                ?: return fail("Enter a valid email address.")
            AuthValidation.passwordError(password)?.let { return fail(it) }
            // Retire any previous device session first: one identity per device.
            val oldToken = securePreferences.getBackendAccessToken()
            val session = try {
                call(email, password)
            } catch (error: Exception) {
                return fail(friendlyMessage(error))
            }
            if (!oldToken.isNullOrBlank() && oldToken != session.accessToken) {
                runCatching { api.revokeSession(oldToken) }
            }
            securePreferences.saveBackendSession(
                session.accessToken,
                Instant.parse(session.expiresAt).toEpochMilli()
            )
            securePreferences.saveUserEmail(session.email)
            _userEmail.value = session.email
            return true
        } catch (error: Exception) {
            return fail(friendlyMessage(error))
        } finally {
            _busy.value = false
        }
    }

    /** Re-reads the account email from the server (null when signed out/expired). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val token = securePreferences.getBackendAccessToken()?.takeIf { it.isNotBlank() } ?: run {
            _userEmail.value = ""
            return@withContext
        }
        try {
            val email = api.getUserProfile(token).orEmpty()
            if (email != _userEmail.value) {
                if (email.isBlank()) securePreferences.clearUserEmail()
                else securePreferences.saveUserEmail(email)
                _userEmail.value = email
            }
        } catch (error: Exception) {
            if (error is BackendApiException && error.statusCode == 401) signOutLocally()
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val token = securePreferences.getBackendAccessToken()
            if (!token.isNullOrBlank()) runCatching { api.logout(token) }
        } finally {
            signOutLocally()
            _busy.value = false
        }
    }

    /** Revokes this user's sessions on every device, then signs out here too. */
    suspend fun signOutEverywhere(): Boolean {
        _busy.value = true
        _error.value = null
        try {
            val token = securePreferences.getBackendAccessToken()?.takeIf { it.isNotBlank() }
                ?: return false
            try {
                api.revokeAllSessions(token)
            } catch (error: Exception) {
                return fail(friendlyMessage(error))
            }
            signOutLocally()
            return true
        } finally {
            _busy.value = false
        }
    }

    /** Drops local identity without a network call (backend sign-out, expiry). */
    fun signOutLocally() {
        securePreferences.clearBackendAccessToken()
        securePreferences.clearUserEmail()
        _userEmail.value = ""
    }

    private fun fail(message: String): Boolean {
        _error.value = message
        return false
    }

    private fun friendlyMessage(error: Exception): String = when (error) {
        is BackendApiException -> error.message ?: "Account request failed (${error.statusCode})."
        is java.io.IOException -> "Could not reach Jarvis. Check your connection and try again."
        else -> "Something went wrong. Try again."
    }
}
