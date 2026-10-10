#!/usr/bin/env bash
# Wave 54 lane L5 — one executed mutation of the Compose mechanism:
#   mutate-compose.sh <name> <file> <python-old-literal-file> <python-new-literal-file>
# backs the file up, swaps ONE exact occurrence of old→new, runs the lane's focused JVM
# suite (red expected), restores the file byte-exact (sha256 before == after, logged),
# re-runs the suite (green expected). Prints per-class tests/failures from the XML.
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
name=$1; file=$2; oldf=$3; newf=$4
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mkdir -p "$ROOT/tools/titan/results/wave54-ua-heading-face/mutations"; bak="$ROOT/tools/titan/results/wave54-ua-heading-face/mutations/$name.bak"; cp "$ROOT/$file" "$bak"
before=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
python3 - "$ROOT/$file" "$oldf" "$newf" <<'PY'
import sys
p, o, n = sys.argv[1:4]
s = open(p, encoding='utf-8').read(); old = open(o, encoding='utf-8').read(); new = open(n, encoding='utf-8').read()
assert s.count(old) == 1, f'old literal occurs {s.count(old)} times'
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
mutated=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
echo "[$name] file=$file sha256 before=$before mutated=$mutated"
results() {
  python3 - "$ROOT"/runtimes/compose/build/test-results/testDebugUnitTest/TEST-com.styleconverter.runtime.typography.UA*Test.xml <<'PY'
import sys, xml.etree.ElementTree as ET
for f in sys.argv[1:]:
    r = ET.parse(f).getroot()
    print(f"  {r.get('name')} tests={r.get('tests')} failures={r.get('failures')} errors={r.get('errors')}")
    for tc in r.iter('testcase'):
        for bad in list(tc.iter('failure')) + list(tc.iter('error')):
            print(f"    RED: {tc.get('name')} :: {(bad.get('message') or '').splitlines()[0][:160] if bad.get('message') else ''}")
PY
}
(cd "$ROOT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests '*UAElementFontRule*' --tests '*UAHeadingFoldGate*' -q > $ROOT/tools/titan/results/wave54-ua-heading-face/mutations/$name.red.log 2>&1); echo "[$name] mutated run rc=$?"
results
cp "$bak" "$ROOT/$file"; rm -f "$bak"
after=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
echo "[$name] restored sha256 after=$after $( [ "$before" = "$after" ] && echo BYTE-EXACT || echo MISMATCH )"
(cd "$ROOT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests '*UAElementFontRule*' --tests '*UAHeadingFoldGate*' -q > $ROOT/tools/titan/results/wave54-ua-heading-face/mutations/$name.green.log 2>&1); echo "[$name] restored run rc=$?"
results
