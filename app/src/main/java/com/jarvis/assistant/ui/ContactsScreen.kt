package com.jarvis.assistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.data.Contact
import com.jarvis.assistant.data.Contacts
import com.jarvis.assistant.vm.JarvisViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val palette = listOf(0xFF7E57C2, 0xFF26A69A, 0xFF42A5F5, 0xFFEF5350, 0xFFFFA726, 0xFF66BB6A, 0xFFEC407A)
private fun initial(n: String): String { val c = n.trim().firstOrNull() ?: return "#"; return if (c.isLetter()) c.uppercaseChar().toString() else "#" }

/** A phone-book style contacts page. Tap the phone to call, the star to give a spoken shortcut (e.g. "mom"). */
@Composable
fun ContactsScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    var has by remember { mutableStateOf(Contacts.hasRead(ctx)) }
    var list by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var aliasFor by remember { mutableStateOf<Contact?>(null) }
    var alias by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { has = it }
    LaunchedEffect(has) { if (has) list = withContext(Dispatchers.IO) { Contacts.load(ctx) } }
    val shown = remember(list, q) {
        val nq = Contacts.norm(q)
        if (nq.isEmpty()) list else list.filter { Contacts.norm(it.name).contains(nq) || it.number.contains(q.trim()) }
    }
    val groups = remember(shown) { shown.groupBy { initial(it.name) }.toSortedMap() }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Gold) }
            Text(vm.tr("مخاطبین", "Contacts"), color = Gold, fontSize = 22.sp)
        }
        if (!has) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.tr("برای نمایش مخاطبین و تماس با اسم، اجازه‌ی دسترسی لازمه.", "Contacts access is needed to show contacts and call by name."), color = GoldText, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Button({ launcher.launch(Manifest.permission.READ_CONTACTS) }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) {
                    Text(vm.tr("دادن اجازه", "Allow access"))
                }
            }
        } else {
            OutlinedTextField(
                q, { q = it }, placeholder = { Text(vm.tr("جستجو در مخاطبین", "Search contacts")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            )
            if (vm.aliases.isNotEmpty()) Text(
                vm.tr("میانبرها: ", "Shortcuts: ") + vm.aliases.keys.joinToString("، "),
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                groups.forEach { (h, rows) ->
                    item(key = "h$h") { Text(h, color = Gold, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 4.dp)) }
                    items(rows, key = { it.name + "|" + it.number }) { c ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape).background(Color(palette[(c.name.hashCode() and 0x7fffffff) % palette.size])),
                                contentAlignment = Alignment.Center,
                            ) { Text(initial(c.name), color = Color.White, fontSize = 18.sp) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(c.name, color = GoldText, fontSize = 16.sp, maxLines = 1)
                                Text(c.number, color = Muted, fontSize = 12.sp, maxLines = 1)
                                val a = vm.aliases.entries.firstOrNull { it.value == c.number }?.key
                                if (a != null) Text("⭐ $a", color = Gold, fontSize = 11.sp)
                            }
                            IconButton({ alias = ""; err = ""; aliasFor = c }) { Icon(Icons.Rounded.Star, vm.tr("میانبر", "Shortcut"), tint = Muted) }
                            IconButton({ vm.callNumber(c.number, c.name) }) { Icon(Icons.Rounded.Call, vm.tr("تماس", "Call"), tint = Color(0xFF4EE06A)) }
                        }
                    }
                }
            }
        }
    }

    aliasFor?.let { c ->
        AlertDialog(
            onDismissRequest = { aliasFor = null }, containerColor = Panel,
            title = { Text(vm.tr("میانبر برای ${c.name}", "Shortcut for ${c.name}"), color = Gold) },
            text = {
                Column {
                    Text(vm.tr("اسمی که موقع صحبت می‌گی (مثلاً مامان). بعد می‌تونی بگی «زنگ بزن مامان».", "The name you will say (e.g. mom). Then you can say “call mom”."), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(alias, { alias = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (err.isNotBlank()) Text(err, color = Color(0xFFFF6B5A), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton({
                    if (vm.addAlias(alias, c.number)) aliasFor = null
                    else err = vm.tr("نسخه‌ی دمو فقط ۳ میانبر داره. حالت توسعه‌دهنده رو فعال کن.", "The demo allows only 3 shortcuts. Unlock developer mode for more.")
                }) { Text(vm.tr("ذخیره", "Save"), color = Gold) }
            },
            dismissButton = { TextButton({ aliasFor = null }) { Text(vm.tr("لغو", "Cancel"), color = Muted) } },
        )
    }
}
