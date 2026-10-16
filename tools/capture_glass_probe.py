"""GPU evidence through the real navbar: optical A/B, production Shows views, TextureView.
Emulator timings describe this fixture only, never physical-device parity.
"""
import atexit, os, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
out = pathlib.Path(os.getenv('GLASS_PROBE_OUTPUT', 'glass-probe-output')); out.mkdir(exist_ok=True, parents=True)
package = 'com.addy37.crazyshitunofficial.dev'
activity = package + '/com.webapp.crazyshit.GlassRenderProbeActivity'
def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True, timeout=35)
@atexit.register
def preserve_failure_diagnostics():
    for name, args in [('logcat.txt', ('logcat','-d','-v','threadtime')),
                       ('activity-top.txt', ('shell','dumpsys','activity','top')),
                       ('window.txt', ('shell','dumpsys','window','windows'))]:
        try: (out / name).write_bytes(adb(*args, check=False).stdout)
        except Exception as error: (out / (name + '.error')).write_text(str(error))
def screenshot(name):
    (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p').stdout)
def start(mode=0, moving=False, collapsed=False, no_lens=False):
    adb('shell', 'am', 'force-stop', package)
    launched = adb('shell', 'am', 'start', '-W', '-n', activity, '--ei', 'mode', str(mode),
        '--ez', 'moving', str(moving).lower(), '--ez', 'collapsed', str(collapsed).lower(),
        '--ez', 'probe_no_lens', str(no_lens).lower())
    with (out / 'launches.txt').open('ab') as log: log.write(launched.stdout + launched.stderr)
    if b'Error' in launched.stdout or b'Error' in launched.stderr:
        raise RuntimeError('Probe Activity did not launch')
    time.sleep(1.5)
def node_bounds(label):
    adb('shell', 'uiautomator', 'dump', '/sdcard/glass-ui.xml')
    xml = adb('shell', 'cat', '/sdcard/glass-ui.xml').stdout
    (out / 'last-ui.xml').write_bytes(xml)
    tree = ET.fromstring(xml)
    node = next(n for n in tree.iter('node') if n.get('text') == label or n.get('content-desc') == label)
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))
def record(name, seconds=8, interaction=None):
    adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
    capture = subprocess.Popen(['adb', 'shell', 'screenrecord', '--time-limit', str(seconds), '/sdcard/glass-motion.mp4'])
    time.sleep(.6)
    if interaction is not None: interaction()
    if capture.wait(timeout=seconds+20) != 0: raise RuntimeError('screenrecord failed')
    adb('pull', '/sdcard/glass-motion.mp4', str(out / (name + '.mp4')))
    (out / (name + '-gfxinfo.txt')).write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package, 'framestats').stdout)
adb('install', '-r', os.getenv('GLASS_PROBE_APK', 'app/build/outputs/apk/debug/app-debug.apk'))
adb('logcat', '-c')
# Fresh API 35 images can leave Quickstep's boot-time ANR over every Activity.
# Stop only the emulator launcher, keeping all app failures visible in diagnostics.
for launcher in ['com.android.launcher3', 'com.google.android.apps.nexuslauncher']:
    adb('shell', 'am', 'force-stop', launcher, check=False)
adb('shell', 'wm', 'size', '720x1600')
adb('shell', 'wm', 'density', '320')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'input', 'keyevent', '82')
# Identical static content, film, blur, geometry. Only shell lens strength changes.
for no_lens, label in [(True, 'blur-control'), (False, 'refractive')]:
    start(no_lens=no_lens); screenshot(label)
for overlay, name in [('com.android.internal.systemui.navbar.gestural', 'gesture'),
                      ('com.android.internal.systemui.navbar.threebutton', 'buttons')]:
    adb('shell', 'cmd', 'overlay', 'enable-exclusive', '--category', overlay)
    for collapsed in [False, True]:
        start(2, moving=False, collapsed=collapsed)
        suffix = name + ('-collapsed' if collapsed else '-expanded')
        screenshot(suffix)
        # A full selector drag includes hold, velocity deformation and release spring.
        interaction = None
        if not collapsed:
            x1,y1,x2,y2 = node_bounds('ShitTok')
            a1,b1,a2,b2 = node_bounds('Library')
            def interaction():
                adb('shell', 'input', 'swipe', str((x1+x2)//2), str((y1+y2)//2),
                    str((a1+a2)//2), str((b1+b2)//2), '1400')
        start(2, moving=True, collapsed=collapsed)
        record(suffix, interaction=interaction)
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
