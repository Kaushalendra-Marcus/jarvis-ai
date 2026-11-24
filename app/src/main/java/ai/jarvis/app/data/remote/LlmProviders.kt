package ai.jarvis.app.data.remote

import ai.jarvis.app.R

/** Transport style of a provider's chat API. */
enum class ProviderApiStyle { GEMINI, OPENAI_COMPATIBLE, ANTHROPIC }

/**
 * LLM providers selectable in Settings. Groq and DeepSeek speak the OpenAI
 * chat-completions dialect (with tool calling); Claude speaks the Anthropic
 * messages dialect; Gemini keeps its native function-calling client.
 */
data class LlmProvider(
    val id: String,
    val label: String,
    val tagline: String,
    val keyHint: String,
    val keyUrl: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<String>,
    val apiStyle: ProviderApiStyle,
    val iconRes: Int
)

val LLM_PROVIDERS = listOf(
    LlmProvider(
        id = "gemini",
        label = "Gemini",
        tagline = "Google",
        keyHint = "Gemini API key (AI Studio)",
        keyUrl = "https://aistudio.google.com/apikey",
        baseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-3.5-flash-lite",
        models = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.1-flash-lite"),
        apiStyle = ProviderApiStyle.GEMINI,
        iconRes = R.drawable.ic_model_gemini
    ),
    LlmProvider(
        id = "groq",
        label = "Groq",
        tagline = "Fast inference",
        keyHint = "Groq API key (gsk_…)",
        keyUrl = "https://console.groq.com/keys",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b"),
        apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
        iconRes = R.drawable.ic_model_groq
    ),
    LlmProvider(
        id = "deepseek",
        label = "DeepSeek",
        tagline = "DeepSeek · OpenAI API",
        keyHint = "DeepSeek API key (sk-…)",
        keyUrl = "https://platform.deepseek.com/api_keys",
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
        apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
        iconRes = R.drawable.ic_model_deepseek
    ),
    LlmProvider(
        id = "claude",
        label = "Claude",
        tagline = "Anthropic · tool use",
        keyHint = "Anthropic API key (sk-ant-…)",
        keyUrl = "https://console.anthropic.com/settings/keys",
        baseUrl = "https://api.anthropic.com",
        defaultModel = "claude-3-5-haiku-latest",
        models = listOf("claude-3-5-haiku-latest", "claude-sonnet-4-20250514", "claude-opus-4-1-20250805"),
        apiStyle = ProviderApiStyle.ANTHROPIC,
        iconRes = R.drawable.ic_model_claude
    )
)

fun providerFor(id: String?): LlmProvider =
    LLM_PROVIDERS.find { it.id == id } ?: LLM_PROVIDERS.first()
