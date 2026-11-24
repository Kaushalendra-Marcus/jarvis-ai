package ai.jarvis.app.service.voice

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Speech-to-text without Google recognition services.
 *
 * - Groq (or any OpenAI-compatible key): Whisper `audio/transcriptions`.
 * - Gemini key: native audio input on `generateContent` ("transcribe exactly").
 *
 * The caller records audio (MediaRecorder AAC) and hands the file over;
 * this only uploads + parses. Temp files are deleted after the attempt.
 */
object WhisperTranscriber {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    sealed class Target {
        data class OpenAiCompatible(val baseUrl: String, val apiKey: String, val model: String) : Target()
        data class Gemini(val apiKey: String, val model: String) : Target()
    }

    suspend fun transcribe(audio: File, target: Target): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val text = when (target) {
                is Target.OpenAiCompatible -> whisper(audio, target)
                is Target.Gemini -> geminiTranscribe(audio, target)
            }
            text.trim().takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Empty transcription")
        }.also {
            runCatching { audio.delete() }
        }
    }

    private fun whisper(audio: File, target: Target.OpenAiCompatible): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", audio.name,
                audio.asRequestBody("audio/mp4".toMediaType())
            )
            .addFormDataPart("model", target.model)
            .addFormDataPart("response_format", "json")
            .build()
        val request = Request.Builder()
            .url("${target.baseUrl.trimEnd('/')}/audio/transcriptions")
            .header("Authorization", "Bearer ${target.apiKey}")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code !in 200..299) {
                throw IllegalStateException("Transcription failed (HTTP ${response.code})")
            }
            return JSONObject(raw).optString("text", "").trim()
                .takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Empty transcription")
        }
    }

    private fun geminiTranscribe(audio: File, target: Target.Gemini): String {
        val bytes = audio.readBytes()
        if (bytes.size > 18 * 1024 * 1024) {
            throw IllegalStateException("Recording too long — keep it under a minute")
        }
        val body = JSONObject()
            .put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray()
                    .put(JSONObject().put("text", "Transcribe this audio exactly, in its original language. Output only the transcription, no commentary."))
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "audio/mp4")
                        .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
                )
            ))
            .put("generationConfig", JSONObject().put("temperature", 0.0).put("maxOutputTokens", 512))
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${target.model}:generateContent")
            .header("x-goog-api-key", target.apiKey)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code !in 200..299) {
                throw IllegalStateException("Transcription failed (HTTP ${response.code})")
            }
            val parts = JSONObject(raw)
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Empty transcription")
            return buildString {
                for (i in 0 until parts.length()) {
                    append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }.trim().takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Empty transcription")
        }
    }
}
