#!/usr/bin/env bash
# Keep benchmark status and device diagnostics in one shell. Emulator-runner
# starts a separate shell for each line of its `script` input.
set -uo pipefail

diagnostics=baselineprofile/build/outputs/final-pass-diagnostics
mkdir -p "$diagnostics"
adb logcat -c || true
gradle --no-daemon "$@"
benchmark_status=$?

adb logcat -d -s ZeroChillBenchmark AndroidRuntime ActivityManager > "$diagnostics/logcat.txt" 2>&1 || true
adb shell dumpsys meminfo com.addy37.crazyshitunofficial > "$diagnostics/memory.txt" 2>&1 || true
adb shell dumpsys gfxinfo com.addy37.crazyshitunofficial > "$diagnostics/frames.txt" 2>&1 || true
adb shell dumpsys thermalservice > "$diagnostics/thermal.txt" 2>&1 || true
adb devices -l > "$diagnostics/devices.txt" 2>&1 || true
exit "$benchmark_status"
