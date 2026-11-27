#!/usr/bin/env bash
set -euo pipefail

package=com.addy37.crazyshitunofficial
# Tests launch activities explicitly. The disposable emulator's home launcher
# must not cover the app with its own startup crash or ANR dialog.
launcher=com.google.android.apps.nexuslauncher
if adb shell pm path "$launcher" | grep -q '^package:'; then
    adb shell am force-stop "$launcher"
    adb shell pm disable-user --user 0 "$launcher"
fi
status=0
for release in 7.4.0=7004000 7.3.0=7003000; do
    baseline="${release%=*}"
    previous_code="${release#*=}"
    adb uninstall "$package" || true  # Disposable emulator only.
    if ! python3 tools/verify_release_upgrade.py \
        --previous "$RUNNER_TEMP/$baseline/ZeroChill.apk" --candidate "$CANDIDATE_APK" \
        --version "$APP_VERSION_NAME" --code "$APP_VERSION_CODE" \
        --earlier-code "$previous_code" --check-launcher \
        --output "upgrade-results/$baseline"; then
        adb shell dumpsys window windows > "upgrade-results/$baseline/windows-on-failure.txt" || true
        adb logcat -d -v threadtime > "upgrade-results/$baseline/logcat-on-failure.txt" || true
        status=1
    fi
done

adb uninstall "$package"
adb install "$CANDIDATE_APK"
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -n "$package/com.webapp.crazyshit.SplashActivity"
sleep 3
mkdir -p upgrade-results/fresh
adb shell dumpsys activity activities > upgrade-results/fresh/activities.txt
grep -E 'ResumedActivity.*StartupWizardActivity' upgrade-results/fresh/activities.txt
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools python3 - <<'PYVERIFY'
from pathlib import Path
from verify_release_upgrade import ui_dump
Path('upgrade-results/fresh/onboarding-ui.xml').write_text(ui_dump())
PYVERIFY
printf '%s\n' '{"fresh_install_opened_startup_wizard":true}' > upgrade-results/fresh/result.json
exit "$status"
