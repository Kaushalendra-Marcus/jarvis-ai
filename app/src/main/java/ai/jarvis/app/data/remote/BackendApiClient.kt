package ai.jarvis.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackendApiException(val statusCode: Int, message: String) : IOException(message)

class BackendApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        // A free-tier host can take 30-50 s to wake from sleep, so allow for a cold start.
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    /** Fire-and-forget request that wakes a sleeping backend before the user needs it. */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("$baseUrl/health").get().build()).execute().close()
        }
    }

    /** Ends only this device's session, leaving the Google account connected on the server. */
    suspend fun revokeSession(accessToken: String): Unit = withContext(Dispatchers.IO) {
        execute(
            authenticatedRequest("$baseUrl/api/v1/auth/session", accessToken).delete().build(),
            expectJson = false
        )
    }

    suspend fun exchangeAuthorizationCode(code: String, linkToken: String? = null): BackendSession =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("code", code).apply {
                if (!linkToken.isNullOrBlank()) put("linkToken", linkToken)
            }.toString()
            val response = execute(
                Request.Builder()
                    .url("$baseUrl/api/v1/auth/exchange")
                    .post(payload.toRequestBody(JSON))
                    .build()
            )
            BackendSession(
                accessToken = response.getString("accessToken"),
                expiresAt = response.getString("expiresAt")
            )
        }

    suspend fun sendEmail(
        accessToken: String,
        recipient: String,
        subject: String,
        body: String,
        idempotencyKey: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("recipient", recipient)
            .put("subject", subject)
            .put("body", body)
            .toString()
        val request = authenticatedRequest("$baseUrl/api/v1/emails/send", accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .post(payload.toRequestBody(JSON))
            .build()
        execute(request)
    }

    suspend fun searchEmails(accessToken: String, query: String, maxResults: Int): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/v1/gmail/messages".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("max", maxResults.coerceIn(1, 15).toString())
                .build()
            execute(Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build())
        }

    suspend fun readEmail(accessToken: String, messageId: String): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/gmail/messages".toHttpUrl().newBuilder()
            .addPathSegment(messageId)
            .build()
        execute(Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build())
    }

    suspend fun createDraft(
        accessToken: String,
        recipient: String,
        subject: String,
        body: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("recipient", recipient)
            .put("subject", subject)
            .put("body", body)
            .toString()
        execute(
            authenticatedRequest("$baseUrl/api/v1/gmail/drafts", accessToken)
                .post(payload.toRequestBody(JSON))
                .build()
        )
    }

    /** Gmail address behind a session, or null for user-only (no Google yet) sessions. */
    suspend fun getAccount(accessToken: String): String? = withContext(Dispatchers.IO) {
        val response = execute(authenticatedRequest("$baseUrl/api/v1/auth/me", accessToken).get().build())
        response.optString("email", "").takeIf { it.isNotBlank() }
    }

    /** App-level accounts: signup, login, profile, logout. Sessions unify user + Gmail. */
    suspend fun signUp(email: String, password: String): UserSession = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email).put("password", password).toString()
        val response = execute(
            Request.Builder().url("$baseUrl/api/v1/users/signup").post(payload.toRequestBody(JSON)).build()
        )
        UserSession(
            email = response.getString("email"),
            accessToken = response.getString("accessToken"),
            expiresAt = response.getString("expiresAt")
        )
    }

    suspend fun login(email: String, password: String): UserSession = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email).put("password", password).toString()
        val response = execute(
            Request.Builder().url("$baseUrl/api/v1/users/login").post(payload.toRequestBody(JSON)).build()
        )
        UserSession(
            email = response.getString("email"),
            accessToken = response.getString("accessToken"),
            expiresAt = response.getString("expiresAt")
        )
    }

    /** App account email behind a session, or null for guest (Google-only) sessions. */
    suspend fun getUserProfile(accessToken: String): String? = withContext(Dispatchers.IO) {
        val response = execute(authenticatedRequest("$baseUrl/api/v1/users/me", accessToken).get().build())
        response.optJSONObject("user")?.optString("email", "")?.takeIf { it.isNotBlank() }
    }

    /** Logs out this device. Server data (Gmail grant, memories) stays intact. */
    suspend fun logout(accessToken: String): Unit = withContext(Dispatchers.IO) {
        execute(
            authenticatedRequest("$baseUrl/api/v1/users/logout", accessToken)
                .post(ByteArray(0).toRequestBody(null))
                .build(),
            expectJson = false
        )
    }

    suspend fun refreshSession(accessToken: String): BackendSession = withContext(Dispatchers.IO) {
        val response = execute(
            authenticatedRequest("$baseUrl/api/v1/auth/session/refresh", accessToken)
                .post(ByteArray(0).toRequestBody(null))
                .build()
        )
        BackendSession(
            accessToken = response.getString("accessToken"),
            expiresAt = response.getString("expiresAt")
        )
    }

    suspend fun disconnect(accessToken: String): Unit = withContext(Dispatchers.IO) {
        execute(
            authenticatedRequest("$baseUrl/api/v1/auth/google", accessToken)
                .delete()
                .build(),
            expectJson = false
        )
    }

    private fun authenticatedRequest(url: String, accessToken: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken")

    private fun execute(request: Request, expectJson: Boolean = true): JSONObject {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = runCatching {
                    response.body?.string()?.let { JSONObject(it).optString("error") }
                }.getOrNull().orEmpty()
                throw BackendApiException(response.code, message.ifBlank { "Jarvis backend request failed (${response.code})." })
            }
            if (!expectJson || response.code == 204) return JSONObject()
            val body = response.body?.string()
                ?: throw IOException("Jarvis backend returned an empty response.")
            return JSONObject(body)
        }
    }

    data class BackendSession(val accessToken: String, val expiresAt: String)

    data class UserSession(val email: String, val accessToken: String, val expiresAt: String)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
