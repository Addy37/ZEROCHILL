"""Small native GPU check using the existing debug fixture and upgrade emulator."""
import atexit
import pathlib
import subprocess
import time

out = pathlib.Path('preference-glass-check')
out.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=35)

@atexit.register
def diagnostics():
    for name, args in [('logcat.txt', ('logcat', '-d', '-v', 'threadtime')),
                       ('activity.txt', ('shell', 'dumpsys', 'activity', 'top')),
                       ('window.txt', ('shell', 'dumpsys', 'window', 'windows'))]:
        result = subprocess.run(['adb', *args], capture_output=True, timeout=35)
        (out / name).write_bytes(result.stdout)

apk = next(pathlib.Path('.').glob('ZeroChill-Beta-*.apk'))
adb('install', '-r', str(apk))
# Match the existing glass fixture's fresh-emulator preparation.
for launcher in ('com.android.launcher3', 'com.google.android.apps.nexuslauncher'):
    adb('shell', 'am', 'force-stop', launcher)
adb('shell', 'wm', 'size', '720x1600')
adb('shell', 'wm', 'density', '320')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'input', 'keyevent', '82')
for setting in ('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale'):
    adb('shell', 'settings', 'put', 'global', setting, '1')
for mode in (0, 1):
    adb('shell', 'am', 'force-stop', 'com.addy37.crazyshitunofficial.dev')
    adb('logcat', '-c')
    recording = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '4',
        '/sdcard/preference-formation.mp4']) if mode == 0 else None
    launch = adb('shell', 'am', 'start', '-W', '-n',
        'com.addy37.crazyshitunofficial.dev/com.webapp.crazyshit.GlassRenderProbeActivity',
        '--ei', 'mode', str(mode), '--ez', 'preference', 'true')
    (out / f'launch-{mode}.txt').write_bytes(launch)
    assert b'Error' not in launch, launch
    deadline = time.monotonic() + 12
    while True:
        log = adb('logcat', '-d', '-s', 'ZeroChillGlass:I', 'AndroidRuntime:E').decode(errors='replace')
        if 'preference renderer=lens' in log or 'FATAL EXCEPTION' in log or time.monotonic() >= deadline:
            break
        time.sleep(.25)
    time.sleep(.5)  # Capture the settled state after the bounded 320ms formation.
    (out / f'panel-{mode}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    (out / f'renderer-{mode}.txt').write_text(log)
    if recording is not None:
        recording.wait(timeout=10)
        adb('pull', '/sdcard/preference-formation.mp4', str(out / 'formation.mp4'))
    assert 'preference renderer=lens' in log, log
    assert 'preference glass fallback' not in log and 'FATAL EXCEPTION' not in log, log
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
print('Native preference lens rendered on both fixture backgrounds; back dismissal completed.')
