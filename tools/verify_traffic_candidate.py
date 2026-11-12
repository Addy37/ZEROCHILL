"""Fail closed before sharing a New Zerochill, side-by-side, production-signed test APK."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

apk, build_tools = Path(sys.argv[1]), Path(sys.argv[2])
badging = subprocess.check_output([str(build_tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
assert package and package[1] == 'com.addy37.crazyshitunofficial.dev', 'Not the side-by-side package'
assert package[2] == '6000046', 'Unexpected candidate version'
certs = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
cert = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', certs)
assert cert and cert[1] == '04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356', 'Production certificate mismatch'
config = Path('app/build/generated/source/buildConfig/debug/com/webapp/crazyshit/BuildConfig.java').read_text()
for name in ('ANALYTICS_ENDPOINT', 'ANALYTICS_V2_ENDPOINT'):
    assert re.search(r'\b' + name + r' = "";', config), 'Analytics must be disabled'
assert 'ZEROCHILL_V7_TRAFFIC = true;' in config
assert 'https://aavjpykssueavbseqdlf.supabase.co' in config
assert os.environ['GITHUB_SHA'] in config, 'Source SHA missing'
with zipfile.ZipFile(apk) as archive:
    dex = b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n))
    for name in archive.namelist():
        data = archive.read(name)
        for marker in ('fketutffusxgjxjlckci'.encode(), 'fketutffusxgjxjlckci'.encode('utf-16le')):
            assert marker not in data, 'Old Supabase project embedded in APK entry: ' + name
assert b'fketutffusxgjxjlckci' not in dex, 'Old Supabase project embedded in APK code'
assert b'aavjpykssueavbseqdlf' in dex, 'New Supabase project missing'
assert b'sb_secret_' not in dex and b'service_role' not in dex, 'Unexpected private client credential marker'
test_results = {}
for variant in ('Debug', 'Release'):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in Path('app/build/test-results/test' + variant + 'UnitTest').glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
    assert totals['tests'] > 0 and totals['failures'] == totals['errors'] == 0, 'Unit test gate failed: ' + variant
    test_results[variant.lower()] = totals
report = dict(source=os.environ['GITHUB_SHA'], branch=os.environ['GITHUB_REF_NAME'],
              package=package[1], version_code=int(package[2]), certificate_sha256=cert[1],
              backend='aavjpykssueavbseqdlf', analytics_enabled=False, unit_tests=test_results,
              verification='Package, signing, source, BuildConfig and packaged APK isolation gates passed.',
              limitations='No physical-device, live Auth/DM or Firebase delivery test; no measured live traffic savings.')
(apk.parent / 'VALIDATION.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
