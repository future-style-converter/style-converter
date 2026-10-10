#!/usr/bin/env bash
# Wave 54 L5 RE-VERIFIER — builds ONE throwaway export tree (git archive HEAD, no worktree, nothing in the shared tree
# touched) = HEAD + U1-android's two owned files + seam-1.patch (which creates UAElementFontRuleSeamWiringTest.kt) +
# the re-verifier probe appended to the EXPORT copy of UAElementFontRuleTest.kt. Mutations then run in the export tree,
# so no lock is needed (the shared seam file is never written).
# Usage: export-tree.sh <dir>
set -eu
W=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
L=$W/tools/titan/results/wave54-ua-heading-face
S=$1/ex-RV; rm -rf $S; mkdir -p $S; cd $S
git -C $W archive HEAD apps/android-harness runtimes/compose schema fixtures | tar -x
cp $W/apps/android-harness/local.properties apps/android-harness/
for f in src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt; do
  cp $W/runtimes/compose/$f runtimes/compose/$f; done
git init -q . && git apply $L/seam-1.patch && echo "EX-RV seam-1 applied: renderer sha256 $(shasum -a 256 runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt | cut -c1-16) (+ $(ls runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt))"
cat $L/reverify/ReverifyGoSmallProbe.kt.txt >> runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt
echo "EX-RV probe appended to the EXPORT copy only; shared-tree test sha256 $(shasum -a 256 $W/runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt | cut -c1-16)"
