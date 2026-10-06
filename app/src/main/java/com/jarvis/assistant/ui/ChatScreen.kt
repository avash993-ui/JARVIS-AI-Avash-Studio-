package com.jarvis.assistant.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.Msg
import com.jarvis.assistant.vm.JarvisViewModel
import com.jarvis.assistant.vm.Phase

@Composable
fun ChatScreen(vm: JarvisViewModel) {
    val s = vm.s
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var imagePrompt by remember { mutableStateOf("") }
    var showImagePrompt by remember { mutableStateOf(false) }
    val singleFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.addAttachment(it) } }
    val multiFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.forEach { vm.addAttachment(it) } }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startListening()
    }
    fun mic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val dim by animateFloatAsState(if (vm.overlay && !vm.wakeConversation) .35f else 1f, tween(600), label = "dim")

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm, { showHistory = true }, { vm.newChat() }, { showSettings = true })
            Box(Modifier.weight(1f).alpha(dim)) {
                if (vm.msgs.isEmpty()) EmptyState(vm) { mic() } else MessageList(vm)
            }
            if (vm.attachments.isNotEmpty()) {
                PendingAttachmentRow(vm, Modifier.fillMaxWidth().padding(horizontal = 12.dp).alpha(dim))
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp).alpha(dim),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconTile(Icons.Rounded.AttachFile, vm.tr("افزودن فایل", "Add file"), { showFiles = true }, 44.dp)
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text(s.hint, color = Muted) },
                    modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                        focusedBorderColor = TileBorder, unfocusedBorderColor = BarGray,
                        focusedTextColor = GoldText, unfocusedTextColor = GoldText, cursorColor = Gold),
                )
                when {
                    vm.busy -> IconTile(Icons.Rounded.Stop, s.stop, { vm.stopAll() })
                    input.isBlank() && vm.attachments.isEmpty() -> IconTile(Icons.Rounded.Mic, s.mic, { mic() })
                    else -> IconTile(Icons.AutoMirrored.Rounded.Send, s.send, { vm.send(input); input = "" })
                }
            }
        }

        // Siri-style summon: small hologram rises at the bottom, the reply is written right above it.
        val shown = vm.overlay
        val offY by animateDpAsState(if (shown) 0.dp else 110.dp, tween(800, easing = FastOutSlowInEasing), label = "offY")
        val sc by animateFloatAsState(if (shown) 1f else .3f, tween(800), label = "sc")
        val al by animateFloatAsState(if (shown) 1f else 0f, tween(600), label = "al")
        if (shown || al > .01f) {
            if (vm.wakeConversation) {
                // Wake Word UI: intentionally small, non-modal, and easy to close without dimming the app.
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 78.dp).graphicsLayer { alpha = al },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    val label = when (vm.phase) {
                        Phase.Listening -> s.stListen
                        Phase.Thinking -> if (vm.webNote) s.stWeb else s.stThink
                        Phase.Speaking -> s.stSpeak
                        Phase.Idle -> ""
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.widthIn(max = 190.dp),
                    ) {
                        if (vm.overlayText.isNotBlank()) {
                            Text(
                                vm.overlayText, color = GoldText, fontSize = 12.sp, lineHeight = 18.sp,
                                textAlign = TextAlign.End, maxLines = 3,
                            )
                        } else {
                            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    HoloOrb(vm.phase, 72.dp, Modifier.scale(sc).clickable { vm.stopAll() })
                    IconTile(Icons.Rounded.Close, vm.tr("بستن", "Close"), { vm.stopAll() }, 32.dp)
                }
            } else {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 78.dp).graphicsLayer { alpha = al },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconTile(Icons.Rounded.Close, vm.tr("بستن", "Close"), { vm.stopAll() }, 38.dp)
                    Spacer(Modifier.height(8.dp))
                    val label = when (vm.phase) {
                        Phase.Listening -> s.stListen
                        Phase.Thinking -> if (vm.webNote) s.stWeb else s.stThink
                        Phase.Speaking -> s.stSpeak
                        Phase.Idle -> ""
                    }
                    Text(
                        if (vm.overlayText.isNotBlank()) vm.overlayText else label,
                        color = if (vm.overlayText.isNotBlank()) GoldText else Muted,
                        fontSize = 15.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, maxLines = 5,
                        modifier = Modifier.widthIn(max = 340.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    HoloOrb(vm.phase, 100.dp, Modifier.offset(y = offY).scale(sc).clickable { vm.stopAll() })
                }
            }
        }
    }
    if (showFiles) AttachmentSheet(
        vm = vm,
        onDismiss = { showFiles = false },
        onFile = { showFiles = false; singleFile.launch(arrayOf("*/*")) },
        onImage = { showFiles = false; singleFile.launch(arrayOf("image/*")) },
        onZip = { showFiles = false; singleFile.launch(arrayOf("application/zip", "application/java-archive")) },
        onCode = { showFiles = false; singleFile.launch(arrayOf("text/*", "application/json", "application/xml", "application/octet-stream")) },
        onPdf = { showFiles = false; singleFile.launch(arrayOf("application/pdf")) },
        onMulti = { showFiles = false; multiFiles.launch(arrayOf("*/*")) },
        onImageGenerate = { showFiles = false; showImagePrompt = true },
    )
    if (showImagePrompt) {
        AlertDialog(
            onDismissRequest = { showImagePrompt = false },
            title = { Text(vm.tr("ساخت تصویر", "Generate image")) },
            text = { OutlinedTextField(value = imagePrompt, onValueChange = { imagePrompt = it }, label = { Text(vm.tr("توضیح تصویر", "Image prompt")) }) },
            confirmButton = { TextButton(onClick = { val p = imagePrompt; imagePrompt = ""; showImagePrompt = false; vm.generateImage(p) }) { Text(vm.tr("ساخت", "Generate")) } },
            dismissButton = { TextButton(onClick = { showImagePrompt = false }) { Text(vm.s.close) } },
        )
    }
    if (showSettings) SettingsSheet(vm) { showSettings = false }
    if (showHistory) HistorySheet(vm) { showHistory = false }
}

