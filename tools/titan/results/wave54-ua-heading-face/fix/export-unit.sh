#!/usr/bin/env bash
# Wave 54 lane L5 FIX PASS — does the GO-SMALL U1-android stand ALONE on HEAD? One export tree (git archive HEAD, no
# worktree) in the session scratchpad: HEAD + UAElementFontRule.kt + UAElementFontRuleTest.kt + seam-1.patch (which
# also creates UAElementFontRuleSeamWiringTest.kt) → the focused JVM suite. Nothing in the shared tree is touched.
# (The unit's fourth path, UAElementFontRule.swift, is comments-only: the shared-tree Catalyst run compiled it.)
# Usage: export-unit.sh <scratch-dir>
set -u
W=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
S=$1; D=$W/tools/titan/results/wave54-ua-heading-face/fix/runs
rm -rf $S/ex-U1; mkdir -p $S/ex-U1; cd $S/ex-U1
git -C $W archive HEAD apps/android-harness runtimes/compose schema fixtures | tar -x
cp $W/apps/android-harness/local.properties apps/android-harness/
for f in src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt; do
  cp $W/runtimes/compose/$f runtimes/compose/$f; done
git init -q . && git apply $W/tools/titan/results/wave54-ua-heading-face/seam-1.patch && echo "EX-U1 seam-1 applied (+ UAElementFontRuleSeamWiringTest.kt)"
(cd apps/android-harness && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :runtime:testDebugUnitTest --tests '*UAElementFontRule*' --tests '*UAHeadingFoldGate*' --tests '*InlineRunFoldTest' -q > $D/export-U1.gradle.log 2>&1); echo "EX-U1 gradle rc=$?"
for x in runtimes/compose/build/test-results/testDebugUnitTest/TEST-*.xml; do grep -o 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' $x; done
python3 -c "b=open('runtimes/compose/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/styleconverter/runtime/core/renderer/ComponentRenderer.class','rb').read(); print('EX-U1 compiled renderer: headingStandsDown refs', b.count(b'headingStandsDown'), '· UAHeadingFoldGate refs', b.count(b'UAHeadingFoldGate'))"
