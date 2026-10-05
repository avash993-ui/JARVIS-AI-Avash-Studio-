package com.jarvis.assistant.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.data.Providers
import com.jarvis.assistant.vm.JarvisViewModel

data class Step(val img: String?, val icon: String, val fa: String, val en: String)

private val LLM7 = listOf(
    Step("tutorial/llm7_01.jpg", "", "مرورگر گوشی رو باز کن و برو به llm7.io. روی منوی ☰ بالا سمت راست بزن.", "Open your browser and go to llm7.io. Tap the ☰ menu at the top right."),
    Step("tutorial/llm7_02.jpg", "", "گزینه‌ی «Dashboard» رو بزن.", "Tap “Dashboard”."),
    Step("tutorial/llm7_03.jpg", "", "اول تیک موافقت با شرایط (Terms of Service) رو بزن (۱)، بعد «ادامه دادن با Google» رو بزن (۲). با GitHub یا ایمیل هم می‌تونی وارد بشی.", "Tick the Terms of Service box (1), then tap “Continue with Google” (2). You can also sign in with GitHub or email."),
    Step("tutorial/llm7_04.jpg", "", "حساب گوگل خودت رو انتخاب کن.", "Choose your Google account."),
    Step("tutorial/llm7_05.jpg", "", "روی «ادامه دادن» بزن.", "Tap “Continue”."),
    Step("tutorial/llm7_06.jpg", "", "تو داشبورد، منوی ☰ بالا سمت چپ رو بزن.", "In the dashboard, tap the ☰ menu at the top left."),
    Step("tutorial/llm7_07.jpg", "", "گزینه‌ی «API Keys» رو بزن.", "Tap “API Keys”."),
    Step("tutorial/llm7_08.jpg", "", "دکمه‌ی «Add API key» رو بزن تا کلید ساخته بشه.", "Tap “Add API key” to create your key."),
    Step("tutorial/llm7_09.jpg", "", "دکمه‌ی کپی ⧉ کنار توکن رو بزن تا توکن کپی بشه. (کلید رو به کسی نشون نده.)", "Tap the copy button ⧉ next to the token. (Never share your key.)"),
    Step(null, "⚙️", "حالا برگرد به جارویس. تو صفحه‌ی «اتصال به هوش مصنوعی» سرویس LLM7 رو انتخاب کن.", "Now go back to Jarvis. On the “Connect an AI service” screen choose LLM7."),
    Step(null, "📋", "روی دکمه‌ی «چسباندن کلید کپی‌شده» بزن، یا تو خانه‌ی «کلید API» نگه دار و Paste رو بزن. توکنی که کپی کردی اینجا میاد.", "Tap “Paste copied key”, or long-press the “API key” field and choose Paste. The token you copied appears there."),
    Step(null, "✅", "یکی از ۳ مدل پیشنهادی رو بزن (fast سریع‌ترینه)، بعد «تست اتصال» رو بزن. اگه نوشت «وصل شد ✓» روی «ذخیره و شروع» بزن.", "Pick one of the 3 suggested models (fast is quickest), tap “Test connection”. If it says “Connected ✓”, tap “Save & start”."),
)

private fun generic(name: String, site: String) = listOf(
    Step(null, "🌐", "مرورگر رو باز کن و برو به $site (سایت $name). بعضی سرویس‌ها از بعضی کشورها قابل استفاده نیستن؛ اگه باز نشد سرویس دیگه‌ای انتخاب کن.", "Open your browser and go to $site ($name). Some services are not available in every country; if it doesn't open, pick another one."),
    Step(null, "👤", "ثبت‌نام کن یا وارد حسابت شو.", "Sign up or sign in."),
    Step(null, "🔑", "بخش «API Keys» رو پیدا کن و یه کلید جدید بساز.", "Find the “API Keys” section and create a new key."),
    Step(null, "📋", "کلید رو کپی کن. معمولاً فقط یه بار کامل نشون داده میشه.", "Copy the key. It is usually shown in full only once."),
    Step(null, "⚙️", "برگرد به جارویس، سرویس $name رو انتخاب کن و کلید رو با دکمه‌ی «چسباندن کلید کپی‌شده» بذار.", "Go back to Jarvis, choose $name and paste the key with “Paste copied key”."),
    Step(null, "✅", "یکی از مدل‌های پیشنهادی رو بزن، «تست اتصال» رو بزن و بعد «ذخیره و شروع».", "Pick a suggested model, tap “Test connection”, then “Save & start”."),
)

@Composable
private fun AssetImage(path: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(path) { runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()?.asImageBitmap() }
    if (bmp != null) Image(bmp, null, modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
}

/** Step-by-step: screenshot on top, explanation under it, Next / Back at the bottom. */
@Composable
fun TutorialScreen(vm: JarvisViewModel) {
    val p = Providers.byId(vm.tutorialProvider)
    val steps = if (p?.id == "llm7") LLM7 else generic(p?.name ?: "API", p?.site ?: "")
    var i by remember(vm.tutorialProvider) { mutableIntStateOf(0) }
    val st = steps[i]
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(14.dp)) {
        Text(vm.tr("مرحله ${i + 1} از ${steps.size}", "Step ${i + 1} of ${steps.size}") + " · " + (p?.name ?: ""), color = Gold, fontSize = 14.sp)
        LinearProgressIndicator(progress = { (i + 1f) / steps.size }, color = Gold, trackColor = BarGray, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (st.img != null) AssetImage(st.img, Modifier.fillMaxSize())
            else Text(st.icon, fontSize = 88.sp)
        }
        Text(
            vm.tr(st.fa, st.en), color = GoldText, fontSize = 16.sp, lineHeight = 26.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(Panel, RoundedCornerShape(14.dp)).padding(14.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ if (i > 0) i-- else vm.back() }, Modifier.weight(1f)) { Text(if (i > 0) vm.tr("قبلی", "Back") else vm.tr("بستن", "Close"), color = Gold) }
            Button(
                { if (i < steps.size - 1) i++ else vm.back() }, Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
            ) { Text(if (i < steps.size - 1) vm.tr("بعدی", "Next") else vm.tr("رفتن به تنظیمات اتصال", "Go to connection settings")) }
        }
    }
}