@Composable
private fun PendingAttachmentRow(vm: JarvisViewModel, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        itemsIndexed(vm.attachments) { index, file ->
            val icon = when {
                file.mime.startsWith("image/") -> Icons.Rounded.Image
                file.mime == "application/pdf" || file.name.endsWith(".pdf", true) -> Icons.Rounded.PictureAsPdf
                file.name.endsWith(".zip", true) || file.name.endsWith(".jar", true) -> Icons.Rounded.FolderZip
                file.name.endsWith(".kt", true) || file.name.endsWith(".java", true) || file.name.endsWith(".py", true) || file.name.endsWith(".js", true) || file.name.endsWith(".ts", true) -> Icons.Rounded.Code
                else -> Icons.Rounded.Description
            }
            Box(Modifier.size(82.dp)) {
                Column(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Panel)
                        .border(androidx.compose.foundation.BorderStroke(1.dp, TileBorder), RoundedCornerShape(16.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = Cyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(file.name, color = GoldText, fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center)
                }
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(BarGray)
                        .clickable { vm.removeAttachment(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, vm.tr("حذف فایل", "Remove file"), tint = Muted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    vm: JarvisViewModel, onDismiss: () -> Unit, onFile: () -> Unit, onImage: () -> Unit,
    onZip: () -> Unit, onCode: () -> Unit, onPdf: () -> Unit, onMulti: () -> Unit, onImageGenerate: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(vm.tr("افزودن فایل", "Add file"), color = Gold, fontSize = 20.sp)
            Text(vm.tr("فایل برای تحلیل به هوش مصنوعی/API فرستاده می‌شود. تصاویر و صفحات PDF نیز قابل دیدن هستند.", "Files are sent to the AI/API for analysis. Images and PDF pages can also be inspected."), color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachmentOption(Icons.Rounded.Description, vm.tr("فایل", "File"), onFile, Modifier.weight(1f))
                AttachmentOption(Icons.Rounded.Image, vm.tr("تصویر", "Image"), onImage, Modifier.weight(1f))
                AttachmentOption(Icons.Rounded.FolderZip, vm.tr("ZIP / پروژه", "ZIP / Project"), onZip, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachmentOption(Icons.Rounded.Code, vm.tr("کد", "Code"), onCode, Modifier.weight(1f))
                AttachmentOption(Icons.Rounded.PictureAsPdf, vm.tr("PDF", "PDF"), onPdf, Modifier.weight(1f))
                AttachmentOption(Icons.Rounded.AttachFile, vm.tr("چند فایل", "Multiple"), onMulti, Modifier.weight(1f))
            }
            androidx.compose.material3.OutlinedButton(onClick = onImageGenerate, modifier = Modifier.fillMaxWidth()) {
                Text(vm.tr("ساخت تصویر با API", "Generate image with API"), color = Gold)
            }
            Text(vm.tr("خود آیکون‌ها متن ندارند؛ نام فارسی/English زیرشان از رابط کاربری می‌آید.", "Icons stay text-free; the Persian/English labels are rendered by the UI below them."), color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AttachmentOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        IconTile(icon, label, onClick, 58.dp)
        Text(label, color = GoldText, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun TopBar(vm: JarvisViewModel, onHistory: () -> Unit, onNew: () -> Unit, onSettings: () -> Unit) {
    val s = vm.s
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HoloOrb(vm.phase, 30.dp)
        Column(Modifier.padding(horizontal = 10.dp).weight(1f)) {
            Text(vm.assistantName, color = Gold, fontSize = 14.sp)
            Text(vm.prefs.apiModel, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        IconTile(Icons.Rounded.Contacts, vm.tr("مخاطبین", "Contacts"), { vm.openContacts() }, 38.dp)
        Spacer(Modifier.size(6.dp))
        IconTile(Icons.Rounded.History, s.history, onHistory, 38.dp)
        Spacer(Modifier.size(6.dp))
        IconTile(Icons.Rounded.AddComment, s.newChat, onNew, 38.dp)
        Spacer(Modifier.size(6.dp))
        IconTile(Icons.Rounded.Settings, s.settings, onSettings, 38.dp)
    }
}

@Composable
private fun EmptyState(vm: JarvisViewModel, onTap: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        HoloOrb(vm.phase, 220.dp, Modifier.clickable { onTap() }, big = true)
        Spacer(Modifier.height(18.dp))
        Text(vm.s.greeting, color = GoldText, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(6.dp))
        Text(vm.s.tapToTalk, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun MessageList(vm: JarvisViewModel) {
    val state = rememberLazyListState()
    val lastLen = vm.msgs.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(vm.msgs.size, lastLen) { if (vm.msgs.isNotEmpty()) state.scrollToItem(vm.msgs.size - 1) }
    LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(vm.msgs) { i, m -> Bubble(vm, i, m) }
    }
}

@Composable
private fun Bubble(vm: JarvisViewModel, index: Int, m: Msg) {
    val s = vm.s
    val ctx = LocalContext.current
    val user = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 320.dp), horizontalAlignment = Alignment.Start) {
            if (user) {
                Text(m.text, color = GoldText, fontSize = 15.sp, lineHeight = 24.sp,
                    modifier = Modifier.background(BarGray, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
            } else {
                Text(m.text.ifEmpty { "…" }, color = GoldText, fontSize = 15.sp, lineHeight = 26.sp)
                if (m.text.isNotBlank()) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.ContentCopy, s.copy, tint = Muted, modifier = Modifier.size(16.dp).clickable {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("jarvis", m.text))
                        })
                        if (m.cloud) Text(s.cloudBadge, color = Cyan, fontSize = 11.sp)
                        if (m.web) Text(s.webBadge, color = Gold, fontSize = 11.sp)
                        if (com.jarvis.assistant.data.ArtifactWriter.extract(m.text).isNotEmpty()) {
                            Icon(Icons.Rounded.Share, vm.tr("ساخت فایل پروژه", "Export project files"), tint = Cyan, modifier = Modifier.size(16.dp).clickable { vm.exportAnswerAsProject(m.text) })
                        }
                    }
                }
            }
        }
    }
}
