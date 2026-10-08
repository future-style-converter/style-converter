#!/usr/bin/env bash
# land-units.sh — wave 53: turn the five lanes' uncommitted work on the shared
# tree into the plan's REVERT UNITS, one commit each, in PLAN.md §4's landing
# order, applying each unit's seam patch right before its commit:
#
#   L5 harness-hygiene            → 1 commit
#   L1 U1 (extractor seam-1 + counter-bake + both PseudoTextFold twins), U2 (bidi-marker-bake + hunk P)
#   L2 F1 (iOS seam-1 + SoftHyphenPolicy + PreBreakPipeline), F2 (Compose seam-2 + InlineRunFold + InertOutOfFlowMember)
#   L4 android (Compose seam-1 + FloatAvoid*.kt), ios (iOS seam-2 + FloatAvoid*.swift)
#   L3 A, B-web, B-ios, B-android — L3's own unit patches, replayed on HEAD's bytes of its 17 paths (make-units.py
#      proved the four patches reproduce the tree sha256-exact), so the three shared canvas files are split by hunk
#
# Each commit message names the unit, its lane note and what a revert of it undoes. Run only on an idle host (no
# gate, probe or A/B: the script refuses otherwise) and only after every lane's window requests are done. Idempotent
# in the weak sense: it stops at the first unit whose paths are already clean (nothing to commit) and says so.
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

# ── L5 ──────────────────────────────────────────────────────────────────────
commit "wave53 L5 harness-hygiene: the gate path stops only its own Gradle daemons and adb server; smoke follows the port guard; the ios-harness tests read their files as bundle resources" \
"Revert unit L5 (tools/titan/results/wave53-harness-hygiene/_note.md). kill_own_gradle_daemons and kill_wedged_adb_server in own-processes.sh replace the two host-wide ./gradlew --stop and the two pkill -9 -x adb; smoke.sh moves off a held :3000 through smoke-port.sh; project.yml bundles CaptureCanvas.swift and the two per-test IR documents as test resources so the 13 simulator-blocked XCTests pass (30/30 measured on the booted simulator, mutations M1-M3 red, restored byte-exact)." \
  apps/ios-harness/project.yml apps/ios-harness/StyleConverterTestTests/ComposedCanvasIcbClipTests.swift apps/ios-harness/StyleConverterTestTests/ComposedCanvasPaddingTests.swift apps/ios-harness/StyleConverterTestTests/ComposedRootInlineFlowTests.swift \
  tools/titan/fixtures/README.md tools/titan/own-processes.sh tools/titan/own-processes.test.mjs tools/titan/own-adb-server.test.mjs tools/titan/own-gradle-daemons.test.mjs \
  tools/titan/gate-driver.sh tools/titan/section-runner.sh tools/titan/provision-devices.sh tools/visual/smoke.sh tools/visual/smoke-port.sh tools/visual/smoke-port.test.mjs tools/visual/web-port-guard.sh \
  "$R/wave53-harness-hygiene"

# ── L1 ──────────────────────────────────────────────────────────────────────
apply "$R/wave53-lists-bakes/seam-1.patch"
commit "wave53 L1 U1: a nested <ol> inside an <li> keeps its nesting (findImpliedClose, HTML §13.2.6.4.7); counter-bake walks it; both PseudoTextFold twins keep the leading text" \
"Revert unit L1-U1 (tools/titan/results/wave53-lists-bakes/_note.md). Reverting undoes css-lists/counter-reset-reversed-nested's structure fix on all three platforms (the extractor flattened the nested list and dropped its last item)." \
  tools/titan/extract-fixture.mjs tools/titan/extract-fixture-implied-close.test.mjs tools/titan/counter-bake.mjs tools/titan/counter-bake.test.mjs \
  "$SW/StyleEngine/content/PseudoTextFold.swift" "$SWT/PseudoTextFoldTests.swift" "$KT/content/PseudoTextFold.kt" "$KTT/content/PseudoTextFoldTest.kt" \
  "$R/wave53-lists-bakes"
commit "wave53 L1 U2: RTL list markers are baked upstream (bidi-marker-bake.mjs: Chromium's ::marker string, box and glyphs become positioned runs; the item gets list-style-type: none) and a bidi-bake root's non-zero padding is kept" \
"Revert unit L1-U2 (hunks M + P together — M alone is a pre-registered loss on counter-suffix android). Reverting undoes the counter-suffix RTL markers on all three platforms and the bidi-lines-001/-002 android padding." \
  tools/titan/bidi-marker-bake.mjs tools/titan/bidi-bake.mjs tools/titan/bidi-bake.test.mjs "$SW/StyleEngine/lists/ListMarkerOutsideHang.swift"

# ── L2 ──────────────────────────────────────────────────────────────────────
apply "$R/wave53-soft-hyphen/seam-1.patch"
commit "wave53 L2 F1: a space-less run carrying U+00AD is a soft-wrap opportunity on both natives (PreBreakPipeline admits it; SoftHyphenPolicy.admitsPreBreak gates the iOS pre-break)" \
"Revert unit L2-F1 (tools/titan/results/wave53-soft-hyphen/_note.md). Reverting undoes hyphens-span-001 / hyphens-out-of-flow-001 on both natives and the hyphenate-character-00x movers." \
  "$SW/Renderer/ComponentRenderer.swift" "$KT/typography/wrapping/PreBreakPipeline.kt" "$KTT/typography/wrapping/PreBreakPipelineTest.kt" \
  "$SW/StyleEngine/typography/wrapping/SoftHyphenPolicy.swift" "$SWT/SoftHyphenPolicyTests.swift" "$SWT/GreedyLineBreakerTests.swift" \
  "$R/wave53-soft-hyphen"
