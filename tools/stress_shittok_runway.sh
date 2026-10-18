#!/usr/bin/env bash
set -euo pipefail
# Disposable debug install only. Up to 180 flicks in 90 seconds, no macrobenchmark.
adb_binary="$(command -v adb)"
adb() { timeout --kill-after=2s 12s "$adb_binary" "$@"; }
output="${1:?output directory}"
mkdir -p "$output"
package=com.addy37.crazyshitunofficial.dev
adb logcat -c
timeout --kill-after=2s 115s "$adb_binary" logcat -v threadtime -s ShitTokInventory:I AndroidRuntime:E > "$output/inventory.txt" 2>&1 &
log_pid=$!
trap 'kill "$log_pid" 2>/dev/null || true' EXIT
adb shell screencap -p /sdcard/runway-before.png
adb pull /sdcard/runway-before.png "$output/before.png"
# Coordinates follow the emulator display; keep the gesture inside the video surface.
size="$(adb shell wm size | tr -d '\r' | tail -1 | awk '{print $NF}')"
width="${size%x*}"; height="${size#*x}"
python3 - "$width" "$height" > "$output/flicks.sh" <<'PY'
import sys
w, h = map(int, sys.argv[1:])
print('deadline=$(( $(date +%s) + 90 ))')
print('for n in $(seq 1 180); do')
print('  [ "$(date +%s)" -lt "$deadline" ] || break')
print(f'  input touchscreen swipe {w//2} {h*3//4} {w//2} {h//4} 100')
print('  echo "flick=$n"')
print('  sleep 0.25')
print('done')
PY
adb push "$output/flicks.sh" /data/local/tmp/zerochill-flicks.sh
timeout --kill-after=2s 105s "$adb_binary" shell sh /data/local/tmp/zerochill-flicks.sh > "$output/gestures.txt" 2>&1
adb shell screencap -p /sdcard/runway-after.png
adb pull /sdcard/runway-after.png "$output/after.png"
adb shell dumpsys gfxinfo "$package" > "$output/gfxinfo.txt"
kill "$log_pid" 2>/dev/null || true
wait "$log_pid" 2>/dev/null || true
python3 - "$output" <<'PY'
import json, pathlib, re, sys
root = pathlib.Path(sys.argv[1]); raw = (root / 'inventory.txt').read_text()
samples = [dict(re.findall(r'(\w+)=(-?\d+)', line)) for line in raw.splitlines() if 'ShitTokInventory' in line]
samples = [{k: int(v) for k, v in s.items()} for s in samples if 'usable' in s]
report = {'observed_samples': len(samples), 'emulator_only': True, 'physical_smoothness': 'not measured',
          'attempted_swipes': len(re.findall(r'flick=\d+', (root / 'gestures.txt').read_text()))}
if samples:
    report.update(last=samples[-1], minimum_observed_usable=min(s['usable'] for s in samples),
                  highest_selected=max(s['selected'] for s in samples))
    # Cumulative counters remain meaningful after the 160-event ring rotates.
    report['tail_hits'] = max(s.get('tailHits', 0) for s in samples)
    report['recent_fallbacks'] = max(s.get('recentFallback', 0) for s in samples)
(root / 'summary.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
if 'FATAL EXCEPTION' in raw: raise SystemExit('App crash during stress sequence')
if not samples: raise SystemExit('No inventory samples; stress result is inconclusive')
PY
