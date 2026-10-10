#!/usr/bin/env bash
# Skeptic (wave 54, L3): replay the U3 / U3b pins and their mutations with seam-3 (+3b) applied to the SHARED tree's
# tools/titan/extract-fixture.mjs under the per-file lock; restore byte-exact from HEAD on every exit path.
set -uo pipefail
R="$(cd "$(dirname "$0")/../../../../.." && pwd)"; cd "$R"; H=tools/titan/results/wave54-hyphenate-character; SK=$H/skeptic
X=tools/titan/extract-fixture.mjs; T=tools/titan/extract-fixture-br-line-context.test.mjs; LOCK=tools/titan/runs/wave54-lock/extract-fixture.mjs
LOG=$SK/sk-u3-pins.log; M=$SK/sk-mutate.sh
until mkdir "$LOCK" 2>/dev/null; do echo "lock held; wait 60s"; sleep 60; done
headsha=$(git show HEAD:$X | shasum -a 256 | cut -d' ' -f1)
restore() { git show HEAD:$X > $X; rm -f $T; a=$(shasum -a 256 $X | cut -d' ' -f1); echo "RESTORE $X sha $a $([ "$a" = "$headsha" ] && echo BYTE-EXACT-TO-HEAD || echo MISMATCH); test file present after restore: $([ -e $T ] && echo YES || echo no)" | tee -a $LOG; rmdir "$LOCK"; }
trap restore EXIT
b=$(shasum -a 256 $X | cut -d' ' -f1); echo "=== sk-u3-pins $(date '+%F %T') HEAD sha $headsha before $b" | tee -a $LOG
[ "$b" = "$headsha" ] || { echo "seam differs from HEAD before apply: refuse" | tee -a $LOG; exit 7; }
git apply $H/seam-3.patch || exit 6
echo "applied seam-3: $(shasum -a 256 $X | cut -d' ' -f1); test file sha $(shasum -a 256 $T | cut -d' ' -f1) (staged $(shasum -a 256 $H/extract-fixture-br-line-context.test.mjs.staged | cut -d' ' -f1))" | tee -a $LOG
node --test $T tools/titan/extract-fixture.test.mjs 2>&1 | grep -E '^# (tests|pass|fail|skipped)|^not ok' | sed 's/^/  seam-3 suite> /' | tee -a $LOG
$M "SK-U3-a drop the re-arm" $X '          childLineCtx.hasInline = true;' '          /* sk-mutated */' -- node --test $T > /dev/null 2>&1
$M "SK-U3-b white-space-only runs count as content" $X '/[^ \t\n\r\f]/.test(e.text))) {' '/[\s\S]/.test(e.text))) {' -- node --test $T > /dev/null 2>&1
$M "SK-U3-c strictly-between window starts at run 0 (text before child i-1 also re-arms)" $X 'node.runs.findIndex((e) => e.childIndex === i - 1) + 1 : 0;' '0 : 0;' -- node --test $T > /dev/null 2>&1
git apply $H/seam-3b.patch || exit 6
echo "applied seam-3b: $(shasum -a 256 $X | cut -d' ' -f1)" | tee -a $LOG
node --test $T tools/titan/extract-fixture.test.mjs 2>&1 | grep -E '^# (tests|pass|fail|skipped)|^not ok' | sed 's/^/  seam-3+3b suite> /' | tee -a $LOG
$M "SK-U3b-a inherited reading (ancestor line box)" $X 'hostLineBox: brHostLineBoxPx(props) };' 'hostLineBox: brHostLineBoxPx(props) ?? lineCtx?.hostLineBox ?? null };' -- node --test $T > /dev/null 2>&1
$M "SK-U3b-b br rule ignores the host line box" $X "(lineCtx?.hostLineBox ?? '20px');" "'20px';" -- node --test $T > /dev/null 2>&1
$M "SK-U3b-c normal maps to 1.0 instead of 1.2" $X "const mult = lh === 'normal' ? 1.2 :" "const mult = lh === 'normal' ? 1.0 :" -- node --test $T > /dev/null 2>&1