apply "$R/wave53-soft-hyphen/seam-2.patch"
commit "wave53 L2 F2: a paint-inert out-of-flow member inside a word no longer splits the fold (InertOutOfFlowMember; the runs-fold breadcrumb counts the dropped members)" \
"Revert unit L2-F2. Reverting undoes hyphens-out-of-flow-002 android and part of -001." \
  "$KT/core/renderer/ComponentRenderer.kt" "$KT/typography/inline/InlineRunFold.kt" "$KTT/typography/inline/InlineRunFoldTest.kt" "$KT/typography/inline/InertOutOfFlowMember.kt" "$KTT/core/renderer/RunFoldBreadcrumbSeamTest.kt"

# ── L4 ──────────────────────────────────────────────────────────────────────
apply "$R/wave53-float-avoid/seam-1.patch"
commit "wave53 L4-android: a flow-root beside floats takes the gap the floats leave (FloatAvoidPlan/FloatAvoidLayout; CSS 2.1 §9.5 + css-contain inline-size)" \
"Revert unit L4-android (tools/titan/results/wave53-float-avoid/_note.md). First device execution of a Compose Layout — probe-gated." \
  "$KT/core/renderer/ComponentRenderer.kt" "$KTT/core/renderer/FloatAvoidSeamWiringTest.kt" "$KT/layout/FloatAvoidPlan.kt" "$KT/layout/FloatAvoidLayout.kt" "$KTT/layout/FloatAvoidPlanTest.kt" \
  "$R/wave53-float-avoid"
apply "$R/wave53-float-avoid/seam-2.patch"
commit "wave53 L4-ios: the SwiftUI twin of the float-avoiding flow-root (FloatAvoidPlan.swift / FloatAvoidLayout.swift)" \
"Revert unit L4-ios." \
  "$SW/Renderer/ComponentRenderer.swift" "$SWT/FloatAvoidLayoutRasterTests.swift" "$SW/StyleEngine/layout/FloatAvoidPlan.swift" "$SW/Renderer/FloatAvoidLayout.swift" "$SWT/FloatAvoidPlanTests.swift"

# ── L3: the four unit patches replayed on HEAD's bytes of its 17 paths ─────
L3=$R/wave53-canvas-root
L3_PATHS=(
  apps/web-harness/src/ui/ComposedCaptureGallery.tsx
  apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt
  apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift
  runtimes/web/src/engine/background/RootBackgroundPropagation.ts
  runtimes/web/tests/background/RootBackgroundPropagation.test.ts
  apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx
  "$KT/background/RootBackgroundPropagation.kt"
  apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasRootBackgroundTest.kt
  "$SW/StyleEngine/background/RootBackgroundPropagation.swift"
  "$SWT/WPTCaptureModeTests.swift"
  apps/web-harness/src/ui/CanvasTableBody.ts
  apps/web-harness/tests/ui/ComposedCanvasTableBody.test.tsx
  "$SW/StyleEngine/table/TableBodyForest.swift"
  "$SWT/TableBodyForestTests.swift"
  "$KT/table/TableBodyForest.kt"
  "$KTT/table/TableBodyForestTest.kt"
  apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt
)
# Save the tree's bytes, go back to HEAD's bytes for these paths, replay the patches with a commit each, then check.
SAVE="$(mktemp -d)"
for p in "${L3_PATHS[@]}"; do mkdir -p "$SAVE/$(dirname "$p")"; [[ -f "$p" ]] && cp "$p" "$SAVE/$p"; done
for p in "${L3_PATHS[@]}"; do if git ls-files --error-unmatch "$p" >/dev/null 2>&1; then git checkout -q HEAD -- "$p"; else rm -f "$p"; fi; done
apply "$L3/unit-A.patch"
commit "wave53 L3-A: a display: contents root's background-image layers reach the canvas on all three platforms (RootBackgroundPropagation; css-backgrounds-3 §2.11.2)" \
"Revert unit L3-A (tools/titan/results/wave53-canvas-root/_note.md). Reverting undoes display-contents-root-background x3 and the two background-attachment-margin-root cells." \
  "${L3_PATHS[@]:0:10}" "$L3"
apply "$L3/unit-B-web.patch"
commit "wave53 L3-B-web: a display: table body gets Chrome's CSS 2.1 §17.2.1 fixup on the web canvas (CanvasTableBody)" "Revert unit L3-B-web." \
  apps/web-harness/src/ui/CanvasTableBody.ts apps/web-harness/tests/ui/ComposedCanvasTableBody.test.tsx apps/web-harness/src/ui/ComposedCaptureGallery.tsx
apply "$L3/unit-B-ios.patch"
commit "wave53 L3-B-ios: the SwiftUI canvas builds the anonymous table forest for a display: table body (TableBodyForest.swift)" "Revert unit L3-B-ios (probe-gated: the native half is the wave's pre-declared revertible item)." \
  "$SW/StyleEngine/table/TableBodyForest.swift" "$SWT/TableBodyForestTests.swift" apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift
apply "$L3/unit-B-android.patch"
commit "wave53 L3-B-android: the Compose canvas builds the anonymous table forest for a display: table body (TableBodyForest.kt)" "Revert unit L3-B-android (probe-gated; the lane predicts its own probe line to fail — revert rule 4)." \
  "$KT/table/TableBodyForest.kt" "$KTT/table/TableBodyForestTest.kt" apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt
# The replay must reproduce the lanes' tree byte for byte.
bad=0
for p in "${L3_PATHS[@]}"; do
  if ! cmp -s "$p" "$SAVE/$p"; then echo "L3 replay differs from the lane's tree: $p" >&2; bad=1; fi
done
rm -rf "$SAVE"
(( bad )) && exit 1
echo "landed $n unit commits; remaining working-tree changes:"; git status --short | head -20
