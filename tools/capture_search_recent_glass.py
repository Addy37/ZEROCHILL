"""Capture the production Search panel over the existing GPU backdrop fixture."""
import atexit
import pathlib
import subprocess
import time

out = pathlib.Path('recent-search-glass-check')
out.mkdir(exist_ok=True)
package = 'com.addy37.crazyshitunofficial.dev'

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=35)

@atexit.register
def diagnostics():
    (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime'))

adb('install', '-r', str(next(pathlib.Path('.').glob('ZeroChill-Beta-*.apk'))))
adb('shell', 'wm', 'size', '720x1600')
adb('shell', 'wm', 'density', '320')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'input', 'keyevent', '82')
for setting in ('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale'):
    adb('shell', 'settings', 'put', 'global', setting, '1')
for count, reduced, no_lens in [(0, False, False), (1, False, False), (3, False, False),
                               (10, False, False), (3, True, False), (3, False, True)]:
    name = f'count-{count}-reduced-{reduced}-no-lens-{no_lens}'
    adb('shell', 'am', 'force-stop', package)
    adb('logcat', '-c')
    launch = adb('shell', 'am', 'start', '-W', '-n',
        package + '/com.webapp.crazyshit.GlassRenderProbeActivity',
        '--ez', 'recent_search', 'true', '--ei', 'recent_count', str(count),
        '--ez', 'reduced_motion', str(reduced).lower(),
        '--ez', 'probe_no_lens', str(no_lens).lower())
    assert b'Error' not in launch, launch
    deadline = time.monotonic() + 15
    while True:
        log = adb('logcat', '-d', '-s', 'ZeroChillSearch:I', 'AndroidRuntime:E').decode(errors='replace')
        if 'PASS natural height' in log or 'FAIL' in log or 'FATAL EXCEPTION' in log or time.monotonic() >= deadline:
            break
        time.sleep(.25)
    (out / (name + '.txt')).write_text(log)
    (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    assert 'PASS natural height' in log and 'FAIL' not in log and 'FATAL EXCEPTION' not in log, log
    if count == 10:
        adb('shell', 'input', 'tap', '180', '145')
        time.sleep(1.5)
        (out / 'overflow-keyboard.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        adb('shell', 'input', 'swipe', '330', '730', '330', '300', '350')
        time.sleep(.5)
        (out / 'overflow-scrolled.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    if count == 3 and not no_lens:
        recording = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '5', '/sdcard/recent-height.mp4'])
        # Remove the first query using its unchanged native x control.
        adb('shell', 'input', 'tap', '640', '342')
        recording.wait(timeout=8)
        adb('pull', '/sdcard/recent-height.mp4', str(out / f'remove-reduced-{reduced}.mp4'))
        (out / f'removed-reduced-{reduced}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
print('Recent Search GPU fixture: 0/1/3/10, reduced motion, lens control, IME and removal captured.')
