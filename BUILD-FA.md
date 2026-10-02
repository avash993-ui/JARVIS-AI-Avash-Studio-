# ساخت APK جارویس بدون اندروید استودیو

## راه ۱ (ساده‌ترین، بدون نصب چیزی): GitHub Actions
1. تو github.com یه ریپوی خالی بساز و کل پوشه‌ی پروژه رو آپلود کن (پوشه‌ی `.github` هم باید باشه).
2. برو تب **Actions** ← **Build Jarvis APK** ← **Run workflow**.
3. بعد ۵ تا ۱۰ دقیقه، پایین صفحه‌ی همون اجرا، فایل **Jarvis-APK** رو دانلود کن (zip که توش `app-debug.apk` هست).
4. APK رو بریز تو گوشی و نصب کن. اگه خطا داد، لاگ قسمت «Run gradle» رو بفرست.

## راه ۲: روی کامپیوتر با یه دستور
نیاز: فقط **JDK 17 یا بالاتر** (Windows: Temurin 17 از adoptium.net).
- Linux / Mac / WSL: `./build.sh`
- Windows: روی `build.bat` دابل‌کلیک کن.

اسکریپت خودش Gradle و ابزار اندروید رو (حدود ۱ تا ۲ گیگ) توی پوشه‌ی `.tools` دانلود می‌کنه و آخرش `Jarvis.apk` کنار پروژه ساخته میشه.
بار اول طولانیه، دفعه‌های بعد سریعه.

## راه ۳: دستی (برای یاد گرفتن)
1. **JDK 17** نصب کن.
2. **Gradle 8.11.1** رو از gradle.org دانلود و unzip کن و پوشه‌ی `bin` رو تو PATH بذار.
3. **Android command-line tools** رو از developer.android.com/studio (پایین صفحه، بخش Command line tools only) دانلود کن. بذارش تو `sdk/cmdline-tools/latest/`.
4. این‌ها رو بزن:
   ```
   export ANDROID_HOME=$PWD/sdk
   sdkmanager --licenses
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   echo "sdk.dir=$ANDROID_HOME" > local.properties
   gradle assembleDebug
   ```
5. APK اینجاست: `app/build/outputs/apk/debug/app-debug.apk`

## نصب روی گوشی
- فایل رو به گوشی بفرست و بازش کن. اگه خواست، «نصب از منابع ناشناس» رو برای همون برنامه فعال کن.
- یا با کابل: `adb install -r Jarvis.apk` (تو گوشی «USB debugging» رو روشن کن).
- حداقل اندروید ۱۲ (API 31) و گوشی arm64 واقعی لازمه. امولاتور برای مدل‌های هوش مصنوعی خیلی کنده.

## چی عوض کردم تا بدون اندروید استودیو بیلد بشه
- `build.sh` و `build.bat`: همه‌چیز (Gradle، SDK، لایسنس‌ها، `local.properties`) رو خودکار آماده و بیلد می‌کنن.
- `.github/workflows/build.yml`: بیلد آنلاین.
- `app/build.gradle.kts`: نسخه‌ی release با کلید debug امضا میشه (بدون keystore هم نصب‌شدنیه). minify خاموش شد تا کتابخونه‌ی llama.cpp خراب نشه. فقط معماری `arm64-v8a` ساخته میشه (APK کوچیک‌تر). lint هم خطا نمیده.
- `build.gradle.kts`: Kotlin از 2.2.0 به 2.2.21 رفت چون کتابخونه‌ی llamatik با Kotlin جدیدتر ساخته شده.
- `gradle.properties`: بیلد موازی و cache روشن شد.

## مفاهیم Gradle که خوبه بدونی
- `settings.gradle.kts`: اسم پروژه و ماژول‌ها (`:app`) و محل دانلود پلاگین‌ها.
- `build.gradle.kts` ریشه: نسخه‌ی پلاگین‌های اندروید و Kotlin.
- `app/build.gradle.kts`: تنظیمات خود برنامه (نسخه‌ی SDK، وابستگی‌ها مثل Compose و llamatik).
- `gradle assembleDebug` ← APK با امضای debug؛ `gradle assembleRelease` ← نسخه‌ی release (اینجا با همون کلید debug).
- `gradle clean` ← پاک کردن بیلد قبلی وقتی چیز عجیبی شد.

## هشدار صادقانه
README پروژه می‌گه این کد هنوز با کامپایلر تست نشده. ممکنه بار اول چندتا خطای کوچیک کامپایل بده (بیشتر تو `engine/LlmEngine.kt` که به API کتابخونه‌ی llamatik وصله). متن خطا رو برام بفرست تا درستش کنم.
