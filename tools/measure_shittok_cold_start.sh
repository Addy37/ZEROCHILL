#!/usr/bin/env bash
set -euo pipefail
# A lost emulator must not turn three short samples into a twenty-minute wait.
adb_binary="$(command -v adb)"
adb() { timeout --kill-after=2s 12s "$adb_binary" "$@"; }
package=com.addy37.crazyshitunofficial.dev
output="${1:?output directory}"
mkdir -p "$output"
trap 'result=$?; if (( result != 0 )); then echo "Measurement command failed: $result" > "$output/harness-failure.txt"; timeout --kill-after=1s 3s "$adb_binary" devices -l > "$output/devices.txt" 2>&1 || true; fi' EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop "$package"
# Seed only onboarding completion in a disposable CI install. Preserve caches across cold processes.
adb shell run-as "$package" mkdir -p shared_prefs
printf '%s\n' '<?xml version="1.0" encoding="utf-8"?><map><boolean name="startup_wizard_v1_complete" value="true"/><boolean name="access_notice_2_8_3_accepted" value="true"/></map>' | adb shell run-as "$package" sh -c '"cat > shared_prefs/app_prefs.xml"'
for sample in 1 2 3; do
  adb shell am force-stop "$package"
  adb logcat -c
  # Stream before launch so a later ADB failure cannot truncate the only phase evidence.
  timeout --kill-after=2s 50s "$adb_binary" logcat -v threadtime -s ZeroChillStartup:I AndroidRuntime:E > "$output/sample-$sample.txt" 2>&1 &
  log_pid=$!
  adb shell am start -W -n "$package/com.webapp.crazyshit.SplashActivity" > "$output/launch-$sample.txt"
  deadline=$((SECONDS + 40))
  while (( SECONDS < deadline )); do
    if grep -q 'frame processMs=' "$output/sample-$sample.txt"; then break; fi
    sleep 1
  done
  kill "$log_pid" 2>/dev/null || true
  wait "$log_pid" 2>/dev/null || true
  cat "$output/sample-$sample.txt"
  if ! grep -q 'frame processMs=' "$output/sample-$sample.txt"; then
    echo "No rendered frame within 40 seconds" | tee "$output/missing-frame-$sample.txt"
  fi
  adb shell screencap -p /sdcard/shittok-startup.png
  adb pull /sdcard/shittok-startup.png "$output/sample-$sample.png"
done
