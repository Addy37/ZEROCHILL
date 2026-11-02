"""Render the real OnlyFap/Glide fixture on an emulator; retain evidence."""
import pathlib
import subprocess
import time

out = pathlib.Path('liquid-rift-check')
out.mkdir(exist_ok=True)
package = 'com.addy37.crazyshitunofficial.dev'


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=35)


try:
    release = adb('shell', 'getprop', 'ro.build.version.release').decode().strip()
    sdk = adb('shell', 'getprop', 'ro.build.version.sdk').decode().strip()
    (out / 'android-version.txt').write_text(f'Android {release}, API {sdk}\n')
    apk = next(pathlib.Path('.').glob('ZeroChill-Beta-*.apk'))
    adb('install', '-r', str(apk))
    for setting in ('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale'):
        adb('shell', 'settings', 'put', 'global', setting, '1')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'input', 'keyevent', '82')
    # Run twice to exercise view/activity destruction and a fresh entry.
    for attempt in range(2):
        adb('shell', 'am', 'force-stop', package)
        adb('logcat', '-c')
        launch = adb('shell', 'am', 'start', '-W', '-n',
                     f'{package}/com.webapp.crazyshit.OnlyFapTransitionProbeActivity')
        (out / f'launch-{attempt}.txt').write_bytes(launch)
        assert b'Error' not in launch, launch
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            log = adb('logcat', '-d', '-s', 'ZeroChillTransition:I', 'AndroidRuntime:E').decode(errors='replace')
            if 'PASS real Glide' in log or 'FAIL' in log or 'FATAL EXCEPTION' in log:
                break
            time.sleep(.25)
        (out / f'transition-{attempt}.txt').write_text(log)
        (out / f'settled-{attempt}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        assert 'PASS real Glide' in log and 'FAIL' not in log and 'FATAL EXCEPTION' not in log, log
        adb('shell', 'am', 'send-trim-memory', package, 'RUNNING_CRITICAL')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'am', 'start', '-W', '-n',
            f'{package}/com.webapp.crazyshit.OnlyFapTransitionProbeActivity')
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    print('PASS real Glide/rift rapid replacement, reentry and lifecycle smoke check')
finally:
    (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime'))
