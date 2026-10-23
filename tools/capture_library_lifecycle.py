"""Scoped GPU Library lifecycle fixture on the existing upgrade emulator."""
import pathlib, subprocess, time
out = pathlib.Path('library-lifecycle-check'); out.mkdir(exist_ok=True)
def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=35)
apk = next(pathlib.Path('.').glob('ZeroChill-Beta-*.apk'))
adb('install', '-r', str(apk))
adb('shell', 'am', 'force-stop', 'com.addy37.crazyshitunofficial.dev')
adb('logcat', '-c')
recording = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', '20', '/sdcard/library-lifecycle.mp4'])
try:
    launch = adb('shell', 'am', 'start', '-W', '-n',
        'com.addy37.crazyshitunofficial.dev/com.webapp.crazyshit.LibraryLifecycleProbeActivity')
    (out / 'launch.txt').write_bytes(launch)
    assert b'Error' not in launch, launch
    deadline = time.monotonic() + 22
    while True:
        log = adb('logcat', '-d', '-s', 'ZeroChillLibrary:I', 'AndroidRuntime:E').decode(errors='replace')
        if 'PASS first-frame' in log or 'FAIL' in log or 'FATAL EXCEPTION' in log or time.monotonic() > deadline:
            break
        time.sleep(.2)
    (out / 'lifecycle.txt').write_text(log)
    (out / 'settled.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    assert 'PASS first-frame' in log and 'FAIL' not in log and 'FATAL EXCEPTION' not in log, log
    print(log)
finally:
    recording.wait(timeout=25)
    adb('pull', '/sdcard/library-lifecycle.mp4', str(out / 'library-lifecycle.mp4'))
    (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime'))
