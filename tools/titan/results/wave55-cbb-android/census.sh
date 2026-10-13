#!/bin/bash
# wave-55 L2 (cbb-android) blast-radius census — the lane's OWN, behavioural (it executes the runtime's Kotlin, not a
# wire mirror). Run in the lane worktree (TREES: line of _note.md) with all three patches applied:
#   1. the two JUnit census pins on the verbatim wave54-final per-test IR (1435 documents):
#      U1 — DynamicValueResolverTest "CBB census" (every box whose incoming containing block differs between the frozen
#           and the WPT reading AND that reads it: oof / spc / vtext / dyn) → runtimes/compose/build/cbb-census.now.txt;
#      U2 — PercentContentBoxBandTest "P-U2-6 census" (every percentage axis after the runtime's ListStyleUaRule +
#           DynamicValueResolver pre-pass, and which of them PercentContentBoxBand.owns) →
#           runtimes/compose/build/percent-content-box-band-census.now.txt;
#   2. copies both records here (census-U1.junit.txt, census-U2.junit.txt);
#   3. census-check.py: the carrier sets per platform + wire vs expectations.json lanes["L2-cbb-android"].
# Output: census.out.txt (this script's stdout).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WT="$(sed -n 's/^TREES: //p' "$HERE/_note.md" | head -1)"
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
rm -f "$WT"/runtimes/compose/build/*census.now.txt "$WT"/runtimes/compose/build/test-results/testDebugUnitTest/*.xml
(cd "$WT/apps/android-harness" && nice ./gradlew :runtime:testDebugUnitTest \
  --tests '*DynamicValueResolverTest.CBB census*' --tests '*PercentContentBoxBandTest.P-U2-6*' 2>&1) | grep -E 'BUILD|FAILED|tests completed'
for f in "$WT"/runtimes/compose/build/test-results/testDebugUnitTest/*.xml; do
  grep -o '<testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | sed 's/<testsuite name="com.styleconverter.runtime.//'
done
cp "$WT/runtimes/compose/build/cbb-census.now.txt" "$HERE/census-U1.junit.txt"
cp "$WT/runtimes/compose/build/percent-content-box-band-census.now.txt" "$HERE/census-U2.junit.txt"
/opt/homebrew/bin/python3.12 -I "$HERE/census-check.py" "$HERE" "$WT"
