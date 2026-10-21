#!/usr/bin/env bash
# rt.sh <filters...> — run focused :runtime tests in the L6 worktree, print a per-suite summary + failed test names.
WT="${L6_WT:?set L6_WT to the lane worktree (the TREES line of _note.md)}"
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
R=$WT/runtimes/compose/build/test-results/testDebugUnitTest
rm -rf "$R"
args=(); for f in "$@"; do args+=(--tests "$f"); done
out=$(cd $WT/apps/android-harness && ./gradlew :runtime:testDebugUnitTest "${args[@]}" -q 2>&1); rc=$?
echo "$out" | grep -E '^e: ' | head -5
echo "gradle rc=$rc"
python3 - "$R" <<'PY'
import sys,glob,os,xml.etree.ElementTree as ET
t=f=0; n=0
for x in sorted(glob.glob(os.path.join(sys.argv[1],'TEST-*.xml'))):
    r=ET.parse(x).getroot(); n+=1
    t+=int(r.get('tests')); bad=[c.get('name') for c in r.iter('testcase') if c.find('failure') is not None or c.find('error') is not None]
    f+=len(bad)
    for b in bad: print('FAIL %s :: %s' % (r.get('name').split('.')[-1], b))
print('SUMMARY suites=%d tests=%d failed=%d' % (n,t,f))
PY
