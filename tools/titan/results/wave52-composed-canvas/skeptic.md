# wave-52 lane L2 · composed-canvas — executed-repro skeptic

Tree `campaign/wave52` @ HEAD 7d9c22a7 (shared; other lanes' uncommitted work present). Audited the lane note
(`_note.md`, ends `STATUS: COMPLETE`), PLAN.md §2 L2 / §3 / §5 / §9, every file in this directory, and the diffs of
every L2-owned file. Owned-file shas at audit time equal the note's "final shas" (ScreenshotCaptureScreen.kt 73485b1d,
UaBlockMargins.kt 53e75a3e, ComposedCaptureGallery.tsx a86c163b, WptCaptureMode.kt 08a13031, WPTCaptureMode.swift 2d78f62e,
ComposedRootStack.swift a69bcca5, CaptureCanvas.swift 076f91c2 — sha256 prefixes).

## Verdict

**NOT ready for the gate — one must-fix.** T3 (RC1 root `zIndex(1)` in the composed root stack, both natives) is applied
to EVERY RC1 root, including roots that DECLARE `z-index: -1`. 11 of the 19 passing T3 carriers are the classic WPT
"red `z-index:-1` abspos box hidden behind a green in-flow box" pattern; T3 lifts the red box above the green on iOS and
Android → predicted **22 P → f cells**, the exact mirror of the align-items-007 failure T3 was built to fix. Everything
else re-derives: pins re-run green, the corpus census reproduces to the row, the frame-ink tripwire reproduces exactly,
the PNG replay is byte-identical on re-run, and the predicted-flip pictures are plausible.

## 1. Pins re-run (all on the final sources)

