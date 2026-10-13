#!/usr/bin/env bash
set -euo pipefail
git merge-base --is-ancestor "$BASE_SHA" "$CANDIDATE_SHA"
comparison="$RUNNER_TEMP/primary-tab-comparison"
mkdir -p "$comparison"
# Keep the driver/probe outside the tree while switching to the approved baseline.
cp tools/install_primary_tab_probe.py "$comparison/install_probe.py"
for label in approved candidate; do
  sha="$BASE_SHA"
  if [[ "$label" == candidate ]]; then sha="$CANDIDATE_SHA"; fi
  git checkout --detach "$sha"
  python3 "$comparison/install_probe.py"
  adb shell pm clear com.addy37.crazyshitunofficial >/dev/null 2>&1 || true
  destination="$comparison/$label"
  mkdir -p "$destination"
  printf '%s\n' "$sha" > "$destination/source-commit.txt"
  result=0
  bash tools/run_performance_audit.sh :baselineprofile:connectedBenchmarkReleaseAndroidTest \
    '-Pandroid.testInstrumentationRunnerArguments.class=com.webapp.crazyshit.baselineprofile.MainFlowsBenchmark#retainedTabSwitching' \
    '-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark' || result=$?
  cp -a baselineprofile/build/outputs/. "$destination/" || true
  timeout --kill-after=5s 15s adb logcat -d -s ZeroChillTabProbe > "$destination/tab-timings.txt" || true
  printf '%s\n' "$result" > "$destination/exit-code.txt"
  git restore app/src/main/java/com/webapp/crazyshit/NativeMainActivity.java
  if [[ "$result" != 0 ]]; then exit "$result"; fi
done
git checkout --detach "$CANDIDATE_SHA"
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
