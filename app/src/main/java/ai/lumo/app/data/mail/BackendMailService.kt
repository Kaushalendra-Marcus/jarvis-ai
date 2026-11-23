package ai.lumo.app.data.mail

import ai.lumo.app.data.remote.BackendAuthManager
import ai.lumo.app.domain.mail.MailService
import org.json.JSONObject

/** Real Gmail account, served through the Lumo backend (OAuth, no passwords on the device). */
class BackendMailService(private val auth: BackendAuthManager) : MailService {
    override fun isConnected() = auth.isConnected()
    override suspend fun search(query: String, maxResults: Int): JSONObject = auth.searchEmails(query, maxResults)
    override suspend fun read(messageId: String): JSONObject = auth.readEmail(messageId)
    override suspend fun createDraft(recipient: String, subject: String, body: String): JSONObject =
        auth.createDraft(recipient, subject, body)
    override suspend fun send(recipient: String, subject: String, body: String, idempotencyKey: String): JSONObject =
        auth.sendEmail(recipient, subject, body, idempotencyKey)
}