| suite | command | result |
|---|---|---|
| web vitest | `cd apps/web-harness && npx vitest run tests/ui/ComposedCanvas{Margin,IcbClip,Padding,IcbFlowRoot,RootClip,Containment,Direction,WritingMode}.test.tsx tests/ui/ComposedCaptureGallery.test.tsx` | 9 files / **69 passed** |
| web tsc | `npx tsc --noEmit -p .` (apps/web-harness) | exit 0 |
| Compose runtime | `./gradlew :runtime:testDebugUnitTest --tests '*ComposedIcbClipTest*'` | **6/6** (fresh XML) |
| Android harness | `./gradlew :app:testDebugUnitTest --tests 'com.styleconverter.test.screenshot.*'` | **155/155**, 15 fresh XMLs, 0 fail |
| SwiftUI Catalyst | `xcodebuild test -scheme StyleConverterRuntime … -only-testing:…/ComposedRootStackTests …/ComposedIcbClipTests …/UABlockMarginTests` | **60 tests, 0 failures** |
| iOS source pins | `node ios-source-pins.mjs` | 6/6 live green, IOS-M1…M5 red (note: the script APPENDS to `mutations.log`; my run's 6 appended lines were truncated back to the original 10 995 bytes, last line `SW-M7 … RED` as before) |
| lane census | `node composed-canvas.census.mjs` | output sha e6e3bdaa… == committed json (unchanged) |
| PNG replay | `node png-replay.mjs` | output sha 347b67b4… == committed json (byte-identical) |
| iOS harness app compile | see §7 | |

## 2. Independent mutations (my own runner, `l2sk-skmut`: exact single-site replace, focused test, restore from memory, sha256 before == after; RED only on ASSERTION evidence — fresh JUnit XML failures>0 without compile errors / vitest "N failed" / xcodebuild "with N failures" N>0)

| id | file | mutation | evidence | verdict | restored |
|---|---|---|---|---|---|
| SK-W1 | ComposedCaptureGallery.tsx | `overflowX: 'clip'` → `'hidden'` | Tests 2 failed | RED | sha a86c163b == |
| SK-W2 | ComposedCaptureGallery.tsx | drop `'TABLE_CELL'` from the §8.3 set | Tests 1 failed | RED | sha a86c163b == |
| SK-S2 | ComposedRootStack.swift | `tableInternalDisplays.contains(…)` → `false` | 60 run, 1 failure (`testU10TableCellBodyHasNoUsedMargin`: got top −15) | RED | sha a69bcca5 == |
| SK-K1 | UaBlockMargins.kt | `val blockMarginsInFold = …` → `= true` | 155 tests, 0 failures | **GREEN — clause unpinned** | sha 53e75a3e == |
| SK-S1 | ComposedRootStack.swift | `let blockMarginsInFold = …` → `= true` | 60 run, 0 failures | **GREEN — twin unpinned** | sha a69bcca5 == |
| SK-K2 | ScreenshotCaptureScreen.kt | drop `- canvasMargin.insetHorizontal` from the LocalContainingBlock `widthPx` | 155 tests, 0 failures | **GREEN — unpinned** | sha 73485b1d == |
| SK-K3 | ScreenshotCaptureScreen.kt | drop `.offset(x = canvasMargin.offsetX, y = canvasMargin.offsetY)` | 155 tests, 0 failures | **GREEN — unpinned** | sha 73485b1d == |

`blockMarginsInFold` guards 0 corpus roots (my census: no self-collapsing root whose block margins bail out of the fold),
so SK-K1/S1 are an unproven guarantee, not a mover. SK-K2 is a render-moving line on the 8 M1 docs (percentages resolve
against it) with no pin; SK-K3 has 0 corpus carriers after §8.3. The lane's own `mutate.py` decides RED on ANY non-zero
exit (`verdict = "RED" if rc not in (0, None)`), contradicting the note's "a compile error … is NOT proof", and
`mutations.log` records neither the mutated text nor the failing assertion — the lane's 30+ RED lines are not replayable
from the record. My RED runs above (assertion-evidenced) corroborate the web/Swift-M1 pins independently.

## 3. Census re-derived with my OWN script (Python; re-implements the Kotlin OLD lambda and NEW `composedRootStackPlan` and `collapsedRootStackGapsPx` from source, over all 1435 wave51-fix per-test IRs)

| item | lane | skeptic | match |
|---|---|---|---|
| M1 concrete non-zero body margin, after §8.3 guard | 8 tests / 24 cells | 8 tests (9 before the guard; s-11-1-1b-005 is the table-cell body) | yes |
| T1 RC1 declared-margin movers | 3 (ellipse-006/7/8, +16) | 3, +16 each (slot 66 → ink 82) | yes |
| T1 UA-only RC1 movers | 0 | 0 (no RC1 root has a UA-margin tag without a declared block margin) | yes |
| T2 / downstream in-flow movers | 1 test (propagation-shadow −16) | 1 test, roots 1 & 2 −16 | yes |
| T3 RC1 root before an in-flow root | 22 rows / 21 tests | 22 rows / 21 tests | yes |
| T6 hoisted UA-tag roots, no block margin | 1 (006 root 3) | 1 | yes |
| **T3 carriers with a DECLARED z-index** | not examined | **13 tests; 11 with `z-index: -1`** | **missed** |
| T2 roots the predicate marks transparent | not listed | 39 rows / 21 tests (incl. `<br>` ×18, `<input>` ×4, aspect-ratio boxes ×6, an `overflow` BFC root) — 0 move | n/a |

Fix A tripwire, re-derived from the PNGs by my own pixel census (`l2sk-frameink.mjs`, method stated in
frame-ink-census.json: same-width pair, frame pixel differs from the ref by >8/255 on any channel; scored = numeric ssim,
no `scoreExcluded`): 4117 scored cells; overrun-right 192 same-size + 16 height-mismatch = **208** (web 73 · iOS 63 ·
Android 72); overrun-left 82 + 2 = **84** (web 29 · iOS 42 · Android 13); top 23; mixed 38; all-sides 47. **Exact match**
with the plan's census the lane re-read.

## 4. Predicted-flip pictures (wave51-fix PNG vs frozen ref, opened)

- clip-path-ellipse-006 ios/android: ellipse top ≈ y 102 vs ref ≈ 118 — +16 lands it. Plausible (007/008 identical captures).
- text-decoration-propagation-shadow ios/android: line ≈ 16 px low vs ref — −16 lands it. Plausible.
- align-items-007 ios/android: red 100×100 over the ref's green — T3 clears it. Plausible.
- flex-gap-decorations-027 web: whole page 200 px left of the ref (border 16 vs 216) — M1 +200 + ICB crop. Plausible (replay 0.9723 is a lower bound).
- flex-gap-decorations-040 web: row runs to x 389 vs the ref's 374 crop — Fix A → identical. Plausible.
- position-absolute-semi-replaced-stretch-input web: right boxes reach x ≈ 377 vs 374; a ~5 px x offset on the second column remains (192 vs 187) — 0.9596 is honest-ish, MED as labelled.
- block-ellipsis-028 web: text runs into the frame — P → 1.0000, picture-correctness only.
- s-11-1-1b-006 ios/android: prose 16 px high (y 19–35 vs ref 35–50), square (16,59) vs ref (16,56). The lane's replay moved
  the square +8 in x only; the fold ALSO moves it −8 in y on natives (body bottom margin 8 no longer separates root 1, which
  T2 now collapses through: square 59 → 51). Re-simulated with (+8, −8): ios 0.9636, android 0.9644 (lane 0.9637/0.9645) — the
  MED flip survives the omission.

## 5. Defects

### MUST-FIX — T3 paints `z-index: -1` RC1 roots above in-flow content (22 predicted P → f)
`ScreenshotCaptureScreen.kt` `if (rootStaticPos[i]) { Box(modifier = Modifier.zIndex(1f)) { inFlow() } }` and
`CaptureCanvas.swift` `.modifier(StaticPositionAnchor()).zIndex(1)` fire for every RC1 root (`isComposedStaticPositionRoot` /
`FixedHoist.rendersInFlowAsStaticPosition` — neither reads `ZIndex`). CSS 2.1 Appendix E puts a negative-z positioned box
in step 3 (BELOW in-flow blocks), and the runtime's own analogue refuses to wrap a declared z
(`ComponentRenderer.autoZForPositionedChild`: "A DECLARED `z-index` → null … an outer wrapper z would shadow it").
Carriers (all T3 rows, all P on both natives at wave51-fix): CSS2/abspos/table-caption-is-containing-block-001,
table-caption-passes-abspos-up-001; css-position/position-absolute-in-inline-margin-top; css-tables/height-distribution/
extra-height-given-to-all-row-groups-001/-002/-005, percentage-sizing-of-table-cell-children-003/-004/-005/-006;
css-ui/box-sizing-026. Each IR: root 1 = `Position ABSOLUTE, ZIndex -1, BackgroundColor red, 100×100`, a later in-flow
green box. Replay (`l2sk-t3neg.mjs`): recolouring the green square red in the wave51-fix native capture yields a PNG
that differs from the wave51-fix **align-items-007** capture (recorded f: colorFailed, novelInkFailed, degenerateFailed,
novel red 10 000 px) in **0 pixels** on 4/5 iOS and 3/5 Android samples (≤ 398 px on the rest). Fix: exclude roots with
a declared z-index from the wrap on both natives (z < 0 must paint before later in-flow roots; a declared z ≥ 0 already
rides PositionApplier), add the negative-z pin on the verbatim extra-height-001 IR, and re-state "Predicted P → f: 0".

### should-fix
1. Unpinned render guarantees (executed GREEN): `blockMarginsInFold` on both natives (SK-K1/SK-S1), Android
   `LocalContainingBlock.widthPx` minus the body margin (SK-K2, moves % geometry on the 8 M1 docs), Android negative-margin
   offset (SK-K3; the plan asked for a "`MarginTop −15` on the wrapper" pin).
2. `isSelfCollapsingRoot` (both natives) omits §8.3.1's "does not establish a new BFC" clause (overflow ≠ visible, float,
   contain, multicol) and treats `AspectRatio` boxes, `<input>` widgets and `<br>`/line-break roots as empty: 39 roots / 21
   tests marked transparent; 0 move in the corpus today (their neighbours carry no block margins), so correctness-only.
