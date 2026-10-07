#!/usr/bin/env bash
# The lane's focused Compose suite (PLAN §2 L1 "May run"), printing per-class testsuite summaries from the XML.
cd "$(dirname "$0")/../../../../../apps/android-harness" || exit 2
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
nice -n 10 ./gradlew :runtime:testDebugUnitTest --tests '*PseudoTextFold*' --tests '*PseudoBucketExtractor*' --tests '*ListOrdinal*' 2>&1 | grep -E 'BUILD (SUCCESSFUL|FAILED)|FAILED|tests completed'
for f in ../../runtimes/compose/build/test-results/testDebugUnitTest/TEST-*.xml; do grep -o '<testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' "$f"; grep -B1 '<failure' "$f" | grep -o 'testcase name="[^"]*"' | sed 's/^/  FAILED /'; done
