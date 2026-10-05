package com.jarvis.assistant.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.vm.JarvisViewModel

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = androidx.compose.ui.graphics.Color.Black),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GoldText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = androidx.compose.ui.graphics.Color.Black))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: JarvisViewModel, onDismiss: () -> Unit) {
    val s = vm.s
    var brave by remember { mutableStateOf(vm.prefs.braveKey) }
    val ctx = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(s.settings, color = Gold, fontSize = 20.sp)

            OutlinedButton({ onDismiss(); vm.openSetup() }, Modifier.fillMaxWidth()) {
                Text(vm.tr("🔌 اتصال به هوش مصنوعی (API / مدل)", "🔌 AI connection (API / model)"), color = Gold)
            }
            OutlinedButton({ onDismiss(); vm.openContacts() }, Modifier.fillMaxWidth()) {
                Text(vm.tr("📒 مخاطبین و میانبرها", "📒 Contacts & shortcuts"), color = Gold)
            }

            Text(s.language, color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("فارسی", vm.lang == "fa") { vm.setLang("fa") }
                Chip("English", vm.lang == "en") { vm.setLang("en") }
            }

            Text("${s.humor}: ${vm.humor}%", color = GoldText, fontSize = 14.sp)
            Slider(
                value = vm.humor.toFloat(), onValueChange = { vm.setHumor(it.toInt()) }, valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.humorLow, color = Muted, fontSize = 11.sp); Text(s.humorHigh, color = Muted, fontSize = 11.sp)
            }

            SwitchRow(s.readTyped, vm.speakTyped) { vm.setSpeakTyped(it) }

            Text(vm.tr("🔊 صدای نریدر", "🔊 Reader voice"), color = Muted, fontSize = 12.sp)
            Text(
                vm.tr(
                    "بهینه: از نریدر و موتور TTS خود گوشی استفاده می‌شود و برای پخش صدا نیازی به مصرف سهمیه API ندارد.",
                    "Optimized: uses the phone's own TTS engine and does not consume your AI/API quota for speech."
                ),
                color = Muted, fontSize = 11.sp, lineHeight = 17.sp
            )
            var voiceMenu by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton({ voiceMenu = true }, Modifier.fillMaxWidth()) {
                    val label = if (vm.ttsVoice == "system-default") vm.tr("بهینه / صدای پیش‌فرض سیستم", "Optimized / system default")
                    else vm.ttsVoices.firstOrNull { it.name == vm.ttsVoice }?.let { "${it.locale.displayLanguage} — ${it.name}" } ?: vm.ttsVoice
                    Text(label, color = GoldText, maxLines = 1)
                }
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(vm.tr("بهینه / صدای پیش‌فرض سیستم", "Optimized / system default")) },
                        onClick = { vm.changeTtsVoice("system-default"); voiceMenu = false }
                    )
                    vm.ttsVoices.take(40).forEach { voice ->
                        DropdownMenuItem(
                            text = { Text("${voice.locale.displayLanguage} — ${voice.name}", maxLines = 1) },
                            onClick = { vm.changeTtsVoice(voice.name); voiceMenu = false }
                        )
                    }
                }
            }
            Text(
                vm.tr(
                    "اگر در آینده صدای آنلاین/سرویسی اضافه شود، ممکن است مصرف سهمیه داشته باشد؛ صدای سیستم فعلی چنین مصرفی ندارد.",
                    "If online/service voices are added later, they may consume a quota; the current system voices do not."
                ),
                color = Gold, fontSize = 10.sp, lineHeight = 15.sp
            )

            SwitchRow(s.webSearch, vm.webOn) { vm.setWebOn(it) }
            SwitchRow(vm.tr("🎙 گوش‌به‌زنگ: با صدا زدن اسم دستیار باز بشه", "🎙 Always listening: open by saying the name"), vm.wakeOn) { vm.setWake(it) }
            if (vm.wakeOn) {
                SwitchRow(vm.tr("فقط وقتی صفحه روشنه (باتری کمتر)", "Only while the screen is on (saves battery)"), vm.wakeScreenOnly) { vm.updateWakeScreenOnly(it) }
                Text(vm.tr("لازمه: اجازه‌ی میکروفون و «نمایش روی برنامه‌های دیگر». اگه گوشی سرویس رو می‌کشه، برنامه رو از بهینه‌سازی باتری مستثنی کن.", "Needs the microphone and “Display over other apps” permissions. If your phone kills the service, exclude the app from battery optimisation."), color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth()) { Text(vm.tr("🔋 تنظیم بهینه‌سازی باتری", "🔋 Battery optimisation settings"), color = Gold) }
            }
            OutlinedTextField(brave, { brave = it; vm.prefs.braveKey = it }, label = { Text(s.braveKey, fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            ExtrasSection(vm)
            AboutSection(vm)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(vm: JarvisViewModel, onDismiss: () -> Unit) {
    val s = vm.s
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.history, color = Gold, fontSize = 20.sp)
            if (vm.history.isEmpty()) Text(s.emptyHistory, color = Muted)
            vm.history.reversed().forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title.ifBlank { "…" }, color = GoldText, fontSize = 15.sp, maxLines = 1,
                        modifier = Modifier.weight(1f).clickable { vm.openChat(c); onDismiss() }.padding(vertical = 10.dp),
                    )
                    Icon(Icons.Rounded.Delete, null, tint = Muted, modifier = Modifier.size(20.dp).clickable { vm.deleteChat(c) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
