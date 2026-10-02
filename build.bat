@echo off
REM Builds Jarvis.apk with NO Android Studio (Windows). Needs JDK 17+ installed.
setlocal
cd /d "%~dp0"
set GV=8.11.1
set T=%CD%\.tools
if not exist "%T%" mkdir "%T%"
where java >nul 2>nul || (echo JDK 17+ not found. Install Temurin 17 from adoptium.net & exit /b 1)
if not exist "%T%\gradle-%GV%\bin\gradle.bat" (
  echo ^>^> downloading Gradle %GV%
  powershell -Command "Invoke-WebRequest https://services.gradle.org/distributions/gradle-%GV%-bin.zip -OutFile '%T%\g.zip'; Expand-Archive '%T%\g.zip' '%T%' -Force; Remove-Item '%T%\g.zip'"
)
set ANDROID_HOME=%T%\android-sdk
set SM=%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat
if not exist "%SM%" (
  echo ^>^> downloading Android command-line tools
  mkdir "%ANDROID_HOME%\cmdline-tools"
  powershell -Command "Invoke-WebRequest https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip -OutFile '%T%\c.zip'; Expand-Archive '%T%\c.zip' '%T%\ct' -Force; Move-Item '%T%\ct\cmdline-tools' '%ANDROID_HOME%\cmdline-tools\latest'; Remove-Item '%T%\c.zip'; Remove-Item '%T%\ct' -Recurse"
)
(for /l %%i in (1,1,30) do @echo y) | call "%SM%" --licenses >nul
call "%SM%" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
set SDKP=%ANDROID_HOME:\=/%
echo sdk.dir=%SDKP%> local.properties
call "%T%\gradle-%GV%\bin\gradle.bat" --no-daemon assembleDebug || exit /b 1
copy /y app\build\outputs\apk\debug\app-debug.apk Jarvis.apk
echo DONE: %CD%\Jarvis.apk
