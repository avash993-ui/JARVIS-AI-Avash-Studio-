package com.jarvis.assistant.data

/** One Jarvis level = one Gemma model. Sizes are NOT hard-coded: they are read live from the source. */
data class Level(
    val n: Int,
    val faName: String,
    val enName: String,
    val base: String,
    val minRamGb: Int,
    val ollama: String,      // "name:tag" on registry.ollama.ai (library namespace)
    val hfRepo: String,      // public GGUF repo (no login needed)
    val hfFile: String,
    val color: Long,
    val slow: Boolean,
)

enum class SourceKind { HF, OLLAMA }

data class Resolved(
    val kind: SourceKind,
    val url: String,
    val size: Long,
)

object Catalog {
    val levels = listOf(
        Level(1, "ریشتر", "Richter", "Gemma 3 · 270M", 3, "gemma3:270m",
            "unsloth/gemma-3-270m-it-GGUF", "gemma-3-270m-it-Q4_K_M.gguf", 0xFF6EA8FE, false),
        Level(2, "بهینه", "Optimized", "Gemma 3 · 1B", 4, "gemma3:1b",
            "ggml-org/gemma-3-1b-it-GGUF", "gemma-3-1b-it-Q4_K_M.gguf", 0xFF3D9BFF, false),
        Level(3, "هوشمند", "Smart", "Gemma 3n · E2B", 6, "gemma3n:e2b",
            "unsloth/gemma-3n-E2B-it-GGUF", "gemma-3n-E2B-it-Q4_K_M.gguf", 0xFF00E5FF, false),
        Level(4, "پیشرفته", "Advanced", "Gemma 3 · 4B", 8, "gemma3:4b",
            "ggml-org/gemma-3-4b-it-GGUF", "gemma-3-4b-it-Q4_K_M.gguf", 0xFF00E5A8, false),
        Level(5, "پرو", "Pro", "Gemma 3 · 12B", 12, "gemma3:12b",
            "ggml-org/gemma-3-12b-it-GGUF", "gemma-3-12b-it-Q4_K_M.gguf", 0xFF9B6BFF, true),
        Level(6, "اولتیمیت", "Ultimate", "Gemma 3 · 27B", 16, "gemma3:27b",
            "ggml-org/gemma-3-27b-it-GGUF", "gemma-3-27b-it-Q4_K_M.gguf", 0xFFFFC857, true),
    )

    fun byN(n: Int) = levels.first { it.n == n }
}
