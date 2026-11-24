package ai.jarvis.app.data.mail

import ai.jarvis.app.core.security.SecurePreferences
import ai.jarvis.app.data.remote.BackendAuthManager

/**
 * Switches between the two mailbox sources:
 *  - OWN: the user's own Gmail (Google sign-in),
 *  - DEMO: the offline sample mailbox.
 * Switching to DEMO keeps the sign-in so coming back is instant.
 */
class MailSourceManager(
    private val preferences: SecurePreferences,
    private val auth: BackendAuthManager
) {
    val source = preferences.mailSource

    suspend fun select(target: String) {
        preferences.setMailSource(target)
        if (target == SecurePreferences.SOURCE_OWN) auth.refreshConnectionState()
    }
}
