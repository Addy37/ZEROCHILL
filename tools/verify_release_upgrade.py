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
        else: ET.SubElement(root, 'string', name=name).text = value
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--previous', required=True)
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
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
    first_install = re.search(r'firstInstallTime=(.*)', before)[1].strip()
    fixtures = {
        'shared_prefs/app_prefs.xml': prefs(startup_wizard_v1_complete=True,
            access_notice_2_8_3_accepted=True, release_gate_marker='keep-7.0.4',
            minimize_on_back=False, swipe_down_minimize=False),
        'shared_prefs/watch_later.xml': prefs(items=json.dumps([{'title':'Release gate saved video',
            'url':'https://example.invalid/release-gate', 'savedAt':int(time.time()*1000),
            'mediaFavorite':True,'watchLater':True}])),
        'shared_prefs/playback_history.xml': prefs(items=json.dumps([{'title':'Release gate history',
            'pageUrl':'https://example.invalid/release-history', 'positionMs':1234,
            'durationMs':60000,'lastWatched':int(time.time()*1000)}])),
        'files/release-gate-backup.json': b'{"release_gate":"existing-backup-preserved"}\n',
    }
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
    assert 'versionCode=7000005' in after and 'versionName=7.0.5' in after
    for relative, data in fixtures.items():
        actual = subprocess.check_output(['adb','exec-out','cat', DATA + '/' + relative])
        assert actual == data, f'Existing data changed during install: {relative}'
    assert expected_download in shell('sha256sum', download)
    adb('logcat', '-c')
    launch = shell('am', 'start', '-W', '-n', PKG + '/com.webapp.crazyshit.NativeMainActivity')
    assert 'Status: ok' in launch, launch
    time.sleep(3)
    assert shell('pidof', PKG).strip(), 'App exited at startup'
    (args.output / 'memory-start.txt').write_text(shell('dumpsys', 'meminfo', PKG))
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
    assert next(n.text for n in app_prefs if n.get('name') == 'release_gate_marker') == 'keep-7.0.4'
    for relative in ('shared_prefs/watch_later.xml', 'shared_prefs/playback_history.xml', 'files/release-gate-backup.json'):
        assert subprocess.check_output(['adb','exec-out','cat',DATA+'/'+relative]) == fixtures[relative]
    assert expected_download in shell('sha256sum', download)
    (args.output / 'result.json').write_text(json.dumps({'installed_update':True,
        'first_install_preserved':True, 'seeded_saved_stores_and_download_preserved':True,
        'offline_startup_and_navigation':True, 'android':shell('getprop','ro.build.version.release').strip(),
        'limitations':'Synthetic emulator data; no live playback/login or OEM crash reproduction.'}, indent=2))
    temporary.unlink()
    print((args.output / 'result.json').read_text())


if __name__ == '__main__':
    main()
