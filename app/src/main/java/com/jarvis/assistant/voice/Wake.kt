package com.jarvis.assistant.voice

import com.jarvis.assistant.data.Contacts

/** Shared state + name matching for the "say the name" feature. */
object Wake {
    /** true while the app itself is using the mic / speaking, so the background listener stays quiet. */
    @Volatile var busy = false
    private val variants = listOf("jarvis", "جارویس", "جاروس", "جارویز", "جاروویس", "جاریس", "جرویس")

    fun matches(text: String, name: String): Boolean {
        val t = Contacts.norm(text)
        val n = Contacts.norm(name)
        val isDefault = n.isEmpty() || n == "jarvis" || n == "جارویس"
        if (n.length >= 3 && t.contains(n)) return true
        return isDefault && variants.any { t.contains(Contacts.norm(it)) }
    }
}
