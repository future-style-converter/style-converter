# wave-50 skeptic lane S6 — honesty audit + BACKLOG-edit verification

Lane S6 of wave 50. **No build system, no device, no browser, no network.**
Everything here was measured from the frozen evidence already in the repo:
`tools/titan/runs/wave49-final/` (manifests + capture PNGs),
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`,
`tools/wpt/css/` (test + reference sources), `tools/visual/baseline/`, and the
tree's own source. **No builder lane's census script was run and no lane's
prose was taken on trust** — every number below was re-derived with the
scripts in this directory.

Nothing in the tree was mutated by this lane. No `_skeptic50` probe file was
written (the two that exist belong to lanes S3 and S4).

## Files

| file | what it is |
|---|---|
| `backlog-corrections.json` | **The deliverable.** 18 verified corrections + 3 new findings + an explicit could-not-test list. Each carries a `verdict`, the `measurement` that produced it, and a `correctedText` the refill writer can paste. |
| `cells.mjs` | Independent cell reader over a titan run dir (the scorer idiom verbatim). `node tools/titan/results/wave50-S6/cells.mjs wave49-final '<test regex>' [section]` |
| `measure.py` | PNG measurement kit (palette, ink box, colour counts) used for every pixel claim. `python3 tools/titan/results/wave50-S6/measure.py <png>…` |
| `zneg-census.mjs` | Written from scratch: which scored tests have a match-reference declaring a negative z-index, and how many corpus refs do. Output `zneg-census.json`. |
| `recheck-degenerate-veto.mjs` | Recomputes lane B12's veto confusion matrix from the PNGs, reading only the hand-verdict labels. |
| `zneg-census.json` | Its output: 42 corpus refs, 8 scored tests, 24 cells, 10 failing. |
| `blank-capture-census.txt` | All 79 single-colour captures among the 4305 wave49-final captures. |
| `blank-capture-vs-inked-ref.json` | The 42 of them that face a reference WITH ink and are still scored. |
| `blank-vs-blank-passes.json` | The 7 tests (21 cells) that are uniform-on-uniform and PASS at ssim 1.0000. |

## Verdict summary

| # | lane | claim | verdict |
|---|---|---|---|
| S6-01 | B7 | 5(h) native `image()` chain is DONE | CONFIRMED |
| S6-02 | B3 | counter-004 is 80 spans → 80 line boxes | CONFIRMED |
| S6-03 | B6 | 3(b) is an explicit-height clamp, band `[-w,0)` | CONFIRMED |
| S6-04 | B6 | 3(a) web is wrong too | CONFIRMED — and **12** degenerate cells, not 8 |
| S6-05 | B9 | `subelements-003`: only Android lacks the blue underline | CONFIRMED; BACKLOG's "every render" REFUTED |
| S6-06 | B4 | `name-case-sensitivity` is float placement, not markers | **PARTIAL** — natives only; web IS missing content |
| S6-07 | B4 | web renders korean + RTL markers correctly | CONFIRMED for markers; "correctly" overstates RTL order |
| S6-08 | B8 | the STATUS "~2.4× over-blur" row is doubly wrong | CONFIRMED (independent σ re-fit) |
| S6-09 | B10 | css-gaps 033 native `P 1.0000` is blank-vs-blank | CONFIRMED (the A/B half needs a browser) |
| S6-10 | B10 | 42 refs / 28 movers / 6 scored | 42 ✓ · 6 ✓ · 28 UNVERIFIABLE |
| S6-11 | B12 | the 100-cell hand sample + the veto's numbers | CONFIRMED — 12/12 spot-checks, matrix reproduced exactly |
| S6-12 | B2 | 0(b) was the channel LEVEL, not a decline | CONFIRMED — corpus-v6.15's note is wrong too |
| S6-13 | B11 | iOS clips the placeholder label | CONFIRMED |
| S6-14 | B5 | the one fully transparent capture | CONFIRMED — and widened to 42 + 21 more cells |
| S6-15 | B9 | the LineBoxCensus twin header | **REFUTED** — a second, unstated `<br>` divergence |
| S6-16 | B10 | GapDecorationBands banner pixel ranges | CONFIRMED-WITH-CORRECTION (exclusive-upper; two platforms merged) |
| S6-17 | — | standing-constraint sweep | CONFIRMED clean (one declared, correctly declared, exception) |
| S6-18 | all | comment-vs-measurement sweep | every quoted cell score CONFIRMED; four prose nits |

## The three findings that are not a lane's claim

1. **21 blank-vs-blank PASS cells at ssim 1.0000** (`blank-vs-blank-passes.json`):
   seven tests where the frozen reference is a single uniform colour and all
   three captures are that same single colour. Obligation #3 evidence that
   needs no hand verdict.
2. **42 blank captures scored against an inked reference**
   (`blank-capture-vs-inked-ref.json`), carrying scores up to **0.9959** for a
   page with no ink in it. Lane B5's proposed blank-capture guard should key
   on **uniform colour**, not on alpha — the alpha-only predicate finds one
   instance, the colour predicate finds forty-two.
3. **`GapDecorationBands` Kotlin/Swift numeric-domain divergence** — Compose
   rounds every input to `Int` before `FlexWrapLines.stretchLines`, iOS stays
   in `CGFloat` through `FlexWrapPlan.stretchLines`. ≤1 px of band edge, no
   twin header to amend, unmeasured on device.

## Two claims a fix lane should act on before the gate

* **S6-15** — `LineBoxCensus.kt`'s twin banner states exactly one deliberate
  divergence from the wave-46 iOS census and there are two: Compose's
  `<br>` / `role == "line-break"` arm in `LineBoxCensusRuns.childRun` has no
  counterpart in `LineClampCensusRuns.childRun`, where the converter's stamped
  `<br>` height would instead make the run monolithic.
* **S6-06** — the `name-case-sensitivity` correction must be split by platform.
  Deleting "missing marker content" wholesale would erase web's real defect.

## What could not be tested, and why

No device, simulator, browser or network on this host, so: every predicted
cell flip in the wave stays a prediction; lane B10's live-browser A/B and its
"28 of 42 refs rasterise differently" could not be re-run; and citation
*semantics* could not be checked — `tools/visual/spec-cite-validate.mjs` passes
(4443 citations, 0 unknown sections) but `tools/visual/spec-sections.json`
stores section NUMBERS only, so it proves a section exists, never that it is
the section a claim needs. One pre-existing citation to re-check with network:
`ImageCandidateChain.kt`/`.swift` (wave 49) cite `css-images-4 §2.5` for the
`image()` candidate walk; §2.5 is `cross-fade()` in the ED as I recall it, and
the walk belongs to "Image Fallbacks and Annotations: the `image()` notation".
