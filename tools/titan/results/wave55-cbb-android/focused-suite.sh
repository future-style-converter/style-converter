#!/bin/bash
# wave-55 L2 focused suite (PLAN §2 L2 "May run"), in the lane worktree named on _note.md's TREES: line; prints the
# JUnit tally per class (read from the XML — "executed" is read from the task outcome, never written by hand).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WT="$(sed -n 's/^TREES: //p' "$HERE/_note.md" | head -1)"
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
rm -rf "$WT/runtimes/compose/build/test-results/testDebugUnitTest"
(cd "$WT/apps/android-harness" && nice ./gradlew :runtime:testDebugUnitTest --tests '*DynamicValueResolver*' --tests '*ChildContainingBlockSeamWiring*' --tests '*PercentContentBoxBand*' --tests '*ContentBox*' --tests '*BoxSizing*' --tests '*SizingMinMaxClamp*' --tests '*SizingOverflowWidth*' --tests '*WptBoxSizingDefault*' 2>&1) | sed "s|$WT|<lane worktree>|g" | grep -E 'FAILED|BUILD|tests completed|e: '
rc=${PIPESTATUS[0]}
for f in "$WT"/runtimes/compose/build/test-results/testDebugUnitTest/*.xml; do grep -o '<testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | sed 's/<testsuite name="com.styleconverter.runtime.//'; done
echo "gradle rc=$rc"; exit $rc
