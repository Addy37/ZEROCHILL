#!/usr/bin/env python3
"""Verify a non-published 7.0.5 APK against the production 7.0.4 artifact."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import zipfile

PACKAGE = 'com.addy37.crazyshitunofficial'
CERT = '04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356'
BACKEND = 'https://aavjpykssueavbseqdlf.supabase.co'


def run(*args):
    return subprocess.check_output([str(a) for a in args], text=True)


def identity(apk, build_tools):
    output = run(build_tools / 'aapt2', 'dump', 'badging', apk)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output)
    assert match, 'Missing APK identity'
    certificate = run(build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    digest = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', certificate)
    assert digest, 'Missing certificate'
    return {'application_id': match[1], 'version_code': int(match[2]),
            'version_name': match[3], 'certificate_sha256': digest[1]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=pathlib.Path, required=True)
    parser.add_argument('--previous', type=pathlib.Path, required=True)
    parser.add_argument('--build-tools', type=pathlib.Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    current, previous = identity(args.apk, args.build_tools), identity(args.previous, args.build_tools)
    assert current['application_id'] == previous['application_id'] == PACKAGE
    assert current['version_name'] == '7.0.5' and current['version_code'] == 7000005
    assert previous['version_name'] == '7.0.4' and previous['version_code'] == 7000004
    assert current['certificate_sha256'] == previous['certificate_sha256'] == CERT
    assert re.fullmatch('[a-f0-9]{40}', args.source_sha)
    generated = pathlib.Path('app/build/generated/source/buildConfig/release/com/webapp/crazyshit/BuildConfig.java').read_text()
    for field, value in {'ACCOUNT_SUPABASE_URL': BACKEND,
                         'SOURCE_CONFIG_ENDPOINT': BACKEND + '/functions/v1/app-config',
                         'FEEDBACK_ENDPOINT': BACKEND + '/functions/v1/dynamic-api',
                         'SOURCE_REVISION': args.source_sha,
                         'ANALYTICS_ENDPOINT': '', 'ANALYTICS_V2_ENDPOINT': ''}.items():
        assert f'{field} = "{value}";' in generated, f'Incorrect {field}'
    assert 'ZEROCHILL_V7_TRAFFIC = true;' in generated
    for field in ('ACCOUNT_SUPABASE_PUBLISHABLE_KEY', 'FEEDBACK_ANON_KEY', 'SOURCE_CONFIG_PUBLISHABLE_KEY'):
        assert f'{field} = "sb_publishable_oSQALszxzeeHxDwEeclucA_5dGEpOLs";' in generated
    with zipfile.ZipFile(args.apk) as package:
        dex = b''
        for name in package.namelist():
            data = package.read(name)
            for forbidden in ('fketutffusxgjxjlckci', 'xswtgidwhesajavxjbjb', 'sb_secret_'):
                for encoding in ('utf-8', 'utf-16le'):
                    assert forbidden.encode(encoding) not in data, f'Forbidden backend/key marker in {name}'
            if name.endswith('.dex'): dex += data
        for required in (BACKEND, BACKEND + '/functions/v1/app-config',
                         BACKEND + '/functions/v1/dynamic-api', args.source_sha):
            assert required.encode() in dex, f'Missing APK marker: {required}'
    current.update(source_sha=args.source_sha, source_branch=run('git', 'branch', '--show-current').strip(),
                   backend=BACKEND, legacy_analytics_enabled=False,
                   sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                   size_bytes=args.apk.stat().st_size, previous=previous,
                   upgrade_eligible=True)
    args.output.write_text(json.dumps(current, indent=2) + '\n')
    print(json.dumps(current, indent=2))


if __name__ == '__main__':
    main()
