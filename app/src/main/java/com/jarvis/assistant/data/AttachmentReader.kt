package com.jarvis.assistant.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

object AttachmentReader {
    data class Payload(val prompt: String, val images: List<String> = emptyList())

    fun read(ctx: Context, uri: Uri, name: String, mime: String): Payload {
        val lower = name.lowercase()
        if (mime.startsWith("image/") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mt = if (mime.isBlank()) "image/*" else mime
            return Payload("Analyze the attached image: $name", listOf("data:$mt;base64,$b64"))
        }
        if (lower.endsWith(".pdf") || mime == "application/pdf") return readPdf(ctx, uri, name)
        if (lower.endsWith(".zip") || lower.endsWith(".jar")) return readZip(ctx, uri, name)
        if (lower.endsWith(".docx")) return readDocx(ctx, uri, name)
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(120_000) } ?: ""
        return Payload("Analyze this file named $name.\n\n$text")
    }

    private fun readZip(ctx: Context, uri: Uri, name: String): Payload {
        val sb = StringBuilder("Analyze project/archive $name. File tree and readable source files:\n")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { z ->
                var e = z.nextEntry; var count = 0
                while (e != null && count < 160) {
                    if (!e.isDirectory) {
                        sb.append("\n- ").append(e.name)
                        val n = e.name.lowercase()
                        if (n.endsWith(".kt") || n.endsWith(".java") || n.endsWith(".xml") || n.endsWith(".json") || n.endsWith(".gradle") || n.endsWith(".kts") || n.endsWith(".py") || n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".md") || n.endsWith(".txt")) {
                            val bytes = z.readBytes().take(18_000).toByteArray()
                            sb.append("\n```").append(String(bytes, Charsets.UTF_8)).append("\n```")
                        }
                        count++
                    }
                    z.closeEntry(); e = z.nextEntry
                }
            }
        }
        return Payload(sb.toString().take(180_000))
    }

    private fun readDocx(ctx: Context, uri: Uri, name: String): Payload {
        val text = StringBuilder()
        ctx.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) { if (e.name == "word/document.xml") text.append(String(z.readBytes(), Charsets.UTF_8)); z.closeEntry(); e = z.nextEntry }
        }}
        val clean = text.toString().replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").take(120_000)
        return Payload("Analyze DOCX $name:\n$clean")
    }

    private fun readPdf(ctx: Context, uri: Uri, name: String): Payload {
        val images = mutableListOf<String>()
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) PdfRenderer(pfd).use { r ->
                for (i in 0 until minOf(r.pageCount, 3)) {
                    r.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE); page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 90, out); bmp.recycle()
                        images.add("data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                    }
                }
            }
        } finally { pfd?.close() }
        return Payload("Analyze the first pages of PDF $name.", images)
    }
}
