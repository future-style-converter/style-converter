#!/usr/bin/env bash
# Skeptic (wave 54 L5) — does each revert unit stand ALONE on HEAD? Builds two export trees
# (git archive HEAD, no worktree) in the session scratchpad: EX-A = HEAD + U1-android's six
# Kotlin files + seam-1 → focused JVM suite; EX-B = HEAD + U2-ios's four Swift files + seam-2
# → focused Catalyst suite. Nothing in the shared tree is touched.
set -u
W=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
S=$1; D=$W/tools/titan/results/wave54-ua-heading-face/skeptic
which=$2
rm -rf $S/ex-$which; mkdir -p $S/ex-$which; cd $S/ex-$which
if [ $which = A ]; then
  git -C $W archive HEAD apps/android-harness runtimes/compose schema fixtures | tar -x
  cp $W/apps/android-harness/local.properties apps/android-harness/
  for f in src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt \
           src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt src/test/java/com/styleconverter/runtime/typography/UAHeadingFoldGateTest.kt \
           src/test/java/com/styleconverter/runtime/typography/UAHeadingFaceReplay.kt src/test/java/com/styleconverter/runtime/typography/UAHeadingFaceWire.kt; do
    cp $W/runtimes/compose/$f runtimes/compose/$f; done
  git init -q . && git apply $W/tools/titan/results/wave54-ua-heading-face/seam-1.patch && echo "EX-A seam-1 applied"
  (cd apps/android-harness && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :runtime:testDebugUnitTest --tests '*UAElementFontRule*' --tests '*UAHeadingFoldGate*' --tests '*InlineRunFoldTest' -q > $D/export-A.gradle.log 2>&1); echo "EX-A gradle rc=$?"
  for x in runtimes/compose/build/test-results/testDebugUnitTest/TEST-*.xml; do grep -o 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' $x; done
  python3 -c "b=open('runtimes/compose/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/styleconverter/runtime/core/renderer/ComponentRenderer.class','rb').read(); print('EX-A compiled renderer gate-refs', b.count(b'UAHeadingFoldGate'))"
else
  git -C $W archive HEAD Package.swift runtimes/swiftui | tar -x
  for f in Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift \
           Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift Tests/StyleConverterRuntimeTests/UAElementFontRuleTests.swift; do
    cp $W/runtimes/swiftui/$f runtimes/swiftui/$f; done
  git init -q . && git apply $W/tools/titan/results/wave54-ua-heading-face/seam-2.patch && echo "EX-B seam-2 applied"
  xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath $S/ex-B-dd \
    -only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests -only-testing:StyleConverterRuntimeTests/UAHeadingFoldGateTests > $D/export-B.xcodebuild.log 2>&1
  echo "EX-B xcodebuild rc=$? $(grep -E 'Executed [0-9]+ tests?' $D/export-B.xcodebuild.log | tail -1)"
fi
