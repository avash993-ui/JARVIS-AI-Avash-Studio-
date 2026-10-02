package com.jarvis.assistant.engine

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings

/**
 * Lets the model control the phone. The model (taught by examples in Persona.kt) ends its reply with
 *   ACTION: name | argument
 * We hide that line from the user and run it here with normal Android intents.
 */
object Actions {
    private const val MARKER = "ACTION:"
    private val re = Regex("""ACTION:\s*([A-Za-z_]+)\s*(?:\|\s*([^\n]*))?""")

    /** Text without the ACTION line (safe to call on partial streaming text). */
    fun strip(t: String): String {
        val i = t.indexOf(MARKER)
        return if (i < 0) t else t.substring(0, i)
    }

    /** Runs the action found in [text]. Returns a short confirmation, or null if there was no valid action. */
    fun run(ctx: Context, text: String, lang: String): String? {
        val m = re.find(text) ?: return null
        val name = m.groupValues[1].lowercase()
        val arg = m.groupValues[2].trim()
        if (name == "none") return null
        val fa = lang == "fa"
        return try {
            if (exec(ctx, name, arg)) (if (fa) "انجام شد." else "Done.") else null
        } catch (e: Throwable) {
            if (fa) "نتونستم این کار رو انجام بدم." else "I couldn't do that."
        }
    }

    private fun go(ctx: Context, i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    private fun digits(s: String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                else -> c
            }
        )
        return sb.toString()
    }

    private val aliases = mapOf(
        "یوتیوب" to "youtube", "اینستاگرام" to "instagram", "تلگرام" to "telegram", "واتساپ" to "whatsapp",
        "دوربین" to "camera", "تنظیمات" to "settings", "ساعت" to "clock", "ماشین حساب" to "calculator",
        "گالری" to "gallery", "مرورگر" to "chrome", "نقشه" to "maps", "مخاطبین" to "contacts", "پیامک" to "messages",
    )

    private fun exec(ctx: Context, name: String, rawArg: String): Boolean {
        val arg = digits(rawArg)
        when (name) {
            "open_app" -> {
                val want = (aliases[rawArg.trim()] ?: rawArg).trim().lowercase()
                if (want.isEmpty()) return false
                val pm = ctx.packageManager
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val hit = pm.queryIntentActivities(main, 0).firstOrNull {
                    it.loadLabel(pm).toString().lowercase().contains(want) ||
                        it.activityInfo.packageName.lowercase().contains(want)
                } ?: return false
                val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return false
                go(ctx, launch); return true
            }
            "call" -> {
                val num = arg.filter { it.isDigit() || it == '+' }
                go(ctx, Intent(Intent.ACTION_DIAL, Uri.parse(if (num.isNotEmpty()) "tel:$num" else "tel:")))
                return true
            }
            "sms" -> {
                val parts = arg.split(";", limit = 2)
                val num = parts[0].filter { it.isDigit() || it == '+' }
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
                if (parts.size > 1) i.putExtra("sms_body", parts[1].trim())
                go(ctx, i); return true
            }
            "alarm" -> {
                val t = Regex("""(\d{1,2})\s*[:.٫]\s*(\d{2})""").find(arg) ?: return false
                val h = t.groupValues[1].toInt(); val mi = t.groupValues[2].toInt()
                if (h > 23 || mi > 59) return false
                go(ctx, Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, mi)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                return true
            }
            "timer" -> {
                val sec = arg.filter { it.isDigit() }.toIntOrNull() ?: return false
                if (sec <= 0) return false
                go(ctx, Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                return true
            }
            "flashlight" -> {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return false
                val on = !(arg.lowercase().contains("off") || rawArg.contains("خاموش"))
                cm.setTorchMode(id, on); return true
            }
            "wifi" -> { go(ctx, Intent(Settings.ACTION_WIFI_SETTINGS)); return true }
            "bluetooth" -> { go(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return true }
            "settings" -> { go(ctx, Intent(Settings.ACTION_SETTINGS)); return true }
            "web" -> {
                if (rawArg.isBlank()) return false
                go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(rawArg))))
                return true
            }
            "youtube" -> {
                if (rawArg.isBlank()) return false
                go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(rawArg))))
                return true
            }
            "maps" -> {
                if (rawArg.isBlank()) return false
                go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(rawArg)))); return true
            }
            "navigate" -> {
                if (rawArg.isBlank()) return false
                go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(rawArg)))); return true
            }
        }
        return false
    }
}
