#!/bin/bash
# skeptic revert-state replay: reverse-apply the delivered patches from the full state (what a revert commit does),
# compile + run the focused suite in each state, then re-apply to the full state.
set -u
W=$1; P=$2; OUT=$3
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
R=$W/runtimes/compose/build/test-results/testDebugUnitTest
suite() {
  rm -f $R/*.xml
  (cd $W/apps/android-harness && ./gradlew :runtime:testDebugUnitTest --rerun --tests '*PositionedParent*' --tests '*SeamReachabilityTest' --tests '*CanvasRootHoistTest' --tests '*StaticPositionSeamWiringTest' --tests '*OutOfFlowContainingBlockTest' > $OUT.$1.gradle.log 2>&1)
  rc=$?
  python3 - "$R" "$1" "$rc" <<'PY'
import sys,glob,xml.etree.ElementTree as ET
t=f=s=0; cls=[]
for x in glob.glob(sys.argv[1]+'/*.xml'):
    r=ET.parse(x).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures'))+int(r.get('errors')); s+=int(r.get('skipped')); cls.append(r.get('name').split('.')[-1]+'='+r.get('tests'))
print('revertState %s: gradle exit %s; focused %d tests, %d failures, %d skipped; %s' % (sys.argv[2], sys.argv[3], t, f, s, ' '.join(sorted(cls))))
PY
}
state_sha() { (cd $W && for f in runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentFlowSlot.kt runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt; do [ -f $f ] && echo "  $(shasum -a 256 $f | cut -c1-12) $(basename $f)" || echo "  absent $(basename $f)"; done); }
{
echo "== full (unit-U1 + seam-1 + unit-U2)"; state_sha; suite full
cd $W && git apply -R $P/unit-U2.patch && echo "== U2 reverted (unit-U1 + seam-1)" && state_sha; suite U1
cd $W && git apply -R $P/seam-1.patch && echo "== unit-U1 without seam-1 (rule 2b)" && state_sha; suite U1-noseam
cd $W && git apply -R $P/unit-U1.patch && echo "== base 611c9f93 (revertOrder [U2, U1] complete)" && state_sha && echo "  git status: $(git -C $W status --porcelain --untracked-files=all -- runtimes apps | wc -l | tr -d ' ') entries"; suite base
cd $W && git apply $P/unit-U1.patch && git apply $P/seam-1.patch && git apply $P/unit-U2.patch && echo "== restored full" && state_sha; suite full-restored
} >> $OUT 2>&1
