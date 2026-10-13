TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-build-L3

# wave 55 · L3 positioned-parent-slot — builder lane note

Plan: `tools/titan/results/wave55-plan/PLAN.md` §2 "### L3 · positioned-parent-slot"; brief `wave55-plan/positioned-parent-slot.md`;
contract `expectations.json` lanes["L3-positioned-parent-slot"]; landing `landing-manifest.json` seq 19 (U1: unit-U1.patch then
seam-1.patch) and seq 20 (U2: unit-U2.patch, needs U1). Base tree 611c9f93 (= dev 0d0d3a3d + the wave-55 plan + the opening-gate
records). Compose only: no web, iOS, converter or extractor byte changes (wire carriers 0).

## What changed and why

The defect (looked at, not only scored): `wave54-final selectors/dir-style-02a android f 0.9425` — next to its frozen ref, rows 6
and 9 read "This element is rt" at the RIGHT edge (ink x246-373, ref x17-153) and row 7 lacks its leading dot;
`wave54-final css-writing-modes/dynamic-offset-rtl-001 android P 0.9952` paints the magenta box at x186, OUTSIDE the yellow box
(ref x86) — a DEGENERATE pass; `wave54-final css-text/bidi/bidi-tab-001 android P 0.9805` mirrors the `0` of its four RTL rows
(strip `wave55-plan/positioned-parent-slot-look/bidi-tab-001.ref-web-ios-android.png`).

Mechanism (re-read at 611c9f93, brief §2 confirmed): `ComponentRenderer.kt` `renderBlockChild` mounted
`PositionedParentFlowSlot.mount` only under `CanvasRootHoist.LocalActive` — a composed capture whose host stayed inactive kept the
stacking Column footprint (row-7 dot: 0 px of remaining height). And the mount reports 0×0, so an RTL Column (provided for a
`Direction: RTL` box, never re-provided Ltr below it) aligns the slot at its end edge: ink at W + left (x186 = 16 + 100 + 70).

- **U1** — `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt` (NEW, 140 lines
  in the U1 state): `engages(hostActive, composedCapture, parentRtl) = hostActive || (composedCapture && !parentRtl)`,
  `decide(…)` → `SlotMount.{IDENTITY, MOUNT}`, `withheldByRtlGuard`, `slotModifier(…)` (the call-site helper; records
  `PositionedParentSlot[rtl-parent-unmounted]` via PropertyTracker when the RTL guard withholds an applying mount).
  Seam 1 (`ComponentRenderer.kt` :3644-3661 → one `PositionedParentSlotGate.slotModifier(child.properties,
  component.properties, hostActive = …CanvasRootHoist.LocalActive.current, composedCapture = LocalWptComposedMode.current,
  parentRtl = LocalLayoutDirection.current == LayoutDirection.Rtl)` call, read in the PARENT's content scope; the
  "Host-gated exactly like RC1" comment re-trued). `PositionedParentFlowSlot.kt` :87-89 header paragraph re-trued (comment only).
  An active host keeps every pre-wave-55 mount byte for byte; the dark stage / inbox path (no composed capture) keep the
  identity Modifier; PLAIN (no `!parentRtl`) is never shipped.
