package com.jarvis.assistant.voice

import com.jarvis.assistant.data.Contacts

/** Shared state + name matching for the "say the name" feature. */
object Wake {
    /** true while the app itself is using the mic / speaking, so the background listener stays quiet. */
    @Volatile private var busyFlag = false
    @Volatile private var busyAt = 0L
    /** Expires by itself after 40s, so a missed "finished" callback can never silence the name listener forever. */
    var busy: Boolean
        get() = busyFlag && System.currentTimeMillis() - busyAt < 40_000
        set(v) { busyFlag = v; if (v) busyAt = System.currentTimeMillis() }
    private val variants = listOf("jarvis", "جارویس", "جاروس", "جارویز", "جاروویس", "جاریس", "جرویس")

    fun matches(text: String, name: String): Boolean {
        val t = Contacts.norm(text)
        val n = Contacts.norm(name)
        val isDefault = n.isEmpty() || n == "jarvis" || n == "جارویس"
        if (n.length >= 3 && t.contains(n)) return true
        return isDefault && variants.any { t.contains(Contacts.norm(it)) }
    }
}
