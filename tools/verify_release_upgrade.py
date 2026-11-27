#!/usr/bin/env python3
"""Offline emulator-only upgrade/data check. Requires a disposable rooted emulator."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = 'com.addy37.crazyshitunofficial'
DATA = '/data/user/0/' + PKG


def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], text=True)


def shell(*args):
    return adb('shell', *args)


def prefs(**values):
    root = ET.Element('map')
    for name, value in values.items():
        if isinstance(value, bool): ET.SubElement(root, 'boolean', name=name, value=str(value).lower())
        elif isinstance(value, int): ET.SubElement(root, 'long', name=name, value=str(value))
        elif isinstance(value, set):
            node = ET.SubElement(root, 'set', name=name)
            for item in sorted(value): ET.SubElement(node, 'string').text = item
        else: ET.SubElement(root, 'string', name=name).text = value
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def tap_node(node):
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    shell('input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def resumed_activity():
    return '\n'.join(line for line in shell('dumpsys', 'activity', 'activities').splitlines()
                     if 'ResumedActivity' in line)


def ui_dump():
    path = '/sdcard/zc-upgrade-ui.xml'
    shell('uiautomator', 'dump', path)
    return shell('cat', path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--previous', required=True)
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    parser.add_argument('--version', default='7.0.5')
    parser.add_argument('--code', type=int, default=7000005)
    parser.add_argument('--earlier-code', type=int)
    parser.add_argument('--check-launcher', action='store_true')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    adb('root'); adb('wait-for-device')
    assert shell('id').startswith('uid=0'), 'Disposable rooted emulator required'
    # Keep both production APKs offline. No test account, feedback, or push registration traffic.
    shell('cmd', 'connectivity', 'airplane-mode', 'enable')
    shell('svc', 'wifi', 'disable'); shell('svc', 'data', 'disable')
    adb('install', args.previous)
    uid = shell('stat', '-c', '%u', DATA).strip()
    before = shell('dumpsys', 'package', PKG)
    if args.earlier_code is not None:
        assert f'versionCode={args.earlier_code} ' in before, 'Wrong previous APK'
    time.sleep(1.1)  # PackageManager's freshness tolerance must not hide the upgrade.
    first_install = re.search(r'firstInstallTime=(.*)', before)[1].strip()
    media = pathlib.Path('app/src/test/resources/shittok-orientation/portrait.mp4').read_bytes()
    local_media = DATA + '/files/release-gate-existing.mp4'
    fixtures = {
        'files/release-gate-existing.mp4': media,
        'shared_prefs/native_video_downloads.xml': prefs(ids={'-446'}, **{'entry_-446': json.dumps({
            'id':-446, 'title':'Release gate existing download',
            'page':'https://example.invalid/release-download', 'media':'https://example.invalid/clip.mp4',
            'mime':'video/mp4', 'created':int(time.time()*1000), 'status':8,
            'downloaded':len(media), 'total':len(media), 'local':'file://' + local_media, 'reason':0})}),
        'shared_prefs/app_prefs.xml': prefs(startup_wizard_v1_complete=True,
            access_notice_2_8_3_accepted=True, release_gate_marker='keep-7.0.4',
            minimize_on_back=False, swipe_down_minimize=False),
        'shared_prefs/watch_later.xml': prefs(items=json.dumps([{'title':'Release gate saved video',
            'url':'https://example.invalid/release-gate', 'savedAt':int(time.time()*1000),
            'mediaFavorite':True,'watchLater':True}])),
        'shared_prefs/playback_history.xml': prefs(items=json.dumps([{'title':'Release gate history',
            'pageUrl':'https://example.invalid/release-history', 'positionMs':34000,
            'durationMs':90000,'lastWatched':int(time.time()*1000)}])),
        'files/release-gate-backup.json': b'{"release_gate":"existing-backup-preserved"}\n',
    }
    if args.check_launcher:
        fixtures['shared_prefs/app_prefs.xml'] = prefs(
            access_notice_2_8_3_accepted=True, release_gate_marker='keep-7.0.4',
            appearance_accent='PURPLE', content_preference='trans_men',
            xmegadrive_access_setup_v1_repair_handled=True,
            remember_video_position=True, player_auto_pip=False,
            new_video_notifications_enabled=False, minimize_on_back=False,
            swipe_down_minimize=False, immersive_motion_enabled=False)
        fixtures['shared_prefs/creator_favorites.xml'] = prefs(creators={'Release fixture creator'})
        fixtures['shared_prefs/shittok_blocked_creators.xml'] = prefs(
            creator_keys={'label:release fixture blocked creator'})
        fixtures['shared_prefs/whats_new_state.xml'] = prefs(
            acknowledged_version_code=args.earlier_code,
            first_install_time=0)
    temporary = args.output / 'fixture'
    for relative, data in fixtures.items():
        temporary.write_bytes(data)
        destination = DATA + '/' + relative
        shell('mkdir', '-p', str(pathlib.PurePosixPath(destination).parent))
        adb('push', temporary, destination)
        shell('chown', uid + ':' + uid, destination)
        shell('chmod', '600', destination)
    shell('chown', '-R', uid + ':' + uid, DATA + '/shared_prefs', DATA + '/files')
    shell('restorecon', '-RF', DATA)
    # Representative existing download bytes in public storage, never a real user file.
    download = '/sdcard/Download/ZEROCHILL-release-gate-existing.bin'
    temporary.write_bytes(b'ZEROCHILL existing download verification\n')
    adb('push', temporary, download)
    expected_download = hashlib.sha256(temporary.read_bytes()).hexdigest()
    adb('install', '-r', args.candidate)
    after = shell('dumpsys', 'package', PKG)
    assert re.search(r'firstInstallTime=(.*)', after)[1].strip() == first_install
    assert f'versionCode={args.code} ' in after and f'versionName={args.version}' in after
    assert shell('stat', '-c', '%u', DATA).strip() == uid, 'App UID changed'
    for relative, data in fixtures.items():
        actual = subprocess.check_output(['adb','exec-out','cat', DATA + '/' + relative])
        assert actual == data, f'Existing data changed during install: {relative}'
    assert expected_download in shell('sha256sum', download)
    adb('logcat', '-c')
    if args.check_launcher:
        launch = shell('am', 'start', '-W', '-a', 'android.intent.action.MAIN',
                       '-c', 'android.intent.category.LAUNCHER',
                       '-n', PKG + '/com.webapp.crazyshit.SplashActivity')
    else:
        launch = shell('am', 'start', '-W', '-n', PKG + '/com.webapp.crazyshit.NativeMainActivity')
    assert 'Status: ok' in launch, launch
    time.sleep(3)
    if args.check_launcher:
        assert 'NativeMainActivity' in resumed_activity(), 'Upgrade unexpectedly left the main flow'
        dump = ui_dump()
        (args.output / 'whats-new-ui.xml').write_text(dump)
        root = ET.fromstring(dump)
        assert any(node.get('text') == 'What’s New' for node in root.iter('node'))
        assert any('five independent feeds' in node.get('text', '') for node in root.iter('node'))
        for _ in range(5):
            acknowledge = next((node for node in root.iter('node')
                                if node.get('text') == 'LET’S GO'), None)
            if acknowledge is not None:
                break
            scroll = next(node for node in root.iter('node')
                          if node.get('class') == 'android.widget.ScrollView')
            left, top, right, bottom = map(int, re.findall(r'\d+', scroll.get('bounds')))
            center = (left + right) // 2
            shell('input', 'swipe', str(center), str(bottom - 40),
                  str(center), str(top + 60), '350')
            root = ET.fromstring(ui_dump())
        assert acknowledge is not None, 'Could not scroll to the acknowledgement button'
        tap_node(acknowledge)
        time.sleep(1)
        state = ET.fromstring(shell('cat', DATA + '/shared_prefs/whats_new_state.xml'))
        assert any(n.get('name') == 'acknowledged_version_code' and n.get('value') == str(args.code)
                   for n in state), 'Clicked popup did not acknowledge this release'
    assert shell('pidof', PKG).strip(), 'App exited at startup'
    (args.output / 'memory-start.txt').write_text(shell('dumpsys', 'meminfo', PKG))
    shell('am', 'start', '-W', '-n', PKG + '/com.webapp.crazyshit.DownloadedActivity')
    time.sleep(2)
    shell('uiautomator', 'dump', '/sdcard/zc-upgrade-ui.xml')
    ui = shell('cat', '/sdcard/zc-upgrade-ui.xml')
    (args.output / 'downloads-ui.xml').write_text(ui)
    root = ET.fromstring(ui)
    actions = [n for n in root.iter('node') if n.get('content-desc') ==
               'Download options for Release gate existing download']
    assert actions, 'Existing download must remain visible in Downloads'
    tap_node(actions[0])
    time.sleep(.5)
    shell('input', 'keyevent', '4')
    shell('input', 'keyevent', '4')
    for screen in ('FavoritesActivity', 'ZeroChillAccountActivity', 'ZeroChillInboxActivity', 'SettingsActivity'):
        shell('am', 'start', '-W', '-n', PKG + '/com.webapp.crazyshit.' + screen)
        shell('input', 'keyevent', '4')
    # Repeated offline secondary navigation and recreation exposes common teardown regressions.
    for rotation in range(6):
        shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
        shell('settings', 'put', 'system', 'user_rotation', str(rotation % 2))
        shell('am', 'start', '-W', '-n', PKG + '/com.webapp.crazyshit.DownloadedActivity')
        shell('input', 'keyevent', '4')
        time.sleep(.5)
    assert shell('pidof', PKG).strip(), 'App exited during navigation'
    (args.output / 'memory-end.txt').write_text(shell('dumpsys', 'meminfo', PKG))
    log = adb('logcat', '-d', '-v', 'threadtime')
    (args.output / 'logcat.txt').write_text(log)
    assert 'FATAL EXCEPTION' not in log, 'Inspect captured fatal exception'
    # Verify semantic app settings and unchanged saved stores after startup, too.
    app_prefs = ET.fromstring(shell('cat', DATA + '/shared_prefs/app_prefs.xml'))
    actual_settings = {n.get('name'): (n.tag, n.get('value'), n.text) for n in app_prefs}
    for n in ET.fromstring(fixtures['shared_prefs/app_prefs.xml']):
        assert actual_settings.get(n.get('name')) == (n.tag, n.get('value'), n.text), n.get('name')
    if args.check_launcher:
        assert actual_settings.get('startup_wizard_v1_complete') == ('boolean', 'true', None)
    for relative, expected in fixtures.items():
        if relative in ('shared_prefs/app_prefs.xml', 'shared_prefs/whats_new_state.xml'):
            continue
        assert subprocess.check_output(['adb', 'exec-out', 'cat', DATA + '/' + relative]) == expected
    assert expected_download in shell('sha256sum', download)
    (args.output / 'result.json').write_text(json.dumps({'installed_update':True,
        'first_install_preserved':True, 'uid_preserved':True,
        'seeded_saved_stores_and_download_preserved':True,
        'launcher_upgrade_popup_acknowledged':args.check_launcher,
        'offline_startup_and_navigation':True, 'android':shell('getprop','ro.build.version.release').strip(),
        'limitations':'Synthetic emulator data; no live playback/login or OEM crash reproduction.'}, indent=2))
    temporary.unlink()
    print((args.output / 'result.json').read_text())


if __name__ == '__main__':
    main()
