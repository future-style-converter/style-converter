#!/bin/bash
# cut-patches.sh <worktree> <lane-dir> <u1|u2>
# Wave 55 lane L3 — cut the lane's patches in its OWN worktree (never the shared tree).
#   u1: run with the worktree in the U1 state (unit-U1 + seam-1 applied on 611c9f93, nothing else):
#       unit-U1.patch = `git diff HEAD` of the unit's owned paths, seam-1.patch = the seam file + its patch-borne tests.
#   u2: run with the worktree in the full state (U1 + U2): unit-U2.patch = the U2 paths diffed against a TEMPORARY
#       index holding 611c9f93 + unit-U1.patch + seam-1.patch (GIT_INDEX_FILE; the worktree's own index is untouched),
#       so U2 is exactly the delta it adds on the tree it lands on.
# New files are added intent-to-add (git add -N) so they diff as creation hunks. No commit, checkout, stash or reset.
set -euo pipefail
WT="$1"; L="$2"; MODE="$3"
P=runtimes/compose/src/main/java/com/styleconverter/runtime
TP=runtimes/compose/src/test/java/com/styleconverter/runtime
GATE=$P/layout/position/PositionedParentSlotGate.kt
FLOW=$P/layout/position/PositionedParentFlowSlot.kt
ORIG=$P/layout/position/PositionedParentRtlOrigin.kt
GATET=$TP/layout/position/PositionedParentSlotGateTest.kt
ORIGT=$TP/layout/position/PositionedParentRtlOriginTest.kt
CR=$P/core/renderer/ComponentRenderer.kt
WIRE=$TP/core/renderer/PositionedParentSlotSeamWiringTest.kt
SRT=$TP/core/renderer/SeamReachabilityTest.kt
cd "$WT"
BASE=611c9f932bcfefa147a35f6c5f356d42709ddf84
[[ "$(git rev-parse HEAD)" == "$BASE"* ]] || { echo "worktree HEAD is not 611c9f93" >&2; exit 2; }
case "$MODE" in
u1)
  git add -N -- "$GATE" "$GATET" "$WIRE"
  {
    echo "# unit-U1.patch — wave 55, lane L3 (positioned-parent-slot), revert unit U1 (composed-capture engagement)"
    echo "# base: $BASE (= dev 0d0d3a3d + the committed wave-55 plan + the opening-gate records)"
    echo "# paths: NEW $GATE, $FLOW (comment only), NEW $GATET"
    echo "# applies BEFORE seam-1.patch (landing-manifest applyOrder); compiles and tests green without it (rule 2b)."
    git diff HEAD -- "$GATE" "$FLOW" "$GATET"
  } > "$L/unit-U1.patch"
  {
    echo "# seam-1.patch — wave 55, lane L3 (positioned-parent-slot), revert unit U1"
    echo "# base: $BASE (= dev 0d0d3a3d + the committed wave-55 plan + the opening-gate records)"
    echo "# seam file: $CR"
    echo "# sha256 at base: $(git show HEAD:$CR | shasum -a 256 | cut -c1-64)"
    echo "# anchor: RenderContent → renderBlockChild, the \`val outOfFlowSlotMount: Modifier =\` expression (base :3650)"
    echo "#         and its comment (base :3644-3649) — PLAN §3 .kt row 4 \"L3 · U1\" (registry :3633-3661)."
    echo "# patch-borne: NEW $WIRE;"
    echo "#   $SRT: the U1 module line, inserted after \`\"transforms/TransformInheritance.kt\",\` (base :182, the"
    echo "#   PINNED anchor; sha256 at base $(git show HEAD:$SRT | shasum -a 256 | cut -c1-64)) — cut on HEAD, never at the list end."
    echo "# consumes PositionedParentSlotGate (unit-U1.patch, applied first); LocalLayoutDirection / LayoutDirection /"
    echo "#   LocalWptComposedMode are already in scope in the seam file."
    git diff HEAD -- "$CR" "$WIRE" "$SRT"
  } > "$L/seam-1.patch"
  ;;
u2)
  TMPIDX="$(mktemp -d)/index"
  GIT_INDEX_FILE="$TMPIDX" git read-tree HEAD
  GIT_INDEX_FILE="$TMPIDX" git apply --cached "$L/unit-U1.patch" "$L/seam-1.patch"
  GIT_INDEX_FILE="$TMPIDX" git add -N -- "$ORIG" "$ORIGT"
  {
    echo "# unit-U2.patch — wave 55, lane L3 (positioned-parent-slot), revert unit U2 (the RTL physical-left origin)"
    echo "# base: $BASE + unit-U1.patch + seam-1.patch (U2 dependsOn U1: it is the RTL branch of U1's gate file)"
    echo "# paths: NEW $ORIG, $GATE (the RTL branch), $FLOW (mount(…, rtlOrigin = false) + one .then),"
    echo "#   NEW $ORIGT, $SRT (the U2 module line, directly under U1's at the :182 anchor)"
    GIT_INDEX_FILE="$TMPIDX" git diff -- "$ORIG" "$GATE" "$FLOW" "$ORIGT" "$SRT"
  } > "$L/unit-U2.patch"
  rm -rf "$(dirname "$TMPIDX")"
  ;;
*) echo "mode u1|u2" >&2; exit 2;;
esac
ls -la "$L"/*.patch
