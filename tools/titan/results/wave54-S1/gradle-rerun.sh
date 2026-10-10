#!/usr/bin/env bash
# focused Compose run that must EXECUTE (fresh XML): --rerun on the test task, then print the per-class counts read from the XML
cd apps/android-harness || exit 9
X=../../runtimes/compose/build/test-results/testDebugUnitTest
rm -f $X/*.xml
./gradlew --no-daemon -q :runtime:testDebugUnitTest --rerun "$@"; rc=$?
python3 - "$X" <<'PY'
import glob,re,sys,os
n=0
for x in sorted(glob.glob(sys.argv[1]+'/*.xml')):
    s=open(x).read(); m=re.search(r'testsuite name="([^"]+)" tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',s)
    if m: print(f'  xml {m.group(1).split(".")[-1]} tests={m.group(2)} failures={m.group(4)} errors={m.group(5)}'); n+=int(m.group(2))
print(f'  fresh-xml tests executed: {n}')
PY
exit $rc
