package com.jarvis.assistant.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class Contact(val name: String, val number: String)

object Contacts {
    fun hasRead(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun hasCall(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** Lower-case, unify Persian/Arabic letters and digits, drop punctuation and diacritics. */
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            sb.append(
                when (ch) {
                    'ي' -> 'ی'
                    'ك' -> 'ک'
                    '\u200c' -> ' '
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u064B'..'\u065F', '\u0640' -> ""
                    else -> if (ch.isLetterOrDigit()) ch else ' '
                }
            )
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun load(ctx: Context): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        val P = ContactsContract.CommonDataKinds.Phone
        ctx.contentResolver.query(P.CONTENT_URI, arrayOf(P.DISPLAY_NAME, P.NUMBER), null, null, P.DISPLAY_NAME + " COLLATE LOCALIZED ASC")?.use { c ->
            val ni = c.getColumnIndexOrThrow(P.DISPLAY_NAME)
            val pi = c.getColumnIndexOrThrow(P.NUMBER)
            while (c.moveToNext()) {
                val n = c.getString(ni) ?: continue
                val p = c.getString(pi) ?: continue
                if (seen.add(n + "|" + p.filter { it.isDigit() }.takeLast(9))) out.add(Contact(n, p))
            }
        }
        return out
    }

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    private fun score(q: String, name: String): Int {
        val a = norm(name)
        if (q.isEmpty() || a.isEmpty()) return 0
        if (a == q) return 100
        if (a.startsWith(q)) return 80
        if (q.split(" ").all { a.contains(it) }) return 70
        if (a.contains(q)) return 60
        if (q.length >= 3 && a.split(" ").any { lev(it, q) <= 1 }) return 45
        return 0
    }

    /** Best-matching contacts (distinct names). Demo = only exact / starts-with, developer = fuzzy. */
    fun find(list: List<Contact>, query: String, fuzzy: Boolean): List<Contact> {
        val q = norm(query)
        val thr = if (fuzzy) 45 else 80
        val scored = list.map { it to score(q, it.name) }
        val best = scored.maxOfOrNull { it.second } ?: 0
        if (best < thr) return emptyList()
        return scored.filter { it.second == best }.map { it.first }.distinctBy { norm(it.name) }
    }
}
