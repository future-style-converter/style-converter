#!/usr/bin/env bash
# land-units.sh — wave 54: turn the seven lanes' uncommitted work on the shared tree into PLAN.md §4's REVERT UNITS,
# one commit each, in the landing order, applying each unit's seam patch right before its commit:
#
#   0. LANE RECORDS (the seven tools/titan/results/wave54-<lane>/ dirs: notes, skeptic reports, censuses, patches,
#      mutation logs — read by no gate; the two L4 census base files its corpus pins read) → 1 commit, before any unit,
#      so every unit below holds CODE paths only and reverts alone (L4 skeptic defect 2; L7 note §2).
#   L7 U1 (tripwire manifest + checker)                                  → 1 commit (tooling only)
#   L1 P, then M′ (bidi-bake hunks split by patch replay on HEAD bytes)   → 2 commits (M′ never without P)
#   L2 TB-android (+ kt seam-1)                                           → 1 commit
#   L6 RS (+ tsx seam-1), then W1 (runtime files; branch R — no seam)     → 2 commits
#   L5 U1-android (+ kt seam-1) — GO-SMALL: leaf headings + sub/sup; U2-ios HELD (wave54-ua-heading-face/held/) → 1 commit
#   L4 OOF-android, OOF-ios, CBB-android (+ kt seam-1), GAP-android, GAP-ios → 5 commits
#   L3 U1, U2-android (+ kt seam-1), U2-ios (+ swift seam-2), U3 (+ seam-3), U3b (+ seam-3b) → 5 commits, LAST
#      (the only lane that changes the wire corpus-wide; [W-L3] runs on the fully landed tree)
#
# Seam-file order (PLAN §3): ComponentRenderer.kt  L2 → L5 → L4 → L3;  ComponentRenderer.swift  L3 (L5's seam-2 is held);
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
CHECK=0; [[ "${1:-}" == "--check" ]] && CHECK=1
if (( CHECK )); then   # verify every unit path exists and every seam patch applies on HEAD's seam bytes — no commit
  # a path a seam patch CREATES (patch-borne test files) is absent until that patch applies — not a defect
  borne() { grep -lsq -- "^+++ b/$1\$" "$R"/wave54-*/seam-*.patch; }
  commit() { local title="$1"; shift 2; local bad=0; for p in "$@"; do [[ -e "$p" ]] || borne "$p" || { echo "  MISSING $p   (unit: $title)"; bad=1; }; done; (( bad )) && exit 1; echo "paths ok   ${title%%:*}"; }
  apply() { git apply --check --whitespace=nowarn "$1" 2>/dev/null && echo "patch ok   ${1#$R/}" || echo "  PATCH DOES NOT APPLY ON THE CURRENT TREE: ${1#$R/} (expected when an earlier patch of the same seam file is not yet applied in --check mode)"; }
fi
KT=runtimes/compose/src/main/java/com/styleconverter/runtime
KTT=runtimes/compose/src/test/java/com/styleconverter/runtime
SW=runtimes/swiftui/Sources/StyleConverterRuntime
SWT=runtimes/swiftui/Tests/StyleConverterRuntimeTests
CR_KT=$KT/core/renderer/ComponentRenderer.kt
CR_SW=$SW/Renderer/ComponentRenderer.swift


# ── 0. Lane records (read by no gate; L4's corpus pins read its two census base files) ──────────────────────────────
commit "wave54 lane records: the seven lanes' notes, skeptic reports, fix passes, censuses, replays, seam / plan hunks and mutation logs (L1 rtl-marker-bake, L2 table-body-cell, L3 hyphenate-character, L4 oof-layout incl. census-compose/census-swift.base.txt, L5 ua-heading-face incl. held/, L6 web-tail, L7 label-chrome)" \
"Records, not code: every revert unit below holds only its code paths, so each reverts alone (PLAN §4; L4 skeptic defect 2)." \
  "$R/wave54-rtl-marker-bake" "$R/wave54-table-body-cell" "$R/wave54-hyphenate-character" "$R/wave54-oof-layout" "$R/wave54-ua-heading-face" "$R/wave54-web-tail" "$R/wave54-label-chrome"

# ── L7 U1 ────────────────────────────────────────────────────────────────────
L7=$R/wave54-label-chrome
commit "wave54 L7 U1: the label-chrome tripwire learns the capture contract's 'iff' — containers and text roots carry no harness label (label-chrome-exempt.json manifest, verified structurally; clause (iv) on exempt stems; the pure checker in label-chrome-check.mjs)" \
"Revert unit L7-U1 ($L7/_note.md). Tooling only; not executed or built by any gate. revertOrder: U2-seed (the 18 all-then-color PNGs, seeded later in [W-L7]) is reverted before U1, never after." \
  tools/visual/label-chrome-tripwire.test.mjs tools/visual/label-chrome-check.mjs tools/visual/label-chrome-exempt.json

