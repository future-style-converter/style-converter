TREES: <scratch>/wave55-skeptic-L6

# Lane L6 · android-paint-order — adversarial skeptic (wave 55)

Worktree on the `TREES:` line: `git worktree add --detach … 611c9f93`, provisioned (both `local.properties`, the
`node_modules` link) before any build; the three delivered patches applied with `git apply` in landing order
(U1 → U2 → U3), all three apply cleanly on 611c9f93. Every artifact of this review is under
`skeptic-census/` beside this file. The lane's patches and `_note.md` were not touched.

## Verdict — MIXED (mustFixBeforeGate: yes)

The mechanism is right and well built. The behind-content band copies androidx foundation 1.11.4's arithmetic. I
re-read the bytecode myself, and every pin goes red under its mutation. All six revert states compile and pass with the
lane's exact counts, and the patches are byte-identical when re-cut. Every claimed cell reads as claimed, and the
geometry self-test holds. **But the blast-radius census under-reports, which is the dangerous direction.** Two SCORED
Android cells outside carriers ∪ mayMove ∪ sharedStems ∪ ring-fence will move by more than R5's 0.002. In both, a
UA-widget `<input>` replica overflows its content box and, under U2, paints over the input's right and bottom border
band. One of them sits in a stage-2 probe section (css-cascade), where it reads as an R4 leak that the rule-5 bisect
lands on U2. The other sits in css-pseudo, which no probe stage covers, so it would surface first at the closing gate.
PLAN §0 says: "A census that grows regenerates the plan before landing."

## Repros executed (outputs in `skeptic-census/`)

1. **Patches.** `git apply` U1, U2, U3 on 611c9f93 → all applied. `shasum -a 256` of the delivered files equals the
   note's `patch:` lines (27dd5bc2… / b10eae97… / 7bf15926…). Re-cut in my worktree (`git add -N` of the 4 new files,
   `git diff HEAD -- <unit paths>`, then `git reset -q -- <the 4 new files>`, index only) → **byte-identical** sha256
   for all three. Each unit patch's path list equals the note's unit section and the PLAN / `revertUnits` commit text
   (U1 4 paths, U2 4, U3 1). No seam file is touched and no path is outside `own:`. `land-units.sh --check --only L6`
   gives "0 error(s), 1 warning(s) — landable". The warning names only this review's own files, written < 120 s
   before the check.
2. **Focused suites, full state.** `:runtime:testDebugUnitTest` with the plan's six filters: 6 suites, 46 tests, 0
   failures.
3. **Pin mutations, replayed by me** (`skeptic-mut.sh`, `skeptic-mut-all.sh`, edits in `skeptic-mutations/*.edit`;
   log `skeptic-mutations.log.txt`). Each one is bracketed: `MUTATING <file> sha256 <pre>` is written before the edit,
   then the four L6 test classes run red, the file is restored byte-exact, `RESTORED … post == pre: yes` is written,
   and the classes run green.

   | id | = lane | mutation | red (of 28) |
   |---|---|---|---|
   | S1 | M1 | swap `drawInOrder`'s body | P1a |
   | S2 | M2 | `result.border(` + androidx import back at the self-paint site | P1b + 2 BorderRadiusSelfPaintTest |
   | S3 | M3 | drop `ceil` | P2b |
   | S4 | M4 | drop the `max(0, …)` clamp | P2e |
   | S5 | M5 | node draws `drawContent(); band()` | P1c |
   | S6 | M6 | revert the StyleApplier hunk | P3a, P3b, P3c, P3d |
   | S7 | M7 | `applyClipPath` moved below `applyForegroundFilters` | P4a, P4b |
   | S8 | M8 | drop-shadow branch → `null` | P4a, P4b |
   | S9 | M9 | slow-path lambdas swapped | P3g |
   | X6 | — (mine) | sides chained OUTER of ColorApplier, still behind content | P3b, P3c, P3d |
   | X8 | — (mine) | fillArea `>` → `>=` | P2c |
   | X1 | — (mine) | rectBand fill case drawn at `topLeft` | **none (green)** |
   | X2 | — (mine) | rectBand `Stroke(strokePx * 2)` | **none (green)** |
   | X3 | — (mine) | rounded-simple `cr.x < half` → `<=` | **none (green)** |
   | X4 | — (mine) | node `update()` never re-caches | **none (green)** |
   | X7 | — (mine) | generic tint keeps the colour's alpha | **none (green)** |

   Every pre sha256 equals the lane's (BehindContentBorder.kt a0f6cf5b…, BorderRadiusApplier.kt 3ad5a63a…,
   StyleApplier.kt 5a8c9da1…, EffectsFacade.kt c2d5f2c9…, FilterExtractor.kt 21af6aa5…, BorderSideApplier.kt
   cda285c3…), and every post equals its pre.
