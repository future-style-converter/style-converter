#!/usr/bin/env bash
# tools/titan/results/wave53-lists-bakes/skeptic/jvm.sh — the L1 skeptic's focused JVM run (PLAN §2 L1 "May run").
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd "$(dirname "$0")/../../../../../apps/android-harness" && nice -n 19 ./gradlew :runtime:testDebugUnitTest --tests '*PseudoTextFold*' --tests '*PseudoBucketExtractor*' --tests '*ListOrdinal*' 2>&1
r=$?; echo "gradle rc=$r"
# Per-class counts from the JUnit XML (the summary line Gradle prints is not stable across versions).
python3 - <<'PY'
import glob, xml.etree.ElementTree as E
t = f = 0
for p in glob.glob('../../runtimes/compose/build/test-results/testDebugUnitTest/*.xml'):
    r = E.parse(p).getroot()
    if any(k in r.get('name') for k in ('PseudoTextFold', 'PseudoBucketExtractor', 'ListOrdinal')):
        t += int(r.get('tests')); f += int(r.get('failures')) + int(r.get('errors'))
        for tc in r.iter('testcase'):
            if tc.find('failure') is not None: print('SK FAILED', r.get('name'), tc.get('name'))
print(f'SK junit tests {t} failures {f}')
PY
exit $r
