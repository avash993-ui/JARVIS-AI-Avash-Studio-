package com.jarvis.assistant.data

/** An OpenAI-compatible API service. Model names change over time: users can fetch the live list in Setup. */
data class Provider(
    val id: String, val name: String, val base: String, val models: List<String>,
    val site: String, val needsKey: Boolean = true,
)

object Providers {
    val all = listOf(
        Provider("llm7", "LLM7", "https://api.llm7.io/v1", listOf("fast", "default", "pro"), "https://dash.llm7.io", needsKey = false),
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
            listOf("openrouter/auto", "meta-llama/llama-3.3-70b-instruct:free", "deepseek/deepseek-chat-v3-0324:free"), "https://openrouter.ai/keys"),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner"), "https://platform.deepseek.com/api_keys"),
        Provider("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
            listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"), "https://aistudio.google.com/apikey"),
        Provider("groq", "Groq", "https://api.groq.com/openai/v1",
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-20b"), "https://console.groq.com/keys"),
        Provider("custom", "Custom / دلخواه", "", emptyList(), ""),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
