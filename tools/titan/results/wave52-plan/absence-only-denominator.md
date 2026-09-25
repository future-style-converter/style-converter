# absence-only-denominator — wave 52 plan brief (INSTRUMENT lane: a decision, not a render fix)

Evidence base: `tools/titan/runs/wave51-fix/sections/<section>/manifest.json` + the three `*-screenshots/` dirs
(every one of the 4305 capture PNGs and all 1435 frozen refs decoded, read-only, on 2026-09-25) and the frozen refs under
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`. Per-cell census:
`tools/titan/results/wave52-plan/absence-only-denominator.census.json` (beside this file). Cell citations are
`wave51-fix <section>/<test> <platform> <P|f> <ssim>`; "ink" is the manifest's own `semanticPresence` coverage
(deviation from the white canvas > 8/channel — `tools/visual/compare-screenshots-metrics.mjs:415`).

## 1. Queue items covered

- Ranked queue **0(n) "The absence-only class — a DENOMINATOR problem, ~67 cells, needing a decision rather than a fix"** —
  "whether these become `scoreExcluded` — which shrinks the denominator … — or stay counted with the caveat documented".
- **"Instrument decisions pending"** → "The blank-capture guard — DECIDE IT, and key it on UNIFORM COLOUR, not on alpha" —
  "Ship it as a CAPTURE FAILURE in the exit-7 family, not as a score, and mutation-prove it against those 42 rows."
- Both re-derived on wave51-fix from the PNGs, as the task asks; the two wave50-S6 populations were NOT taken on trust.

## 2. Target cells (cheapest first) — what the PNGs show

Every capture and ref named below was opened with the Read tool. "uniform" = one pixel value over the whole bitmap.

**T1 — the absence-only class: 19 scored PASS cells / 7 tests, ref uniform white, capture uniform white, ssim 1.0000.**
A capture with NO ink at all receives exactly this pass (the null-renderer control, §3). Web captures are 390×600 RGB
(255,255,255); iOS/Android are RGBA (255,255,255,255); the ref is 390×600 RGBA white. Nothing is visible on either side.

| cell (wave51-fix, all `P 1.0000`) | platforms | ref source | what the test asserts |
|---|---|---|---|
| css-backgrounds/animations/background-color-transparent-animation-in-body | web, ios, android | `*-ref.html` (blank body) | body bg animates between two alpha-0 colours → paints nothing |
| css-backgrounds/animations/invalidation/background-color-animation-with-zero-alpha | web, ios, android | `*-ref.html` (blank) | 100×100 div animated between alpha-0 colours → paints nothing |
| css-cascade/scope-implicit-crash-print | web, ios, android | `/css/reference/blank.html` | "Shouldn't crash, should just be a blank page" |
| css-flexbox/abspos/flexbox_inline-abspos | web, ios, android | `/css/reference/blank.html` | abspos inline-flex `FAIL` box with `margin-top:-20em` is off-canvas |
| css-images/gradient-refcrash | web, ios, android | `../reference/blank.html` | "Does this gradient crash the browser?" (empty div) |
| css-transforms/backface-visibility-hidden-006 | web, ios, android | `*-ref.html` (empty) | text on a hidden backface "should be invisible" |
| css-view-transitions/animation-name-ua-prefix | **web only** | `/css/reference/blank.html` | red 100×100 div faded out by a UA animation |

Not in T1 although uniform-on-uniform at 1.0000: `wave51-fix css-backgrounds/animations/background-color-animation-in-body
web|ios|android P 1.0000` — ref AND all three captures are uniform olive (100,100,0) (PNGs opened: solid olive edge to edge).
That is a positive assertion (the runtimes computed the paused animation midpoint of rgb(0,200,0)→rgb(200,0,0)); a null
renderer paints white and FAILS it (`bCoveragePct` 100 → presence veto). S6's "21 blank-vs-blank" included these 3; they stay
counted. Same for the 3 already-excluded green `overlay-transition-backdrop-entry` cells (uniform (0,128,0) both sides).

**T2 — blank capture vs inked ref: 36 cells / 15 tests (33 scored, 3 excluded), ALL 33 scored cells already FAIL.**
S6's 42 (wave49-final) → 36 on wave51-fix. The 6 departures are FIXES that registered as gains, not guard fires:
`s-11-1-1b-005` ×3 (black canvas → inked; web now P 0.9947), `counter-cjk-decimal android f 0.9407 → P 0.9777` (BACKLOG 2(a),
the Compose blank-paint bug), `attr-notype-fallback android P 0.9983`, `attr-style-sharing-4 android P 0.9998`.
Representative PNGs opened: `access-from-shadow-dom` ref shows "A. B. C." list markers top-left, web capture solid white
(`wave51-fix css-counter-styles/counter-style-at-rule/access-from-shadow-dom web f 0.9925`, presenceFailed); `composited-under-
rotateY-180deg-preserve-3d` ref shows a 100×100 green square at (16,16), iOS capture solid white (`ios f 0.9565`);
`empty-render-target-capture` ref shows the same green square, Android capture solid white (`android f 0.9565`);
`clip-path-inset-round-rendering` ref shows a large green rounded shape, iOS capture solid white (`ios f 0.7483`).
Split by shape: 27 cells / 9 tests blank on ALL THREE platforms (shadow-DOM ×5, scope-visited, color-layers-no-blend-mode,
gradient-eval-predefined-color-spaces, direction-propagation-body-contain-root — pipeline delivery gaps, every one tagged
`requires-script-mutation` and/or `requires-shadow-dom`); 6 cells / 5 tests blank on ONE or TWO platforms while a sibling is
inked (clip-path-inset-round-rendering iOS, broken-column-rule-1 iOS, composited-under-rotateY iOS+Android,
empty-render-target-capture Android, text-decoration-inset-025 Android = the only fully TRANSPARENT capture, (0,0,0,0) 390×9470).

**T3 — 9 uniform-vs-uniform FAILS on an ERASED ref (side finding, calibration lane, not this lane's target).**
`wave51-fix css-cascade/initial-background-color web|ios|android f 0.5414` (captures solid green (0,128,0) — PNG opened) and
`css-values/calc-in-media-queries-001|-002 web|ios|android f 0.5535` (captures solid red (255,0,0)). Their refs are
`reference/all-green.html` = `<html style="background: green"></html>`, yet the frozen PNGs are uniform WHITE. Mechanism:
`tools/titan/capture-browser-ref.mjs:490` `:where(html, body) { …; background: ${CANVAS_BG}; }` — the author's inline style
wins the `html` half, but the `body` half paints an opaque white `min-height: 100vh` box (line 491) over the root canvas. This is
lane B10's erased-reference family by a sub-mechanism its negative-z-index census does not count (`wave50-B10/erased-refs.json`
has no all-green row). B10's committed patch (`wave50-B10/capture-browser-ref-body-background.patch` lines 52–54, moves
`background` to `:where(html)`) fixes it. After the re-freeze `initial-background-color` ×3 become PASSES (green on green);
the calc-in-media-queries cells stay honest fails (red = the test's own fallback: calc() in a media query unsupported).

**T4 — inked capture vs blank ref: 2 cells**, `wave51-fix css-view-transitions/animation-name-ua-prefix ios|android f 0.9570`
(PNG: a 100×100 red square at (16,16) on white; the natives do not run the UA fade-out). Honest fails; no change.

## 3. Mechanism (one scorer, three platforms — identical code path for web-ref / ios-ref / android-ref)

- `tools/titan/inject-wpt-block.mjs:743` `WPT_PRESENCE_REF_MIN_PCT = 0.02`, whose banner (lines 719–742) says in so many words
  "A blank ref therefore never arms the gate: blank-vs-blank stays a pass." `computePresenceFailed` (:905) returns false when
  `ref < 0.02`; `computeCoverageRatioFailed` (:889) returns false when `max(cap, ref) < 0.02`; `computeWptPass` (:627) then passes
  on `ssim ≥ 0.95`, and SSIM of two constant white images is 1. So the pass on every T1 cell is decided by the ref's blankness
  alone — the capture's content never enters. That was a deliberate wave-13 choice against FALSE FAILS; it was never a
  decision to COUNT them.
- Conversely every T2 cell is vetoed by the same predicate: `aCoveragePct 0 / bCoveragePct ≥ 0.102 → ratio 0 < 0.05` →
  `presenceFailed` (32 of 33; the transparent Android capture reads aCov 100 because alpha is ignored —
  `compare-screenshots-metrics.mjs` `foregroundCoverage` — and fails on `coverageRatioFailed` + ssim 0). **The BACKLOG's "There
  is no blank-capture guard anywhere in tools/ today" is wrong in substance**: the scoring guard exists at :905 and fires 33/33.
  What does not exist is a NAMED stamp and a loud listing.
- The scorer idiom (`tools/titan/score-gate.mjs:50` `isScored`, README "The scorer idiom") counts a cell iff numeric `ssim` and
  `!scoreExcluded`; a string stamp is truthy, so `'absence-only'` drops the cell from numerator and denominator in every
  aggregator byte-for-byte, exactly as `NATIVE_FONT_PARITY_STAMP` (:1276) does today.
- The 057-class B12 named ("ref paints only the prose line") is NOT reachable by a pixel rule and is not literally un-failable
  (a null renderer presence-fails it; a renderer that paints the mark ssim-fails it). 216 wave51-fix refs carry < 1 % non-modal
  pixels — that superset is mostly legitimate small-ink tests. The literal class is the 19 cells above (0.56 % of the 3384
  passing cells), not ~67: B12's 2/100 extrapolation counted 057, which carries prose-line information.

## 4. The decision and the fix

**DECISION A (0(n)) — EXCLUDE, per cell, with a named reason.** Stamp `wptPass = null`, `scoreExcluded = 'absence-only'` on
every browser-ref diff for which `wptPass === true && semanticPresence.bCoveragePct < WPT_PRESENCE_REF_MIN_PCT` — read: "the
pass that a capture with no ink at all would also have received". Mechanism-free (no hue, no test name, no new constant: it
reuses the instrument's own blank-ref floor). Why exclusion and not caveat: a pass in the numerator is a claim that the
runtime rendered the test's assertion; on these 19 the runtime may have rendered nothing (the SAME corpus has 33 cells where
it did exactly that), and the scorer cannot tell. A fail on a blank ref IS information (T3/T4 stay scored), so the cut is
per cell, not per test, mirroring `applyNativeFontParityGate` (:1513, per-platform string stamp, raw metrics kept).
Cost, named: the ratchet — a runtime that repairs a blank-ref FAIL (T4 natives) moves to `absence-only`, never to P; and a
regression on a stamped cell surfaces as NEWLY MEASURED f, not LOST. §4 item 3 closes the second half.
**DECISION B (blank-capture guard) — a TRIAGE STAMP plus a loud listing; scoring UNCHANGED; the exit-7 escalation DECLINED.**
Stamp `captureUniform: [r,g,b,a] | null` on every diff (computed on the raw decoded capture before `padToCanvas`, :425), and
`blankCaptureVsInkedRef = captureUniform !== null && bCoveragePct ≥ 0.02` (36 fires on wave51-fix, 0 verdict changes).
Why not a capture FAILURE: the population is measurements — `counter-cjk-decimal android` was in S6's 42 and was a Compose
paint bug fixed in wave 50 and counted as a GAIN; an exit-7 guard would have hidden it from the fail column and reported the
fix as "newly measured". A gate that exits non-zero on 15 known, partly undiagnosed defects (BACKLOG 1(c): the 9470 px
blow-up "could not be diagnosed") is permanently red — the pre-excused shape in reverse. The one true capture failure
(`text-decoration-inset-025 android`, alpha 0 everywhere) stays f 0.0000 (its layout IS wrong) and gets `captureUniform:
[0,0,0,0]` so the listing names it.
Spec ground: WPT's `css/reference/blank.html` is "Intentionally blank" — a match against it asserts only that nothing paints,
which is exactly the assertion a runtime that renders nothing also satisfies; the CSS the tests exercise (css-transforms-2
`backface-visibility`, css-flexbox-1 §4.1 abspos items, css-backgrounds-3 `background-color` animation) is untouched — no
runtime file changes in this lane. T3 rests on css-backgrounds-3 §2.11.2 (root background propagates to the canvas; the body
pin must not cover it) and is handed to the B10 calibration.

Implementation, in order:
1. `inject-wpt-block.mjs`: export `ABSENCE_ONLY_STAMP = 'absence-only'` beside :1276; add pure `isAbsenceOnly(diff)` and
   `applyAbsenceOnlyGate(diffsByPlatform)` (same shape/contract as :1513: skips null/error diffs, NEVER overwrites an existing
   `scoreExcluded` — a wall-excluded blank test must not be downgraded to "absence-only"); call it at :2032 after the font-parity
   gate with `isNa ? [] : …`, record `absenceOnlyExcluded: [...]` beside :2111; count it in the :2358 summary line
   (`absence-only=<n> blank-captures=<n>`). `scoreEligible` (:2039) stays true — the test IS scored where it can fail.
2. `inject-wpt-block.mjs` `diffWebVsRef` (:420): compute `captureUniform` from `a` before :425; stamp
   `blankCaptureVsInkedRef` next to `presenceFailed` at :1749 and :1808 (both paths — the two measured wave-12 vacuous
   passes came through the composed path).
3. `score-gate.mjs`: `loadRun` (:60–86) records `stamp: x.scoreExcluded` for `'absence-only'` cells; `diffRuns` (:89–130)
   treats prev `absence-only` → cur scored-f as **LOST** (a pass-by-construction that became a visible wrong paint) and cur
   `absence-only` ← prev scored-f as **GAINED-TO-BLANK** (listed under its own heading, not counted in totals); prev scored-P →
   cur `absence-only` on the adoption run is listed as `UNMEASURED NOW (absence-only)` and is the expected 19.
4. Snapshot `corpus-v6-18.json`: `criterion` gains "+ per-cell absence-only exclusion (v6.18)"; totals from §6; BACKLOG
   "Current:" line and STATUS wave paragraph quote the three new fractions (`doc-staleness-check.sh:302–345` enforces it).
   README "The scorer idiom" gains one sentence. This is a denominator change BY THE RULE (BACKLOG: "newly measured … never
   lost"); the 19 are listed by name in the snapshot `_note`.

## 5. Blast radius — how a builder censuses it

- Carriers are a MANIFEST predicate, not an IR shape: over the 30 `manifest.json`, count diffs with `wptPass === true &&
  browserRef.diffs[k].semanticPresence.bCoveragePct < 0.02` → expect web 7 / ios 6 / android 6 (the census JSON lists them).
  Cross-check with the PNG predicate (all 13 uniform refs have `bCoveragePct` exactly 0; no sub-floor-ink ref exists on
  wave51-fix, so the two predicates coincide — a stray anti-aliased pixel in a future ref is why the floor, not
  uniformity, is the rule). IR shape of the 7 tests, for the curious: `per-test-ir/*.json` with a single component carrying
  only `Width`/`Height` (scope-implicit-crash-print, gradient-refcrash) or `BackgroundColor` + `Position: ABSOLUTE` +
  `MarginTop -20em` (flexbox_inline-abspos) — nothing a runtime is asked to paint on-canvas.
- For DECISION B: `captureUniform !== null` over all 4305 captures → 79 on wave49-final (S6), expect **73** on wave51-fix
  (37 A-class + 36 B-class; the 6 departures above). Zero of them change verdict.
- Currently-PASSING cells at risk: exactly the 19 (they leave the pass column). No other cell's `wptPass` can move: the gate
  reads only `wptPass` and `bCoveragePct`, both already on the diff. `assertPlatformColumns` (:2187) is unaffected — it tests
  presence, not scoring, and a stamped diff is present. Fixture-net / test-all.sh: untouched.

## 6. Predicted flips (confidence, falsifier)

- `score-gate wave51-fix → wave52-open` after adoption: **0 gained / 0 lost / 0 newly measured / 19 unmeasured-now
  (absence-only) / 0 movers**; totals **web 1208/1372 (88.05 %), iOS 1083/1363 (79.46 %), Android 1074/1363 (78.80 %)** from
  1215/1379 (88.11 %) / 1089/1369 (79.55 %) / 1080/1369 (78.89 %). Confidence HIGH (arithmetic over committed cells; the
  opening gate is expected byte-identical, obligation #0). Falsified if any of the 19 shows non-zero `bCoveragePct` on
  re-run (the ref is frozen, so only a ref re-freeze can do that) or if `unmeasured-now` ≠ 19.
- DECISION B: `blankCaptureVsInkedRef` fires on 36 cells (33 scored f + 3 excluded), `captureUniform` on 73; **no verdict
  changes**. Confidence HIGH. Falsified by any cell whose `wptPass` differs between the stamped and unstamped run.
- If the wave also applies B10's canvas patch and re-freezes (CANVAS_REV bump): +3 gained on `initial-background-color`
  (green on green), and the 19 absence-only cells are unchanged (their refs are blank by construction, not by erasure).
  Confidence MEDIUM — the patch's live rasterisation was never re-run by a skeptic (B10's own caveat).

## 7. Pins a builder must write (each named with the mutation that fails it)

1. `isAbsenceOnly` positive: `{wptPass:true, semanticPresence:{bCoveragePct:0}}` → true; negative: `bCoveragePct 0.102` (the
   access-from-shadow-dom ref) → false; `wptPass:false` with `bCoveragePct 0` (calc-in-media-queries) → false; the olive
   `bCoveragePct 100` pass → false. Mutation: drop the `wptPass === true` conjunct → the calc cell stamps (test fails);
   change `<` to `<=` with `0.02` → a boundary fixture stamps.
2. `applyAbsenceOnlyGate` never overwrites: a diff already carrying `scoreExcluded: true` or `'native-font-parity'` keeps its
   stamp and `wptPass` null. Mutation: remove the guard → the wall stamp is downgraded (assert fails on the string).
3. Source pin (the :1654 idiom): `assert.match(src, /absenceOnlyExcluded = isNa \? \[\] : applyAbsenceOnlyGate\(/)` so the
   gate cannot be wired before the NA gate.
4. Scorer idiom unchanged: `isScored({ssim:1, scoreExcluded:'absence-only'})` → false; `isPass` untouched. Mutation: make
   `isScored` test `=== true` → the string stamp is counted (fails).
5. `score-gate diffRuns`: prev `{ssim:1, scoreExcluded:'absence-only'}` → cur `{ssim:0.71, wptPass:false}` lands in `lost`,
   not `newlyMeasured`; prev scored-P → cur stamped lands in `unmeasuredNow`. Mutation: revert step 3 → the regression is
   listed as newly measured (fails).
6. `captureUniform`: a synthetic 4×4 all-(255,255,255,255) PNG → `[255,255,255,255]`; one pixel changed → `null`; an
   all-(0,0,0,0) PNG → `[0,0,0,0]` (the alpha case the BACKLOG names). `blankCaptureVsInkedRef` true for uniform + ref 0.102,
   false for uniform + ref 0. Mutation: compute on the padded buffer `A` instead of `a` → a black uniform capture padded white
   reads non-uniform (fails).
7. Committed-run replay (the S6 discipline): a test that loads `wave51-fix` manifests, applies the gate to a deep copy, and
   asserts exactly the 19 cells listed in the census JSON are stamped and the totals in §6 come out. Mutation: any change to
   the predicate moves the count off 19.
8. `wave36 M6` pin (`inject-wpt-block.test.mjs:1697`): the new stamp is per-cell and tag-free, so REFUSED_EXCLUSION_TAGS
   needs no change — but the pin's family list must NOT be extended with a tag set (there is none); assert
   `ABSENCE_ONLY_STAMP === 'absence-only'` like :1451 does for the font stamp.

## 8. File ownership (exact paths) and seams

- `tools/titan/inject-wpt-block.mjs` (stamp, gate, call site :2032, record :2111, summary :2358), `tools/titan/inject-wpt-block.test.mjs`.
- `tools/titan/score-gate.mjs` (:60 loadRun, :89 diffRuns), `tools/titan/score-gate.test.mjs`.
- `tools/titan/README.md` ("The scorer idiom"), `tools/titan/results/corpus-v6-18.json` (new snapshot, after the gate),
  `docs/BACKLOG.md` ("Current:" line; 0(n) and the blank-capture bullet re-written as DECIDED), `docs/STATUS.md` (wave paragraph).
- Shared seam: `inject-wpt-block.mjs` is also the file lanes B12/novel-ink/degenerate touch (stamps beside :1749/:1808) — the
  shared-file lane protocol applies (one owner per hunk, rebase before the gate). `capture-browser-ref.mjs` belongs to the B10
  calibration lane, not this one — T3 is a hand-off, not a change here.
- `aggregate-sections.mjs:210` counts NA-excluded TESTS from `scoreEligible`; an `absence-only=` cell count next to it is optional.

## 9. Not verified

- No build, test, capture or gate was run (hard rule). The predicted totals are arithmetic over wave51-fix cells; the 0/0/0/19
  diff assumes `wave52-open` reproduces `wave51-fix` byte-for-byte (obligation #0's own expectation).
- T3's fix was not exercised: B10's patch effect on the two all-green refs is inferred from `canvasFrameCss` (:490–491) and
  the ref sources, not from a re-render; B10's "28 of 42 rasterise differently" remains UNVERIFIABLE (S6-10).
- The 057-class size is UNKNOWN by construction — it needs hand verdicts; only the literal class (19) is measured here.
- Whether any of the 7 absence-only tests' runtimes actually painted nothing FOR THE RIGHT REASON (e.g. flexbox_inline-abspos:
  off-canvas vs component dropped) is exactly what the scorer cannot see and this lane does not claim to know.
- The S6 population files were not regenerated on wave51-fix by S6's scripts (they are pinned to wave49-final paths); the
  re-derivation here used an independent decoder (PIL/numpy) and the manifest's own coverage numbers, which agree on all 73.
