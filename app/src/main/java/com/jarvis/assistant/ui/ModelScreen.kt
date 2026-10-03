package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.data.Catalog
import com.jarvis.assistant.data.Hardware
import com.jarvis.assistant.data.Level
import com.jarvis.assistant.vm.JarvisViewModel
import java.util.Locale

private fun fmtSize(b: Long?): String? = when {
    b == null || b <= 0 -> null
    b >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", b / 1_073_741_824.0)
    else -> String.format(Locale.US, "%d MB", b / 1_048_576L)
}

@Composable
fun ModelScreen(vm: JarvisViewModel) {
    val s = vm.s
    // 0 none, 1 hardware warning, 2 license
    var step by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(0) }

    fun begin(n: Int) {
        target = n
        val l = Catalog.byN(n)
        step = if (!Hardware.fits(vm.hw, l)) 1 else if (!vm.prefs.licenseOk) 2 else 0
        if (step == 0) vm.install(n)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
          Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (vm.engineReady) TextButton({ vm.backToChat() }) { Text("←", color = Gold, fontSize = 20.sp) }
                Spacer(Modifier.weight(1f))
                OutlinedButton({ vm.setLang(if (vm.lang == "fa") "en" else "fa") }) { Text(if (vm.lang == "fa") "EN" else "FA", color = Gold) }
            }
            Text(s.pickTitle, color = Gold, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(s.pickSub, color = Muted, fontSize = 14.sp)
          }
        }
        item {
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Memory, s.hardware, tint = Cyan, modifier = Modifier.size(22.dp))
                Text(
                    String.format(Locale.US, s.hwLine, "%.1f".format(Locale.US, vm.hw.ramGb), vm.hw.soc, "%.0f".format(Locale.US, vm.hw.freeGb)),
                    color = GoldText, fontSize = 13.sp,
                )
            }
        }
        vm.modelError?.let { err -> item { Text(s.error + err, color = Color(0xFFFF8A80), fontSize = 13.sp) } }
        items(Catalog.levels) { l -> LevelCard(vm, l) { begin(l.n) } }
        item { AboutSection(vm) }
    }

    if (step == 1) {
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text(s.tooBig) },
            text = { Text("${Catalog.byN(target).base}: ${s.slow}") },
            confirmButton = { TextButton({ step = if (vm.prefs.licenseOk) 0 else 2; if (step == 0) vm.install(target) }) { Text(s.download, color = Gold) } },
            dismissButton = { TextButton({ step = 0 }) { Text(s.cancel) } },
            containerColor = Panel,
        )
    }
    if (step == 2) {
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text(s.licenseTitle) }, text = { Text(s.licenseBody) },
            confirmButton = { TextButton({ vm.prefs.licenseOk = true; step = 0; vm.install(target) }) { Text(s.accept, color = Gold) } },
            dismissButton = { TextButton({ step = 0 }) { Text(s.decline) } },
            containerColor = Panel,
        )
    }
}

@Composable
private fun LevelCard(vm: JarvisViewModel, l: Level, onDownload: () -> Unit) {
    val s = vm.s
    val fits = Hardware.fits(vm.hw, l)
    val isRec = vm.recommended.n == l.n
    val busyHere = vm.downloading == l.n
    val downloaded = vm.isDownloaded(l.n)
    val size = vm.sizes[l.n]

    Row(
        Modifier.fillMaxWidth().alpha(if (fits) 1f else .55f)
            .background(Panel, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LevelIcon(l.n, Color(l.color), 60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${s.appName} ${l.n} · ${if (vm.lang == "fa") l.faName else l.enName}", color = GoldText, fontSize = 15.sp)
            Text(l.base, color = Muted, fontSize = 12.sp)
            Text(fmtSize(size) ?: s.sizeUnknown, color = Cyan, fontSize = 12.sp)
            if (isRec) Text(s.recommended, color = Gold, fontSize = 12.sp)
            if (!fits) Text(s.tooBig, color = Color(0xFFFF8A80), fontSize = 12.sp)
            else if (l.slow) Text(s.slow, color = Color(0xFFFFB74D), fontSize = 12.sp)
            if (busyHere) {
                val p = vm.progress[l.n] ?: 0f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    CircularProgressIndicator(progress = { p }, color = Gold, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                    Text("${(p * 100).toInt()}%", color = GoldText, fontSize = 13.sp)
                }
            }
        }
        when {
            busyHere -> TextButton({ vm.cancelDownload() }) { Text(s.cancel, color = Gold) }
            vm.installed == l.n -> Text(s.inUse, color = Gold, fontSize = 12.sp)
            downloaded -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button({ vm.useInstalled(l.n) }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text(s.use) }
                Icon(Icons.Rounded.Delete, s.delete, tint = Muted, modifier = Modifier.padding(top = 6.dp).size(20.dp).clickable { vm.removeModel(l.n) })
            }
            else -> Button(
                onDownload, enabled = vm.downloading == 0,
                colors = ButtonDefaults.buttonColors(containerColor = if (isRec) Gold else BarGray, contentColor = if (isRec) Color.Black else GoldText),
            ) { Text(s.download) }
        }
    }
}
