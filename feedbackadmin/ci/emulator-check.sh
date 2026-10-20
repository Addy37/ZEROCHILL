#!/usr/bin/env bash
set -euo pipefail

if [ -f baseline-admin.apk ]; then
  adb install -r baseline-admin.apk
  adb install -r feedbackadmin/build/outputs/apk/androidTest/debug/feedbackadmin-debug-androidTest.apk
  adb shell am instrument -w -e class com.addy37.crazyshitadmin.TokenUpgradeTest#seed com.addy37.crazyshitadmin.test/androidx.test.runner.AndroidJUnitRunner > upgrade-seed.txt
  cat upgrade-seed.txt
  grep -q 'OK (1 test)' upgrade-seed.txt
  adb install -r feedbackadmin/build/outputs/apk/release/feedbackadmin-release.apk
  adb shell am instrument -w -e class com.addy37.crazyshitadmin.TokenUpgradeTest#verify com.addy37.crazyshitadmin.test/androidx.test.runner.AndroidJUnitRunner > upgrade-verify.txt
  cat upgrade-verify.txt
  grep -q 'OK (1 test)' upgrade-verify.txt
fi
RESULT=0
gradle --no-daemon :feedbackadmin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.addy37.crazyshitadmin.ConsoleFlowTest || RESULT=$?
adb pull /sdcard/Android/data/com.addy37.crazyshitadmin/files/console-review console-review || true
exit "$RESULT"
