#!/bin/bash
# L4 seam-1 verification under the per-file lock (build-workflow rule 2): apply
# seam-1.patch, run the focused suite (expected GREEN), mutate the seam (drop
# the named argument; the patch-borne wiring pin must go RED), then restore the
# seam file byte-exact from HEAD, delete the patch-borne test, release the lock.
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
SEAM=runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt
PIN=runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ChildContainingBlockSeamWiringTest.kt
LOCK=$ROOT/tools/titan/runs/wave54-lock/ComponentRenderer.kt
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd "$ROOT" || exit 2
mkdir -p "$ROOT/tools/titan/runs/wave54-lock"
# mkdir is the mutex: wait 60 s while another lane holds this seam file.
until mkdir "$LOCK" 2>/dev/null; do echo "lock held; waiting 60s"; perl -e 'select(undef,undef,undef,60)'; done
echo "lock acquired $(date -u +%FT%TZ)"
echo "sha256 before: $(shasum -a 256 $SEAM | cut -d' ' -f1)"
git apply tools/titan/results/wave54-oof-layout/seam-1.patch && echo applied
echo "sha256 applied: $(shasum -a 256 $SEAM | cut -d' ' -f1)"
(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*ChildContainingBlockSeamWiring*' --tests '*DynamicValueResolver*' --tests '*OutOfFlowContainingBlock*' --tests '*CanvasRootHoist*') > $ROOT/tools/titan/results/wave54-oof-layout/mutations/seam-green.run.txt 2>&1; echo "green suite rc=$?"
grep -E "BUILD|FAILED" $ROOT/tools/titan/results/wave54-oof-layout/mutations/seam-green.run.txt | head -5
for c in ChildContainingBlockSeamWiringTest DynamicValueResolverTest OutOfFlowContainingBlockTest CanvasRootHoistTest; do
  grep -o 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' runtimes/compose/build/test-results/testDebugUnitTest/TEST-*.$c.xml
done
echo "--- mutation: drop the named argument (seam applied) ---"
python3 - "$SEAM" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
old = ".childContainingBlock(effectiveProperties, containingBlock, wptCaptureMode = wptCaptureModeForStretch)"
assert s.count(old) == 1
open(p, 'w').write(s.replace(old, ".childContainingBlock(effectiveProperties, containingBlock)"))
PY
echo "sha256 mutated: $(shasum -a 256 $SEAM | cut -d' ' -f1)"
(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*ChildContainingBlockSeamWiring*') > $ROOT/tools/titan/results/wave54-oof-layout/mutations/seam-red.run.txt 2>&1; echo "mutated suite rc=$? (expected non-zero)"
grep -E "BUILD|FAILED" $ROOT/tools/titan/results/wave54-oof-layout/mutations/seam-red.run.txt | head -5
echo "--- restore seam file byte-exact from HEAD, remove patch-borne test ---"
git show HEAD:$SEAM > $SEAM; rm -f $PIN
echo "sha256 after: $(shasum -a 256 $SEAM | cut -d' ' -f1)"
git status --short -- $SEAM $PIN | sed 's/^/status: /'
# the two suite logs stay in mutations/ as the run record
rmdir "$LOCK" && echo "lock released $(date -u +%FT%TZ)"
