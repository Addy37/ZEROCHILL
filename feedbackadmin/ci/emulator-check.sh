#!/usr/bin/env bash
set -euo pipefail
# Synthetic tokens must never reach the hosted admin API. Host-side Gradle downloads remain available.
adb shell svc wifi disable
adb shell svc data disable
UPGRADE_RESULT=0
if [ -f baseline-admin.apk ]; then
  bash feedbackadmin/ci/token-upgrade-check.sh || UPGRADE_RESULT=$?
fi
RESULT=0
gradle --no-daemon :feedbackadmin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.addy37.crazyshitadmin.ConsoleFlowTest || RESULT=$?
adb pull /sdcard/Android/data/com.addy37.crazyshitadmin/files/console-review console-review || true
if [ "$RESULT" -ne 0 ]; then exit "$RESULT"; fi
exit "$UPGRADE_RESULT"
