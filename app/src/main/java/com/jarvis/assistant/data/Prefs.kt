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
    var braveKey: String get() = sp.getString("braveKey", "")!!; set(v) = sp.edit().putString("braveKey", v).apply()
    var termsOk: Boolean get() = sp.getBoolean("termsV2", false); set(v) = sp.edit().putBoolean("termsV2", v).apply()

    // ---- API connection ----
    var provider: String get() = sp.getString("provider", "llm7")!!; set(v) = sp.edit().putString("provider", v).apply()
    var apiBase: String get() = sp.getString("apiBase", "")!!; set(v) = sp.edit().putString("apiBase", v).apply()
    var apiKey: String get() = sp.getString("apiKey", "")!!; set(v) = sp.edit().putString("apiKey", v).apply()
    var apiModel: String get() = sp.getString("apiModel", "")!!; set(v) = sp.edit().putString("apiModel", v).apply()

    // ---- personalisation ----
    var assistantName: String get() = sp.getString("assistantName", "")!!; set(v) = sp.edit().putString("assistantName", v).apply()
    var devOk: Boolean get() = sp.getBoolean("devOk", false); set(v) = sp.edit().putBoolean("devOk", v).apply()

    fun loadList(key: String): MutableList<String> {
        val out = mutableListOf<String>()
        try { val a = JSONArray(sp.getString(key, "[]")); for (i in 0 until a.length()) out.add(a.getString(i)) } catch (ignored: Exception) {}
        return out
    }
    fun saveList(key: String, v: List<String>) { sp.edit().putString(key, JSONArray(v).toString()).apply() }

    fun loadMap(key: String): MutableMap<String, String> {
        val out = linkedMapOf<String, String>()
        try { val o = JSONObject(sp.getString(key, "{}")); o.keys().forEach { out[it] = o.getString(it) } } catch (ignored: Exception) {}
        return out
    }
    fun saveMap(key: String, v: Map<String, String>) {
        val o = JSONObject(); v.forEach { (k, x) -> o.put(k, x) }
        sp.edit().putString(key, o.toString()).apply()
    }

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