# ── L1 P, then M′ (patch replay on HEAD's bytes of the two shared files) ─────
L1=$R/wave54-rtl-marker-bake
if (( ! CHECK )); then   # the replay rewinds the two shared files to HEAD's bytes — NEVER in --check mode (it wiped the lane's edits once)
  SAVE="$(mktemp -d)"; for p in tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs tools/titan/bidi-marker-bake.mjs; do mkdir -p "$SAVE/$(dirname $p)"; [ -f "$p" ] && cp "$p" "$SAVE/$p"; done
  git checkout -q HEAD -- tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs; rm -f tools/titan/bidi-marker-bake.mjs
  apply "$L1/unit-P.patch"
else echo "check: L1 replay skipped (unit-P.patch / unit-Mprime.patch verified by make-units.sh, see make-units.pre-landing.out.txt)"; fi
commit "wave54 L1 P: a bidi-bake root's spent padding is zeroed (paddingIsSpent; CSS 2.1 §10.1 item 4)" \
"Revert unit L1-P ($L1/_note.md). Reverting moves bidi-lines-001/-002 android and anchor-center-safe-rtl back to wave54-open. M′ depends on P (revertOrder [Mprime, P])." \
  tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs
(( CHECK )) || apply "$L1/unit-Mprime.patch"
commit "wave54 L1 M′: RTL list markers baked as runs owned by the bake ROOT (bidi-marker-bake.mjs; css-lists-3 outside marker on the inline-start side) — device-gated at stage 1" \
"Revert unit L1-Mprime ($L1/_note.md). Reverting undoes counter-suffix's RTL markers on all three platforms; never lands without P." \
  tools/titan/bidi-marker-bake.mjs tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs
if (( ! CHECK )); then for p in tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs tools/titan/bidi-marker-bake.mjs; do cmp -s "$p" "$SAVE/$p" || { echo "L1 replay differs from the lane's tree: $p" >&2; exit 1; }; done; rm -rf "$SAVE"; fi

# ── L2 TB-android (+ kt seam-1) ──────────────────────────────────────────────
L2=$R/wave54-table-body-cell
apply "$L2/seam-1.patch"
commit "wave54 L2 TB-android: the Compose anonymous table forest for a display: table body hugs each cell to its max-intrinsic width (TableBodyForest / TableCellHug; CSS 2.1 §17.2.1, §17.5.2) — device-gated at stage 1" \
"Revert unit L2-TB-android ($L2/_note.md). Captures: CSS2/css21-errata/s-11-1-1b-006 android only; wire: none." \
  "$KT/table/TableBodyForest.kt" "$KTT/table/TableBodyForestTest.kt" "$KT/table/TableCellHug.kt" "$KTT/table/TableCellHugTest.kt" "$KT/table/TableApplier.kt" \
  apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt \
  "$CR_KT" "$KTT/table/TableCellHugSeamWiringTest.kt"

# ── L6 RS (+ tsx seam-1), then W1 ────────────────────────────────────────────
L6=$R/wave54-web-tail
apply "$L6/seam-1.patch"
commit "wave54 L6 RS: one collapsed space between inline-level ROOTS on the composed web canvas (ComposedRootSeparator; CSS Text 3 §4.1)" \
"Revert unit L6-RS ($L6/_note.md). Reverting undoes box-sizing-007/-008/-022 and position-absolute-semi-replaced-stretch-other web." \
  apps/web-harness/src/ui/ComposedRootSeparator.ts apps/web-harness/tests/ui/ComposedRootSeparator.test.ts apps/web-harness/src/sdui/ComponentRenderer.tsx apps/web-harness/src/ui/ComposedCaptureGallery.tsx apps/web-harness/tests/ui/ComposedRootSeparatorWire.test.tsx
commit "wave54 L6 W1: a paint-inert out-of-flow member no longer splits an auto-hyphenated word on web (InertOutOfFlowWordJoin; branch R of step 0)" \
"Revert unit L6-W1 ($L6/_note.md). Reverting undoes hyphens-out-of-flow-002 web." \
  runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx runtimes/web/src/renderer/InlineRuns.ts runtimes/web/src/renderer/NodeRenderer.ts

# ── L5 U1-android (+ kt seam-1) — GO-SMALL (U2-ios HELD) ────────────────────
L5=$R/wave54-ua-heading-face
apply "$L5/seam-1.patch"
commit "wave54 L5 U1-android: the UA element-font step for leaf headings and sub/sup on Compose (UAElementFontRule; HTML §15.3.6 rendering) — GO-SMALL, the folded-host gate held" \
"Revert unit L5-U1-android ($L5/_note.md). Reverting undoes block-in-inline-015-print android. U2-ios is HELD (wave54-ua-heading-face/held/): no Swift behaviour change this wave; UAElementFontRule.swift carries a docs-only twin-status line." \
  "$KT/typography/UAElementFontRule.kt" "$KTT/typography/UAElementFontRuleTest.kt" "$KTT/typography/UAElementFontRuleSeamWiringTest.kt" "$SW/StyleEngine/typography/UAElementFontRule.swift" "$CR_KT"

