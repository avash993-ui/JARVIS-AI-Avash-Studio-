#!/usr/bin/env bash
# Builds Jarvis.apk with NO Android Studio. Linux / macOS / WSL.
set -e
cd "$(dirname "$0")"
GV=8.11.1; T=".tools"; mkdir -p "$T"
command -v java >/dev/null || { echo "JDK 17+ not found. Install it first (Ubuntu: sudo apt install openjdk-17-jdk)"; exit 1; }
V=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
[ "$V" -ge 17 ] || { echo "Java $V is too old, need 17+"; exit 1; }
OS=linux; [ "$(uname)" = Darwin ] && OS=mac
if [ ! -x "$T/gradle-$GV/bin/gradle" ]; then
  echo ">> downloading Gradle $GV"
  curl -L -o "$T/g.zip" "https://services.gradle.org/distributions/gradle-$GV-bin.zip"
  unzip -q "$T/g.zip" -d "$T"; rm "$T/g.zip"
fi
export ANDROID_HOME="$PWD/$T/android-sdk"
SM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SM" ]; then
  echo ">> downloading Android command-line tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -L -o "$T/c.zip" "https://dl.google.com/android/repository/commandlinetools-$OS-11076708_latest.zip"
  unzip -q "$T/c.zip" -d "$T/ct"; mv "$T/ct/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"; rm -r "$T/ct" "$T/c.zip"
fi
yes | "$SM" --licenses >/dev/null || true
"$SM" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
"$T/gradle-$GV/bin/gradle" --no-daemon assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk Jarvis.apk
echo; echo "DONE -> $(pwd)/Jarvis.apk"
