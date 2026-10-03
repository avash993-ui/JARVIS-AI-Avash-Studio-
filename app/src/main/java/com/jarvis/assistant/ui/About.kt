package com.jarvis.assistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.engine.Updater
import com.jarvis.assistant.vm.JarvisViewModel
import kotlinx.coroutines.launch
import java.io.File

/** Credits + in-app update (shown on the model screen and in Settings). */
@Composable
fun AboutSection(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fa = vm.lang == "fa"
    var status by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<Updater.Info?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pct by remember { mutableStateOf(0f) }

    fun check() {
        scope.launch {
            busy = true
            status = if (fa) "در حال بررسی آپدیت…" else "Checking for updates…"
            try {
                val r = Updater.check(ctx)
                info = r
                status = if (r == null) (if (fa) "آخرین نسخه رو داری ✓" else "You have the latest version ✓")
                else (if (fa) "نسخه‌ی جدید آماده است: " else "New version available: ") + r.name
            } catch (e: Throwable) {
                status = if (fa) "اتصال به سرور آپدیت برقرار نشد" else "Could not reach the update server"
            }
            busy = false
        }
    }

    fun getAndInstall(url: String) {
        scope.launch {
            busy = true; pct = 0f
            try {
                val f = Updater.download(ctx, url) { pct = it }
                file = f
                if (!Updater.install(ctx, f))
                    status = if (fa) "اجازه‌ی نصب رو بده، برگرد و دوباره «نصب» رو بزن" else "Allow installs, come back and tap Install again"
            } catch (e: Throwable) {
                status = if (fa) "دانلود آپدیت ناموفق بود" else "Update download failed"
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { check() }

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("JARVIS  v${Updater.currentName(ctx)}", color = Gold, fontSize = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            if (fa) "ساخته‌شده توسط آوش ماتریکس (Avash Matrix)" else "Created by Avash Matrix",
            color = GoldText, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Text("Avash Studio", color = Gold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        if (status.isNotEmpty()) Text(status, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        if (busy && pct > 0f) LinearProgressIndicator(progress = { pct }, color = Gold, trackColor = BarGray, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        val i = info
        val f = file
        if (i != null && f == null) {
            OutlinedButton({ getAndInstall(i.url) }, enabled = !busy) {
                Text(if (fa) "دانلود و نصب نسخه‌ی جدید" else "Download & install update", color = Gold)
            }
        } else if (i != null && f != null) {
            OutlinedButton({ Updater.install(ctx, f) }, enabled = !busy) {
                Text(if (fa) "نصب" else "Install", color = Gold)
            }
        } else {
            OutlinedButton({ check() }, enabled = !busy) {
                Text(if (fa) "بررسی آپدیت" else "Check for updates", color = Gold)
            }
        }
    }
}