# ── L4 OOF-android, OOF-ios, CBB-android (+ kt seam-1), GAP-android, GAP-ios ─
L4=$R/wave54-oof-layout
commit "wave54 L4 OOF-android: a fixed box's containing block is the nearest positioned/transformed/contained ancestor on Compose (OutOfFlowContainingBlock; CSS Positioned Layout 3 §3.1, css-contain)" \
"Revert unit L4-OOF-android ($L4/_note.md). Reverting undoes contain-content-003/-011 android and the fixed-position carriers." \
  "$KT/layout/position/OutOfFlowContainingBlock.kt" "$KTT/layout/position/OutOfFlowContainingBlockTest.kt" "$KT/layout/position/CanvasRootHoist.kt" "$KTT/layout/position/CanvasRootHoistTest.kt"
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

# ── L3 U1, U2-android (+ kt seam-1), U2-ios (+ swift seam-2), U3 (+ seam-3), U3b (+ seam-3b) — LAST ────────────────
L3=$R/wave54-hyphenate-character
commit "wave54 L3 U1: hyphenate-character keeps \"\" and decodes css-syntax-3 escapes in the reader (CssStringParser; css-text-4 §6.3, css-syntax-3 §4.3.5/§4.3.7)" \
"Revert unit L3-U1 ($L3/_note.md). Wire: hyphenate-character-001/-002/-003/-005 change value inside the existing string variant (no new byte shape). revertOrder: U3b, U3, then U1." \
  converter/src/main/kotlin/app/parsing/css/properties/primitiveParsers/CssStringParser.kt \
  converter/src/main/kotlin/app/parsing/css/properties/longhands/typography/HyphenateCharacterPropertyParser.kt \
  converter/src/test/kotlin/app/parsing/css/properties/longhands/typography/HyphenateCharacterPropertyParserTest.kt
apply "$L3/seam-1.patch"
commit "wave54 L3 U2-android: Compose paints the hyphenate-character string in the pre-break fold (HyphenateCharacter triplet, SoftHyphenCuts walk)" \
"Revert unit L3-U2-android ($L3/_note.md). Carries hyphenate-character-001/-003/-004 android. Undeclared/auto keeps U+2010: every other run identical." \
  "$KT/typography/wrapping/HyphenateCharacterConfig.kt" "$KT/typography/wrapping/HyphenateCharacterExtractor.kt" \
  "$KT/typography/wrapping/HyphenateCharacterApplier.kt" "$KT/typography/wrapping/SoftHyphenCuts.kt" \
  "$KT/typography/wrapping/PreBreakPipeline.kt" "$KTT/typography/wrapping/PreBreakPipelineTest.kt" \
  "$KTT/typography/wrapping/HyphenateCharacterExtractorTest.kt" "$CR_KT"
apply "$L3/seam-2.patch"
commit "wave54 L3 U2-ios: SwiftUI paints the hyphenate-character string and treats it as spent (TextConfig.hyphenateCharacter, SpentHyphen; css-text-3 §5.5)" \
"Revert unit L3-U2-ios ($L3/_note.md). Carries hyphenate-character-001/-002/-003/-004 and hyphenate-limit-chars-001 ios. nil keeps U+2010 and no strip." \
  "$SW/StyleEngine/typography/wrapping/HyphenateCharacterConfig.swift" "$SW/StyleEngine/typography/wrapping/HyphenateCharacterExtractor.swift" \
  "$SW/StyleEngine/typography/wrapping/HyphenateCharacterApplier.swift" "$SW/StyleEngine/typography/wrapping/SpentHyphen.swift" \
  "$SW/StyleEngine/typography/TypographyAggregate.swift" "$SW/Renderer/StyleBuilder.swift" \
  "$SW/StyleEngine/typography/GreedyLineBreaker.swift" "$SWT/GreedyLineBreakerTests.swift" "$SWT/TypographyTests.swift" "$CR_SW"
apply "$L3/seam-3.patch"
commit "wave54 L3 U3: a <br> that ends an interleaved text line is 0px, not a blank line (extract-fixture child-scope re-arm; CSS 2.1 §9.5)" \
"Revert unit L3-U3 ($L3/_note.md). Wire: 54 brs in 18 documents. Never lands before U1 + U2-android + U2-ios (degenerate -003 ios trap)." \
  tools/titan/extract-fixture.mjs tools/titan/extract-fixture-br-line-context.test.mjs
apply "$L3/seam-3b.patch"
commit "wave54 L3 U3b: a line-start <br> takes its host's own declared line box (brHostLineBoxPx; CSS 2.1 §10.8) — probe-decided" \
"Revert unit L3-U3b ($L3/_note.md). Wire: 12 brs in hyphenate-character-001…004 (20px → 19.2px). Reverted FIRST if its probeDecided rule fails." \
  tools/titan/extract-fixture.mjs
echo "landed $n unit commits; remaining working-tree changes:"; git status --short | head -20
