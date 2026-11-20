#!/usr/bin/env bash
set -euo pipefail

package=com.addy37.crazyshitunofficial
app_data="/data/user/0/$package"
candidate="$(find candidate -maxdepth 1 -name '*.apk' -print -quit)"
approved="$(find previous-test -name '*.apk' -print -quit)"
test -f "$candidate" && test -f "$approved" && test -f stable/ZeroChill.apk

fixtures="$(mktemp -d)"
trap 'rm -rf "$fixtures"' EXIT
cat > "$fixtures/app_prefs.xml" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="appearance_accent">PURPLE</string><string name="shittok_source_mode">onlyfap</string></map>
XML
cat > "$fixtures/watch_later.xml" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="items">[{"title":"RC saved video","url":"https://example.invalid/saved","savedAt":1700000000000}]</string></map>
XML
cat > "$fixtures/playback_history.xml" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="items">[{"title":"RC watched video","pageUrl":"https://example.invalid/watched","positionMs":12000,"durationMs":60000,"lastWatched":1700000000000}]</string></map>
XML

adb root
adb wait-for-device
test "$(adb shell id -u | tr -d '\r')" = 0

install_stamp() {
    adb shell dumpsys package "$package" | tr -d '\r' | sed -n 's/.*firstInstallTime=//p'
}

verify_upgrade() {
    local previous="$1" label="$2" uid group installed after pref
    adb install "$previous"
    uid="$(adb shell stat -c %u "$app_data" | tr -d '\r')"
    group="$(adb shell stat -c %g "$app_data" | tr -d '\r')"
    installed="$(install_stamp)"
    test -n "$installed" && test "$uid" -ge 10000
    adb shell mkdir -p "$app_data/shared_prefs"
    adb shell chown "$uid:$group" "$app_data/shared_prefs"
    adb shell chmod 771 "$app_data/shared_prefs"
    for pref in app_prefs watch_later playback_history; do
        adb push "$fixtures/$pref.xml" "$app_data/shared_prefs/$pref.xml"
        adb shell chown "$uid:$group" "$app_data/shared_prefs/$pref.xml"
        adb shell chmod 660 "$app_data/shared_prefs/$pref.xml"
    done
    adb shell restorecon -RF "$app_data/shared_prefs"
    adb install -r "$candidate"
    after="$(install_stamp)"
    test "$installed" = "$after"
    test "$(adb shell stat -c %u "$app_data" | tr -d '\r')" = "$uid"
    adb shell dumpsys package "$package" | grep "versionCode=$APP_VERSION_CODE "
    for pref in app_prefs watch_later playback_history; do
        adb exec-out cat "$app_data/shared_prefs/$pref.xml" | cmp "$fixtures/$pref.xml" -
    done
    printf '%s\n' "$label: in-place install kept its original installation, app UID, and seeded settings, saved videos and playback history." | tee -a candidate/UPGRADE-VERIFICATION.txt
    adb uninstall "$package"
}

verify_upgrade stable/ZeroChill.apk 'Stable 7.2.0'
verify_upgrade "$approved" 'Approved production-package test 7002011'