4. **Revert states** (`skeptic-states.sh` → `skeptic-states.log.txt`). Each state starts from `git checkout -- runtimes/compose`
   plus `git clean -f -- <the 4 created files>` in MY worktree, then `git apply` of the kept patches, then
   `compileDebugKotlin` + `compileDebugUnitTestKotlin` + the lane's 16 focused filters. The counts match the lane's
   `revertStatesCompiled:` lines exactly, and the run ends on the full state:
   - `U3 reverted (kept U1 U2)`: rc 0, 76 suites / 608 tests / 0 failed
   - `U2 reverted (kept U1 U3)`: rc 0, 76 / 603 / 0
   - `U2 then U1 reverted (kept U3)`: rc 0, 75 / 595 / 0
   - `U2 and U3 reverted (kept U1)`: rc 0, 75 / 601 / 0
   - `base 611c9f93`: rc 0, 74 / 593 / 0
   - `full (U1 U2 U3)`: rc 0, 77 / 610 / 0
5. **Bytecode, read again independently.** I ran `javap -p -c` on foundation-android 1.11.4's `BorderModifierNode`,
   `BorderKt` and `BorderCache` from the Gradle cache's `foundation.aar`. Each part matches `BehindContentBorder.kt`:
   - `hasValidBorderParams` is `toPx(width) >= 0 && minDimension > 0`. The NaN case goes to "invalid" on both sides.
   - `strokeWidthPx = Math.min(Hairline ? 1 : (float) Math.ceil(toPx), (float) Math.ceil(minDimension / 2))`, with the
     half-stroke topLeft, `borderSize = size − stroke`, and the strict `2·stroke > minDimension`.
   - Rounded simple: fill / `clipRect(stroke, stroke, w − stroke, h − stroke, Difference)` / a stroke at
     topLeft / borderSize with `shrink(half)`.
   - Rounded non-simple: `createRoundRectPath`, which is `addRoundRect` minus `createInsetRoundedRect`. That
     constructor receives the shrunk corners in the order TL, TR, **BR, BL** (lload 10 before lload 8), the same as
     `insetRoundRect`; P2e pins the order (28 / 38).
   - Generic: Alpha8 + `ColorFilter.tint(value.copy(alpha = 1f))` for a SolidColor and Argb8888 otherwise, a Clear
     rect, a 2× stroke, and a `(w+1)/w` Clear mask.
   - Rectangle: Fill of (0, 0, cache size) or a Stroke on (topLeft, borderSize).
   - Every one of the three outline branches starts with `drawContent()`.

   Not mirrored: `shouldAutoInvalidate` (androidx false; ours defaults to true, which only means extra invalidation)
   and the semantics shape. The lane says so in its note.
6. **Cells** (`node tools/titan/results/wave52-gate/cells.mjs … wave54-final`). Every value the note claims reads back:
   - clip-rect android P 0.9559
   - flex safe-001 / safe-002 P 0.9619 / 0.9635
   - grid align-self-001 / justify-self-001 / vertWM-001 / rtl-001 P 0.9673 / 0.9667 / 0.9612 / 0.9646
   - grid safe-001 f 0.9441
   - powerless-hue hsl / hwb / lch f 0.7542 / 0.755 / 0.7013
   - clip-path-filter-order android P 0.9548
   - basic-blur android f 0.904
   - anchor-center-overflow-003 and display-contents-dynamic-pseudo-insertion-001: unscored (no cell), as the note says
7. **Geometry self-test.** `geometry-gate.py wave54-final --base wave54-open --lanes L6 --self-test` exits 0 with
   "HOLDS — 0 violation(s)": 7 gating FAIL, 29 controls PASS, 1 report FAIL. That is identical to the lane's
   `geometry-selftest.wave54-final.txt` apart from its trailing `exit 0` line.
8. **Looks** (ref / web / iOS / Android).
   - clip-rect, rows 150–240 × x 100–300: the red px are 560 / 560 / 560 / **144**. The 416 missing red px are the
     menu edges, which Android runs the navbar's blue band across (zoomed, 10×). This matches the lane.
   - flex safe-001 at 3×: Android draws the black 3-px square through the yellow, dotted-purple children; ref, web and
     iOS paint the children over it. This matches the lane.
