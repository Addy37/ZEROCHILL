#!/usr/bin/env bash
set -euo pipefail
package=com.addy37.crazyshitunofficial.dev
output="${1:?output directory}"
mkdir -p "$output"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop "$package"
# Seed only onboarding completion in a disposable CI install. Preserve caches across cold processes.
adb shell run-as "$package" mkdir -p shared_prefs
printf '%s\n' '<?xml version="1.0" encoding="utf-8"?><map><boolean name="startup_wizard_v1_complete" value="true"/><boolean name="access_notice_2_8_3_accepted" value="true"/></map>' | adb shell run-as "$package" sh -c '"cat > shared_prefs/app_prefs.xml"'
for sample in 1 2 3; do
  adb shell am force-stop "$package"
  adb logcat -c
  adb shell am start -W -n "$package/com.webapp.crazyshit.SplashActivity" > "$output/launch-$sample.txt"
  deadline=$((SECONDS + 40))
  while (( SECONDS < deadline )); do
    adb logcat -d -s ZeroChillStartup:I AndroidRuntime:E > "$output/sample-$sample.txt"
    if grep -q 'frame processMs=' "$output/sample-$sample.txt"; then break; fi
    sleep 1
  done
  cat "$output/sample-$sample.txt"
  if ! grep -q 'frame processMs=' "$output/sample-$sample.txt"; then
    echo "No rendered frame within 40 seconds" | tee "$output/missing-frame-$sample.txt"
  fi
  adb shell screencap -p /sdcard/shittok-startup.png
  adb pull /sdcard/shittok-startup.png "$output/sample-$sample.png"
done
