#!/usr/bin/env bash
set -euo pipefail
git merge-base --is-ancestor "$BASE_SHA" "$CANDIDATE_SHA"
mkdir -p "$RUNNER_TEMP/pinch-comparison"
for label in approved candidate; do
  sha="$BASE_SHA"
  if [[ "$label" == candidate ]]; then sha="$CANDIDATE_SHA"; fi
  git checkout --detach "$sha"
  # Fresh app data/cache for each source; every measured iteration is a warm launch.
  adb shell pm clear com.addy37.crazyshitunofficial >/dev/null 2>&1 || true
  destination="$RUNNER_TEMP/pinch-comparison/$label"
  mkdir -p "$destination"
  printf '%s\n' "$sha" > "$destination/source-commit.txt"
  result=0
  bash tools/run_performance_audit.sh :baselineprofile:connectedBenchmarkReleaseAndroidTest \
    '-Pandroid.testInstrumentationRunnerArguments.class=com.webapp.crazyshit.baselineprofile.MainFlowsBenchmark#chaosScroll' \
    '-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark' || result=$?
  cp -a baselineprofile/build/outputs/. "$destination/" || true
  printf '%s\n' "$result" > "$destination/exit-code.txt"
  if [[ "$result" != 0 ]]; then exit "$result"; fi
done
git checkout --detach "$CANDIDATE_SHA"
