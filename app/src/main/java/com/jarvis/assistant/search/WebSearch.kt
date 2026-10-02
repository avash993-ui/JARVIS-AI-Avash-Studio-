package com.jarvis.assistant.search

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class Hit(val title: String, val snippet: String, val url: String)

object WebSearch {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) JarvisApp/1.0"

    /** Brave API when a key is set, otherwise the keyless DuckDuckGo HTML page. */
    suspend fun search(q: String, braveKey: String): List<Hit> = withContext(Dispatchers.IO) {
        try {
            if (braveKey.isNotBlank()) brave(q, braveKey) else ddg(q)
        } catch (ignored: Exception) { emptyList() }
    }

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", UA)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun brave(q: String, key: String): List<Hit> {
        val body = get(
            "https://api.search.brave.com/res/v1/web/search?count=5&q=" + URLEncoder.encode(q, "UTF-8"),
            mapOf("X-Subscription-Token" to key, "Accept" to "application/json")
        )
        val arr = JSONObject(body).getJSONObject("web").getJSONArray("results")
        return (0 until minOf(5, arr.length())).map {
            val o = arr.getJSONObject(it)
            Hit(clean(o.optString("title")), clean(o.optString("description")), o.optString("url"))
        }
    }

    private fun ddg(q: String): List<Hit> {
        val html = get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
        val link = Regex("""class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snip = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val links = link.findAll(html).toList()
        val snips = snip.findAll(html).toList()
        return links.take(5).mapIndexed { i, m ->
            var href = m.groupValues[1]
            val uddg = Regex("""uddg=([^&]+)""").find(href)
            if (uddg != null) href = URLDecoder.decode(uddg.groupValues[1], "UTF-8")
            Hit(clean(m.groupValues[2]), clean(snips.getOrNull(i)?.groupValues?.get(1) ?: ""), href)
        }
    }

    private fun clean(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT).toString().trim()

    fun asContext(hits: List<Hit>): String =
        hits.mapIndexed { i, h -> "[${i + 1}] ${h.title}: ${h.snippet}" }.joinToString("\n")
}
