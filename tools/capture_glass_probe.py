"""Debug-only hardware rendering evidence. Synthetic motion is not a playback benchmark."""
import os, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
out = pathlib.Path(os.getenv('GLASS_PROBE_OUTPUT', 'glass-probe-output')); out.mkdir(exist_ok=True, parents=True)
package = 'com.addy37.crazyshitunofficial.dev'
activity = package + '/com.webapp.crazyshit.GlassRenderProbeActivity'
def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True)
def screenshot(name):
    (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p').stdout)
def start(mode=0, moving=False):
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', activity, '--ei', 'mode', str(mode), '--ez', 'moving', str(moving).lower())
    time.sleep(1)
adb('install', '-r', os.getenv('GLASS_PROBE_APK', 'app/build/outputs/apk/debug/app-debug.apk'))
adb('logcat', '-c')
for overlay, name in [('com.android.internal.systemui.navbar.gestural', 'gesture'),
                      ('com.android.internal.systemui.navbar.threebutton', 'buttons')]:
    adb('shell', 'cmd', 'overlay', 'enable-exclusive', '--category', overlay)
    for mode, label in [(0, 'bright'), (1, 'black')]:
        start(mode)
        screenshot(name + '-' + label)
        adb('shell', 'uiautomator', 'dump', '/sdcard/glass-ui.xml')
        tree = ET.fromstring(adb('shell', 'cat', '/sdcard/glass-ui.xml').stdout)
        target = next(node for node in tree.iter('node') if node.get('text') == 'Library')
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', target.get('bounds')))
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(.35)
        screenshot(name + '-' + label + '-selected')
start(0, True)
adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
adb('shell', 'screenrecord', '--time-limit', '12', '/sdcard/glass-motion.mp4')
screenshot('moving')
(out / 'gfxinfo.txt').write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package, 'framestats').stdout)
(out / 'meminfo.txt').write_bytes(adb('shell', 'dumpsys', 'meminfo', package).stdout)
(out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime').stdout)
adb('pull', '/sdcard/glass-motion.mp4', str(out / 'glass-motion.mp4'))
logs = (out / 'logcat.txt').read_text(errors='replace')
if ('FATAL EXCEPTION' in logs or (os.getenv('GLASS_PROBE_REQUIRE_REFRACTION') == 'true'
        and ('tier=refractive' not in logs or 'Refraction unavailable' in logs or 'Backdrop unavailable' in logs))):
    raise SystemExit('Renderer fallback or application crash. Inspect logcat.')
