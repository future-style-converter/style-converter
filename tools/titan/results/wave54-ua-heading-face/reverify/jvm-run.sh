#!/usr/bin/env bash
# Wave 54 L5 RE-VERIFIER — one focused JVM run of the lane's suite in tree $1 (default: the shared tree, UNSEAMED).
# Clears ONLY this suite's result XMLs first (never another lane's), so a compile failure can never read as green.
# Usage: jvm-run.sh <tree> <tag>   → prints one line per suite + every failing case.
set -u
T=${1:-/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf}; TAG=${2:-run}
X=$T/runtimes/compose/build/test-results/testDebugUnitTest
LOG=$(cd "$(dirname "$0")" && pwd)/$TAG.gradle.log
rm -f $X/TEST-com.styleconverter.runtime.typography.UAElementFontRule*.xml $X/TEST-com.styleconverter.runtime.typography.inline.InlineRunFoldTest.xml $X/TEST-com.styleconverter.runtime.typography.ReverifyGoSmallProbe.xml
(cd $T/apps/android-harness && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :runtime:testDebugUnitTest --tests '*UAElementFontRule*' --tests '*InlineRunFoldTest' ${EXTRA_TESTS:-} > $LOG 2>&1); RC=$?
echo "[$TAG] gradle rc=$RC $(date +%H:%M:%S)"
if [ $RC -ne 0 ]; then grep -E '^e: |FAILED|error:' $LOG | head -20; fi
python3 - "$X" "$TAG" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
X, tag = sys.argv[1], sys.argv[2]
fs = sorted(glob.glob(X + '/TEST-com.styleconverter.runtime.typography.UAElementFontRule*.xml') + glob.glob(X + '/TEST-com.styleconverter.runtime.typography.inline.InlineRunFoldTest.xml') + glob.glob(X + '/TEST-com.styleconverter.runtime.typography.ReverifyGoSmallProbe.xml'))
if not fs: print(f'[{tag}] NO RESULT XML')
for f in fs:
    r = ET.parse(f).getroot()
    print(f"[{tag}] {r.get('name').split('.')[-1]} tests={r.get('tests')} failures={r.get('failures')} errors={r.get('errors')} ts={r.get('timestamp')}")
    for tc in r.iter('testcase'):
        for bad in list(tc.iter('failure')) + list(tc.iter('error')):
            print(f"[{tag}]   RED {tc.get('name')} :: {((bad.get('message') or '').splitlines() or [''])[0][:200]}")
PY