- **U2** — `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt` (NEW, 178):
  `decline(parentRtl, parentProps, childProps)` → `RtlOriginDecline.{NONE, NOT_RTL, PARENT_WIDTH_NOT_PX,
  NO_PHYSICAL_INLINE_INSET, BOTH_INLINE_INSETS}` (each keep-today reason carries its own `PositionedParentSlot[rtl-origin-declined:…]`
  breadcrumb), `applies`, `originPx(boundedMaxPx) = −maxWidth`, `placedXPx`, the pure `inkLeftPx` model (Column start
  alignment + origin + `CanvasRootHoist.anchorPlacePx`'s `right` anchor + the physical offset), and `originModifier()` — one outer
  `Modifier.layout` that passes the Column's constraints through untouched and `place`s (never `placeRelative`) the zero-flow
  anchor at x = −maxWidth, so the ink lands at colWidth − maxWidth + left = left (CSS 2.1 §10.3.7 / css-position-3 §3.1: `left`
  is physical; css-writing-modes-4 §2.1). The gate's RTL branch (`rtlOriginEngages = parentRtl && (hostActive ||
  composedCapture)`, `SlotMount.MOUNT_RTL_ORIGIN`, `recordRtlOriginDecline`) and `PositionedParentFlowSlot.mount(childProperties,
  rtlOrigin: Boolean = false)` = `(if (rtlOrigin) PositionedParentRtlOrigin.originModifier() else Modifier).then(…zeroFlowAnchor…)`
  (the default is the one-element wave-46 mount: `Modifier.then(x)` is `x`).
- Breadcrumbs (PLAN §0 "No silent fallthroughs"): `PositionedParentSlot[rtl-parent-unmounted]` (U1),
  `PositionedParentSlot[rtl-origin-declined:parent-width-not-px | no-physical-inline-inset | both-inline-insets]` (U2). The
  wave-54 corpus has none of the three decline shapes (census: all 11 RTL Column-loop parents declare a px Width, all 13 children
  `left` only), so no carrier records one.
- Finding (instrument): the first cut named the gate's nested enum `Slot`. With seam 1 UN-applied, `SeamReachabilityTest` stayed
  GREEN — it resolves every declared type name corpus-wide (`typeDecl` matches nested classes), and other files name `Slot`, so the
  gate looked reachable without its only caller. Renamed `SlotMount` (unique; the kdoc says why) and re-executed: red, naming
  `[layout/position/PositionedParentSlotGate.kt]`. Any module with a common nested type name can defeat that pin — queued below.

House rules: every new file ≤ 200 lines (gate 198 in the full state); oversized files touched only as PLAN §0 allows
(`ComponentRenderer.kt` 8488 → 8492 via seam 1 only; `PositionedParentFlowSlot.kt` 169 → 179: the comment, one defaulted
parameter and one `.then`; `SeamReachabilityTest.kt` 239 → 241: one module line per unit at the pinned :182 anchor). No scratchpad
pointer in any delivered file (grep of the three patches, `census/`, `cut-patches.sh`: none). "runtime", never "engine".

## Revert unit U1 — composed-capture engagement (LTR parents)  [landing seq 19; revertOrder U2 → U1]
Commit paths (exactly):
- unit-U1.patch: NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt`;
  `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentFlowSlot.kt` (comment);
  NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGateTest.kt`.
- seam-1.patch: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` (seam, base sha256
  2445d9ea2674576fc0946e3f630f48b88d48f0f9d8c98df3249ba67d7a026aa5, anchor `val outOfFlowSlotMount: Modifier =` base :3650);
  NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/PositionedParentSlotSeamWiringTest.kt` (patch-borne);
  `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt` (patch-borne U1 line after
  `"transforms/TransformInheritance.kt",` base :182, base sha256 7a1b061f1de5d288af6581d81b35744de9b579a41a7e9388d4ded1c0d9b08f56).
Apply order: unit-U1.patch, then seam-1.patch (manifest applyOrder). Rule 2b: the owned files compile and test green without
seam-1 (state "unit-U1 without seam-1" below).

## Revert unit U2 — the RTL physical-left origin  [landing seq 20; needs U1; revertOrder U2]
Commit paths (exactly), unit-U2.patch (cut against 611c9f93 + unit-U1.patch + seam-1.patch through a temporary index — the
worktree's own index untouched): NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt`;
`runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt` (the RTL branch);
`runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentFlowSlot.kt` (`rtlOrigin` + one `.then`);
NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOriginTest.kt`;
`runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt` (the U2 line, directly under U1's).
Cut tool: `cut-patches.sh <worktree> <lane-dir> u1|u2` (in this dir).

Cumulative / revert dry-run (informative, temporary index, `git apply --cached` of ONE concatenated input as land-units.sh does):
`wave55-cbb-android/seam-1.patch` (as delivered at 22:3x) → unit-U1 → seam-1 → unit-U2 applies; then −U2, −seam-1, −unit-U1
reverse-apply. Note: `git apply` with SEVERAL file arguments does not chain patches on one path (unit-U2 then fails on the gate
"does not exist in index"); the concatenated single input — land-units.sh's form — does.

## Census (own script, runtime predicates; PLAN §0 "Skeptic census")
- Script: `census/L3CensusRunTest.kt` (a JVM test run by `census/run.sh <worktree> <run-dir> <out.tsv>`, which copies it into the
  worktree's test tree, runs it and deletes it) — `IRDocumentDecoder.decode` → `SlotComposer.compose`,
  `CanvasRootHoist.hostActivates` / `collectCanvasHoisted`, the Box-branch test, `PositionedParentFlowSlot.applies`, and the lane's
  own `PositionedParentSlotGate.engages` / `.decide`; `census/summarize.py` joins `cells-wave54-final.json` and cross-checks
  `expectations.json`. Approximations are stated in the script banner.
- Output: `census/census.wave54-final.tsv` (138 pairs) and `census/census.wave54-final.out.txt`.
- Result over the 1435 wave54-final per-test IR documents (host inactive on 1221): 107 Column-loop parents / 61 docs; host-active
  54 / 44; host-inactive 53 / 17 (65 children); laid out RTL 11 / 7 (13 children, 7 host-inactive); Box-branch 28. U1 reach 56
  children / 46 parents / 16 docs: STACK carriers 3 (`anchor-position-multicol-004`, `-multicol-nested-001`, `dir-style-02a`),
  controls 13 (identity ×10, CLAMP `block-end-aligned-abspos-nested`, AUTO-W/H `text-decoration-subelements-002`, plus
  `bidi-tab-001`'s LTR rows). U2 reach 13 children / 11 parents / 7 docs. Union 9.
- Cross-check: U1 3 = 3, U2 7 = 7, union 9 = 9, wire 0 = 0 — **CENSUS IDENTICAL to expectations (no growth)**; every number
  equals the brief's JS port. Reached parents' displays: 75 none-declared, 27 BLOCK, 2 FLEX (host-active, unchanged), 2 INLINE
  (`dir-selector-change-003/-004`, single child at cursor 0: identity), 1 NONE (host-active, unchanged).
- The shared tree's `expectations.json` carries an UNCOMMITTED round-4 edit while this lane ran; L3's `captureCarriers`,
  `wireCarriers`, `revertUnits`, `predictions`, `carrierRule`, `stage1Decision` and `neverShip` equal HEAD 611c9f93's (only the
  `geometryProbes[0].teeth` prose differs), and `positioned-parent-slot.geometry.py` / `geometry-gate.py` are HEAD bytes.
- `land-units.sh --check --only L3` on the shared tree (read-only): 0 errors, 1 warning (the note was < 120 s old) — patches
  parsed, 8 paths (5 created) clean at HEAD, cumulative apply OK, revert dry-runs [U2, U1] and [U2] OK.
- Dark stage / fixture net: `LocalWptComposedMode provides true` exists only in `ScreenshotCaptureScreen.kt` :587 (titan composed
  mode), so committed baselines see composedCapture = false and hostActive = false → `decide` = IDENTITY (pinned row U1a).

## Geometry probe self-test (task rule 4)
- `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave54-final --base wave54-final --lanes L3 --self-test`
  → exit 0, HOLDS: gating 7 FAIL / 7, report 2 FAIL / 2, control 32 PASS / 32 (`geometry-selftest.base-wave54-final.txt`).
- Same with `--base wave55-open` → exit 0, HOLDS (`geometry-selftest.base-wave55-open.txt`).
- As the task text spells it, `--base wave54-open` → exit 1: one control FAILs, `[C] bidi-lines-002 android` — the documented
  wave-54 hunk-P mover between wave54-open and wave54-final (`wave55-plan/positioned-parent-slot.geometry.control-can-fail.txt`:
  the identity controls CAN fail; `anchor-center-safe-rtl`, the other one, is dropped by C6). Not an L3 defect: the base that
  matches the plan (§ header item 4, wave54-final) and the wave's real base (wave55-open) both hold.

## Pins (each proven able to fail by an executed mutation; bracketed lines in the mutation log below)
Focused suite = PLAN "May run": `:runtime:testDebugUnitTest --tests '*PositionedParent*' --tests '*SeamReachabilityTest'
--tests '*CanvasRootHoistTest' --tests '*StaticPositionSeamWiringTest' --tests '*OutOfFlowContainingBlockTest'`, run with
`--rerun` (executed, never UP-TO-DATE) and with the wave53-final corpus visible so the OutOfFlowContainingBlockTest whole-corpus
tripwire EXECUTES (0 skipped; it is unchanged and green in every state).
- U1 (a) truth table — `PositionedParentSlotGateTest.U1a`: drop `composedCapture &&` → red at :42 (dark-stage row) [U1-a1-final];
  drop `!parentRtl` → red at :40 (RTL row) [U1-a2-b-final]; collapse to `hostActive` → red at :38 (F,T,F row) [U1-a3-final].
- U1 (b) verbatim dir-style-02a mount set — `U1b` (Position / Direction / insets / size declarations of the rows 4-9 subtrees,
  verbatim from wave54-final, programmatically checked against the file): plain mount = exactly the runs of `__3__0`, `__3__1`,
  `__4__0`, `__4__1`; drop `!parentRtl` → red at :159 [U1-a2-b-final].
- U1 (c) seam wiring — `PositionedParentSlotSeamWiringTest` PW1/PW2: the pre-seam text (ComponentRenderer.kt at its 611c9f93
  bytes) → both red [U1-c-d-final].
- U1 (d) `SeamReachabilityTest` with the U1 line: same mutation → red, `unreachable … [layout/position/PositionedParentSlotGate.kt]`
  [U1-c-d-final] (and the earlier [U1-cd-seam1-unapplied] run that stayed green — the `Slot` collision above).
- U2 (a) `originPx(358) == −358`, `originPx(null) == null` — `U2a`; flip the sign → red [U2-a1-origin-sign].
- U2 (b) the applies / decline table — `U2b` (RTL+px+left T; LTR F; RTL no Width F; RTL % Width F; RTL no inset F; RTL logical-only
  F; RTL `right`-only T; RTL both F); drop the px-Width rule → red [U2-b1-drop-px-width-rule].
- U2 (c) `inkLeftPx` — `U2c`: (100,100,rtl,no origin,70) = 170; (100,100,rtl,origin,70) = 70; (358,358,rtl,origin,4.61) = 4.61;
  LTR = left; `right`-only 10-px = 80. Drop the origin → red "expected 70.0 but was 170.0" [U2-c1-drop-the-origin];
  placeRelative semantics (−w − x) → red "expected 70.0 but was 270.0" [U2-c2-placeRelative-semantics]; the literal
  `placeable.placeRelative(` in `originModifier` → red (source pin) [U2-c3-literal-placeRelative].
- U2 (d) verbatim bidi-tab-001 `__2__0` / `__3__0`, dynamic-offset-rtl-001 `__1` (host ACTIVE) and dir-style-02a `__3__2`: applies T and
  `decide` = MOUNT_RTL_ORIGIN in their own host state, IDENTITY on the dark stage — `U2d`; drop the gate's RTL branch → red
  [U2-d1-drop-gate-rtl-branch].
- U2 (e) `PositionedParentFlowSlotTest`'s one-element pin stays green unchanged (default `rtlOrigin = false`); new two-element pin +
  the decline breadcrumb + the RTL-origin rows — `U2e`; default `rtlOrigin = true` → BOTH red [U2-e1-default-rtlOrigin-true].
## Mutation log (bracketed: MUTATING before the edit, RESTORED after the byte-exact restore; worktree-relative paths)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f1e88dab220cec18f0cdda750a88bbd4887625ead731ba66fa4433d1ebefcc0   [U1-a1-drop-composedCapture]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f1e88dab220cec18f0cdda750a88bbd4887625ead731ba66fa4433d1ebefcc0   [U1-a1-drop-composedCapture]
  [U1-a1-drop-composedCapture] red: gradle exit 1, failed tests: > Task :runtime:testDebugUnitTest FAILED;PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f1e88dab220cec18f0cdda750a88bbd4887625ead731ba66fa4433d1ebefcc0   [U1-a2-drop-not-parentRtl]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f1e88dab220cec18f0cdda750a88bbd4887625ead731ba66fa4433d1ebefcc0   [U1-a2-drop-not-parentRtl]
  [U1-a2-drop-not-parentRtl] red: gradle exit 1, failed tests: > Task :runtime:testDebugUnitTest FAILED;PositionedParentSlotGateTest > U1b dir-style-02a - the plain mount takes the LTR parents' children only FAILED;PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f1e88dab220cec18f0cdda750a88bbd4887625ead731ba66fa4433d1ebefcc0   [U1-a3-collapse-to-hostActive]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f1e88dab220cec18f0cdda750a88bbd4887625ead731ba66fa4433d1ebefcc0   [U1-a3-collapse-to-hostActive]
  [U1-a3-collapse-to-hostActive] red: gradle exit 1, failed tests: > Task :runtime:testDebugUnitTest FAILED;PositionedParentSlotGateTest > U1b dir-style-02a - the plain mount takes the LTR parents' children only FAILED;PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-cd-seam1-unapplied: the pre-seam text — ComponentRenderer.kt back to its 611c9f93 bytes, seam-1's test files kept]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-cd-seam1-unapplied]
  [U1-cd-seam1-unapplied] red: gradle exit 1, failed tests: > Task :runtime:testDebugUnitTest FAILED;PositionedParentSlotSeamWiringTest > PW2 the host-only gate is gone from the block-child loop FAILED;PositionedParentSlotSeamWiringTest > PW1 the block-child mount is the gate's helper, fed by all three flags FAILED; · green after restore: gradle exit 0
  NOTE: the first [U1-cd-seam1-unapplied] run kept SeamReachabilityTest GREEN — the gate's nested `enum class Slot` collided with other files' `Slot` identifiers (the pin resolves type names corpus-wide), so the file looked reachable without the seam. Renamed to SlotMount and re-executed below.
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-cd-seam1-unapplied-r2: the pre-seam text — ComponentRenderer.kt back to its 611c9f93 bytes, seam-1's test files kept]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-cd-seam1-unapplied-r2]
  [U1-cd-seam1-unapplied-r2] red: gradle exit 1, failed tests: PositionedParentSlotSeamWiringTest > PW2 the host-only gate is gone from the block-child loop FAILED;PositionedParentSlotSeamWiringTest > PW1 the block-child mount is the gate's helper, fed by all three flags FAILED;SeamReachabilityTest > every module wave 49 added to the compose runtime has a live call site FAILED;> Task :runtime:testDebugUnitTest FAILED; · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-cd-seam1-unapplied-r3]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-cd-seam1-unapplied-r3]
  [U1-cd-seam1-unapplied-r3] red: gradle exit 1, failed: PositionedParentSlotSeamWiringTest > PW2 the host-only gate is gone from the block-child loop FAILED;PositionedParentSlotSeamWiringTest > PW1 the block-child mount is the gate's helper, fed by all three flags FAILED;SeamReachabilityTest > every module wave 49 added to the compose runtime has a live call site FAILED; msgs: AssertionError: the LocalActive-only guard must be gone" type="java.lang.AssertionError">java.lang.AssertionError: the LocalActive-only guard must be gone|AssertionError: mount must be PositionedParentSlotGate.slotModifier(" type="java.lang.AssertionError">java.lang.AssertionError: mount must be PositionedParentSlotGate.slotModifier(|AssertionError: unreachable from ScreenshotCaptureScreen (no live call site): [layout/position/PositionedParentSlotGate.kt]" type="java.lang.AssertionError">java.lang.AssertionError: unreachable from ScreenshotCaptureScr| · green after restore: gradle exit 0
  (re-executed below on the post-rename bytes: U1-a1..a3)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 300d3b0f57d39e7cd348fec296c9bf89b2b95cd8b485cb9b835ea67e3df8901d   [U1-a1-drop-composedCapture-r2]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 300d3b0f57d39e7cd348fec296c9bf89b2b95cd8b485cb9b835ea67e3df8901d   [U1-a1-drop-composedCapture-r2]
  [U1-a1-drop-composedCapture-r2] red: gradle exit 1, failed: PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; msgs: AssertionError: expected same:|AssertionError: expected same:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 300d3b0f57d39e7cd348fec296c9bf89b2b95cd8b485cb9b835ea67e3df8901d   [U1-a2b-drop-not-parentRtl-r2]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 300d3b0f57d39e7cd348fec296c9bf89b2b95cd8b485cb9b835ea67e3df8901d   [U1-a2b-drop-not-parentRtl-r2]
  [U1-a2b-drop-not-parentRtl-r2] red: gradle exit 1, failed: PositionedParentSlotGateTest > U1b dir-style-02a - the plain mount takes the LTR parents' children only FAILED;PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected same:|AssertionError: expected same:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 300d3b0f57d39e7cd348fec296c9bf89b2b95cd8b485cb9b835ea67e3df8901d   [U1-a3-collapse-to-hostActive-r2]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 300d3b0f57d39e7cd348fec296c9bf89b2b95cd8b485cb9b835ea67e3df8901d   [U1-a3-collapse-to-hostActive-r2]
  [U1-a3-collapse-to-hostActive-r2] red: gradle exit 1, failed: PositionedParentSlotGateTest > U1b dir-style-02a - the plain mount takes the LTR parents' children only FAILED;PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-c1-drop-the-origin]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-c1-drop-the-origin]
  [U2-c1-drop-the-origin] red: gradle exit 1, failed: PositionedParentRtlOriginTest > U2c inkLeftPx - the physical-left origin and the element's place FAILED; msgs: AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-c2-placeRelative-semantics]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-c2-placeRelative-semantics]
  [U2-c2-placeRelative-semantics] red: gradle exit 1, failed: PositionedParentRtlOriginTest > U2c inkLeftPx - the physical-left origin and the element's place FAILED; msgs: AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-c3-literal-placeRelative]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-c3-literal-placeRelative]
  [U2-c3-literal-placeRelative] red: gradle exit 1, failed: PositionedParentRtlOriginTest > U2c inkLeftPx - the physical-left origin and the element's place FAILED; msgs: — · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-a1-origin-sign]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-a1-origin-sign]
  [U2-a1-origin-sign] red: gradle exit 1, failed: PositionedParentRtlOriginTest > U2a originPx is minus the bounded max width, none when unbounded FAILED;PositionedParentRtlOriginTest > U2c inkLeftPx - the physical-left origin and the element's place FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-b1-drop-px-width-rule]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt sha256 dc7a408e1a27ada4188ca5ce497933419437266ac2fc5a1bbd62e27680bf6493   [U2-b1-drop-px-width-rule]
  [U2-b1-drop-px-width-rule] red: gradle exit 1, failed: PositionedParentRtlOriginTest > U2b applies - the decline table FAILED;PositionedParentRtlOriginTest > U2e the origin mount is two elements, and a declined origin is named FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 754085db42a04caafc50a46f71b1fe21d83d528388e22deda84192db140ffc3a   [U2-d1-drop-gate-rtl-branch]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 754085db42a04caafc50a46f71b1fe21d83d528388e22deda84192db140ffc3a   [U2-d1-drop-gate-rtl-branch]
  [U2-d1-drop-gate-rtl-branch] red: gradle exit 1, failed: PositionedParentRtlOriginTest > U2d the verbatim RTL carriers take the origin in their own host state FAILED;PositionedParentRtlOriginTest > U2e the origin mount is two elements, and a declined origin is named FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentFlowSlot.kt sha256 be07853b2433cabb0d190e89eaec0d2a0d04baa3baaceafc1d6209c4ae170045   [U2-e1-default-rtlOrigin-true]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentFlowSlot.kt sha256 be07853b2433cabb0d190e89eaec0d2a0d04baa3baaceafc1d6209c4ae170045   [U2-e1-default-rtlOrigin-true]
  [U2-e1-default-rtlOrigin-true] red: gradle exit 1, failed: PositionedParentFlowSlotTest > mount installs one layout element and reads the end anchor through anchorGate FAILED;PositionedParentRtlOriginTest > U2e the origin mount is two elements, and a declined origin is named FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
  (final bytes: re-executed in the patch-built U1 state — base + unit-U1.patch + seam-1.patch as delivered)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f9584c4d714282a50af0bde11729024462dbd07f5f93b053c570c94f46802bd   [U1-a1-final]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f9584c4d714282a50af0bde11729024462dbd07f5f93b053c570c94f46802bd   [U1-a1-final]
  [U1-a1-final] red: gradle exit 1, failed: PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; msgs: AssertionError: expected same:|AssertionError: expected same:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f9584c4d714282a50af0bde11729024462dbd07f5f93b053c570c94f46802bd   [U1-a2-b-final]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f9584c4d714282a50af0bde11729024462dbd07f5f93b053c570c94f46802bd   [U1-a2-b-final]
  [U1-a2-b-final] red: gradle exit 1, failed: PositionedParentSlotGateTest > U1b dir-style-02a - the plain mount takes the LTR parents' children only FAILED;PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected same:|AssertionError: expected same:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f9584c4d714282a50af0bde11729024462dbd07f5f93b053c570c94f46802bd   [U1-a3-final]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt sha256 3f9584c4d714282a50af0bde11729024462dbd07f5f93b053c570c94f46802bd   [U1-a3-final]
  [U1-a3-final] red: gradle exit 1, failed: PositionedParentSlotGateTest > U1b dir-style-02a - the plain mount takes the LTR parents' children only FAILED;PositionedParentSlotGateTest > U1a engages - every row of the truth table FAILED;PositionedParentSlotGateTest > U1c slotModifier - identity, the one-element mount, the RTL breadcrumb FAILED; msgs: AssertionError: expected:|AssertionError: expected:|AssertionError: expected:|AssertionError: expected:| · green after restore: gradle exit 0
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-c-d-final]
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 bd3910015afaae4054a8f2ca2d444820eec80d469db081a2608300cbfe75212c   [U1-c-d-final]
  [U1-c-d-final] red: gradle exit 1, failed: PositionedParentSlotSeamWiringTest > PW2 the host-only gate is gone from the block-child loop FAILED;PositionedParentSlotSeamWiringTest > PW1 the block-child mount is the gate's helper, fed by all three flags FAILED;SeamReachabilityTest > every module wave 49 added to the compose runtime has a live call site FAILED; msgs: AssertionError: the LocalActive-only guard must be gone" type="java.lang.AssertionError">java.lang.AssertionError: the LocalActive-only guard must be gone|AssertionError: mount must be PositionedParentSlotGate.slotModifier(" type="java.lang.AssertionError">java.lang.AssertionError: mount must be PositionedParentSlotGate.slotModifier(|AssertionError: unreachable from ScreenshotCaptureScreen (no live call site): [layout/position/PositionedParentSlotGate.kt]" type="java.lang.AssertionError">java.lang.AssertionError: unreachable from ScreenshotCaptureScr| · green after restore: gradle exit 0

## Revert states (PLAN §0 "Revert states compile"; built from the delivered patches on 611c9f93 owned paths — HEAD bytes / new
## files removed, then `git apply` of the kept patches — byte-compared with the authoring copies, focused suite with --rerun)
revertStatesCompiled: full (unit-U1 + seam-1 + unit-U2): compiled; focused 60 tests, 0 failures, 0 skipped (final restore: 60 / 0 / 0 again)
revertStatesCompiled: U1 = U2 reverted (unit-U1 + seam-1): compiled; focused 55 tests, 0 failures, 0 skipped
revertStatesCompiled: base = U1 revertOrder [U2, U1] fully reverted (611c9f93): compiled; focused 50 tests, 0 failures, 0 skipped
revertStatesCompiled: unit-U1 without seam-1 (rule 2b, not a landing state): compiled; focused 53 tests, 0 failures, 0 skipped
(Compose `:runtime` test count delta for the orchestrator's sweep / doc restamp: +10 — GateTest 3, RtlOriginTest 5, SeamWiringTest 2.)

## Predictions per target cell (wave54-final → the wave-55 closing gate; unchanged from expectations.json — the census did not move)
| cell | from → to | confidence | geometry |
|---|---|---|---|
| selectors/dir-style-02a android | f 0.9425 → P ≈0.987 (replay U1+U2 0.9866) | MED-HIGH (U1 only: f ≈0.943) | `[S] … row6 / row7 / row9 → GEOMETRY OK` |
| css-text/bidi/bidi-tab-001 android | P 0.9805 → P ≈0.990 (DEGENERATE → faithful) | MED-HIGH | `[S] bidi-tab-001 android rtl-rows → GEOMETRY OK` |
| css-writing-modes/dynamic-offset-rtl-001 android | P 0.9952 → P ≈1 | MED-HIGH | `[S] dynamic-offset-rtl-001 android → GEOMETRY OK` |
| css-writing-modes/dynamic-offset-rtl-002 android | P 0.9953 → P ≈0.9956 (web's picture; stays DEGENERATE) | MED | `[W] dynamic-offset-rtl-002 android → GEOMETRY OK` |
| css-writing-modes/dynamic-offset-vrl-rtl-001 android | P 0.9952 → P ≈1 | MED (vertical-rl, R2) | report key |
| css-writing-modes/dynamic-offset-vrl-rtl-002 android | P 0.9952 → P ≈0.9955 | MED | report key |
| css-anchor-position/anchor-position-multicol-nested-001 android | f 0.9573 → f ≈0.969 (coverage veto holds: NOT a flip) | MED | `[W] … android stack → GEOMETRY OK` |
| css-anchor-position/anchor-position-multicol-004 android | P 0.9686 → P, \|Δ\| ≤ 0.01, red unchanged | LOW | `[C] … red` |
| css-text/hyphens/hyphenate-character-005 android | f 0.9474 → f, undirected (kashida run 4.5 px left) | LOW | — |
Every other capture: byte-identical (web 1435, iOS 1435, the wire, Android outside the 9 carriers — including the 13 U1 controls);
0 lost. My build adds no evidence beyond the JVM model (`inkLeftPx` reproduces the device's x186 − 16 = 170 today and the ref's 70
with the origin); the device is the evidence (stage 1).

## ORCHESTRATOR WINDOW REQUESTS
None of L3's own (PLAN §2 L3). L3 is READ in the orchestrator's windows:
1. Stage 1 (union tree, after `beforeEveryDeviceRun`): `tools/titan/gate-driver.sh wave55-pre --sections css-counter-styles,css-flexbox,css-grid,css-masking,css-text,css-text-decor,css-writing-modes,filter-effects,selectors --skip-fixture-net`,
   then `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/positioned-parent-slot.geometry.py wave55-pre wave55-open`
   — expected: `[S] dir-style-02a android row6`, `row7`, `row9`, `[S] bidi-tab-001 android rtl-rows`, `[S] dynamic-offset-rtl-001 android`,
   `[W] dynamic-offset-rtl-002 android` each `→ GEOMETRY OK`; `[C] dir-selector-change-003 / -004 android`, `[C] dir-style-02a android
   unaffected-rows`, `[C] dir-style-02a web / ios`, `[C] bidi-tab-001 android ltr-rows / web / ios` each `identical to wave55-open →
   GEOMETRY OK`; and `node tools/titan/results/wave52-gate/cells.mjs 'dir-style|dir-selector|bidi-tab|dynamic-offset' wave55-open wave55-pre`
   → dir-style-02a android P ≥ 0.95 (≈0.987). OPEN dir-style-02a android next to its ref: rows 6 / 7 / 9 start at x16 with their dot.
   Decision (`stage1Decision`): row 6 / 9 / rtl-rows / dynamic-offset not OK, or the cell < 0.95 → revert U2; row 7 not OK, or an
   Android selectors capture outside the union carriers moved and the rule-5 bisect lands on U1 / U2 → revert U2, then U1.
2. Stage 2 `wave55-probe` (26 sections): `[W] anchor-position-multicol-nested-001 android stack → GEOMETRY OK` (not OK → revert U2
   then U1) and the U1 identity controls (`[C] css-tags-paint-order(-with-entry)`, `block-end-aligned-abspos-nested`,
   `position-absolute-scrollable-overflow-zero`, `attachment-local-positioning-3/-4`, `text-decoration-subelements-002`, …)
   `identical to wave55-open → GEOMETRY OK`.
3. Closing gate: `[C] composited-filters-under-opacity android` (css-color, first read there; a host-ACTIVE LTR parent — identity by
   construction under U1 and U2).

## Hand-offs / queued (not this lane's to edit)
- BACKLOG 0(i) re-true (brief §3): 8 parents / 5 docs; reach 53 / 17 + the 4 host-active RTL documents; dots alone +0.0003.
- New queue item (R7): the SwiftUI twin — SwiftUI's out-of-flow overlay treats physical `left` as leading under RTL
  (`wave54-final selectors/dir-style-02a ios f 0.9455`, `wave54-final css-writing-modes/dynamic-offset-rtl-001 ios P 0.9955`).
- New queue item (instrument): `SeamReachabilityTest`'s `typeDecl` resolves NESTED type names corpus-wide, so a module whose nested
  type shares a common name (`Slot`) looks reachable with its only call site removed — executed above. Fix: index only top-level
  declarations (or qualify nested names by their owner) and add the `Slot` case as its negative control.
- `CLAUDE.md` / `docs/STATUS.md` Compose test count (+10), via the orchestrator's sweep.

## Not verified
- Nothing on a device: the U1 mount under host-INACTIVE composed captures and the U2 origin are first executed at stage 1
  (`wave55-pre`); the vertical-rl twins (R2) and `hyphenate-character-005`'s direction are undetermined until a capture.
- The census's own approximations (banner): the harness root rewrite is not applied; Compose's used direction is modelled.
- No full suite (single-writer rule); other lanes' patches were not composed with mine except L2's seam-1 (informative check).

## Delivered patches (sha256 of the file bytes, after the final restore)
patch: unit-U1.patch sha256 b22a582a68f992e35370a2cb15f43be1dc05b25fea4890c22e7b0db233a0f4c3
patch: seam-1.patch sha256 1f9c4717ea4aeb3c8003324f9199f7216f1316adf901e92e7df5b33c1839468b
patch: unit-U2.patch sha256 13c8ee014503ce4bd8c9f40bf1f1e9899e9595feffc58febfb88208170c4db36

STATUS: COMPLETE
