package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.data.Providers
import com.jarvis.assistant.search.Api
import com.jarvis.assistant.vm.JarvisViewModel
import kotlinx.coroutines.launch

/** Connect an OpenAI-compatible AI service: provider, API key, model (3 suggestions + live list), test, save. */
@Composable
fun SetupScreen(vm: JarvisViewModel) {
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(Providers.byId(vm.prefs.provider) ?: Providers.all[0]) }
    var base by remember { mutableStateOf(vm.prefs.apiBase.ifBlank { provider.base }) }
    var key by remember { mutableStateOf(vm.prefs.apiKey) }
    var model by remember { mutableStateOf(vm.prefs.apiModel.ifBlank { provider.models.firstOrNull() ?: "" }) }
    var show by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>?>(null) }
    var filter by remember { mutableStateOf("") }

    fun cfg() = Api.Cfg(base.trim(), key.trim(), model.trim())
    val canSave = base.isNotBlank() && model.isNotBlank() && (key.isNotBlank() || !provider.needsKey)

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.configured) IconButton({ vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Gold) }
            Text(vm.tr("اتصال به هوش مصنوعی", "Connect an AI service"), color = Gold, fontSize = 22.sp)
        }
        Text(
            vm.tr("جارویس با هر سرویسی که API سازگار با OpenAI بده کار می‌کنه. سرویس رو انتخاب کن، کلید رو بذار و تست کن.",
                "Jarvis works with any OpenAI-compatible API. Pick a service, paste your key and test it."),
            color = Muted, fontSize = 13.sp, lineHeight = 20.sp,
        )

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Providers.all.forEach { p ->
                FilterChip(
                    selected = p.id == provider.id,
                    onClick = {
                        provider = p; base = p.base; model = p.models.firstOrNull() ?: ""; ok = null; status = ""
                    },
                    label = { Text(p.name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Color.Black),
                )
            }
        }

        if (provider.site.isNotBlank()) {
            OutlinedButton({ vm.openTutorial(provider.id) }, Modifier.fillMaxWidth()) {
                Text(vm.tr("📖 آموزش گرفتن کلید ${provider.name}", "📖 How to get a ${provider.name} key"), color = Gold)
            }
        }
        if (!provider.needsKey && provider.id != "custom")
            Text(vm.tr("این سرویس بدون کلید هم کار می‌کنه، ولی با کلید سقف بیشتری داری.", "This service works without a key, but a key gives higher limits."), color = Muted, fontSize = 12.sp)

        OutlinedTextField(base, { base = it; ok = null }, label = { Text(vm.tr("آدرس API (تا /v1)", "API base URL (ending in /v1)"), fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            key, { key = it; ok = null },
            label = { Text(vm.tr("کلید API", "API key"), fontSize = 12.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton({ show = !show }) { Text(if (show) vm.tr("پنهان", "Hide") else vm.tr("نمایش", "Show"), color = Gold, fontSize = 12.sp) } },
        )
        OutlinedButton({ clip.getText()?.text?.let { key = it.trim(); ok = null } }, Modifier.fillMaxWidth()) {
            Text(vm.tr("📋 چسباندن کلید کپی‌شده", "📋 Paste copied key"), color = Gold)
        }

        Text(vm.tr("مدل", "Model"), color = Muted, fontSize = 12.sp)
        if (provider.models.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                provider.models.forEach { m ->
                    FilterChip(
                        selected = m == model, onClick = { model = m; ok = null }, label = { Text(m, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Color.Black),
                    )
                }
            }
        }
        OutlinedTextField(model, { model = it; ok = null }, label = { Text(vm.tr("نام مدل", "Model name"), fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            {
                scope.launch {
                    busy = true; status = vm.tr("در حال گرفتن لیست مدل‌ها…", "Loading model list…")
                    Api.listModels(cfg()).onSuccess { models = it; filter = ""; status = "" }
                        .onFailure { ok = false; status = vm.tr("لیست مدل‌ها گرفته نشد: ", "Couldn't load models: ") + (it.message ?: "") }
                    busy = false
                }
            },
            Modifier.fillMaxWidth(), enabled = !busy && base.isNotBlank(),
        ) { Text(vm.tr("➕ بیشتر… (لیست زنده‌ی همه‌ی مدل‌های این سرویس)", "➕ More… (live list of all models)"), color = Gold) }

        Button(
            {
                scope.launch {
                    busy = true; ok = null; status = vm.tr("در حال تست…", "Testing…")
                    val r = Api.test(cfg())
                    ok = r.isSuccess
                    status = if (r.isSuccess) vm.tr("وصل شد ✓", "Connected ✓") else vm.tr("خطا: ", "Error: ") + (r.exceptionOrNull()?.message ?: "")
                    busy = false
                }
            },
            Modifier.fillMaxWidth(), enabled = !busy && canSave,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
        ) { Text(vm.tr("تست اتصال", "Test connection")) }

        if (status.isNotBlank()) Text(status, color = if (ok == true) Color(0xFF4EE06A) else if (ok == false) Color(0xFFFF6B5A) else Muted, fontSize = 13.sp, lineHeight = 20.sp)

        Button(
            { vm.saveApi(provider.id, base.trim(), key.trim(), model.trim()) },
            Modifier.fillMaxWidth(), enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = if (ok == true) Gold else BarGray, contentColor = if (ok == true) Color.Black else GoldText),
        ) { Text(vm.tr("ذخیره و شروع", "Save & start")) }

        Text(
            vm.tr("کلید فقط روی همین گوشی ذخیره میشه. اسم مدل‌ها ممکنه با گذر زمان عوض بشه؛ اگه خطا داد از «بیشتر» لیست جدید بگیر.",
                "The key is stored only on this phone. Model names change over time; if one fails, load the live list with “More…”."),
            color = Muted, fontSize = 11.sp, lineHeight = 17.sp,
        )
        Spacer(Modifier.height(24.dp))
    }

    models?.let { all ->
        val list = remember(all, filter) { if (filter.isBlank()) all else all.filter { it.contains(filter.trim(), ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { models = null },
            containerColor = Panel,
            title = { Text(vm.tr("${all.size} مدل", "${all.size} models"), color = Gold) },
            text = {
                Column {
                    OutlinedTextField(filter, { filter = it }, placeholder = { Text(vm.tr("جستجو…", "Search…")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(list) { id ->
                            Text(id, color = GoldText, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { model = id; ok = null; models = null }.padding(vertical = 10.dp))
                            HorizontalDivider(color = BarGray)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ models = null }) { Text(vm.tr("بستن", "Close"), color = Gold) } },
        )
    }
}
