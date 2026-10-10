#!/bin/bash
# Skeptic (wave 54 L4): replay seam-1 under the per-file lock — apply, focused
# suite GREEN, drop the named argument → wiring pin RED, restore the seam file
# byte-exact from HEAD, delete the patch-borne test, release the lock.
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
D=$ROOT/tools/titan/results/wave54-oof-layout/skeptic
SEAM=runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt
PIN=runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ChildContainingBlockSeamWiringTest.kt
LOCK=$ROOT/tools/titan/runs/wave54-lock/ComponentRenderer.kt
cd "$ROOT" || exit 2
until mkdir "$LOCK" 2>/dev/null; do echo "lock held; waiting 60s"; perl -e 'select(undef,undef,undef,60)'; done
echo "lock acquired $(date -u +%FT%TZ)"
echo "HEAD $(git rev-parse --short HEAD); seam sha256 before: $(shasum -a 256 $SEAM | cut -d' ' -f1); HEAD blob sha256: $(git show HEAD:$SEAM | shasum -a 256 | cut -d' ' -f1)"
git apply --check tools/titan/results/wave54-oof-layout/seam-1.patch && git apply tools/titan/results/wave54-oof-layout/seam-1.patch && echo "applied; sha256 $(shasum -a 256 $SEAM | cut -d' ' -f1)"
"$D/kt.sh" SEAM-green '*ChildContainingBlockSeamWiring*' '*DynamicValueResolver*' '*OutOfFlowContainingBlock*' '*CanvasRootHoist*' '*TransformContainingBlock*' '*GapDecoration*'; echo "green rc=$?"
python3 - "$SEAM" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
old = ".childContainingBlock(effectiveProperties, containingBlock, wptCaptureMode = wptCaptureModeForStretch)"
assert s.count(old) == 1
open(p, 'w').write(s.replace(old, ".childContainingBlock(effectiveProperties, containingBlock)"))
PY
echo "mutated sha256 $(shasum -a 256 $SEAM | cut -d' ' -f1)"
"$D/kt.sh" SEAM-red '*ChildContainingBlockSeamWiring*' '*DynamicValueResolver*'; echo "red rc=$? (expected non-zero)"
git show HEAD:$SEAM > $SEAM; rm -f $PIN
echo "seam sha256 after: $(shasum -a 256 $SEAM | cut -d' ' -f1)"
git status --short -- $SEAM $PIN | sed 's/^/status: /'
rmdir "$LOCK" && echo "lock released $(date -u +%FT%TZ)"
