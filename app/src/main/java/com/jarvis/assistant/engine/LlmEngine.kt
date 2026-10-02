package com.jarvis.assistant.engine

import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.CompletableDeferred

/**
 * The only file that touches the llama.cpp wrapper (Llamatik 0.12.x).
 * If you switch library/version, only this file changes.
 */
object LlmEngine {
    @Volatile private var loadedPath: String? = null
    @Volatile private var cancelled = false

    fun load(path: String): Boolean = try {
        if (loadedPath != path) {
            LlamaBridge.initGenerateModel(path)
            loadedPath = path
        }
        true
    } catch (ignored: Throwable) { false }

    val isLoaded get() = loadedPath != null

    fun stop() { cancelled = true }

    /** Streams tokens through [onDelta]. Returns null on success or an error text. Call from a background dispatcher. */
    suspend fun generate(system: String, context: String, user: String, onDelta: (String) -> Unit): String? {
        cancelled = false
        val done = CompletableDeferred<String?>()
        try {
            LlamaBridge.generateStreamWithContext(
                system = system,
                context = context,
                user = user,
                onDelta = { token -> if (!cancelled) onDelta(token) },
                onDone = { done.complete(null) },
                onError = { e -> done.complete(e.toString()) },
            )
        } catch (t: Throwable) {
            done.complete(t.message ?: "error")
        }
        return done.await()
    }

    fun unload() {
        try { LlamaBridge.shutdown() } catch (ignored: Throwable) {}
        loadedPath = null
    }
}
