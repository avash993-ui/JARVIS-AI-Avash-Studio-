package com.jarvis.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label, fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: JarvisViewModel, onDismiss: () -> Unit) {
    val s = vm.s
    var url by remember { mutableStateOf(vm.prefs.cloudUrl) }
    var key by remember { mutableStateOf(vm.prefs.cloudKey) }
    var model by remember { mutableStateOf(vm.prefs.cloudModel) }
    var brave by remember { mutableStateOf(vm.prefs.braveKey) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(s.settings, color = Gold, fontSize = 20.sp)

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
            SwitchRow(s.webSearch, vm.webOn) { vm.setWebOn(it) }

            Text(s.cloudMode, color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(s.off, vm.cloudMode == "off") { vm.setCloudMode("off") }
                Chip(s.ask, vm.cloudMode == "ask") { vm.setCloudMode("ask") }
                Chip(s.auto, vm.cloudMode == "auto") { vm.setCloudMode("auto") }
            }
            Text(s.cloudHelp, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Field(s.cloudUrl, url) { url = it; vm.prefs.cloudUrl = it }
            Field(s.cloudKey, key) { key = it; vm.prefs.cloudKey = it }
            Field(s.cloudModel, model) { model = it; vm.prefs.cloudModel = it }
            Field(s.braveKey, brave) { brave = it; vm.prefs.braveKey = it }

            SwitchRow(s.sourceOrder, !vm.hfFirst) { vm.setHfFirst(!it) }

            OutlinedButton({ onDismiss(); vm.openModels() }, Modifier.fillMaxWidth()) { Text(s.changeModel, color = Gold) }
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
