#!/usr/bin/env bash
# Wave 55 lane L4 — `revertStatesCompiled` (PLAN §0 "Revert states compile", round 1 S12): every state the
# four revertOrder chains can produce is built on a clean 611c9f93 checkout of the LANE WORKTREE
# (`git checkout -- runtimes` + `git clean -fd` of the lane's new files + `git apply` of the kept patches),
# compiled, and its focused suites run; then the full state is restored from the patches. Compose and
# SwiftUI are separate modules, so three tree states cover all chains:
#   A  = U2-android AND U2-ios reverted  (chains [U2-android] and [U2-ios]; landing states 13-14)
#   B  = all four reverted = base HEAD   (chains [U2-android, U1-android] and [U2-ios, U1-ios])
#   F  = the full lane (landing state 16; restored last)
#   revert-states.sh <lane-worktree> <patch-dir>
set -uo pipefail
WT=$1; P=$2
C=runtimes/compose/src; S=runtimes/swiftui
NEW=($C/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt
     $C/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginTest.kt
     $C/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginSeamWiringTest.kt
     $C/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt
     $C/test/java/com/styleconverter/runtime/typography/UAHeadingFoldGateTest.kt
     $S/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift
     $S/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginTests.swift
     $S/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginSeamWiringTests.swift
     $S/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift
     $S/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift)
state() {  # $1 = label, rest = patches kept (landing order)
  local label=$1; shift
  git -C "$WT" checkout -- runtimes && git -C "$WT" clean -fdq -- "${NEW[@]}"
  for p in "$@"; do git -C "$WT" apply "$P/$p.patch" || { echo "revertStatesCompiled: $label: APPLY-FAIL $p"; return 1; }; done
  echo "state $label: patches [${*:-none}] — tree $(git -C "$WT" status --porcelain -- runtimes | wc -l | tr -d ' ') changed path(s)"
}
kt() {  # $1 = label, rest = --tests filters
  local label=$1; shift; local args=(); for t in "$@"; do args+=(--tests "$t"); done
  ( cd "$WT/apps/android-harness" && JAVA_HOME=$(/usr/libexec/java_home -v 21) nice ./gradlew :runtime:testDebugUnitTest "${args[@]}" ) \
    > "$WT/../l4-rs-$label-kt.log" 2>&1
  local rc=$? r=$WT/runtimes/compose/build/test-results/testDebugUnitTest
  local counts=$(for f in "$r"/TEST-*.xml; do python3 -c "import re,sys;t=open(sys.argv[1]).read();m=re.search(r'tests=\"(\d+)\" skipped=\"\d+\" failures=\"(\d+)\" errors=\"(\d+)\"',t);print(sys.argv[1].split('.')[-2], m.group(1)+'/'+str(int(m.group(2))+int(m.group(3))))" "$f"; done | tr '\n' ';')
  echo "revertStatesCompiled: $label compose: $([ $rc = 0 ] && echo 'BUILD OK, GREEN' || echo "FAILED rc=$rc") [$counts]"
}
sw() {  # $1 = label, rest = test classes
  local label=$1; shift; local args=(); for t in "$@"; do args+=("-only-testing:StyleConverterRuntimeTests/$t"); done
  ( cd "$WT" && nice xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
      -derivedDataPath "$WT/.dd" "${args[@]}" ) > "$WT/../l4-rs-$label-sw.log" 2>&1
  local rc=$?
  echo "revertStatesCompiled: $label swiftui: $([ $rc = 0 ] && echo 'BUILD OK, GREEN' || echo "FAILED rc=$rc") [$(grep -E 'Executed [0-9]+ tests' "$WT/../l4-rs-$label-sw.log" | tail -1 | sed 's/^[[:space:]]*//')]"
}
KT_ALL=('*UaPositionedHeadingMargin*' '*UAElementFontRule*' '*UAHeadingFoldGate*' '*SeamReachabilityTest' '*InlineRunFoldTest')
SW_ALL=(UAPositionedHeadingMarginTests UAPositionedHeadingMarginSeamWiringTests UAElementFontRuleTests UAHeadingFoldGateTests)
state A unit-U1-android seam-1 unit-U1-ios seam-2 && {
  kt "A(U2-android-reverted)" '*UaPositionedHeadingMargin*' '*UAElementFontRule*' '*SeamReachabilityTest' '*InlineRunFoldTest'
  sw "A(U2-ios-reverted)" UAPositionedHeadingMarginTests UAPositionedHeadingMarginSeamWiringTests UAElementFontRuleTests; }
state B && {
  kt "B(U2-android+U1-android-reverted=HEAD)" '*UAElementFontRule*' '*SeamReachabilityTest' '*InlineRunFoldTest'
  sw "B(U2-ios+U1-ios-reverted=HEAD)" UAElementFontRuleTests; }
state F unit-U1-android seam-1 unit-U1-ios seam-2 unit-U2-android seam-3 unit-U2-ios seam-4 && {
  kt "F(full)" "${KT_ALL[@]}"
  sw "F(full)" "${SW_ALL[@]}"; }
