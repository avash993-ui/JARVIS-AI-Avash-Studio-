package com.jarvis.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Public sources only, no login or token:
 *  - Hugging Face public GGUF repos  (standard llama.cpp-compatible files)
 *  - registry.ollama.ai              (manifest -> GGUF blob)
 */
class Downloader(private val dir: File) {
    private val ua = "Jarvis-Android/1.0"

    fun fileFor(level: Level) = File(dir, "jarvis_${level.n}.gguf")

    private fun open(url: String, method: String = "GET", headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", ua)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c
    }

    private suspend fun resolveHf(l: Level): Resolved? = withContext(Dispatchers.IO) {
        try {
            val url = "https://huggingface.co/${l.hfRepo}/resolve/main/${l.hfFile}"
            val c = open(url, "HEAD")
            if (c.responseCode !in 200..299) return@withContext null
            val size = c.contentLengthLong
            c.disconnect()
            Resolved(SourceKind.HF, url, size)
        } catch (ignored: Exception) { null }
    }

    private suspend fun resolveOllama(l: Level): Resolved? = withContext(Dispatchers.IO) {
        try {
            val (name, tag) = l.ollama.split(":")
            val c = open(
                "https://registry.ollama.ai/v2/library/$name/manifests/$tag", "GET",
                mapOf("Accept" to "application/vnd.docker.distribution.manifest.v2+json")
            )
            if (c.responseCode !in 200..299) return@withContext null
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            val layers = json.getJSONArray("layers")
            for (i in 0 until layers.length()) {
                val ly = layers.getJSONObject(i)
                if (ly.getString("mediaType") == "application/vnd.ollama.image.model") {
                    return@withContext Resolved(
                        SourceKind.OLLAMA,
                        "https://registry.ollama.ai/v2/library/$name/blobs/${ly.getString("digest")}",
                        ly.getLong("size")
                    )
                }
            }
            null
        } catch (ignored: Exception) { null }
    }

    /** Candidates in the preferred order; unreachable sources are skipped. */
    suspend fun resolve(l: Level, order: List<SourceKind>): List<Resolved> =
        order.mapNotNull { if (it == SourceKind.HF) resolveHf(l) else resolveOllama(l) }

    /** Resumable download. Returns the final file. Throws on failure; cancel the coroutine to stop (partial file is kept). */
    suspend fun download(l: Level, r: Resolved, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val part = File(dir, "jarvis_${l.n}.part")
        val dest = fileFor(l)
        var have = if (part.exists()) part.length() else 0L
        if (r.size > 0 && have > r.size) { part.delete(); have = 0 }

        val headers = if (have > 0) mapOf("Range" to "bytes=$have-") else emptyMap()
        val c = open(r.url, "GET", headers)
        val code = c.responseCode
        if (code == 200 && have > 0) { part.delete(); have = 0 }   // server ignored Range
        if (code !in 200..299) throw IllegalStateException("HTTP $code")

        RandomAccessFile(part, "rw").use { raf ->
            raf.seek(have)
            c.inputStream.use { input ->
                val buf = ByteArray(256 * 1024)
                var done = have
                var lastUi = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    done += n
                    val now = System.nanoTime()
                    if (now - lastUi > 150_000_000L && r.size > 0) { lastUi = now; onProgress(done.toFloat() / r.size) }
                }
            }
        }
        c.disconnect()
        if (r.size > 0 && part.length() != r.size) throw IllegalStateException("incomplete")
        dest.delete()
        part.renameTo(dest)
        onProgress(1f)
        dest
    }

    fun delete(l: Level) { fileFor(l).delete(); File(dir, "jarvis_${l.n}.part").delete() }
}
