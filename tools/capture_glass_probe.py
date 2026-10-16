"""GPU evidence through the real navbar: optical A/B, production Shows views, TextureView.
Emulator timings describe this fixture only, never physical-device parity.
"""
import os, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
out = pathlib.Path(os.getenv('GLASS_PROBE_OUTPUT', 'glass-probe-output')); out.mkdir(exist_ok=True, parents=True)
package = 'com.addy37.crazyshitunofficial.dev'
activity = package + '/com.webapp.crazyshit.GlassRenderProbeActivity'
def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True)
def screenshot(name):
    (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p').stdout)
def start(mode=0, moving=False, collapsed=False, no_lens=False):
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', activity, '--ei', 'mode', str(mode),
        '--ez', 'moving', str(moving).lower(), '--ez', 'collapsed', str(collapsed).lower(),
        '--ez', 'probe_no_lens', str(no_lens).lower())
    time.sleep(1.5)
def node_bounds(label):
    adb('shell', 'uiautomator', 'dump', '/sdcard/glass-ui.xml')
    tree = ET.fromstring(adb('shell', 'cat', '/sdcard/glass-ui.xml').stdout)
    node = next(n for n in tree.iter('node') if n.get('text') == label or n.get('content-desc') == label)
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))
def record(name, seconds=8):
    adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
    adb('shell', 'screenrecord', '--time-limit', str(seconds), '/sdcard/glass-motion.mp4')
    adb('pull', '/sdcard/glass-motion.mp4', str(out / (name + '.mp4')))
    (out / (name + '-gfxinfo.txt')).write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package, 'framestats').stdout)
adb('install', '-r', os.getenv('GLASS_PROBE_APK', 'app/build/outputs/apk/debug/app-debug.apk'))
adb('logcat', '-c')
# Identical static content, film, blur, geometry. Only shell lens strength changes.
for no_lens, label in [(True, 'blur-control'), (False, 'refractive')]:
    start(no_lens=no_lens); screenshot(label)
for overlay, name in [('com.android.internal.systemui.navbar.gestural', 'gesture'),
                      ('com.android.internal.systemui.navbar.threebutton', 'buttons')]:
    adb('shell', 'cmd', 'overlay', 'enable-exclusive', '--category', overlay)
    for collapsed in [False, True]:
        start(2, moving=True, collapsed=collapsed)
        suffix = name + ('-collapsed' if collapsed else '-expanded')
        screenshot(suffix)
        # A full selector drag includes hold, velocity deformation and release spring.
        if not collapsed:
            x1,y1,x2,y2 = node_bounds('ShitTok')
            a1,b1,a2,b2 = node_bounds('Library')
            adb('shell', 'input', 'swipe', str((x1+x2)//2), str((y1+y2)//2),
                str((a1+a2)//2), str((b1+b2)//2), '1400')
        record(suffix)
        # Background/foreground uses the same instance and recorded page lifecycle.
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME'); time.sleep(.3)
        adb('shell', 'am', 'start', '-W', '-n', activity); time.sleep(.5)
        screenshot(suffix + '-returned')
start(3); record('media3-textureview')
(out / 'meminfo.txt').write_bytes(adb('shell', 'dumpsys', 'meminfo', package).stdout)
(out / 'logcat.txt').write_bytes(adb('logcat', '-d', '-v', 'threadtime').stdout)
logs = (out / 'logcat.txt').read_text(errors='replace')
if 'FATAL EXCEPTION' in logs or (os.getenv('GLASS_PROBE_REQUIRE_REFRACTION') == 'true'
        and ('Square shader=Refraction' not in logs or 'probe video surface=TextureView' not in logs)):
    raise SystemExit('Missing actual shader/TextureView execution or application crash. Inspect logcat.')
