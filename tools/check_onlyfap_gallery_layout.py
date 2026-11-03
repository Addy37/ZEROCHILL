"""Run the synthetic real-adapter gallery fixture on a Pixel-profile emulator."""
import pathlib
import subprocess
import time

out = pathlib.Path('gallery-layout-check')
out.mkdir(exist_ok=True)
package = 'com.addy37.crazyshitunofficial.dev'


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=40)


try:
    (out / 'device.txt').write_text('\n'.join(
        adb('shell', 'getprop', prop).decode().strip() for prop in
        ('ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.fingerprint')))
    apk = next(pathlib.Path('.').glob('ZeroChill-Beta-*.apk'))
    adb('install', '-r', str(apk))
    for setting in ('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale'):
        adb('shell', 'settings', 'put', 'global', setting, '1')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'input', 'keyevent', '82')
    for attempt in range(2):
        adb('shell', 'am', 'force-stop', package)
        adb('logcat', '-c')
        launch = adb('shell', 'am', 'start', '-W', '-n',
                     f'{package}/com.webapp.crazyshit.OnlyFapGalleryLayoutProbeActivity')
        (out / f'launch-{attempt}.txt').write_bytes(launch)
        assert b'Error' not in launch, launch
        pid = adb('shell', 'pidof', package).decode().strip().split()[0]
        deadline = time.monotonic() + 60
        log = ''
        while time.monotonic() < deadline:
            log = adb('logcat', '-d', '--pid', pid, '-s', 'ZeroChillGalleryLayout:I', 'AndroidRuntime:E').decode(errors='replace')
            if 'PASS mixed ratios' in log or 'FAIL' in log or 'FATAL EXCEPTION' in log:
                break
            time.sleep(.25)
        (out / f'gallery-{attempt}.txt').write_text(log)
        (out / f'gallery-{attempt}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        assert 'PASS mixed ratios' in log and 'FAIL' not in log and 'FATAL EXCEPTION' not in log, log
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    print('PASS real gallery/Glide geometry, delayed dimensions, favorites, recycling, tabs and reopen')
finally:
    (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime'))
