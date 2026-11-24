package ai.jarvis.app.data.remote

/** Pure signup/login input rules. JVM-testable: no Android dependencies. */
object AuthValidation {
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** Normalized lowercase email, or null when invalid. */
    fun cleanEmail(raw: String): String? {
        val value = raw.trim().lowercase()
        if (value.isEmpty() || value.length > 254) return null
        if ("\r" in value || "\n" in value) return null
        return if (EMAIL.matches(value)) value else null
    }

    /** Null when the password is acceptable, otherwise a human-readable reason. */
    fun passwordError(password: String): String? = when {
        password.length < 8 -> "Password must be at least 8 characters."
        password.length > 128 -> "Password must be at most 128 characters."
        else -> null
    }
}
