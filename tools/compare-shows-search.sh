#!/usr/bin/env bash
# Compare both revisions' metadata-only search; run after compiling the Android variant.
set -euo pipefail
base_sha="${1:?Provide the verified main SHA}"
variant="${2:-release}"
classes="$PWD/app/build/intermediates/javac/$variant/compile${variant^}JavaWithJavac/classes"
test -d "$classes"
deps="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
jsoup="$(find "$deps/org.jsoup/jsoup/1.23.1" -name 'jsoup-*.jar' -print -quit)"
json="$(find "$deps/org.json/json" -name 'json-*.jar' -print -quit)"
test -n "$jsoup"
test -n "$json"
classpath="$classes:$jsoup:$json:$ANDROID_HOME/platforms/android-36/android.jar"
probe="$(mktemp -d)"
trap 'rm -rf "$probe"' EXIT
mkdir -p "$probe"/{common,baseline,candidate}
javac -cp "$classpath" -d "$probe/common" tools/LiveShowsProbe.java

# Instrument the existing Jsoup call sites to count attempts, including retries and fallbacks.
# Each mode has its own lightweight copy of the repositories and shares the same bundled source config.
python3 - "$base_sha" "$probe" <<'PY'
from pathlib import Path
import subprocess
import sys

base, output = sys.argv[1:]
for name in ('CrazyShitRepository.java', 'EfuktRepository.java', 'WebVideoSourceRepository.java'):
    source = Path('app/src/main/java/com/webapp/crazyshit') / name
    baseline = subprocess.check_output(['git', 'show', f'{base}:{source}'], text=True)
    for mode, code in [('baseline', baseline), ('candidate', source.read_text())]:
        for call in ('return connection.get();', 'Document doc = connection.get();'):
            code = code.replace(f'        {call}', f'        LiveShowsProbe.request();\n        {call}')
        (Path(output) / mode / name).write_text(code)
PY
for mode in baseline candidate; do
  javac -cp "$probe/common:$classpath" -d "$probe/$mode" "$probe/$mode/"*.java
  java -cp "$probe/$mode:$probe/common:$classpath" com.webapp.crazyshit.LiveShowsProbe "$mode"
done
