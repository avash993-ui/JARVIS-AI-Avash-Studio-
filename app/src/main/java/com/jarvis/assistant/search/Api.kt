package com.jarvis.assistant.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal OpenAI-compatible client: chat, connection test and live model list. Works with any provider. */
object Api {
    data class Cfg(val base: String, val key: String, val model: String)

    private fun open(url: String, method: String, key: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("Accept", "application/json")
        if (key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $key")
        return c
    }

    private fun explain(code: Int, txt: String): String {
        val msg = try {
            val j = JSONObject(txt); val e = j.opt("error")
            if (e is JSONObject) e.optString("message") else (e?.toString() ?: j.optString("message"))
        } catch (t: Throwable) { txt.take(120) }
        val hint = when (code) { 401, 403 -> " (invalid key / کلید نامعتبر)"; 404 -> " (wrong URL or model / آدرس یا مدل اشتباه)"; 429 -> " (rate limit / سقف درخواست)"; else -> "" }
        return "HTTP $code$hint" + (if (!msg.isNullOrBlank()) ": " + msg.take(140) else "")
    }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        val st = if (code in 200..299) c.inputStream else c.errorStream
        val txt = st?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IllegalStateException(explain(code, txt))
        return txt
    }

    suspend fun chat(cfg: Cfg, messages: List<Pair<String, String>>, maxTokens: Int = 380): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            messages.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
            val b = JSONObject().put("model", cfg.model).put("messages", arr).put("max_tokens", maxTokens).put("temperature", 0.6)
            val c = open(cfg.base.trimEnd('/') + "/chat/completions", "POST", cfg.key)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(b.toString().toByteArray()) }
            val j = JSONObject(read(c))
            var t = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
            t = t.replace(Regex("(?s)<think>.*?</think>"), "").trim()
            if (t.isEmpty()) throw IllegalStateException("empty answer")
            t
        }
    }

    suspend fun analyzeParts(cfg: Cfg, text: String, imageDataUrls: List<String>, maxTokens: Int = 900): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val content = JSONArray()
            content.put(JSONObject().put("type", "text").put("text", text))
            imageDataUrls.take(6).forEach { content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", it))) }
            val arr = JSONArray().put(JSONObject().put("role", "user").put("content", content))
            val b = JSONObject().put("model", cfg.model).put("messages", arr).put("max_tokens", maxTokens)
            val c = open(cfg.base.trimEnd('/') + "/chat/completions", "POST", cfg.key)
            c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(b.toString().toByteArray()) }
            val j = JSONObject(read(c)); val t = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "").trim()
            if (t.isBlank()) throw IllegalStateException("empty answer")
            t
        }
    }

    suspend fun generateImage(cfg: Cfg, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val b = JSONObject().put("model", cfg.model).put("prompt", prompt).put("n", 1).put("size", "1024x1024")
            val c = open(cfg.base.trimEnd('/') + "/images/generations", "POST", cfg.key)
            c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(b.toString().toByteArray()) }
            val j = JSONObject(read(c)); val d = j.optJSONArray("data") ?: throw IllegalStateException("no image data")
            val item = d.getJSONObject(0)
            item.optString("url").ifBlank { item.optString("b64_json").let { if (it.isBlank()) "" else "data:image/png;base64,$it" } }
        }
    }

    suspend fun test(cfg: Cfg): Result<String> = chat(cfg, listOf("user" to "Reply with the single word: OK"), 12)

    /** Live list of model ids from {base}/models (can be 50-300+ entries depending on the service). */
    suspend fun listModels(cfg: Cfg): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val c = open(cfg.base.trimEnd('/') + "/models", "GET", cfg.key)
            val txt = read(c).trim()
            val arr: JSONArray = if (txt.startsWith("[")) JSONArray(txt) else {
                val o = JSONObject(txt); o.optJSONArray("data") ?: o.optJSONArray("models") ?: JSONArray()
            }
            val out = sortedSetOf<String>()
            for (i in 0 until arr.length()) {
                val e = arr.get(i)
                val id = if (e is JSONObject) e.optString("id", e.optString("name", "")) else e.toString()
                if (id.isNotBlank()) out.add(id.removePrefix("models/"))
            }
            out.toList()
        }
    }
}
