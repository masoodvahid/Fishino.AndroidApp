#!/usr/bin/env bash
set -euo pipefail
apk=$1
package=$2
label=$3
out="launch-results/$label"
mkdir -p "$out"

capture() {
  adb logcat -d > "$out/logcat.txt" || true
  adb logcat -d -b crash > "$out/crash.txt" || true
  adb shell screencap -p /sdcard/fishino-launch.png || true
  adb pull /sdcard/fishino-launch.png "$out/screen.png" || true
  adb shell uiautomator dump /sdcard/fishino-ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/fishino-ui.xml "$out/ui.xml" >/dev/null 2>&1 || true
  if [ -s "$out/crash.txt" ]; then cat "$out/crash.txt"; fi
}
trap capture EXIT

adb install -r "$apk"
adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/rdsco.fishino.app.MainActivity" | tee "$out/start.txt"
for check in $(seq 1 15); do
  sleep 1
  if ! adb shell pidof "$package" >/dev/null; then
    echo "::error::$label terminated immediately after launch."
    exit 1
  fi
done
adb shell am force-stop "$package"
adb shell am start -W -n "$package/rdsco.fishino.app.MainActivity" | tee "$out/restart.txt"
sleep 5
adb shell pidof "$package" >/dev/null
adb shell uiautomator dump /sdcard/fishino-visible-ui.xml >/dev/null
adb pull /sdcard/fishino-visible-ui.xml "$out/visible-ui.xml" >/dev/null
python3 - "$out/visible-ui.xml" "$package" <<'PY'
import sys
import xml.etree.ElementTree as ET
tree = ET.parse(sys.argv[1])
assert any(node.get("package") == sys.argv[2] for node in tree.iter("node")), "App UI is not visible after launch"
PY
echo "$label stayed alive and rendered its UI across cold start and restart."