3. `mutate.py` RED = any non-zero exit; `mutations.log` lacks the mutation text and the failing assertion (not replayable);
   `ios-source-pins.mjs` appends to `mutations.log` on every run (re-running it dirties the evidence).
4. `png-replay.mjs` 006 natives omit the −8 y square shift (§4) — numbers move by 0.0001, the flip holds.
5. The new iOS-harness XCTest `ComposedCanvasIcbClipTests.swift` has never been compiled (§7).

### nit
- `apps/android-harness/app/src/test/…/screenshot/ComposedCanvasMarginTest.kt` is 205 lines (> 200 new-file target).
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/ComposedIcbClipTests.swift` is not named in PLAN §2 L2 "own:" (new
  file, no other owner — not a foreign edit).

## 6. Ownership, seams, ring-fence, carve-outs, device A/B

- Every L2-tagged change is in an L2-owned file or a new L2 test file beside one; no L2 hunk in any non-owned modified
  file (grep of every non-owned diff for L2 tags: none); `FixedHoist.swift`'s diff is L3's (F3 tags). Seam files
  (`ComponentRenderer.{kt,swift,tsx}`, `extract-fixture.mjs`) carry no L2 change (a transient `ComponentRenderer.swift`
  diff seen mid-audit was another lane's lock-window verification and was gone a minute later).
- Seam patches: none delivered (documented deviation: T3 moved harness-side). `git apply --check` therefore N/A; the L7
  hand-off ("cut `:927-950` against HEAD") follows from it.
- Ring-fence: no code line names `backdrop-filter-basic-blur`; the incidental move is reported (0.9469 → 0.9526).
- Test-name carve-outs: none in added code (comments only).
- Device A/B: none staged by L2 → hash sentence N/A.

## 7. iOS harness compile

- App target: `xcodebuild build -project apps/ios-harness/StyleConverterTest.xcodeproj -target StyleConverterTest
  -configuration Debug -sdk iphonesimulator SYMROOT/OBJROOT=<scratch> CODE_SIGNING_ALLOWED=NO` → **BUILD SUCCEEDED** on
  CaptureCanvas.swift sha256 076f91c2 (no simulator boot; tree's `git status apps/ios-harness` unchanged before/after).
- Test bundle: the generated (gitignored) `StyleConverterTest.xcodeproj` is from 2025-09-25 and does NOT contain
  `StyleConverterTestTests/ComposedCanvasIcbClipTests.swift`; an out-of-tree `xcodegen generate --project <scratch>` copy
  could not resolve the local SwiftPM package (exit 74). So the new iOS-harness XCTest file has **never been compiled**
  by the lane or by me — its six assertions exist only as `ios-source-pins.mjs` regex replays. Should-fix: regenerate and
  `build-for-testing` it before the sweep.

## Appendix A — the skeptic census (verbatim core; the scratchpad copy was overwritten by another session, so the
## durable record is here). Python 3, read-only over `tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json`.

```python
UA = {'p':16,'h1':21,'h2':19,'h3':16,'h4':21,'h5':27,'h6':37,'ul':16,'ol':16,'blockquote':16,'pre':16,'figure':16}  # UaBlockChildMargins.kt
INSETS = ['Top','Right','Bottom','Left','InsetBlockStart','InsetBlockEnd','InsetInlineStart','InsetInlineEnd']
TOPS, BOTS = ('MarginTop','MarginBlockStart'), ('MarginBottom','MarginBlockEnd')
def pos(c): v = get(c,'Position'); return v.upper() if isinstance(v,str) else ''
def has_inset(c): return any((d := get(c,t)) is not None and not (isinstance(d,str) and d.lower()=='auto') for t in INSETS)
is_rc1     = lambda c: pos(c)=='ABSOLUTE' and not has_inset(c)                       # rendersInFlowAsStaticPosition (root)
is_hoisted = lambda c: pos(c)=='FIXED' or (pos(c)=='ABSOLUTE' and has_inset(c))      # shouldHoistToCanvasRoot (root)
oof        = lambda c: pos(c) in ('ABSOLUTE','FIXED')                               # isOutOfFlowRoot
def edge(d, base):        # StaticEmMargin.edgePx: absent→0; auto/negative/%/calc→BAIL; em at own font (16 default)
    ...
