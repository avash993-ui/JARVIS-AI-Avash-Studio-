package com.jarvis.assistant.engine

import com.jarvis.assistant.data.Contacts

/** Tiny on-device command understanding (works without internet): calling, contacts, search, memory. Everything else goes to the AI. */
sealed class Cmd {
    data class Call(val name: String) : Cmd()
    object ShowContacts : Cmd()
    data class Search(val q: String) : Cmd()
    data class Remember(val text: String) : Cmd()
    object Forget : Cmd()
    object None : Cmd()
}

object Intents {
    private val callFa1 = Regex("^(?:لطفا |خب |ببین )*(?:زنگ بزن|تماس بگیر|تلفن بزن|زنگ بزنید)\\s+(?:به |با )?(.+)$")
    private val callFa2 = Regex("^(?:لطفا |خب )*(?:به |با )?(.+?)\\s+(?:زنگ بزن|تماس بگیر|تلفن بزن|زنگ بزنید|زنگ بزنی)(?:\\s+لطفا)?$")
    private val callEn1 = Regex("^(?:please )?(?:call|phone|dial|ring)(?: up)?\\s+(.+)$")
    private val callEn2 = Regex("^(?:please )?give\\s+(.+?)\\s+a (?:call|ring)$")
    private val searchA = Regex("^(?:سرچ کن|جستجو کن|جست و جو کن|گوگل کن|search for|search|google)\\s+(.+)$")
    private val searchB = Regex("^(.+?)\\s+(?:رو |را )?(?:سرچ کن|جستجو کن|گوگل کن)$")
    private val remember = Regex("^(?:یادت باشه|یادت باشد|به یاد داشته باش|یاد بگیر|remember that|remember)\\s+(.+)$")
    private val tail = Regex("\\s+(?:رو|را|please|لطفا|الان|الآن)$")
    private val contactWords = listOf("مخاطبین", "مخاطب ها", "مخاطبام", "دفتر تلفن", "contacts", "phonebook", "phone book", "contact list")
    private val forgetWords = listOf("حافظه رو پاک کن", "همه چیز رو فراموش کن", "حافظه ات رو پاک کن", "forget everything", "clear memory", "clear your memory")

    private fun clean(s: String): String {
        var t = s.trim().replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
        t = t.trim('.', '!', '؟', '?', '،', ',', ' ')
        return t.replace(Regex("\\s+"), " ")
    }

    private fun name(s: String): String {
        var n = s.trim()
        repeat(2) { n = n.replace(tail, "").trim() }
        return n
    }

    fun parse(text: String): Cmd {
        val t = clean(text); val low = t.lowercase()
        if (t.isEmpty() || t.length > 160) return Cmd.None
        if (forgetWords.any { low.contains(it) }) return Cmd.Forget
        remember.find(low)?.let { return Cmd.Remember(it.groupValues[1].trim()) }
        val callVerb = low.contains("زنگ") || low.contains("تماس بگیر") || low.contains("تلفن بزن") || Regex("^(?:please )?(?:call|phone|dial|ring|give)\\b").containsMatchIn(low)
        if (callVerb) {
            for (r in listOf(callFa1, callFa2, callEn1, callEn2)) {
                val m = r.find(low) ?: continue
                val n = name(m.groupValues[1])
                if (n.isNotEmpty() && n.length <= 30 && !n.contains('?') && Contacts.norm(n).isNotEmpty()) return Cmd.Call(n)
            }
        }
        if (!callVerb && low.length <= 45 && contactWords.any { low.contains(it) }) return Cmd.ShowContacts
        searchA.find(low)?.let { return Cmd.Search(it.groupValues[1].trim()) }
        searchB.find(low)?.let { return Cmd.Search(it.groupValues[1].trim()) }
        return Cmd.None
    }
}
