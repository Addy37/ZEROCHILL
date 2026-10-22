#!/usr/bin/env bash
set -euo pipefail
adb install -r baseline-admin.apk
adb install -r feedbackadmin/build/outputs/apk/androidTest/debug/feedbackadmin-debug-androidTest.apk
adb shell am instrument -w -e class com.addy37.crazyshitadmin.TokenUpgradeTest#seed com.addy37.crazyshitadmin.test/androidx.test.runner.AndroidJUnitRunner > upgrade-seed.txt
cat upgrade-seed.txt
grep -q 'OK (1 test)' upgrade-seed.txt
adb shell am instrument -w -e class com.addy37.crazyshitadmin.TokenUpgradeTest#beforeUpgrade com.addy37.crazyshitadmin.test/androidx.test.runner.AndroidJUnitRunner > upgrade-before.txt
cat upgrade-before.txt
grep -q 'OK (1 test)' upgrade-before.txt
adb shell dumpsys activity activities > upgrade-activity.txt
adb install -r feedbackadmin/build/outputs/apk/release/feedbackadmin-release.apk
adb shell am instrument -w -e class com.addy37.crazyshitadmin.TokenUpgradeTest#verify com.addy37.crazyshitadmin.test/androidx.test.runner.AndroidJUnitRunner > upgrade-verify.txt
cat upgrade-verify.txt
grep -q 'OK (1 test)' upgrade-verify.txt
