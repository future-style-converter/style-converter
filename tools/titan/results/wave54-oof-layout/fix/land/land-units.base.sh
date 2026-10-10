#!/usr/bin/env bash
# land-units.sh — wave 54: turn the seven lanes' uncommitted work on the shared tree into PLAN.md §4's REVERT UNITS,
# one commit each, in the landing order, applying each unit's seam patch right before its commit:
#
#   L7 U1 (tripwire manifest + checker)                                  → 1 commit (lands first: tooling only)
#   L1 P, then M′ (bidi-bake hunks split by patch replay on HEAD bytes)   → 2 commits (M′ never without P)
#   L2 TB-android (+ kt seam-1)                                           → 1 commit
#   L6 RS (+ tsx seam-1), then W1 (runtime files; branch R — no seam)     → 2 commits
#   L5 U1-android (+ kt seam-1), then U2-ios (+ swift seam-2)             → 2 commits
#   L4 OOF-android, OOF-ios, CBB-android (+ kt seam-1), GAP-android, GAP-ios → 5 commits
#   L3 U1, U2-android (+ kt seam-1), U2-ios (+ swift seam-2), U3 (+ seam-3), U3b (+ seam-3b) → 5 commits, LAST
#      (the only lane that changes the wire corpus-wide; [W-L3] runs on the fully landed tree)
#
# Seam-file order (PLAN §3): ComponentRenderer.kt  L2 → L5 → L4 → L3;  ComponentRenderer.swift  L5 → L3;
#                            ComponentRenderer.tsx  L6;  extract-fixture.mjs  L3.
# Run only on an idle host (no gate, probe or A/B — refused otherwise), after every pre-landing window is served, and
# after re-reading every lane's "## Fix pass" section (a fix lane may have moved a unit's paths). Stops at the first
# unit with nothing to commit. L3's block is filled from its note before this script may pass the L3 sentinel.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"; cd "$ROOT"
R=tools/titan/results
if pgrep -f 'gate-driver.sh|section-runner.sh|test-all.sh|provision-devices.sh' >/dev/null; then echo "a gate / probe is running — refusing" >&2; exit 2; fi
for seam in runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift apps/web-harness/src/sdui/ComponentRenderer.tsx tools/titan/extract-fixture.mjs; do
  git diff --quiet -- "$seam" || { echo "seam file already modified: $seam — refusing" >&2; exit 2; }
done
CO="Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
n=0
commit() { # <title> <body> <paths…>
  local title="$1" body="$2"; shift 2
  git add -- "$@" || { echo "git add failed for $title" >&2; exit 1; }
  if git diff --cached --quiet; then echo "nothing to commit for: $title — stopping" >&2; exit 1; fi
  git commit -q -m "$title" -m "$body" -m "$CO" || exit 1
  n=$((n+1)); printf 'ok %2d  %s  %s\n' "$n" "$(git rev-parse --short HEAD)" "$title"
}
apply() { git apply --check --whitespace=nowarn "$1" && git apply --whitespace=nowarn "$1" || { echo "seam patch failed: $1" >&2; exit 1; }; }
KT=runtimes/compose/src/main/java/com/styleconverter/runtime
KTT=runtimes/compose/src/test/java/com/styleconverter/runtime
SW=runtimes/swiftui/Sources/StyleConverterRuntime
SWT=runtimes/swiftui/Tests/StyleConverterRuntimeTests
CR_KT=$KT/core/renderer/ComponentRenderer.kt
CR_SW=$SW/Renderer/ComponentRenderer.swift

# ── L7 U1 ────────────────────────────────────────────────────────────────────
L7=$R/wave54-label-chrome
commit "wave54 L7 U1: the label-chrome tripwire learns the capture contract's 'iff' — containers and text roots carry no harness label (label-chrome-exempt.json manifest, verified structurally; clause (iv) on exempt stems; the pure checker in label-chrome-check.mjs)" \
"Revert unit L7-U1 ($L7/_note.md). Tooling only; not executed or built by any gate. revertOrder: U2-seed (the 18 all-then-color PNGs, seeded later in [W-L7]) is reverted before U1, never after." \
  tools/visual/label-chrome-tripwire.test.mjs tools/visual/label-chrome-check.mjs tools/visual/label-chrome-exempt.json "$L7"

