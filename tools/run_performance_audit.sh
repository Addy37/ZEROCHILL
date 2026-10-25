#!/usr/bin/env bash
# Keep benchmark status and device diagnostics in one shell. Emulator-runner
# starts a separate shell for each line of its `script` input.
set -uo pipefail

diagnostics=baselineprofile/build/outputs/final-pass-diagnostics
mkdir -p "$diagnostics"
# A missing emulator is a host failure, not an Android assertion. Preserve the
# host's memory/process history and kernel evidence before emulator-runner cleanup.
(
    while :; do
        date -u +%FT%TZ
        free -m
        ps -eo pid,ppid,rss,comm --sort=-rss | head -n 16
        sleep 5
    done
) > "$diagnostics/host-memory.txt" 2>&1 &
diagnostics_pid=$!
trap 'kill "$diagnostics_pid" 2>/dev/null || true' EXIT
# Missing emulators can leave adb waiting indefinitely; keep diagnostics bounded.
timeout --kill-after=5s 15s adb logcat -c || true
gradle --no-daemon "$@"
benchmark_status=$?
sudo -n dmesg --ctime 2>&1 | tail -n 200 > "$diagnostics/host-kernel.txt" || true
shopt -s nullglob
crash_reports=(/tmp/android-runner/emu-crash-*.db)
if ((${#crash_reports[@]})); then
    tar -czf "$diagnostics/emulator-crashes.tgz" "${crash_reports[@]}" \
        2> "$diagnostics/emulator-crashes.txt" || true
fi

timeout --kill-after=5s 15s adb logcat -d -s ZeroChillBenchmark AndroidRuntime ActivityManager XMegaDrive chromium cr_WebViewFactory > "$diagnostics/logcat.txt" 2>&1 || true
timeout --kill-after=5s 15s adb shell dumpsys meminfo com.addy37.crazyshitunofficial > "$diagnostics/memory.txt" 2>&1 || true
timeout --kill-after=5s 15s adb shell dumpsys gfxinfo com.addy37.crazyshitunofficial > "$diagnostics/frames.txt" 2>&1 || true
timeout --kill-after=5s 15s adb shell dumpsys thermalservice > "$diagnostics/thermal.txt" 2>&1 || true
timeout --kill-after=5s 15s adb devices -l > "$diagnostics/devices.txt" 2>&1 || true
exit "$benchmark_status"
