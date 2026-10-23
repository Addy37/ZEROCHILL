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

# Use the real production ShitTok player and TextureView with a local fixture clip.
for paused in (False, True):
    adb('shell', 'am', 'force-stop', package)
    adb('logcat', '-c')
    launch = adb('shell', 'am', 'start', '-W', '-n',
        package + '/com.webapp.crazyshit.SearchShitTokProbeActivity',
        '--ez', 'manual_pause', str(paused).lower())
    assert b'Error' not in launch, launch
    deadline = time.monotonic() + 22
    while True:
        log = adb('logcat', '-d', '-s', 'ZeroChillSearchVideo:I', 'AndroidRuntime:E').decode(errors='replace')
        if 'READY live selected video' in log or 'FAIL' in log or 'FATAL EXCEPTION' in log or time.monotonic() >= deadline:
            break
        time.sleep(.2)
    (out / f'shittok-search-manual-{paused}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    assert 'READY live selected video' in log and 'FAIL' not in log and 'FATAL EXCEPTION' not in log, log
    # Two settled frames prove the glass is sampling moving content, not just a moving
    # TextureView hidden behind a stale glass recording. Ignore the native row ink.
    import io
    from PIL import Image, ImageChops
    first = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')
    time.sleep(.43)
    second = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')
    first.save(out / f'shittok-motion-a-{paused}.png')
    second.save(out / f'shittok-motion-b-{paused}.png')
    if not paused:
        # Right edge inside the Recent Searches pane, clear of labels and buttons.
        diff = ImageChops.difference(first.crop((620, 230, 652, 560)), second.crop((620, 230, 652, 560)))
        changed = sum(max(pixel) > 4 for pixel in diff.getdata())
        assert changed > 20, f'Search glass did not sample motion: {changed} changed pixels'
    adb('shell', 'input', 'tap', '180', '145')
    time.sleep(1)
    (out / f'shittok-search-keyboard-manual-{paused}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(.5)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    deadline = time.monotonic() + 17
    while True:
        log = adb('logcat', '-d', '-s', 'ZeroChillSearchVideo:I', 'AndroidRuntime:E').decode(errors='replace')
        if 'PASS live video' in log or 'FAIL' in log or 'FATAL EXCEPTION' in log or time.monotonic() >= deadline:
            break
        time.sleep(.2)
    (out / f'shittok-search-manual-{paused}.txt').write_text(log)
    (out / f'shittok-return-manual-{paused}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    assert 'PASS live video' in log and 'FAIL' not in log and 'FATAL EXCEPTION' not in log, log
# Actual background while Search is open, separate from overlay dismissal.
adb('shell', 'am', 'force-stop', package)
adb('logcat', '-c')
adb('shell', 'am', 'start', '-W', '-n', package + '/com.webapp.crazyshit.SearchShitTokProbeActivity')
deadline = time.monotonic() + 22
while time.monotonic() < deadline:
    log = adb('logcat', '-d', '-s', 'ZeroChillSearchVideo:I', 'AndroidRuntime:E').decode(errors='replace')
    if 'READY live selected video' in log or 'FAIL' in log:
        break
    time.sleep(.2)
assert 'READY live selected video' in log and 'FAIL' not in log, log
adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
time.sleep(.5)
log = adb('logcat', '-d', '-s', 'ZeroChillSearchVideo:I', 'AndroidRuntime:E').decode(errors='replace')
(out / 'shittok-background.txt').write_text(log)
assert 'PASS background released playback' in log and 'FAIL' not in log, log
print('Live ShitTok texture, advancing position, moving glass, silence, same-surface return and background release passed.')
