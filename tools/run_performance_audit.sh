#!/usr/bin/env bash
# Keep benchmark status and device diagnostics in one shell. Emulator-runner
# starts a separate shell for each line of its `script` input.
set -uo pipefail

diagnostics=baselineprofile/build/outputs/final-pass-diagnostics
mkdir -p "$diagnostics"
# Missing emulators can leave adb waiting indefinitely; keep diagnostics bounded.
timeout --kill-after=5s 15s adb logcat -c || true
gradle --no-daemon "$@"
benchmark_status=$?

timeout --kill-after=5s 15s adb logcat -d -s ZeroChillBenchmark AndroidRuntime ActivityManager > "$diagnostics/logcat.txt" 2>&1 || true
timeout --kill-after=5s 15s adb shell dumpsys meminfo com.addy37.crazyshitunofficial > "$diagnostics/memory.txt" 2>&1 || true
timeout --kill-after=5s 15s adb shell dumpsys gfxinfo com.addy37.crazyshitunofficial > "$diagnostics/frames.txt" 2>&1 || true
timeout --kill-after=5s 15s adb shell dumpsys thermalservice > "$diagnostics/thermal.txt" 2>&1 || true
timeout --kill-after=5s 15s adb devices -l > "$diagnostics/devices.txt" 2>&1 || true
exit "$benchmark_status"
