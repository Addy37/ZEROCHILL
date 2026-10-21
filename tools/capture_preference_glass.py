"""Small native GPU check using the existing debug fixture and upgrade emulator."""
import pathlib
import subprocess
import time

out = pathlib.Path('preference-glass-check')
out.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args])

apk = next(pathlib.Path('.').glob('ZeroChill-Beta-*.apk'))
adb('install', '-r', str(apk))
for setting in ('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale'):
    adb('shell', 'settings', 'put', 'global', setting, '1')
for mode in (0, 1):
    adb('shell', 'am', 'force-stop', 'com.addy37.crazyshitunofficial.dev')
    adb('logcat', '-c')
    recording = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '3',
        '/sdcard/preference-formation.mp4']) if mode == 0 else None
    adb('shell', 'am', 'start', '-W', '-n',
        'com.addy37.crazyshitunofficial.dev/com.webapp.crazyshit.GlassRenderProbeActivity',
        '--ei', 'mode', str(mode), '--ez', 'preference', 'true')
    time.sleep(2)
    (out / f'panel-{mode}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    log = adb('logcat', '-d', '-s', 'ZeroChillGlass:I', 'AndroidRuntime:E').decode(errors='replace')
    (out / f'renderer-{mode}.txt').write_text(log)
    assert 'preference renderer=lens' in log, log
    assert 'preference glass fallback' not in log and 'FATAL EXCEPTION' not in log, log
    if recording is not None:
        recording.wait(timeout=10)
        adb('pull', '/sdcard/preference-formation.mp4', str(out / 'formation.mp4'))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
print('Native preference lens rendered on both fixture backgrounds; back dismissal completed.')
