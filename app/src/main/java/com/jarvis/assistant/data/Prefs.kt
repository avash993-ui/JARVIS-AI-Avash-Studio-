package com.jarvis.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Msg(val role: String, var text: String, val cloud: Boolean = false, val web: Boolean = false)
data class Convo(val id: Long, var title: String, val msgs: MutableList<Msg>)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var lang: String get() = sp.getString("lang", "fa")!!; set(v) = sp.edit().putString("lang", v).apply()
    var humor: Int get() = sp.getInt("humor", 40); set(v) = sp.edit().putInt("humor", v).apply()
    var speakTyped: Boolean get() = sp.getBoolean("speakTyped", false); set(v) = sp.edit().putBoolean("speakTyped", v).apply()
    var webOn: Boolean get() = sp.getBoolean("webOn", true); set(v) = sp.edit().putBoolean("webOn", v).apply()

    /** "off" | "ask" | "auto" : what to do with hard questions (cloud fallback) */
    var cloudMode: String get() = sp.getString("cloudMode", "ask")!!; set(v) = sp.edit().putString("cloudMode", v).apply()
    var cloudUrl: String get() = sp.getString("cloudUrl", "")!!; set(v) = sp.edit().putString("cloudUrl", v).apply()
    var cloudKey: String get() = sp.getString("cloudKey", "")!!; set(v) = sp.edit().putString("cloudKey", v).apply()
    var cloudModel: String get() = sp.getString("cloudModel", "")!!; set(v) = sp.edit().putString("cloudModel", v).apply()
    var braveKey: String get() = sp.getString("braveKey", "")!!; set(v) = sp.edit().putString("braveKey", v).apply()

    /** "hf,ollama" or "ollama,hf" */
    var sourceOrder: String get() = sp.getString("sourceOrder", "hf,ollama")!!; set(v) = sp.edit().putString("sourceOrder", v).apply()
    var licenseOk: Boolean get() = sp.getBoolean("licenseOk", false); set(v) = sp.edit().putBoolean("licenseOk", v).apply()
    var termsOk: Boolean get() = sp.getBoolean("termsV1", false); set(v) = sp.edit().putBoolean("termsV1", v).apply()
    var installed: Int get() = sp.getInt("installed", 0); set(v) = sp.edit().putInt("installed", v).apply()

    fun loadHistory(): MutableList<Convo> {
        val out = mutableListOf<Convo>()
        try {
            val arr = JSONArray(sp.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ms = mutableListOf<Msg>()
                val ma = o.getJSONArray("m")
                for (j in 0 until ma.length()) {
                    val m = ma.getJSONObject(j)
                    ms.add(Msg(m.getString("r"), m.getString("t"), m.optBoolean("c"), m.optBoolean("w")))
                }
                out.add(Convo(o.getLong("id"), o.getString("title"), ms))
            }
        } catch (ignored: Exception) {}
        return out
    }

    fun saveHistory(list: List<Convo>) {
        val arr = JSONArray()
        list.takeLast(40).forEach { c ->
            val ma = JSONArray()
            c.msgs.forEach { ma.put(JSONObject().put("r", it.role).put("t", it.text).put("c", it.cloud).put("w", it.web)) }
            arr.put(JSONObject().put("id", c.id).put("title", c.title).put("m", ma))
        }
        sp.edit().putString("history", arr.toString()).apply()
    }
}
