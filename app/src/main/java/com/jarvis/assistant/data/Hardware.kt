package com.jarvis.assistant.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class Hw(val ramGb: Double, val freeGb: Double, val soc: String, val cores: Int)

object Hardware {
    fun read(ctx: Context): Hw {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else Build.HARDWARE
        return Hw(
            ramGb = mi.totalMem / 1_073_741_824.0,
            freeGb = stat.availableBytes / 1_073_741_824.0,
            soc = soc,
            cores = Runtime.getRuntime().availableProcessors(),
        )
    }

    /** Phones report slightly less than the marketed RAM (8 GB -> ~7.4), so a small allowance is added. */
    fun fits(hw: Hw, l: Level) = hw.ramGb + 0.8 >= l.minRamGb

    fun fitsStorage(hw: Hw, sizeBytes: Long) = sizeBytes <= 0 || hw.freeGb * 1_073_741_824.0 > sizeBytes * 1.15

    /** Best level that fits. Levels 5-6 are only suggested on very large-RAM phones (CPU inference is slow). */
    fun recommend(hw: Hw): Level {
        val cap = if (hw.ramGb >= 14) 5 else 4
        return Catalog.levels.filter { it.n <= cap && fits(hw, it) }.maxByOrNull { it.n } ?: Catalog.levels.first()
    }
}
