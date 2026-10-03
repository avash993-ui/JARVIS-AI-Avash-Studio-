package com.jarvis.assistant.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** In-app updater: reads the latest GitHub Release, downloads its APK and hands it to the system installer. */
object Updater {
    private const val API = "https://api.github.com/repos/avash993-ui/JARVIS-AI-Avash-Studio-/releases/latest"

    data class Info(val code: Int, val name: String, val url: String)

    private fun pkgInfo(ctx: Context) = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    fun currentCode(ctx: Context): Long = PackageInfoCompat.getLongVersionCode(pkgInfo(ctx))
    fun currentName(ctx: Context): String = pkgInfo(ctx).versionName ?: ""

    /** Returns the newer release, or null if already up to date. Throws on network errors. */
    suspend fun check(ctx: Context): Info? = withContext(Dispatchers.IO) {
        val c = URL(API).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15000; c.readTimeout = 15000
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val tag = j.getString("tag_name")
        val code = tag.substringAfterLast('-').toIntOrNull()
        var url: String? = null
        val assets = j.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
        }
        if (code == null || url == null || code <= currentCode(ctx)) null
        else Info(code, j.optString("name", tag), url)
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "Jarvis-update.apk")
        if (f.exists()) f.delete()
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20000; c.readTimeout = 30000
        val total = c.contentLengthLong
        c.inputStream.use { inp ->
            f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var got = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); got += n
                    if (total > 0) onProgress(got.toFloat() / total)
                }
            }
        }
        f
    }

    /** Opens the system installer. Returns false if the user must first allow installs from this app. */
    fun install(ctx: Context, f: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
