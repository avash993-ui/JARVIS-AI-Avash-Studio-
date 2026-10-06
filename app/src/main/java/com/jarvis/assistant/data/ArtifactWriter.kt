package com.jarvis.assistant.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ArtifactWriter {
    data class FilePart(val name: String, val body: String)

    private val block = Regex("(?s)```(?:[A-Za-z0-9_+.-]+)?\\s*\\n(.*?)```")
    private val fileName = Regex("(?m)^\\s*(?://|#|/\\*)\\s*FILE:\\s*([^*\\n]+?)(?:\\*/)?\\s*$", RegexOption.IGNORE_CASE)

    fun extract(text: String): List<FilePart> {
        val out = mutableListOf<FilePart>()
        block.findAll(text).forEachIndexed { i, m ->
            var body = m.groupValues[1].trimEnd()
            val header = fileName.find(body)
            val name = header?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: "jarvis_output_${i + 1}.txt"
            if (header != null) body = body.removeRange(header.range).trimStart()
            if (body.isNotBlank()) out += FilePart(safe(name), body)
        }
        return out
    }

    fun writeZip(ctx: Context, parts: List<FilePart>, baseName: String = "jarvis-project.zip"): File {
        val dir = File(ctx.cacheDir, "jarvis-artifacts").apply { mkdirs() }
        val f = File(dir, safe(baseName))
        ZipOutputStream(f.outputStream()).use { z ->
            parts.forEach { part ->
                z.putNextEntry(ZipEntry(part.name)); z.write(part.body.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
        }
        return f
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply { type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }
        ctx.startActivity(Intent.createChooser(i, "JARVIS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun safe(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "file.txt" }
}