9. **My own census** (`SkepticL6CensusTest.kt.txt` + `skeptic-census.sh` → `skeptic-census-full.tsv.gz`, then
   `skeptic-census.py` → `skeptic-census.out.txt`). It is a separate JVM test, not the lane's. It reads, per component:
   - Compose's own `extractConfig`: band, fast path, and the self-paint predicate as StyleApplier calls it;
   - opacity, the outline and its offset, `borderImage.hasBorderImage`;
   - children by `slot.parent` (with and without `<br>`), text, runs, pseudos, marker;
   - the source tag, the meta keys, and the full-tree folded chain.

   It subtracts nothing for `display: contents`. Results:
   - 1973 banded components: 10 self-paint, all fast; 1963 legacy, of which 1494 fast and 469 slow. This reproduces
     the lane's and the plan's D21 count.
   - Sanity on the patched chain: 0 fast rows without `BehindContentBorderElement`, and 0 unbanded rows with one.
   - 0 banded components carry a border-image. `BorderImageBox` paints its 9-slice back over the border box INNER of
     the band, so that class is latent but absent from the corpus. 0 outlines have a negative offset.
   - 283 documents have an exposed banded component, and 279 of them are inside the plan's set. **GROWTH 4:**
     - display-contents-dynamic-pseudo-insertion-001: the lane's growth item, confirmed.
     - revert-layer-015 and first-line-and-placeholder: real, see D1.
     - gradient-hue-direction: three `<hr>` bands with no content. The renderer has no `hr` painter, so these are
       inert; I checked and dismissed them.
   - All 27 carriers have an exposed component on their own unit's route.
10. **The widget replay** (`widget-replay.py` + `widget-replay-score.mjs` → `widget-replay.out.txt`, pictures in
    `replay/`). It paints the predicted U2 picture onto the wave54-final Android capture and scores it with the gate's
    `diffWebVsRef`. The captures reproduce the cells (0.9663 / 0.9743).
    - `css-cascade/revert-layer-015 android`: 0.9663 → **0.9707 (Δ +0.0044)**, 326 px change.
    - `css-pseudo/first-line-and-placeholder android`: 0.9743 → **0.9765 (Δ +0.0022)**, 326 px change.

## Defects, ranked

### D1 · must-fix — census under-count: UA-widget inputs move under U2, outside every plan set

`skeptic-census.out.txt`, GROWTH. The two components are `wpt__css-cascade__revert-layer-015__0-077` and
`first-line-and-placeholder__0__0-199`. Both are an `<input>` with no `attrs.type`, which makes it a TEXTFIELD widget
in WPT capture mode. Each has a content box of 145×15, padding 1/2, a 2 px INSET #767676 band, `overflow: clip`, a
white background, and no children or text. That is why the lane's predicate (and the brief's mayMove scan) classify
them inert.

ComponentRenderer's UA-widget mount hook paints `UAWidgets.Render(spec)`, which is
`Canvas(Modifier.size(153.dp, 21.dp))`. Inside a 145×15 content box the size is coerced to 145×15, but the
FillRect(0, 0, 153, 21, WHITE) and the hairline StrokeRect are drawn unclipped. The only clip is the element's own
`overflow: clip`, which is `OverflowApplier` `clip(RectangleShape)` at the node bounds, i.e. the BORDER box (inner of
the band).

The wave54-final Android capture shows the replica's top hairline running through the padding at row y19,
x165..166, past the content box (x20..164). Today the band paints after it. Under U2 the band paints first, and the
white fill covers:
- the right band, x167..168 for rows 20..36;
- the bottom band, rows 35..36 for x21..168.

Look (`replay/`, enlarged next to the ref): the ref has a thin frame on all four sides. Android today has the dark
top/left band, a gray right/bottom band and an inner hairline. After U2 the right and bottom edges are gone. **The
score rises while the picture moves away from the ref, so these moves are DEGENERATE.**

Why this blocks:
- Both cells are in `mustNotMoveComplement` (R5, 0.002). No lane names either stem. Neither is in
  `unionCaptureCarriers` or `unionMayMoveCaptures`.
- revert-layer-015's section (css-cascade) is a stage-2 probe section. Its move reads as an Android mover outside
  carriers ∪ mayMove, and `stage1Decision` / revert rule 5 would bisect it onto U2 and **revert U2**.
- first-line-and-placeholder's section (css-pseudo) is in neither stage-1 nor stage-2 `probeSections`. It surfaces
  first at the closing gate as an unpredicted R5 mover, which stops the ship.
- `display-contents-state-change-001` (an `<input type=text>`, same geometry) is already in L6 mayMove. Its
  "band now under its child" reading is really this same widget-over-band picture, so the orchestrator should know to
  expect it.

Fix, before landing and before the stage-1 window:
- Regenerate the plan (`plan-build.py`, an appended rule row). Add both stems to L6 `mayMove` BY RULE: "a UA-widget
  replica (`UAWidgets.Render`) whose fixed canvas overflows the content box of a bordered legacy-route component now
  paints over the band".