def static_edges(c): t, b = edge(first(c,TOPS), font_px(c)), edge(first(c,BOTS), font_px(c)); return None if 'BAIL' in (t,b) else (t,b)
def plan_old(c):          # wave-51 rootPlans lambda
    if is_hoisted(c): return dict(top=0,bot=0,t=True)
    sp = host and is_rc1(c); se = None if (oof(c) and not sp) else static_edges(c)
    p = rootStackMargin(tag, declTop, declBot, se); p['t'] = sp; return p
def plan_new(c, cc):      # wave-52 composedRootStackPlan; cc = body-root with canvas-owned px sides stripped (M1)
    if is_hoisted(c) or (host and is_rc1(c)): return dict(top=0,bot=0,t=True,strip=False)
    b = rootStackMargin(..., None if oof(cc) else static_edges(cc))
    b['t'] = self_collapsing(cc, has_children) and (b['strip'] or (not b['dt'] and not b['db'])); return b
def fold(plans):          # collapsedRootStackGapsPx, verbatim port
    run = em = 0; gaps = []
    for p in plans:
        run = max(run, max(0,p['top'])); g = run - em; gaps.append(g)
        if p['t']: em += g; run = max(run, max(0,p['bot']))
        else: run = max(0,p['bot']); em = 0
    return gaps + [run - em]
