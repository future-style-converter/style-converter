# wave-52 lane L2 · composed-canvas — lane note

Tree: `campaign/wave52` (HEAD 7d9c22a7), shared with up to eleven other lanes. Plan: `tools/titan/results/wave52-plan/PLAN.md` §2 L2 (+ §3/§4/§5/§9).
Briefs: `page-padding-overrun.md` §3-A/§4 Fix A · `runtime-bugs-and-gaps.md` §3-M1/§4-2 · `native-near-misses.md` §3.1/3.2(roots)/3.3/3.6.

## 0. Resume record (third start)

The two killed starts left UNCOMMITTED code in every owned file and NOTHING in this results dir (no note, census or mutation log).
Read and verified against §2 L2, KEPT: Fix A on all three canvases, the M1 resolvers ×3, the fold T1/T2 (`composedRootStackPlan`
extracted pure on both natives), T3 (zIndex on the RC1 root, harness-side) and their pins. Found and FIXED on this start — each a
real defect in the earlier work:

1. **M1 applied the body margin TWICE.** The body-root also renders as the first root and kept its own `Margin*`: web wrapper 60 +
   the body-root div's 60; natives Column inset + the fold's 60/60 gaps (collapsed-border would have gone from iOS's measured 136
   to 196, ref 76; background-attachment-margin-root-001's 300-tall body box from (66,66) to (116,116)). Fix — ONE OWNER:
   `withCanvasOwnedBodyMargin` ×3 strips exactly the resolved (concrete-px) sides from the body-root before the forest is rendered
   and folded (web `flowRoots`; Android shadows `roots` right after the resolver; iOS `splitRoots` via the runtime helper
   `UABlockMargin.withCanvasOwnedBodyMargin`, since the harness cannot reach `IRComponent`'s internal init). Identity when zero.
2. **M1 moved `s-11-1-1b-005` (web P 0.9947).** Its body is `display: table-cell`; CSS 2.1 §8.3 — margins do not apply to
   table-internal boxes. Guard added ×3 → 005 resolves to zero, no wrapper, byte-identical. M1: 9 → **8 tests / 24 cells**.
3. **T2's empty-root predicate accepted 35 roots that paint generated content** (`::before`/`::after`, e.g. counter-name-case-
   sensitive's `content:"1-5"` divs, counter-cjk-decimal's 17 `::after` roots). Line boxes ⇒ not self-collapsing (§8.3.1). Guard
   added on both natives (any `pseudos` ⇒ opaque = wave-51 behaviour). The census shows no position changes for those docs even
   without it (zero margins), so correctness-only — pinned (U7 / u7).
4. **The `Display`-BLOCK guard was unpinned** (mutation executed GREEN: u5's table row is ALSO the body-root). New pin U8/u8 on the
   verbatim empty inline `<a>` of selectors/invalidation/any-link-attribute-removal.
5. **iOS harness pin anchor stale** (`.padding(pad)` gone after M1 folded the inset into `.padding(EdgeInsets(…))`) — fixed.
6. The iOS M1 resolver moved into the runtime (`UABlockMargin.canvasBodyMargin`) so it is Catalyst-pinned.
7. **T6 BUILT** (not started before): a canvas-hoisted root (fixed, or absolute WITH an inset) whose tag has a UA block margin and
   which declares none gets the UA margin as two declared longhands (`withUaBlockMarginOnHoistedRoot`, Kotlin harness + Swift
   runtime), applied in the same composed-roots rewrite as M1. CSS 2.1 §9.3.2 / §10.6.4: `top` offsets the MARGIN edge. Census:
   exactly 1 carrier (s-11-1-1b-006 root 3).

## 0b. Fix pass (skeptic must-fix, `skeptic.md` §5) — T3 lifted `z-index: -1` boxes above the flow

Resume check for this pass: the owned files' shas equalled the first-pass record (73485b1d / 53e75a3e / a69bcca5 / 076f91c2 …), no
seam file dirty, no lock held by L2. Only the T3 lines, their pins and the results-dir tooling changed; Fix A, M1, T1, T2, T6 untouched.

**Defect (confirmed, and wider than reported).** The first cut wrapped EVERY RC1 root in `Box(Modifier.zIndex(1f))` / `.zIndex(1)`.
CSS 2.1 Appendix E: a DECLARED z-index is the box's own stacking level (negative → step 3, BELOW in-flow blocks), and content that
follows the RC1 root in tree order at step 8 (a positioned descendant, a stacking context) must stay ABOVE it — a Column/VStack-level
z cannot express either. Re-deriving the T3 rows with the rule below found the skeptic's 11 negative-z tests (22 P cells) PLUS 4 tests
it did not count, whose later root carries step-8 content: `CSS2/abspos/static-inside-inline-001/002/003` (root 2 nests an abspos
green that covers the red RC1 box) and `css-masking/clip-path/clip-path-path-with-zoom` (the later green is a clip-path layer) — all P
on both natives → **8 more P → f cells**; and `css-cascade/unset-val-002` ×2 at risk (its RC1 root paints only through a `display:
unset` span the converter leaves `Generic`-unmapped). First-cut damage: **30 predicted P → f cells (+2 at risk)**, not 0.

**Fix (both natives, harness + runtime helper, no seam).** `composedRootsPaintingAboveFlow` (Kotlin `UaBlockMargins.kt`, Swift twin
`UABlockMargin.composedRootsPaintingAboveFlow` in `ComposedRootStack.swift`) lifts a root only when (1) it is the RC1 slot, (2) it
declares NO z-index (the runtime's own readers: `ItemPlacementExtractor.zIndex` / `.paint.zIndex` — the `autoZForPositionedChild`
rule), (3) it is a single painted box (no children / text / runs / pseudos — deliberately narrow), (4) every LATER in-stack root is
lifted too or free of step-8+ content (non-static `Position` or a stacking-context property type, anywhere in its subtree; canvas-
hoisted roots paint in the overlay and are skipped). Walked back to front. Every unlifted root keeps the wave-51 order byte-identical;
`isComposedStaticPositionRoot` still drives the FOLD (T1 shape) unchanged. Wiring: Android `rootAboveFlow` → `if (rootAboveFlow[i])`;
iOS `let aboveFlow = …(split.flow)` threaded into `rootBody(…, aboveFlow:)` at all three walks, `.zIndex(1)` only in `if aboveFlow[idx]`.

**Census (fix-pass port in `composed-canvas.census.mjs`, column `why` on every T3 row).** T3 22 rows / 21 tests →
**lifted 2** (align-items-007 — the target; css-sizing/aspect-ratio/abspos-011 — a green box lifted over a transparent root and a
non-overlapping green bar: no pixel moves) · **declared-z 13** (11 × `-1`, all P ×2 natives; anchor-scroll-chained-003 `1` and
author-overlay-top-layer-removal `999`, unscored) · **later-step8-root 5** (the 4 above + css-scale-nested-001 root 0) ·
**content-bearing 2** (unset-val-002, css-scale-nested-001 root 1). All other census sections byte-identical to the first cut.
Every T3 carrier was already on the plan watchlist (`watchlist.txt` lines 60–70) — no new watch lines.

**Restated prediction:** T3 P → f **0** (the 18 scored T3 tests other than align-items-007 — 19 rows ×2 natives — replay as the
identity, calibrated: `png-replay.json` `T3-*` rows);
T3 f → P **+2 HIGH** (align-items-007 ios/android, unchanged).

## 1. What changed and why (final state)

| mechanism | web | Android | iOS | spec |
|---|---|---|---|---|
| **Fix A** inline-axis viewport crop | `composedIcbStyle.overflowX='clip'` | `.drawWithContent{ clipRect(band) }` after the `.background(canvasBackground)` chain; band = `composedIcbClipBandPx(size.width, CaptureCanvasFrame.toPx())` (runtime `WptCaptureMode.kt`) | `.clipShape(WPTCanvas.IcbClipBand(frame: Self.padding))` before `.background(canvasBackground)`; the positive-z overlay moved INSIDE the clipped group (runtime `WPTCaptureMode.swift`) | CSS 2.1 §9.1.1; css-overflow-3 §3.1/§3.2 |
| **M1** body margin | `resolveCanvasMargin` + `display:flow-root` wrapper `data-capture-flow` + `withCanvasOwnedBodyMargin` | `resolveComposedCanvasMargin` → Column padding (+) / offset (−), containing block minus inset, `withCanvasOwnedBodyMargin` | runtime `canvasBodyMargin` / `withCanvasOwnedBodyMargin` → VStack padding / offset, `flowWidth` | CSS 2.1 §8.3, §10.1 |
| **T1** RC1 root margin not joined | — (browser) | `composedRootStackPlan` shape 2 → (0,0), transparent, NOT stripped | `UABlockMargin.composedRootStackPlan` shape 1, same | CSS 2.1 §8.3.1, §10.6.4 |
| **T2-roots** empty root collapses through | — | `isSelfCollapsingRoot` (+pseudos, +display guards) | twin | CSS 2.1 §8.3.1 |
| **T3** RC1 root paints above the flow | — | `Box(Modifier.zIndex(1f))` around a LIFTED RC1 root (`composedRootsPaintingAboveFlow`: no declared z, single painted box, no later step-8 root — §0b) | `.zIndex(1)` on the anchored RC1 `ComponentHost` only when `aboveFlow[idx]` (runtime twin) | CSS 2.1 Appendix E steps 3/8/9 |
| **T6** hoisted UA-tag root keeps its UA margin | — (browser) | `withUaBlockMarginOnHoistedRoot` (UaBlockMargins.kt) mapped over the composed roots | runtime `UABlockMargin.withUaBlockMarginOnHoistedRoot` in `splitRoots` | CSS 2.1 §9.3.2, §10.6.4 |

**Seam deviation (deliberate):** the plan put T3's Compose half in a `ComponentRenderer.kt:911-948` seam patch. NOT delivered: the
root-stack draw order lives in the harness Column (`ScreenshotCaptureScreen.kt`, L2-owned), the same place the iOS twin already had
to put it. Every T3 census carrier is a ROOT (21 tests) and the harness wrap reorders exactly those. **Consequence for L7: there is no
L2 hunk in `:921-948`; L7's `:927-950` patch is cut against HEAD** (HEAD *is* the L2-applied tree for that file) — the C6 overlap is
gone. L2 never edits a seam file (at 18:08 `ComponentRenderer.swift` was dirty under ANOTHER lane's live verification lock,
`tools/titan/runs/wave52-lock/ComponentRenderer.swift`, 4 min old — not L2's; the other three were clean). Not covered (measured, not staffed): a NESTED no-inset absolute
child with a later in-flow sibling and no positioned ancestor — **7 tests** (census `t3Nested`: between-float-and-text,
anchor-center-scroll-001, contain-inline-size-grid-indefinite-height-min-height-flex-row, contain-inline-size-grid-stretches-auto-rows,
abspos-after-spanner, abspos-after-spanner-static-pos, flexbox_align-items-stretch-writing-modes) — that one would need the seam.

## 2. Census (`composed-canvas.census.mjs` → `composed-canvas.census.json`, all 1435 wave51-fix per-test IR docs)

Re-derived from the IR, joined to the three wave51-fix verdicts; OLD vs NEW natives fold replayed through a JS port of
`collapsedRootStackGapsPx` (box heights cancel; em at 16 px in 58 docs, flagged):
- **M1** concrete non-zero body margin AFTER the §8.3 guard: **8 tests / 24 cells** — 027 (f×3), first-letter-skip-marker (f×3),
  collapsed-border-{sideways-rl,vertical-lr,vertical}-rtl-overflow (f×9), background-attachment-margin-root-001/002 (f×6),
  s-11-1-1b-006 (web **P 0.9656**, natives f).
- **T1** RC1 roots whose ink moves: **3 tests** (clip-path-ellipse-006/007/008), +16 px each — exactly the targets. UA-only RC1 movers: **0**.
- **T2** in-flow movers: **1 test** (text-decoration-propagation-shadow: text −16, trailing `<p>` −16) — exactly the target.
- **T3** RC1 root followed by an in-flow root: **21 tests / 22 rows** (19 passing + align-items-007 + 2 unscored); nested 7 (above).
  Fix pass: the lift rule keeps **2** (align-items-007, abspos-011) and leaves 13 declared-z + 5 later-step8 + 2 content-bearing rows
  in wave-51 order (§0b).
- **T6** hoisted UA-tag roots without a block margin: **1 test** (s-11-1-1b-006 root 3).
- **Fix A tripwire** (re-read from `wave52-plan/frame-ink-census.json`, scored = no `scoreExcluded`): overrun-right **208** (web 73 ·
  iOS 63 · Android 72) + overrun-left **84** (web 29 · iOS 42 · Android 13) = **292**; same-size subset 192 + 82 = 274. Must print 0/0
  at the closing gate.

## 3. PNG replay (`png-replay.mjs` → `png-replay.json`; ssim.js `fast` on the frozen PNGs, as inject-wpt-block.mjs scores)

- **Fix A**, 274 same-size rows: as-is vs recorded **0 mismatches**; frame-replaced vs the brief's prediction **0 mismatches**; 172
  passing cells rise, 4 drop (borders-002 web 0.9646→0.9633 / android 0.9594→0.9581; text-decoration-color-recalc ios 0.9819→0.9818;
  has-style-sharing-002 ios 0.9633→0.9624); **no passing cell below 0.95**; f→P candidates exactly the plan's 8. gaps-040 web 0.9939 →
  **1.0000**, iOS/Android 0.9934 → 0.9995.
- **T1** ellipse-006/007/008 (+16): ios 0.9488 → **0.9921**, android 0.9480 → **0.9901**.
- **T2** propagation-shadow (−16): ios 0.9484 → **0.9994**, android 0.9486 → **0.9989**.
- **T3** align-items-007 (red → green): ssim 0.9974 / 0.9967 unchanged — the cell failed on the COLOUR veto (novel red 10 000 px); with
  the RC1 green on top no red remains.
- **M1** 027 web (+200, then the ICB crop): 0.9012 → **0.9723**, a LOWER BOUND ("One" sat at x −132 and is not in the frozen PNG; the
  real render paints its x 68–199). 027 natives with M1 alone: 0.9378 / 0.9379 (L10 owns the rest). 006 web (square +8): P 0.9656 →
  **0.9635** (stays P; at risk under today's ref, which zeroes the body margin).
- **T6 + M1** 006 natives (prose +16, square (+8, −8) — the fix pass adds the −8 y the skeptic found: T2 collapses through root 1):
  ios 0.9321 → **0.9636**, android 0.9332 → **0.9644** (skeptic's re-sim exactly).
- **T3 fix pass**: the 19 scored unlifted/no-op T3 rows (18 tests) ×2 natives replayed as the identity (`T3-declared-z` / `T3-later-step8-root` /
  `T3-content-bearing` / `T3-lifted` abspos-011 rows) — every replay equals its recorded ssim (calibration), i.e. no T3 mover besides
  align-items-007.

## 4. Pins and executed mutations (`mutate.py` → `mutations.log`)

**Fix pass — runner upgraded (skeptic should-fix 3).** `mutate.py` now decides RED only on ASSERTION evidence (`--evidence
junit:<dir>:<Class>` = a JUnit XML of that class written during the run with a `<failure>`; `xcode` = "Executed N tests, with M
failures" M > 0; `vitest` = "Tests N failed"); no fresh report / no Executed line ⇒ **INCONCLUSIVE**, never RED. Each log line now
carries `old=` / `new=` (the exact mutated text), `evidence=` (the failing assertion) and `cmd=` — replayable. `run-mutations-t3.sh`
replays the whole fix-pass set. `ios-source-pins.mjs` appends to the log only with `--record`. Fix-pass mutations, all on the final
shas below, all **RED with an assertion**, all `restored=ok` (sha-verified):
- `ScreenshotCaptureScreen.kt` 5dc88221 → `ComposedCanvasRc1ZOrderSourceTest`: ZO-M1 (zIndex dropped from the wrap: expected 1 but 0),
  ZO-M2 (lift rule fed `hostActive=false`), ZO-M3 (T6 map dropped), **ZO-M4** (wiring reverted to the first cut's bare RC1 flag).
- `UaBlockMargins.kt` 4279f6a3 → `UaBlockMarginsTest`: **UB-M6** rule 2 (`…ItemPlacementExtractor.zIndex(…) == null &&` → `true &&`:
  u10 "never lifted expected [F,F,F] but was [F,T,F]" on the verbatim extra-height-001 IR), **UB-M7** rule 4 (`paintsAsLayer(roots[j])`
  → `false`: u11), **UB-M8** rule 3 (children clause → `true`: u12). The first UB-M6 attempt cut a qualified name in half → compile
  error → logged INCONCLUSIVE (the new runner refusing to call it RED); re-cut on the whole expression, RED.
- `ComposedRootStack.swift` 3eb8e26c → Catalyst `ComposedRootStackTests`: **SW-M8** rule 2 (testU13), **SW-M9** rule 4 (testU14),
  **SW-M10** rule 3 (testU15). SW-M8's first run was INCONCLUSIVE (exit 65, no Executed line — a transient build failure on the shared
  tree); re-run RED with the testU13 assertion.
- `CaptureCanvas.swift` 4407cfed → `ios-source-pins.mjs --record`: 7/7 live green; IOS-M1…M7 RED (**IOS-M6** gate forced `if true`,
  **IOS-M7** gate list reverted to the bare RC1 flag → `testRc1LiftIsGatedByTheWholeListRule`); IOS-M3 re-cut to the new indentation.
NOT re-run in the fix pass (sites textually untouched by it; old exit-code runner, pre-fix shas; the skeptic corroborated SK-W1/W2/S2
with assertion evidence): FA-M1/2, MW1–3, CR-M1, MA1–4, CS-M1/2, UB-M1–M5, SW-M1–M7, SW-CB1/2.

First pass (kept for the record): `mutate.py` replaced exactly one site, ran the focused test, restored from an in-memory copy and
checked sha256 before == after; it decided RED on any non-zero exit (the skeptic's should-fix 3, fixed above). **The last line per id
in `mutations.log` is authoritative**; earlier lines are
superseded runs on older shas, plus four transient GREENs explained here: UB-M3 first run (genuinely unpinned → U8 added, then RED);
SW-M5 first run (the build broke on another lane's in-flight Swift edit; re-run RED); SW-M7 first two runs (an xcodebuild runner
crash, then a runner restart after U12's index trap hid the failure; U12 hardened with a guard, re-run RED). First-pass shas:
ScreenshotCaptureScreen.kt 73485b1d, UaBlockMargins.kt 53e75a3e, ComposedRootStack.swift a69bcca5, CaptureCanvas.swift 076f91c2.
**Final shas (fix pass):** ScreenshotCaptureScreen.kt 5dc88221, UaBlockMargins.kt 4279f6a3, ComposedCaptureGallery.tsx a86c163b,
WptCaptureMode.kt 08a13031, WPTCaptureMode.swift 2d78f62e, ComposedRootStack.swift 3eb8e26c, CaptureCanvas.swift 4407cfed.
- web `ComposedCanvasIcbClip.test.tsx` FA-M1 (key deleted), FA-M2 (`overflow:'clip'` both axes); `ComposedCanvasMargin.test.tsx` MW1
  (MarginLeft read dropped), MW2 (§8.3 guard), MW3 (wrapper renders unstripped `roots`).
- Compose runtime `ComposedIcbClipTest` CR-M1 (frame not subtracted).
- Android harness `ComposedCanvasMarginTest` MA1–MA4; `ComposedCanvasIcbClipSourceTest` CS-M1 (resolved pad fed), CS-M2 (clip node
  above the background chain); `ComposedCanvasRc1ZOrderSourceTest` ZO-M1 (zIndex dropped), ZO-M2 (`hostActive=false`), ZO-M3 (T6 map
  dropped), ZO-M4 (fix pass); `UaBlockMarginsTest` UB-M1 (RC1 join restored), UB-M2 (predicate false), UB-M3 (display guard), UB-M4
  (pseudos guard), UB-M5 (T6 identity), UB-M6–M8 (fix pass: lift rules 2/4/3 — u10/u11/u12).
- SwiftUI Catalyst `ComposedRootStackTests` SW-M1…SW-M10 (M8–M10 fix pass: testU13–U15); `ComposedIcbClipTests` SW-CB1 (frame not
  subtracted), SW-CB2 (Shape inset).
- iOS harness `ComposedCanvasIcbClipTests.swift` needs a simulator — NOT run; its SEVEN assertions (fix pass adds
  `testRc1LiftIsGatedByTheWholeListRule`) are replayed EXECUTED by `ios-source-pins.mjs` on the live CaptureCanvas.swift (7/7 green)
  and on seven in-memory mutations IOS-M1…M7 (all RED).
Payloads are VERBATIM wave51-fix per-test IR (ellipse-006, propagation-shadow, align-items-007, gradient-hue-direction `<hr>`,
has-style-sharing-002, s-11-1-1b-005/006 body-roots and 006's inset `<p>`, 027 and collapsed-border body-roots, contain-body-dir-001,
counter-name-case-sensitive root 1, any-link-attribute-removal root 0, gaps-040 container; fix pass: the FULL docs of
css-tables/height-distribution/extra-height-given-to-all-row-groups-001 (the negative-z pin the skeptic asked for), CSS2/abspos/
static-inside-inline-001, css-cascade/unset-val-002, css-masking/clip-path/clip-path-path-with-zoom, and align-items-007 as the
positive control — on both natives).

## 5. Predicted flips (wave51-fix → wave52 gate) and confidence

- **+6 HIGH** `css-masking/clip-path/clip-path-ellipse-00{6,7,8}` ios/android (replay ≥ 0.990). Falsifier: ellipse not at y 118.
- **+2 HIGH** `css-text-decor/text-decoration-propagation-shadow` ios/android (replay ≥ 0.998). Falsifier: line not at y 35.
- **+2 HIGH** `css-flexbox/align-items-007` ios/android (colour veto clears; still lifted by the fix-pass rule). Falsifier: any red
  pixel remains.
- **+1 HIGH** `css-gaps/flex/flex-gap-decorations-027` web (M1). Natives need L10's M2/M3.
- **+2 MED** `CSS2/css21-errata/s-11-1-1b-006` ios/android (T6 + M1 replay 0.9637 / 0.9645; small ink masses, thin margin).
  Falsifier: prose not at y 35, or the overlay does not render a declared margin on a hoisted box.
- **Fix A**: gaps-040 web → 1.0000 exactly (HIGH; falsifier: ink at x ≥ 374); block-ellipsis-028 web → 1.0000;
  `css-position/position-absolute-semi-replaced-stretch-input` web f → P (MED, likely honest); DEGENERATE scorer flips (label, never
  claim): `css-gaps/flex/flex-gap-decorations-034` ×3, `css-contain/contain-inline-size-bfc-floats-001` ios/android,
  `css-anchor-position/anchor-position-multicol-007` android; ring-fenced backdrop-filter-basic-blur ios 0.9469 → ~0.9526 — report the
  number, no code names it.
- **Predicted P → f: 0** — RESTATED after the fix pass. The first cut's T3 would have cost ~30 (22 the skeptic found + 8 more, §0b);
  the lift rule leaves all 18 scored T3 tests other than align-items-007 in wave-51 paint order (identity replay) except abspos-011,
  which is lifted with no pixel moving (green over a transparent root; the later green bar does not overlap). Confidence HIGH that
  no T3 cell flips P → f; the residual risk is a runtime that ALREADY ordered differently from the Column (not seen: all 19 rows'
  replays equal their recorded ssim). Fix A / M1 closest: borders-002 android 0.9594 → 0.9581; s-11-1-1b-006 web 0.9656 → 0.9635.
- If HIGH lands: web +1, iOS +5, Android +5 honest (+2 MED natives, +1 MED web); the 6 degenerate cells labelled.

## 6. Not verified (honest)

- **No device render.** Every after-number is a pixel simulation on the frozen captures (§3). Never executed: the Compose `clipRect`
  inside the composed `graphicsLayer.record`, `Modifier.zIndex` in the Column (now on 2 docs only), the SwiftUI `.clipShape` (a 2 000 000-pt band path)
  under `ImageRenderer`, the backdrop two-pass interaction with the clip, and whether each runtime's hoisted overlay renders T6's
  declared margin as an offset of the inset box (read from the code paths, not measured).
- iOS harness XCTest target not run (simulator); replayed by `ios-source-pins.mjs`. The iOS harness APP target DOES compile on the
  final sources (`xcodebuild build -target StyleConverterTest -configuration Debug -sdk iphonesimulator`, no simulator boot: BUILD
  SUCCEEDED). A Release build of the same target crashed the Swift frontend in SILGen inside the seam file
  `ComponentRenderer.swift` `styledContent(now:)` — not an L2 file, not touched by L2 (other lanes apply seam patches to it
  temporarily under the verification lock, so the crash may be theirs or pre-existing); recorded for the orchestrator.
- **iOS-harness TEST bundle still never compiled** (skeptic should-fix 5): the in-tree generated `StyleConverterTest.xcodeproj` predates
  `ComposedCanvasIcbClipTests.swift`; regenerating it in place would rewrite a project other lanes build from, so the fix pass tried an
  OUT-OF-TREE `xcodegen generate` (absolute source paths, package path pointed at a scratch mirror holding only `Package.swift` +
  `runtimes/swiftui`): it sat in "Generating project…" at ~96 % CPU for 10 min 52 s and was killed (exit by signal; tree's `git status
  apps/ios-harness` unchanged). Its seven assertions are executed only as the `ios-source-pins.mjs` regex replay. Orchestrator: run
  `xcodegen generate` in `apps/ios-harness` + `xcodebuild build-for-testing -scheme StyleConverterTestTests -destination 'generic/platform=iOS
  Simulator'` once, in the sequential sweep.
- File size: `UaBlockMargins.kt` (669 lines) and `ComposedRootStack.swift` (674) were already past the ~300 split threshold at HEAD
  (380 / 328) and grew with L2's helpers; a split is a follow-up chore, not done mid-wave on shared files.
- Chrome's `overflow-x: clip; overflow-y: visible` on the `flow-root` + `translateZ(0)` ICB div not probed under puppeteer.
- The brief's "converter-verbatim raster of 027" was not rendered (no layout engine device-free); the §3 replay is the substitute.
- M1 does not reproduce the ref's PAGE HEIGHT on body-margin docs (the ref body is `min-height:100vh` PLUS margins: collapsed-border
  refs are 390×720, captures 600) — height-mismatch class, out of L2's brief.
- Negative body `margin-right/bottom` are not emulated on natives (no corpus carrier after §8.3). T6 adds vertical UA margins only
  (blockquote/figure inline 40 px on a hoisted root: 0 carriers).
- Nested T3 (7 tests, §1) needs the renderer seam; not staffed. The fix-pass rule is deliberately narrow: content-bearing RC1 roots
  (unset-val-002, css-scale-nested-001 root 1) and RC1 roots followed by step-8 content keep the wave-51 order even where CSS would
  interleave them — interleaving needs per-descendant z in the runtime, not a Column/VStack wrap.
- Whether the natives paint unset-val-002's `display: unset` span as a 100×100 red box (it sits under the green in every wave-51
  capture, so no PNG can tell) — moot now that rule 3 leaves that root unlifted, but a latent converter gap (`display: unset` →
  `Generic`).
- Skeptic should-fix items NOT addressed in this fix pass (it was scoped to the must-fix): SK-K1/SK-S1 `blockMarginsInFold` (0 corpus
  carriers), SK-K2 Android `LocalContainingBlock.widthPx` minus the body margin (moves % geometry on the 8 M1 docs, unpinned), SK-K3
  the negative-margin offset (0 carriers after §8.3), `isSelfCollapsingRoot` without the §8.3.1 BFC clause (39 roots / 21 tests
  marked transparent, 0 move), `ComposedCanvasMarginTest.kt` 205 lines.

## 7. Verification commands (final sources, all GREEN)

- web: `cd apps/web-harness && npx vitest run tests/ui/ComposedCanvasMargin.test.tsx tests/ui/ComposedCanvasIcbClip.test.tsx tests/ui/ComposedCanvasPadding.test.tsx tests/ui/ComposedCanvasIcbFlowRoot.test.tsx tests/ui/ComposedCanvasRootClip.test.tsx tests/ui/ComposedCaptureGallery.test.tsx tests/ui/ComposedCanvasContainment.test.tsx tests/ui/ComposedCanvasDirection.test.tsx tests/ui/ComposedCanvasWritingMode.test.tsx` → 9 files / 69 tests; `npx tsc --noEmit -p .` → 0 errors.
- Compose runtime: `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*ComposedIcbClipTest*')` → 6/6.
- Android harness: `(cd apps/android-harness && ./gradlew :app:testDebugUnitTest --tests 'com.styleconverter.test.screenshot.*')` → **159/159**
  (fix pass: 15 fresh XMLs, 0 failures — +u10/u11/u12 and `rc1Decision_isTheLiftRuleNotTheBareRc1Flag`).
- SwiftUI: `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -only-testing:StyleConverterRuntimeTests/ComposedRootStackTests -only-testing:StyleConverterRuntimeTests/ComposedIcbClipTests -only-testing:StyleConverterRuntimeTests/UABlockMarginTests` → **63/63** (fix pass: +testU13–U15).
- iOS harness app compile (fix pass, no simulator boot): `xcodebuild build -project apps/ios-harness/StyleConverterTest.xcodeproj -target StyleConverterTest -configuration Debug -sdk iphonesimulator SYMROOT/OBJROOT=<scratch> CODE_SIGNING_ALLOWED=NO` → **BUILD SUCCEEDED** on CaptureCanvas.swift 4407cfed (tree's `git status apps/ios-harness` unchanged). Census / replay / iOS pins: `node composed-canvas.census.mjs`, `node png-replay.mjs`, `node ios-source-pins.mjs` (+`--record` to log), mutations: `bash run-mutations-t3.sh`.

## 8. Hand-offs

- Seam patches: **none** (T3 harness-side, §1) — so no seam lock was taken and none needed verifying. L3's `FixedHoist.swift`
  untouched (the iOS regroup stayed in `CaptureCanvas.swift`). **L7: cut the Compose `:927-950` patch against HEAD.**
- L10: 027's M1 half is in on both natives (Column inset 200); L10's M2/M3 remain for the native flips.
- L12: Fix B's re-frozen s-11-1-1b-006 ref is the geometry M1 now renders (body margin 8 honoured).
- Watch lines NOT already in the plan: `watchlist-additions.txt` (3 lines; `WATCH=… watchlist-check.mjs` → unmatched 0). Fix pass:
  no new lines — every T3 carrier (the 13 declared-z, the 5 later-step8, the 2 content-bearing, abspos-011, align-items-007) is
  already on `wave52-plan/watchlist.txt` lines 30–31 / 60–70; any T3 row in LOST at the gate falsifies §0b.
- Fix pass seams: still **none** — T3 stays harness-side (+ the runtime helper in L2-owned `ComposedRootStack.swift`); no lock taken.
- Orchestrator: closing-gate tripwire = the frame-ink census method on the new run, overrun-right + overrun-left = 0/0; the sequential
  sweep must include `:app:testDebugUnitTest` and `npm -w apps/web-harness run test` (L2 changed the harness fold and canvases).
- BACKLOG paragraph for the orchestrator: "Composed canvases (wave 52 L2): the ICB clips the inline axis on all three platforms (Fix A);
  a concrete body margin is owned by the canvas flow stack, not the body-root box (M1, §8.3 table-internal bodies excluded); the
  natives' root-stack fold no longer joins an RC1 root's margins (T1), collapses through empty text-less pseudo-less block roots (T2),
  paints a content-free, undeclared-z RC1 root above later plain in-flow roots (T3 — a declared z-index, a content-bearing root or
  later step-8 content keeps the wave-51 order: the wave-52 skeptic caught the first cut lifting `z-index: -1` boxes) and gives
  hoisted UA-tag roots their UA block margin (T6). Open: nested RC1 z-order (7 tests, renderer seam) and true step-8 interleaving
  (per-descendant z in the runtimes); ref page height on body-margin docs."

STATUS: COMPLETE