# ── L1 P, then M′ (patch replay on HEAD's bytes of the two shared files) ─────
L1=$R/wave54-rtl-marker-bake
SAVE="$(mktemp -d)"; for p in tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs tools/titan/bidi-marker-bake.mjs; do mkdir -p "$SAVE/$(dirname $p)"; [ -f "$p" ] && cp "$p" "$SAVE/$p"; done
git checkout -q HEAD -- tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs; rm -f tools/titan/bidi-marker-bake.mjs
apply "$L1/unit-P.patch"
commit "wave54 L1 P: a bidi-bake root's spent padding is zeroed (paddingIsSpent; CSS 2.1 §10.1 item 4)" \
"Revert unit L1-P ($L1/_note.md). Reverting moves bidi-lines-001/-002 android and anchor-center-safe-rtl back to wave54-open. M′ depends on P (revertOrder [Mprime, P])." \
  tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs "$L1"
apply "$L1/unit-Mprime.patch"
commit "wave54 L1 M′: RTL list markers baked as runs owned by the bake ROOT (bidi-marker-bake.mjs; css-lists-3 outside marker on the inline-start side) — device-gated at stage 1" \
"Revert unit L1-Mprime ($L1/_note.md). Reverting undoes counter-suffix's RTL markers on all three platforms; never lands without P." \
  tools/titan/bidi-marker-bake.mjs tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs
for p in tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs tools/titan/bidi-marker-bake.mjs; do cmp -s "$p" "$SAVE/$p" || { echo "L1 replay differs from the lane's tree: $p" >&2; exit 1; }; done; rm -rf "$SAVE"

# ── L2 TB-android (+ kt seam-1) ──────────────────────────────────────────────
L2=$R/wave54-table-body-cell
apply "$L2/seam-1.patch"
commit "wave54 L2 TB-android: the Compose anonymous table forest for a display: table body hugs each cell to its max-intrinsic width (TableBodyForest / TableCellHug; CSS 2.1 §17.2.1, §17.5.2) — device-gated at stage 1" \
"Revert unit L2-TB-android ($L2/_note.md). Captures: CSS2/css21-errata/s-11-1-1b-006 android only; wire: none." \
  "$KT/table/TableBodyForest.kt" "$KTT/table/TableBodyForestTest.kt" "$KT/table/TableCellHug.kt" "$KTT/table/TableCellHugTest.kt" "$KT/table/TableApplier.kt" \
  apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt \
  "$CR_KT" "$KTT/table/TableCellHugSeamWiringTest.kt" "$L2"

# ── L6 RS (+ tsx seam-1), then W1 ────────────────────────────────────────────
L6=$R/wave54-web-tail
apply "$L6/seam-1.patch"
commit "wave54 L6 RS: one collapsed space between inline-level ROOTS on the composed web canvas (ComposedRootSeparator; CSS Text 3 §4.1)" \
"Revert unit L6-RS ($L6/_note.md). Reverting undoes box-sizing-007/-008/-022 and position-absolute-semi-replaced-stretch-other web." \
  apps/web-harness/src/ui/ComposedRootSeparator.ts apps/web-harness/tests/ui/ComposedRootSeparator.test.ts apps/web-harness/src/sdui/ComponentRenderer.tsx apps/web-harness/src/ui/ComposedCaptureGallery.tsx apps/web-harness/tests/ui/ComposedRootSeparatorWire.test.tsx "$L6"
commit "wave54 L6 W1: a paint-inert out-of-flow member no longer splits an auto-hyphenated word on web (InertOutOfFlowWordJoin; branch R of step 0)" \
"Revert unit L6-W1 ($L6/_note.md). Reverting undoes hyphens-out-of-flow-002 web." \
  runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx runtimes/web/src/renderer/InlineRuns.ts runtimes/web/src/renderer/NodeRenderer.ts

# ── L5 U1-android (+ kt seam-1), then U2-ios (+ swift seam-2) ────────────────
L5=$R/wave54-ua-heading-face
apply "$L5/seam-1.patch"
commit "wave54 L5 U1-android: the UA element-font step for headings on Compose (UAElementFontRule / UAHeadingFoldGate; HTML §15.3.6 rendering)" \
"Revert unit L5-U1-android ($L5/_note.md). Reverting undoes block-in-inline-015-print android and the text-decoration-inset-005/-006/-014 android movers." \
  "$KT/typography/UAElementFontRule.kt" "$KT/typography/UAHeadingFoldGate.kt" "$KTT/typography/UAElementFontRuleTest.kt" "$KTT/typography/UAHeadingFoldGateTest.kt" "$KTT/typography/UAHeadingFaceReplay.kt" "$KTT/typography/UAHeadingFaceWire.kt" "$CR_KT" "$L5"