# ink(root) = cumulative gaps (+ M1 Column top inset in NEW); an RC1 root adds its own declared top margin in NEW
# (rendered, not stripped) and in OLD only when the static classifier bailed. host = any abs/fixed box in the doc.
# T3 row = host and RC1 root followed by any later non-OOF root; ZIndex read from the root's own 'ZIndex' leaf.
```

Outputs: `M1 8 rows/8 tests (M1raw 9)`, `moves_RC1 3 (+16)`, `RC1_UA_tag_no_decl 0`, `moves_inflow 2 rows/1 test (−16)`,
`moves_M1doc 6 rows/4 tests` (vertical, inside M1 docs: collapsed-border ×3 old 120 → new 60 = ref 76 − 16 frame;
006 roots −8), `T3 22 rows/21 tests`, `T3_rc1_with_zindex 13 tests (11 × z −1, 1 × z 1 unscored, 1 × z 999 unscored)`,
`T3_later_positioned 2`, `T6 1`, `T2_transparent_roots 39 rows/21 tests`.

## Re-verify (fix pass) — 2026-10-05, executed

Scope: the one must-fix above (T3 lifted `z-index: -1` RC1 roots). Owned-file shas at re-verify = the note's fix-pass
"final shas" (ScreenshotCaptureScreen.kt 5dc88221, UaBlockMargins.kt 4279f6a3, ComposedRootStack.swift 3eb8e26c,
CaptureCanvas.swift 4407cfed; ComposedCaptureGallery.tsx a86c163b, WptCaptureMode.kt 08a13031, WPTCaptureMode.swift
2d78f62e unchanged since the first audit). Note ends `STATUS: COMPLETE`.

**Verdict: must-fix CONFIRMED FIXED. No regression found. Ready for the gate** (one should-fix and two nits below, none of
which changes a render).

### R1. The fix, read
Both natives now gate the wrap on a whole-list rule (`composedRootsPaintingAboveFlow`, Kotlin harness `UaBlockMargins.kt`
+ Swift runtime `ComposedRootStack.swift`): RC1 slot ∧ NO declared z-index (the runtime readers
`ItemPlacementExtractor.zIndex` / `.paint.zIndex`, which decode the `{value:-1,…}` wire) ∧ a single painted box ∧ no
later in-Column root with step-8+ content unless it is lifted too. Android `if (rootAboveFlow[i]) Box(Modifier.zIndex(1f))
{ inFlow() } else inFlow()`; iOS `if aboveFlow[idx] { … .zIndex(1) } else { …StaticPositionAnchor() }`, list threaded to
all three `rootBody` call sites. Both else-branches are byte-for-byte the HEAD node (Android's `inFlow` lambda is the HEAD
`if (m.left>0||m.right>0) Box(padding){hosted()} else hosted()`). Harness roots are `SlotComposer.compose`d (Android
:316) / `IRDocument`-decoded (iOS), the same path the pins' `rootsOf` use, so rule 3/4 see real children.

### R2. Pins re-run (fresh evidence)
| suite | result |
|---|---|
| Android harness `:app:testDebugUnitTest --tests 'com.styleconverter.test.screenshot.*'` | 1st attempt: compile error in `runtimes/compose/…/columns/GapDecorationHook.kt:121` ("No value passed for parameter 'inheritedRtl'") — NOT an L2 file (another lane's in-flight edit); 2nd attempt minutes later: **159/159, 15/15 XMLs fresh, 0 fail** (u10/u11/u12 + `rc1Decision_isTheLiftRuleNotTheBareRc1Flag` present) |
| SwiftUI Catalyst `ComposedRootStackTests`+`ComposedIcbClipTests`+`UABlockMarginTests` (private derivedData) | **63 tests, 0 failures** (testU13/U14/U15 ran and passed) |
| `node ios-source-pins.mjs` (no `--record`) | 7/7 live green; IOS-M1…M7 RED; `mutations.log` sha unchanged (3926487b) — the append-on-run should-fix is fixed |
| iOS harness APP compile, `xcodebuild build … -target StyleConverterTest -configuration Debug -sdk iphonesimulator`, scratch SYMROOT/OBJROOT | **BUILD SUCCEEDED** on CaptureCanvas.swift 4407cfed; `git status apps/ios-harness` identical before/after |
| pin payloads | Kotlin `extraHeight001` and Swift `extraHeight001` JSON == the wave51-fix per-test IR `components` array (all 7, `==` in Python) — the negative-z pin is on the verbatim IR as asked |

### R3. Independent mutations (my runner `rv/rvmut.py`: one exact site, per-file lock dir taken/released, focused test, restore from memory, RED only on assertion evidence)
| id | file | mutation | evidence | verdict | restored |
|---|---|---|---|---|---|
| RV-A1 | UaBlockMargins.kt | rule 2 `…ItemPlacementExtractor.zIndex(root.properties) == null &&` → `true &&` | u10 "never lifted expected [F,F,F] but was [F,T,F]" | **RED** | 4279f6a3 == |
| RV-A2 | ScreenshotCaptureScreen.kt | `if (rootAboveFlow[i]) {` → `if (isComposedStaticPositionRoot(roots[i], hostActive)) {` (the first cut's bare flag) | `rc1Root_isWrappedInAZIndexAboveTheFlow`: "RC1 branch missing" | **RED** | 5dc88221 == |
| RV-S1 | ComposedRootStack.swift | rule 2 `&& …paint.zIndex == nil` → `&& true` | Executed 44, 1 failure: testU13 `[false, true, false]` ≠ `[false, false, false]` | **RED** | 3eb8e26c == |
| RV-A3 | UaBlockMargins.kt | rule 2 narrowed to `(zIndex ?: 0) >= 0` (lift positive declared z) | 159 pass | GREEN — see nit N1 | 4279f6a3 == |

### R4. Census re-derived with my OWN script (`rv/t3_lift_census.py`, Python, over all 1435 wave51-fix per-test IR, verdicts from the section manifests)
- T3 rows **22 / 21 tests**: lifted **2** (align-items-007 f/f; abspos-011 P/P) · declared-z **13** (11 × `-1`, all P on both
  natives; anchor-scroll-chained-003 z 1 and author-overlay-top-layer-removal z 999, both unscored) · later-step8 **5**
  (static-inside-inline-001/002/003, clip-path-path-with-zoom, css-scale-nested-001 root 0) · content-bearing **2**
  (unset-val-002, css-scale-nested-001 root 1). **Identical** to the lane's `t3` rows (`why` counter 13/5/2/2).
- All **11 negative-z carriers NOT lifted** (the 22 P cells the first cut endangered are back on the wave-51 path).
- Android-vs-iOS twin: re-ran the rule with the iOS inputs (`split.flow` = hoisted roots removed, fixed descendants
  stripped) — **0 docs** where the two lift sets differ. Swift rule 3 omits `meta.runs` and tests `pseudos == nil` (Kotlin
  `isNullOrEmpty`): **0** RC1 candidates are blocked by `runs` alone, so corpus-equivalent (nit N2).
- Lane scripts re-run: `composed-canvas.census.mjs` / `png-replay.mjs` reproduce (sha 728ec25b / 2f03b798 stable across two
  runs; sizes 11840 / 11794 = the pre-run files). NB they REWRITE their JSON in place (`fs.writeFileSync`), not stdout
  as the usage line `[> json]` suggests — my re-run regenerated both files deterministically (nit N3).

### R5. Pictures (wave51-fix ios/android PNG vs frozen ref, opened)
- align-items-007: ref green 100×100 at (16,88); both natives paint the RED `<img>` there. IR: root 1 = abspos green
  100×100 (no z, no content), root 2 = flex column 100×100 holding the img at the same origin → the lift puts the green
  exactly over the red. Flip +2 plausible.
- abspos-011 (lifted, predicted no pixel move): root 1 green 100×80 (80 content + 20 padding, aspect 1/1 on content box),
  root 2 transparent h 80, root 3 green bar from y+80 — no overlap, so the reorder is invisible. Agrees.
- extra-height-given-to-all-row-groups-001 (negative-z carrier): ref and both natives green, red hidden — and the root is
  now NOT lifted, so it stays green. The 22-cell regression is gone.

### R6. Regression hunt — what the fix pass did NOT say
**Should-fix S1 — the wrap fires on 35 roots / 24 docs, not "2 docs only" (note §6).** The lift rule runs on every doc,
not just T3 rows. Lifted outside the T3 rows: anchor-scroll-chained-001/002/004/-fallback (unscored), background-clip-
content-box-001 (P), gradient-single-stop-001…008 (P ×8, roots 2 and 3), clip-path-ellipse-006/007/008 (the T1 targets),
clip-path-filter-order (f), clip-path-polygon-003 (P), aspect-ratio/abspos-001/002/010 (P), backdrop-filter-edge-pixels-2
(P) — **14 passing tests / 28 P native cells**, none on `wave52-plan/watchlist.txt` or `watchlist-additions.txt` (only
the ellipse targets are). Measured: in every one the lifted root is the LAST in-Column painter or followed only by other
lifted roots (equal z keeps tree order), and no earlier root carries a declared z — so draw order is the identity; the
Android `Box` wrapper is layout-neutral (Column min constraints are already 0, the anchor reports 0×0, Box does not clip;
`hosted` is a CompositionLocalProvider, so no ColumnScope parent data is hidden). Predicted effect 0, which is why this is
not must-fix — but the note mis-states where the code fires, and a gate move on any of these 14 tests would not be
caught. Fix: correct §6 and add the 14 to the watch set (one bare-substring line per family:
`css-images/gradient/gradient-single-stop-00`, `css-sizing/aspect-ratio/abspos-0`, `css-backgrounds/background-clip-
content-box-001`, `css-masking/clip-path/clip-path-polygon-003`, `filter-effects/backdrop-filter-edge-pixels-2`).

Nits:
- **N1** (RV-A3 GREEN): rule 2's exclusion is pinned only for NEGATIVE z; a mutation that lifts positive declared z
  survives. Carriers: 2, both unscored, and CSS paints positive z above the flow anyway, so no wrong render.
- **N2**: the Swift rule 3 has no `runs` clause and uses `pseudos == nil` vs Kotlin `isNullOrEmpty()` — a twin
  asymmetry with 0 corpus carriers.
- **N3**: `composed-canvas.census.mjs` / `png-replay.mjs` overwrite their committed JSON on every run; the usage comment
  says stdout.

Unchanged from the first audit (not in this re-verify's scope): should-fix 1/2/5 (SK-K1/S1/K2/K3 unpinned, §8.3.1 BFC
clause, iOS-harness test bundle never compiled), `ComposedCanvasMarginTest.kt` 205 lines; the lane lists them as not
addressed (§6).

### R7. Ownership / seams / ring-fence / A-B
- Every changed file carrying an L2 tag is in PLAN §2 L2 `own:` or a test beside one; the two foreign hits
  (`wave52-flex-nowrap-gaps/mutate.py`, `wave52-static-position/seam-1.patch`) are other lanes MENTIONING L2. No L2 hunk
  in `CanvasRootHoist.kt`, `FixedHoist.swift`, `extract-fixture.test.mjs` (the dirty seam-adjacent files).
- Seam patches: none (T3 stays harness-side) → `git apply --check` N/A. Lock dir empty at the end; my locks released.
- Ring-fence: no code line names backdrop-filter-basic-blur. No test-name carve-outs in added non-comment code.
- Device A/B: none staged → hash sentence N/A.
- All owned files restored to the note's shas after my mutations (re-checked at the end).

### Appendix R — the re-verify census core (durable copy; scratchpad copies are purged after ~3 days)
```python
# roots = components with no slot.parent; kids by slot.parent; host = any ABSOLUTE/FIXED in the doc
is_rc1     = lambda c: pos(c)=='ABSOLUTE' and not any(prop(c,t) is not None for t in INSETS)
is_hoisted = lambda c: pos(c)=='FIXED' or (pos(c)=='ABSOLUTE' and has_inset(c))
LAYER = {ZIndex, Opacity, Transform, Translate, Rotate, Scale, Perspective, TransformStyle, Filter, BackdropFilter,
         ClipPath, MaskImage, MixBlendMode, Isolation, WillChange, Contain, ContainerType, ViewTransitionName}
def layer(c): return pos(c) not in ('', 'STATIC') or any(p.type in LAYER for p in c.props) or any(layer(k) for k in kids[c])
for i in reversed(range(n)):                        # back to front, rule 4 reads later verdicts
    r = roots[i]
    if not (host and is_rc1(r)): continue
    if zindex(r) is not None: why='declared-z'; continue          # {value:-1,...} -> -1
    if kids[r] or r.text or r.meta.runs or r.pseudos: why='content-bearing'; continue
    if any(not lifted[j] and not is_hoisted(roots[j]) and layer(roots[j]) for j in range(i+1,n)): why='later-step8'; continue
    lifted[i] = True
# T3 row = host and RC1 root i followed by any later root whose position is not ABSOLUTE/FIXED
```
Outputs: `T3 22 rows / 21 tests {declared-z 13, later-step8 5, content-bearing 2, lifted 2}`, `negative-z T3 tests 11,
lifted 0`, `lifted roots anywhere 35 (24 docs)`, `android-vs-ios lift-set differences 0`, `runs-only blockers 0`.
