package com.jarvis.assistant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.vm.JarvisViewModel

/** Assistant name, developer mode (PIN), memory and call shortcuts. */
@Composable
fun ExtrasSection(vm: JarvisViewModel) {
    var name by remember { mutableStateOf(vm.assistantNameState) }
    var pin by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf("") }
    var mem by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = BarGray)
        Text(vm.tr("نام دستیار", "Assistant name"), color = Muted, fontSize = 12.sp)
        OutlinedTextField(
            name, { name = it; vm.setAssistantName(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (vm.lang == "fa") "جارویس" else "Jarvis") },
        )

        HorizontalDivider(color = BarGray)
        Text(vm.tr("حالت توسعه‌دهنده", "Developer mode"), color = Muted, fontSize = 12.sp)
        if (vm.devOk) {
            Text(vm.tr("فعاله ✓ — تماس مستقیم، پیدا کردن هوشمند اسم‌ها، حافظه‌ی تماس، میانبر نامحدود، جستجوی خودکار وب و حافظه‌ی ۵۰ مورد.", "Active ✓ — direct calling, smart name matching, remembered call choices, unlimited shortcuts, automatic web search and 50 memory items."), color = Color(0xFF4EE06A), fontSize = 12.sp, lineHeight = 18.sp)
            OutlinedButton({ vm.lockDev() }, Modifier.fillMaxWidth()) { Text(vm.tr("غیرفعال کردن", "Turn off"), color = Gold) }
        } else {
            Text(vm.tr("نسخه‌ی دمو: تماس فقط با باز کردن شماره‌گیر، حداکثر ۳ میانبر و ۵ مورد حافظه، جستجوی وب فقط با دستور «سرچ کن».", "Demo: calls open the dialer only, max 3 shortcuts and 5 memory items, web search only on “search …”."), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pin, { pin = it; pinMsg = "" }, label = { Text(vm.tr("رمز توسعه‌دهنده", "Developer PIN"), fontSize = 12.sp) }, singleLine = true, modifier = Modifier.weight(1f), visualTransformation = PasswordVisualTransformation())
                Button({ if (vm.unlockDev(pin)) { pin = ""; pinMsg = "" } else pinMsg = vm.tr("رمز اشتباه است.", "Wrong PIN.") }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text(vm.tr("فعال", "Unlock")) }
            }
            if (pinMsg.isNotBlank()) Text(pinMsg, color = Color(0xFFFF6B5A), fontSize = 12.sp)
        }

        HorizontalDivider(color = BarGray)
        Text(vm.tr("حافظه (چیزهایی که یادش بمونه)", "Memory (things to remember)"), color = Muted, fontSize = 12.sp)
        vm.memory.forEachIndexed { i, m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m, color = GoldText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Delete, null, tint = Muted, modifier = Modifier.size(20.dp).clickable { vm.memoryRemove(i) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(mem, { mem = it }, placeholder = { Text(vm.tr("مثلاً: اسم من آوشه", "e.g. my name is Avash")) }, singleLine = true, modifier = Modifier.weight(1f))
            Button({ if (vm.memoryAdd(mem)) mem = "" }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text(vm.tr("افزودن", "Add")) }
        }
        Text(vm.tr("می‌تونی بگی «یادت باشه …» یا «remember …».", "You can also say “remember …”."), color = Muted, fontSize = 11.sp)

        HorizontalDivider(color = BarGray)
        Text(vm.tr("میانبرهای تماس", "Call shortcuts"), color = Muted, fontSize = 12.sp)
        if (vm.aliases.isEmpty()) Text(vm.tr("هنوز میانبری نداری. تو صفحه‌ی مخاطبین روی ⭐ بزن.", "No shortcuts yet. Tap ⭐ on the Contacts page."), color = Muted, fontSize = 12.sp)
        vm.aliases.entries.toList().forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$k → $v", color = GoldText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Delete, null, tint = Muted, modifier = Modifier.size(20.dp).clickable { vm.removeAlias(k) })
            }
        }
    }
}
