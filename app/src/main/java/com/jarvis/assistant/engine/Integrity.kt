package com.jarvis.assistant.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Checks that this APK is signed with the official Avash Studio key. Never blocks on errors. */
object Integrity {
    const val OFFICIAL_SHA256 = "49e3cee3125a1997819a72946abc3d5696211d96945e3d93acdac23bd927bcf3"
    const val OFFICIAL_URL = "https://github.com/avash993-ui/JARVIS-AI-Avash-Studio-/releases"

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    fun isOfficial(ctx: Context): Boolean = try {
        val pm = ctx.packageManager
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            if (si == null) null else if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        sigs?.any { sha256(it.toByteArray()) == OFFICIAL_SHA256 } ?: true
    } catch (t: Throwable) { true }
}
