package com.jarvis.assistant.voice

import com.jarvis.assistant.data.Contacts

/** Wake phrase matching for the always-listening service.
 *
 * The activation phrase is intentionally strict: the user must say
 * "هی جارویس". Saying only "جارویس" must never activate Wake Mode.
 */
object Wake {
    @Volatile private var busyFlag = false
    @Volatile private var busyAt = 0L

    var busy: Boolean
        get() = busyFlag && System.currentTimeMillis() - busyAt < 40_000
        set(v) {
            busyFlag = v
            if (v) busyAt = System.currentTimeMillis()
        }

    private const val HEY = "هی"
    private const val JARVIS = "جارویس"
    private const val EN_HEY = "hey"
    private const val EN_JARVIS = "jarvis"

    /**
     * Strict Persian wake phrase.
     *
     * Accepted examples:
     *  - "هی جارویس"
     *  - "هی، جارویس" (punctuation is normalized)
     *  - "هی جارویس ساعت چنده" (wake phrase followed by a request)
     *
     * Rejected examples:
     *  - "جارویس"
     *  - "سلام جارویس"
     *  - "هی جاروس"
     */
    fun matches(text: String): Boolean {
        val t = Contacts.norm(text)
        if (t.isBlank()) return false
        val tokens = t.split(' ').filter { it.isNotBlank() }
        if (tokens.size < 2) return false
        val fa = tokens[0] == HEY && tokens[1] == JARVIS
        val en = tokens[0] == EN_HEY && tokens[1] == EN_JARVIS
        return fa || en
    }
}
