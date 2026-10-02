package com.jarvis.assistant.engine

object Persona {
    /** humor 0..100 -> prompt. Original persona, inspired by a calm butler-style AI assistant. */
    fun system(lang: String, humor: Int): String {
        val tone = when {
            humor <= 5 -> "Never joke. Be strictly professional and precise."
            humor <= 33 -> "Be professional. Allow a rare, very dry remark."
            humor <= 66 -> "Be polite and calm with light, tasteful wit now and then."
            else -> "Be polite but openly witty: frequent dry humor and gentle sarcasm, never rude."
        }
        val language = if (lang == "fa")
            "Always answer in Persian (Farsi), natural spoken style, unless the user writes in another language."
        else "Answer in the user's language (default English)."
        return "You are JARVIS, a personal AI assistant that runs privately on the user's phone. " +
            "$tone $language Keep answers short and clear (voice friendly): no markdown, no emojis, no long lists. " +
            "Always give your best direct answer to what the user asks. Never answer with only \"I don't know\"; if you are unsure, still give your best attempt and add a short note about the uncertainty."
    }

    private val hardWords = listOf(
        "prove", "algorithm", "code", "debug", "analyze", "analyse", "derive", "essay", "legal", "diagnos",
        "اثبات", "الگوریتم", "کد", "برنامه بنویس", "تحلیل", "مقاله", "حقوقی", "تشخیص", "محاسبه کن", "استدلال"
    )
    private val webWords = listOf(
        "compare", " vs ", "latest", "today", "news", "price", "who is the", "current", "weather",
        "مقایسه", "آخرین", "امروز", "خبر", "قیمت", "الان", "جدیدترین", "سرچ", "جستجو"
    )

    fun isHard(text: String, level: Int): Boolean {
        val t = text.lowercase()
        val kw = hardWords.any { t.contains(it) }
        return (kw && level <= 4) || t.length > 500
    }

    fun needsWeb(text: String): Boolean {
        val t = " " + text.lowercase() + " "
        return webWords.any { t.contains(it) }
    }
}