apply "$L5/seam-2.patch"
commit "wave54 L5 U2-ios: the SwiftUI heading fold gate (UAHeadingFoldGate.swift twin)" \
"Revert unit L5-U2-ios ($L5/_note.md). Reverting undoes the text-decoration-inset-005/-006/-014 ios movers." \
  "$SW/StyleEngine/typography/UAHeadingFoldGate.swift" "$SW/StyleEngine/typography/UAElementFontRule.swift" "$SWT/UAHeadingFoldGateTests.swift" "$SWT/UAElementFontRuleTests.swift" "$CR_SW"

# ── L4 OOF-android, OOF-ios, CBB-android (+ kt seam-1), GAP-android, GAP-ios ─
L4=$R/wave54-oof-layout
commit "wave54 L4 OOF-android: a fixed box's containing block is the nearest positioned/transformed/contained ancestor on Compose (OutOfFlowContainingBlock; CSS Positioned Layout 3 §3.1, css-contain)" \
"Revert unit L4-OOF-android ($L4/_note.md). Reverting undoes contain-content-003/-011 android and the fixed-position carriers." \
  "$KT/layout/position/OutOfFlowContainingBlock.kt" "$KTT/layout/position/OutOfFlowContainingBlockTest.kt" "$KT/layout/position/CanvasRootHoist.kt" "$KTT/layout/position/CanvasRootHoistTest.kt" "$L4"
commit "wave54 L4 OOF-ios: the SwiftUI twin of the out-of-flow containing block (OutOfFlowContainingBlock.swift / FixedHoist)" \
"Revert unit L4-OOF-ios ($L4/_note.md)." \
  "$SW/StyleEngine/layout/position/OutOfFlowContainingBlock.swift" "$SWT/OutOfFlowContainingBlockTests.swift" "$SW/Renderer/FixedHoist.swift" "$SWT/FixedHoistTests.swift"
apply "$L4/seam-1.patch"
commit "wave54 L4 CBB-android: under WPT capture a content-box box sizes against its containing block's content band (ContainingBlockBands; CSS 2.1 §10.2)" \
"Revert unit L4-CBB-android ($L4/_note.md)." \
  "$KT/core/variables/ContainingBlockBands.kt" "$KT/core/variables/DynamicValueResolver.kt" "$KTT/core/variables/DynamicValueResolverTest.kt" "$CR_KT" "$KTT/core/renderer/ChildContainingBlockSeamWiringTest.kt"
commit "wave54 L4 GAP-android: a zero-extent flex gap keeps its position for column-rule segments (GapDecorationSegments; css-gaps-1)" \
"Revert unit L4-GAP-android ($L4/_note.md). Reverting undoes flex-gap-decorations-033 android." \
  "$KT/columns/GapDecorationGeometry.kt" "$KT/columns/GapDecorationLines.kt" "$KT/columns/GapDecorationSegments.kt" "$KTT/columns/GapDecorationSegmentsTest.kt"
commit "wave54 L4 GAP-ios: the SwiftUI twin of the zero-extent gap rule" \
"Revert unit L4-GAP-ios ($L4/_note.md). Reverting undoes flex-gap-decorations-033 ios." \
  "$SW/StyleEngine/columns/GapDecorationGeometry.swift" "$SW/StyleEngine/columns/GapDecorationLines.swift" "$SW/StyleEngine/columns/GapDecorationSegments.swift" "$SWT/GapDecorationSegmentsTests.swift"

# ── L3 (filled from tools/titan/results/wave54-hyphenate-character/_note.md once the lane is COMPLETE) ──
if [[ "${L3_READY:-0}" != "1" ]]; then echo "landed $n unit commits (L7, L1, L2, L6, L5, L4); L3 block not yet filled — set L3_READY=1 after adding it"; git status --short | head -20; exit 0; fi
L3_BLOCK_PLACEHOLDER
echo "landed $n unit commits; remaining working-tree changes:"; git status --short | head -20