- Add two predictions: up-or-stay +0.002 to +0.005 (replay), each labelled DEGENERATE (the picture loses its
  right/bottom border).
- Name revert-layer-015 in the stage-2 reading.
- Add css-pseudo to the stage-2 `probeSections`, or accept that the closing gate reads that stem for the first time.

The generic code fix is to clip the widget replica to its content box (`widgets/UAWidgets.kt`). It is outside L6's
ownership, so it belongs to a next-wave item, not this lane.

### D2 · should-fix — the band painters and node lifecycle carry no JVM pin (raster parity R1 is device-only)

X1, X2, X3, X4 and X7 stay green. P2 pins `bandGeometry` and `insetRoundRect` / `shrinkCorner`, but nothing pins:
- what `rectBand`, `roundedBand` or `genericBand` actually draw (positions, sizes, style, the fill-case origin, the
  simple-branch threshold, the tint alpha);
- `BehindContentBorderNode.update()`.

The routes involved are rectBand (1494 legacy fast bands plus 0 self-paint) and roundedBand (10 self-paint bands).

The lane discloses this ("pinned only arithmetically (P2)"), and the device `[ID]` keys are the stated defence. I
re-read the bytecode and found no divergence, so this is a pin-coverage gap, not a known bug. A cheap closure would be
to factor each branch into a pure op plan (the `UAWidgetsGeometry.plan` pattern) and pin the op list. A recording
canvas is likely blocked by the stubbed `android.graphics.Paint` in the plain JVM suite.

### D3 · nit — "verbatim" P3 payloads are re-ordered

`BorderSidesBehindContentTest.sides()` interleaves width / style / color per side. The wire lists the four widths,
then the four styles, then the four colors (I checked `…__0-023` and `…__2-095`). The sets are identical and nothing
is duplicated, so extraction is the same. The word "verbatim" is slightly too strong. P4's `…__1-072` payload is
verbatim in order.

### D4 · nit — house-rule comments

`rectBand`, `roundedBand`, `genericBand` and `bandGeometry` have no doc or comment line above their declarations; their
bodies are commented. `BehindContentBorder.kt` sits exactly at the 200-line ceiling.

### D5 · nit — P4b's chain call omits `wptCaptureMode = true`

P4b calls `applyConfig` without `wptCaptureMode = true`, while its `extractConfig` passes it. The border and effects
steps do not branch on it today, but the census and the device both run capture mode.

### D6 · nit — stale kdoc outside the lane

The `color/ColorApplier.kt` `applyColors` kdoc ("border draws sit OUTSIDE the alpha layer") is stale after U2. The
lane already flagged this for the closing docs pass, since the file is read-only for L6.

## Checked, no defect

- **Silent fallthroughs.** Every outline kind androidx handles is handled. An invalid width or an empty box paints
  content only, as androidx does. The `when (outline)` is exhaustive.
- **Edits outside ownership.** None.
- **Scratchpad pointers in the delivered files.** None; `TREES:` is exempt.
- **Probe leftovers in the patches.** None.
- **Rule 2b.** Not applicable: L6 has no seam.
- **New-logic file size.** 200 lines or fewer.
- **The BorderSideApplier slow-path refactor.** The only changed lines are the defaulted parameter, the fast-path
  condition and the one dispatch line; the side-painting lines are unchanged. The plan allowed "a defaulted parameter
  and two one-line conditions", and the lane reported the deviation (deviation 3).
- **Opacity groups.** 49 components in 2 documents (basic-blur, ring-fenced; anchor-center-overflow-003, unscored,
  mayMove), matching the lane.
- **Inset box-shadow.** It sits in EffectsFacade OUTER of the band, both before and after U2, so it is unchanged.
- **Background layers and blend paths.** These are draw-only nodes, and the band still lands after them.
- **The four throwing components** (conic-gradient-line-height-relative-units-003/004). They carry no Border or
  Outline property.
- **Honesty of the predictions.** Flips 0. The P→P grid rows are claimed only through their `[X]` geometry keys. The
  basic-blur move is reported, never claimed.

## Not checked

- No device run: every pixel statement here is a replay or a reading of the bytecode and the chain, including D1's
  picture. Stage 1 and stage 2 are the proof.
- Whether `UAWidgets.Render` places the replica in exactly the same spot on the post-U2 tree. Its layout is untouched
  by L6, so I assumed it does.
- `BehindContentBorderNode`'s attach and invalidation lifecycle on a real device.
- The renderer's full cascade (UA rules, inheritance merges). Like the lane's census, mine reads each component's own
  declarations through the real extractors.
- Whether revert-layer-015 would flip. It fails today on coverage and degenerate vetoes, not on SSIM, so a flip is
  unlikely but unproven.
- No full suite and no `:app` suite.
