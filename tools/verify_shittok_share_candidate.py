"""Verify the existing production identity before offering the unmerged share-menu beta."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

apk, build_tools = Path(sys.argv[1]), Path(sys.argv[2])
badging = subprocess.check_output([str(build_tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
assert package and package[1] == 'com.addy37.crazyshitunofficial', 'Production package changed'
assert int(package[2]) == int(os.environ['APP_VERSION_CODE']) > 7000007, 'Not an upgrade from Beta 3'
assert package[3] == os.environ['APP_VERSION_NAME'], 'Unexpected beta version'
certs = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
cert = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', certs)
assert cert and cert[1] == '04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356', 'Production certificate mismatch'
source = os.environ['ZEROCHILL_SOURCE_SHA']
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() == source, 'Source SHA differs from checkout'
subprocess.run(['git', 'merge-base', '--is-ancestor', '605e85c7c58a95bb8fd91c3921040f10f472ae50', source], check=True)
config = Path('app/build/generated/source/buildConfig/release/com/webapp/crazyshit/BuildConfig.java').read_text()
assert source in config and 'ZEROCHILL_V7_TRAFFIC = true;' in config, 'Missing source or current backend routing'
for name in ('ANALYTICS_ENDPOINT', 'ANALYTICS_V2_ENDPOINT'):
    assert re.search(r'\b' + name + r' = "";', config), 'Candidate analytics must be disabled'
assert 'https://aavjpykssueavbseqdlf.supabase.co' in config
with zipfile.ZipFile(apk) as archive:
    dex = b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n))
    for name in archive.namelist():
        data = archive.read(name)
        for marker in ('fketutffusxgjxjlckci', 'xswtgidwhesajavxjbjb'):
            assert marker.encode() not in data and marker.encode('utf-16le') not in data, 'Retired/test backend packaged: ' + name
assert source.encode() in dex, 'Packaged source revision missing'
assert b'sb_secret_' not in dex and b'service_role' not in dex, 'Private credential marker packaged'
tests = {}
for variant in ('Debug', 'Release'):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = list(Path('app/build/test-results/test' + variant + 'UnitTest').glob('TEST-*.xml'))
    assert any('ShitTokVideoShareTest' in p.name for p in suites), 'Missing direct-sharing tests'
    assert any('ShitTokShareSheetTest' in p.name for p in suites), 'Missing menu tests'
    assert any('ZeroChillVideoCardTest' in p.name for p in suites), 'Missing existing DM-card tests'
    focus = next((p for p in suites if p.name.endswith('.ShitTokFocusModeViewportTest.xml')), None)
    assert focus is not None, 'Missing Focus Mode regressions'
    assert any(case.get('name') == 'lateSettledEntranceKeepsChromeHiddenAcrossFocusSwipesUntilExit'
               and case.find('skipped') is None for case in ET.parse(focus).getroot().findall('testcase')), 'Missing forward/reverse Focus swipe regression'
    replay = next((p for p in suites if p.name.endswith('.ShitTokAutoScrollIntegrationTest.xml')), None)
    assert replay is not None, 'Missing Auto Scroll regressions'
    names = {case.get('name') for case in ET.parse(replay).getroot().findall('testcase')
             if case.find('skipped') is None}
    assert {'completedRetainedVideoRewindsBeforeReplayAndAdvancesOnlyAfterNewEnd',
            'oldPostedEndCannotAdvanceAfterRapidBackAndForwardVisits',
            'completedRetiredLeaseRestoresZeroWhilePartialWatchKeepsPosition',
            'manualDragCancelsQueuedEndWithoutDisablingSavedAutoScroll'} <= names, 'Missing replay regression'
    for path in suites:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
    assert totals['tests'] > 0 and totals['failures'] == totals['errors'] == 0, 'Unit test gate failed: ' + variant
    tests[variant.lower()] = totals
report = dict(source=source, branch=os.environ['ZEROCHILL_SOURCE_BRANCH'], package=package[1],
              version=package[3], version_code=int(package[2]), certificate_sha256=cert[1],
              apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), unit_tests=tests,
              limitations='Physical receiving-app playback, live source restrictions, Focus Mode gestures, rotation, installed-user upgrade and device performance still require physical testing.')
(apk.parent / 'VALIDATION.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
