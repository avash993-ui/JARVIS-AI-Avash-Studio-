package com.jarvis.assistant.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud fallback for HARD questions only. Prompts leave the phone, so the app asks first (default).
 * Order: your own OpenAI-compatible endpoint (Gemini / OpenRouter / ...) if set,
 * otherwise the free keyless Pollinations text endpoints.
 * These free endpoints change often; if one stops working, add your own URL+key in Settings.
 */
object CloudAi {
    private data class Provider(val url: String, val key: String, val model: String)

    suspend fun ask(system: String, user: String, customUrl: String, customKey: String, customModel: String): Result<String> =
        withContext(Dispatchers.IO) {
            val providers = mutableListOf<Provider>()
            if (customUrl.isNotBlank()) providers.add(Provider(customUrl, customKey, customModel.ifBlank { "gpt-4o-mini" }))
            providers.add(Provider("https://gen.pollinations.ai/v1/chat/completions", "", "openai"))
            providers.add(Provider("https://text.pollinations.ai/openai", "", "openai"))
            var last: Throwable = IllegalStateException("no provider")
            for (p in providers) {
                try { return@withContext Result.success(call(p, system, user)) } catch (t: Throwable) { last = t }
            }
            Result.failure(last)
        }

    private fun call(p: Provider, system: String, user: String): String {
        val body = JSONObject()
            .put("model", p.model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        val c = URL(p.url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (p.key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer ${p.key}")
        c.outputStream.use { it.write(body.toByteArray()) }
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        val resp = c.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
}
