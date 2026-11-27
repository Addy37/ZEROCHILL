#!/usr/bin/env python3
"""Verify a signed candidate against the production APK it must replace."""
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
    parser.add_argument('--source-branch', default='')
    parser.add_argument('--version', default='7.0.5')
    parser.add_argument('--code', type=int, default=7000005)
    parser.add_argument('--previous-version', default='7.0.4')
    parser.add_argument('--previous-code', type=int, default=7000004)
    parser.add_argument('--approved-code', type=int, default=0)
    parser.add_argument('--verify-bundled-notes', action='store_true')
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    current, previous = identity(args.apk, args.build_tools), identity(args.previous, args.build_tools)
    assert current['application_id'] == previous['application_id'] == PACKAGE
    assert (current['version_name'], current['version_code']) == (args.version, args.code)
    assert (previous['version_name'], previous['version_code']) == (
        args.previous_version, args.previous_code)
    assert current['version_code'] > max(previous['version_code'], args.approved_code)
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
        if args.verify_bundled_notes:
            notes = pathlib.Path('app/src/main/res/raw/whats_new_7_0.md').read_bytes()
            assert any(package.read(name) == notes for name in package.namelist()
                       if name.startswith('res/') and name.endswith('.md')), 'Bundled notes differ'
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
    current.update(source_sha=args.source_sha, source_branch=args.source_branch or run('git', 'branch', '--show-current').strip(),
                   backend=BACKEND, legacy_analytics_enabled=False,
                   sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                   size_bytes=args.apk.stat().st_size, previous=previous,
                   upgrade_eligible=True)
    args.output.write_text(json.dumps(current, indent=2) + '\n')
    print(json.dumps(current, indent=2))


if __name__ == '__main__':
    main()
