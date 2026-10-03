package com.jarvis.assistant.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.engine.Integrity
import com.jarvis.assistant.vm.JarvisViewModel

private const val FA_TERMS = """۱. جارویس ساخته‌ی «آوش ماتریکس» (Avash Matrix) و «Avash Studio» است. حق نشر و نام سازنده محفوظه. کپی، تغییر نام یا انتشار دوباره‌ی برنامه بدون اجازه‌ی کتبی ممنوعه.

۲. جواب‌های هوش مصنوعی ممکنه اشتباه باشه. برای تصمیم‌های پزشکی، حقوقی و مالی بهش تکیه نکن.

۳. اگه مدل روی گوشی جواب رو نداند یا مطمئن نباشه، متن سؤالت برای گرفتن جواب به یه سرویس هوش مصنوعی ابری فرستاده میشه. اگه نمی‌خوای، تو تنظیمات «هوش مصنوعی ابری» رو «خاموش» کن.

۴. جستجوی وب و دستورهای گوشی (باز کردن برنامه، آلارم و...) فقط به درخواست خودت انجام میشن.

۵. مدل‌های Gemma تابع شرایط استفاده‌ی گوگل هستن (ai.google.dev/gemma/terms).

۶. برنامه «همان‌طور که هست» و بدون هیچ ضمانتی ارائه میشه و مسئولیت استفاده با خودته."""

private const val EN_TERMS = """1. JARVIS is created by Avash Matrix of Avash Studio. Copyright and the creator's name are reserved. Copying, renaming or redistributing the app without written permission is not allowed.

2. AI answers can be wrong. Do not rely on them for medical, legal or financial decisions.

3. If the on-device model doesn't know or isn't sure, your question text is sent to a cloud AI service to get an answer. To avoid this, set "Cloud AI" to Off in Settings.

4. Web search and phone commands (opening apps, alarms, etc.) run only when you ask for them.

5. Gemma models are subject to Google's terms (ai.google.dev/gemma/terms).

6. The app is provided "as is" without any warranty; you use it at your own risk."""

/** Agreement the user must accept before the first chat. */
@Composable
fun TermsGate(vm: JarvisViewModel) {
    var ok by remember { mutableStateOf(vm.prefs.termsOk) }
    if (ok) return
    val ctx = LocalContext.current
    val fa = vm.lang == "fa"
    AlertDialog(
        onDismissRequest = {},
        containerColor = Panel,
        title = { Text(if (fa) "توافقنامه‌ی استفاده از جارویس" else "JARVIS Terms of Use", color = Gold) },
        text = { Text(if (fa) FA_TERMS else EN_TERMS, color = GoldText, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton({ vm.prefs.termsOk = true; ok = true }) { Text(if (fa) "قبول دارم و ادامه" else "I agree", color = Gold) } },
        dismissButton = { TextButton({ (ctx as? Activity)?.finish() }) { Text(if (fa) "قبول ندارم (خروج)" else "Decline (exit)", color = Muted) } },
    )
}

/** Soft warning when the APK is not signed by Avash Studio (a re-packaged copy). */
@Composable
fun IntegrityGate(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    val official = remember { Integrity.isOfficial(ctx) }
    var hide by remember { mutableStateOf(false) }
    if (official || hide) return
    val fa = vm.lang == "fa"
    AlertDialog(
        onDismissRequest = { hide = true },
        containerColor = Panel,
        title = { Text(if (fa) "نسخه‌ی غیررسمی" else "Unofficial copy", color = Gold) },
        text = {
            Text(
                if (fa) "این نسخه با امضای رسمی Avash Studio ساخته نشده. ممکنه کپی یا دستکاری‌شده باشه. نسخه‌ی اصلی رو از صفحه‌ی رسمی دانلود کن."
                else "This copy is not signed by Avash Studio and may be copied or modified. Please get the original from the official page.",
                color = GoldText, fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Integrity.OFFICIAL_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                Text(if (fa) "نسخه‌ی رسمی" else "Official page", color = Gold)
            }
        },
        dismissButton = { TextButton({ hide = true }) { Text(if (fa) "ادامه" else "Continue", color = Muted) } },
    )
}
