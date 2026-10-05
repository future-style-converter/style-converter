#!/bin/zsh
# rv2-l6 re-verifier: run a focused compose JUnit selection with --rerun, then
# summarise ONLY the JUnit XML written after this run started.
# usage: run.sh <tag> <gradle --tests args...>
set -u
TAG=$1; shift
R=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
D=/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/rv2-l6
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
touch $D/$TAG.start; sleep 1
echo "start $(date '+%H:%M:%S') sha $(shasum -a 256 $R/runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt | cut -c1-16)" > $D/$TAG.summary
(cd $R/apps/android-harness && ./gradlew :runtime:testDebugUnitTest --rerun "$@" > $D/$TAG.gradle.log 2>&1); echo "gradle exit $?" >> $D/$TAG.summary
python3 - $D/$TAG.start $R/runtimes/compose/build/test-results/testDebugUnitTest >> $D/$TAG.summary <<'PY'
import sys, os, glob, xml.etree.ElementTree as ET
start = os.path.getmtime(sys.argv[1]); tot = [0, 0, 0]; n = 0
for f in sorted(glob.glob(sys.argv[2] + '/TEST-*.xml')):
    if os.path.getmtime(f) <= start: continue
    r = ET.parse(f).getroot(); n += 1
    t, fl, e = int(r.get('tests')), int(r.get('failures')), int(r.get('errors'))
    tot[0] += t; tot[1] += fl; tot[2] += e
    for tc in r.findall('testcase'):
        fe = tc.find('failure') if tc.find('failure') is not None else tc.find('error')
        if fe is not None:
            print('  RED', r.get('name').split('.')[-1], '|', tc.get('name'), '|', (fe.get('message') or '')[:160])
print(f'fresh classes {n} tests {tot[0]} failures {tot[1]} errors {tot[2]}')
PY
echo "end $(date '+%H:%M:%S')" >> $D/$TAG.summary
cat $D/$TAG.summary
