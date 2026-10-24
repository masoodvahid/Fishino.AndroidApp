#!/usr/bin/env bash
# Installs the release APK on the running emulator, opens the app and fails
# (with the de-obfuscated stack trace as a GitHub annotation) if it crashes.
set -u

APK="$1"
MAPPING="$2"
PACKAGE="rdsco.fishino.app"

adb install -r "$APK" || { echo "::error::adb install failed"; exit 1; }
adb logcat -c
adb shell am start -W -n "$PACKAGE/.MainActivity"
sleep 20
adb shell am start -W -n "$PACKAGE/.MainActivity"
sleep 10

adb logcat -d -b crash > crash.txt 2>/dev/null || true
adb logcat -d -v brief AndroidRuntime:E '*:S' >> crash.txt 2>/dev/null || true

if ! grep -q "FATAL EXCEPTION\|Process: $PACKAGE" crash.txt; then
  echo "The app started without crashing."
  adb shell pidof "$PACKAGE" && exit 0
  echo "::warning::No crash in the log, but the app is not running."
  exit 0
fi

RETRACE="$(ls "$ANDROID_HOME"/cmdline-tools/*/bin/retrace 2>/dev/null | head -1)"
if [ -n "$RETRACE" ] && [ -f "$MAPPING" ]; then
  "$RETRACE" "$MAPPING" crash.txt > crash-retraced.txt 2>&1 || cp crash.txt crash-retraced.txt
else
  cp crash.txt crash-retraced.txt
fi

cat crash-retraced.txt
# One annotation with the first lines of the trace (readable on the run page).
message="$(grep -v '^-----' crash-retraced.txt | head -60 | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
echo "::error title=App crashed on start::${message}"
exit 1
