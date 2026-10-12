#!/usr/bin/env bash
# Wave 55 · L4 SKEPTIC — re-run every revertOrder state the lane claims compiled, in the skeptic worktree, ONE platform
# at a time (the other platform's files are never touched, so a concurrent build of it is not disturbed):
# the platform's paths reset to 611c9f93 (`git checkout -- <platform dir>` + removal of the lane's NEW files there),
# the kept patches `git apply`-ed in landing order, the focused suites run; the full state is re-applied last.
#   skeptic-revert-states.sh <worktree> k|s
set -u
WT=$1; PLAT=$2
LD=$(cd "$(dirname "$0")" && pwd)
cd "$WT" || exit 9
LOG=$(mktemp)
if [ "$PLAT" = k ]; then
  DIR=runtimes/compose
  NEW="runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginTest.kt runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginSeamWiringTest.kt runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAHeadingFoldGateTest.kt"
  STATES=("A(U2-android-reverted):unit-U1-android seam-1" "B(U2-android+U1-android-reverted=HEAD):" "F(full):unit-U1-android seam-1 unit-U2-android seam-3")
else
  DIR=runtimes/swiftui
  NEW="runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginTests.swift runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginSeamWiringTests.swift runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift"
  STATES=("A(U2-ios-reverted):unit-U1-ios seam-2" "B(U2-ios+U1-ios-reverted=HEAD):" "F(full):unit-U1-ios seam-2 unit-U2-ios seam-4")
fi
for st in "${STATES[@]}"; do
  name=${st%%:*}; keep=${st#*:}
  git checkout -- "$DIR"; rm -f $NEW
  for p in $keep; do git apply "$LD/$p.patch" || { echo "revertStatesCompiled: $name $PLAT: APPLY FAIL $p"; continue 2; }; done
  dirty=$(git status --porcelain -- "$DIR" | wc -l | tr -d ' ')
  if [ "$PLAT" = k ]; then
    rm -rf runtimes/compose/build/test-results/testDebugUnitTest
    ( cd apps/android-harness && JAVA_HOME=$(/usr/libexec/java_home -v 21) nice ./gradlew :runtime:testDebugUnitTest \
        --tests '*SeamReachabilityTest' --tests '*UaPositionedHeadingMargin*' --tests '*UAElementFontRule*' \
        --tests '*UAHeadingFoldGate*' --tests '*InlineRunFoldTest' ) > "$LOG" 2>&1; rc=$?
    res=""; for f in runtimes/compose/build/test-results/testDebugUnitTest/TEST-*.xml; do [ -f "$f" ] || continue
      res="$res$(basename $f .xml | sed 's/.*\.//') $(grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f" | head -1 | sed -E 's/tests="([0-9]+)".*failures="([0-9]+)" errors="([0-9]+)"/\1\/\2+\3/');"; done
    echo "revertStatesCompiled: $name compose: rc=$rc (touched paths vs HEAD: $dirty) [$res]"
  else
    T="-only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests"
    [ -f runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginTests.swift ] && T="$T -only-testing:StyleConverterRuntimeTests/UAPositionedHeadingMarginTests"
    [ -f runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginSeamWiringTests.swift ] && T="$T -only-testing:StyleConverterRuntimeTests/UAPositionedHeadingMarginSeamWiringTests"
    [ -f runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift ] && T="$T -only-testing:StyleConverterRuntimeTests/UAHeadingFoldGateTests"
    nice xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
        -derivedDataPath "$WT/.dd" $T > "$LOG" 2>&1; rc=$?
    echo "revertStatesCompiled: $name swiftui: rc=$rc (touched paths vs HEAD: $dirty) [$(grep -Eo 'Executed [0-9]+ tests?, with [0-9]+ failures?' "$LOG" | tail -1)] $(grep -o "Test Case '-\[[^]]*\]' failed" "$LOG" | sort -u | tr '\n' ' ')"
  fi
done
rm -f "$LOG"
