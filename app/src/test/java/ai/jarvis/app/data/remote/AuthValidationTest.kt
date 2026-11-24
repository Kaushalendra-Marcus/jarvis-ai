package ai.jarvis.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun cleanEmail_normalizesAndRejects() {
        assertEquals("user@example.com", AuthValidation.cleanEmail("  User@Example.com  "))
        assertNull(AuthValidation.cleanEmail("not-an-email"))
        assertNull(AuthValidation.cleanEmail("user@nodot"))
        assertNull(AuthValidation.cleanEmail("user @example.com"))
        assertNull(AuthValidation.cleanEmail(""))
        assertNull(AuthValidation.cleanEmail("a@b.co\r\nBcc: evil@x.com"))
        assertNull(AuthValidation.cleanEmail("u@${"x".repeat(250)}.com"))
    }

    @Test
    fun passwordError_enforcesLength() {
        assertEquals("Password must be at least 8 characters.", AuthValidation.passwordError("short"))
        assertEquals("Password must be at least 8 characters.", AuthValidation.passwordError(""))
        assertNull(AuthValidation.passwordError("exactly-8"))
        assertNull(AuthValidation.passwordError("a-very-long-but-valid-password-123"))
        assertEquals("Password must be at most 128 characters.", AuthValidation.passwordError("x".repeat(129)))
    }
}
