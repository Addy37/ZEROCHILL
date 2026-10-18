#!/usr/bin/env bash
set -euo pipefail
comparison="$RUNNER_TEMP/primary-tab-comparison"
phase="${1:-measure}"
mkdir -p "$comparison"
if [[ "$phase" == build ]]; then
  git merge-base --is-ancestor "$BASE_SHA" "$CANDIDATE_SHA"
  cp tools/install_primary_tab_probe.py "$comparison/install_probe.py"
  for label in approved candidate; do
    sha="$BASE_SHA"
    if [[ "$label" == candidate ]]; then sha="$CANDIDATE_SHA"; fi
    git checkout --detach "$sha"
    python3 "$comparison/install_probe.py"
    destination="$comparison/$label"
    mkdir -p "$destination/apk"
    printf '%s\n' "$sha" > "$destination/source-commit.txt"
    gradle --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
      :app:assembleBenchmarkRelease :baselineprofile:assembleBenchmarkRelease
    cp app/build/outputs/apk/benchmarkRelease/*.apk "$destination/apk/app.apk"
    cp baselineprofile/build/outputs/apk/benchmarkRelease/*.apk "$destination/apk/benchmark.apk"
    git restore app/src/main/java/com/webapp/crazyshit/NativeMainActivity.java
  done
  git checkout --detach "$CANDIDATE_SHA"
  exit 0
fi

# Both APK pairs are built before booting the emulator. Keep compilation memory
# separate from the device and run instrumentation directly, as Android documents.
for label in approved candidate; do
  destination="$comparison/$label"
  adb install -r "$destination/apk/app.apk"
  adb install -r "$destination/apk/benchmark.apk"
  instrumentation="$(adb shell pm list instrumentation | awk '$1 ~ /crazyshit.baselineprofile/ {sub(/^instrumentation:/,"",$1);print $1;exit}')"
  test_package="${instrumentation%%/*}"
  test -n "$instrumentation"
  adb shell pm clear com.addy37.crazyshitunofficial >/dev/null
  adb shell pm clear "$test_package" >/dev/null
  adb logcat -c
  result=0
  timeout --kill-after=10s 720s adb shell am instrument -w -r \
    -e class 'com.webapp.crazyshit.baselineprofile.MainFlowsBenchmark#retainedTabSwitching' \
    -e androidx.benchmark.enabledRules Macrobenchmark \
    -e androidx.benchmark.suppressErrors EMULATOR \
    "$instrumentation" \
    | tee "$destination/instrumentation.txt" || result=$?
  timeout --kill-after=5s 15s adb logcat -d -s ZeroChillTabProbe AndroidRuntime > "$destination/tab-timings.txt" || true
  timeout --kill-after=5s 15s adb shell dumpsys meminfo com.addy37.crazyshitunofficial > "$destination/memory.txt" || true
  timeout --kill-after=5s 15s adb shell dumpsys gfxinfo com.addy37.crazyshitunofficial > "$destination/frames.txt" || true
  timeout --kill-after=5s 15s adb devices -l > "$destination/devices.txt" || true
  timeout --kill-after=5s 30s adb pull "/sdcard/Android/media/$test_package/" "$destination/device-output" || true
  if ! grep -qF 'OK (1 test)' "$destination/instrumentation.txt"; then result=1; fi
  printf '%s\n' "$result" > "$destination/exit-code.txt"
  if [[ "$result" != 0 ]]; then exit "$result"; fi
done
python3 - "$comparison" <<'PY'
import json, pathlib, re, statistics, sys
for label in ('approved', 'candidate'):
    path = pathlib.Path(sys.argv[1], label)
    text = (path/'tab-timings.txt').read_text()
    for metric in ('TAP_DISPATCH', 'TAB_VISIBLE'):
        for page, name in ((1,'Shows'),(3,'OnlyFap'),(4,'Library')):
            values = [float(v) for v in re.findall(rf'{metric} page={page} latencyMs=([0-9.]+)',text)]
            print(json.dumps(dict(build=label, metric=metric, tab=name, samples=len(values),
                median_ms=statistics.median(values) if values else None,
                maximum_ms=max(values) if values else None)))
PY
