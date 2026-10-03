package com.jarvis.assistant.engine

/** Decides if a LOCAL model answer is unusable ("I don't know", empty, wrong language, looping) so we can ask the cloud AI instead. */
object Quality {
    private val unsure = listOf(
        "نمی‌دونم", "نمیدونم", "نمی دونم", "نمی‌دانم", "نمیدانم", "نمی دانم", "اطلاعی ندارم", "اطلاعات کافی ندارم",
        "اطلاعاتی ندارم", "مطمئن نیستم", "نمی‌تونم کمک", "نمیتونم کمک", "متاسفانه نمی", "متأسفانه نمی", "دسترسی ندارم",
        "i don't know", "i do not know", "i'm not sure", "i am not sure", "i don't have information", "i do not have information",
        "i don't have access", "i cannot help", "i can't help", "i'm unable", "i am unable", "as an ai", "i'm sorry, but",
    )

    private fun hasFa(s: String) = s.any { it in '\u0600'..'\u06FF' }

    fun bad(question: String, answer: String, lang: String): Boolean {
        val a = answer.trim()
        if (a.isEmpty()) return true
        val low = a.lowercase()
        if (unsure.any { low.contains(it) }) return true
        // asked in Persian but answered with no Persian at all (small models drift to English)
        if (hasFa(question) && a.length > 15 && !hasFa(a)) return true
        // question is a real question but the answer is only a couple of characters
        if (question.trim().length > 25 && a.length < 6 && a.none { it.isDigit() }) return true
        // looping / repeating text
        val words = low.split(Regex("\\s+")).filter { it.length > 1 }
        if (words.size >= 12 && words.toSet().size.toFloat() / words.size < 0.35f) return true
        return false
    }
}
