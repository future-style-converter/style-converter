# cbb-android — wave-55 lane brief L2-cbb-android (BACKLOG "Next-wave obligations" 0(g))

Read-only planning brief, written 2026-10-11 while the opening gate `wave55-open` runs on this tree (HEAD 0d0d3a3d, the
wave-54 merge; `tools/titan/runs/wave55-open/gate-driver/driver.log` was at css-break when this was written). Nothing was
built, captured or edited outside `tools/titan/results/wave55-plan/`.

**Evidence.**
- Run of record: `wave54-final` (`tools/titan/runs/wave54-final/sections/<sec>/`). Its totals are web 1243/1372, iOS
  1131/1362 and Android 1128/1362 (`tools/titan/results/wave54-gate/score-final.txt:3`).
- The probe run: `wave54-probe` (tree 206d71d9). On every Android-relevant path it differs from HEAD 0d0d3a3d ONLY by the
  reverted unit. `git diff --stat 206d71d9 0d0d3a3d -- runtimes/compose apps/android-harness tools/titan/extract-fixture.mjs
  converter` lists exactly the five 5b333c8a files plus two READMEs. The wave54-probe Android captures are therefore
  "HEAD + the old unit" on device.
- Cells: `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave54-open wave54-probe wave54-final` →
  `cbb-android.cells.out.txt`.
- Pictures:
  - opened with the Read tool: `wave54-gate/stage2/sheets-gained/gained-05.png` row 6, plus zoomed ref | android | iOS |
    web sheets of every target;
  - measured: `cbb-android.look.py` → `.look.out.txt` (colour runs along one row and one column).
- Census: `cbb-android.census.mjs` → `.census.out.txt` (1435 per-test IR documents).
- Replay: `cbb-android.replay.py` + `.replay-score.mjs` → `.replay.out.txt`, scored with the gate's own metric
  `diffWebVsRef`.
- Fakes: `cbb-android.fakes.py`.
- Geometry: `wave54-plan/compose-wpt-content-box-cb.geometry.py` (re-used unchanged; run on wave54-final and
  wave54-probe → `cbb-android.reused-geometry.out.txt`) plus the new supplementary `cbb-android.geometry.py` →
  `.geometry.out.txt`.
- PIL lives in `/opt/homebrew/bin/python3.12` on this host; `/usr/bin/python3` has no PIL.

**Verdict in one paragraph.** The record's hypothesis is right about the size and wrong about the cause.
- Size: the missing 20 px on each axis is #middle's padding: 10 + 10 horizontally, 10 + 10 vertically.
- Cause: the old unit fixed the containing-block CHANNEL, and that fix is right. Under it the out-of-flow
  `#backdrop-filter` is sized 200×100. What stays wrong is the IN-FLOW percentage frame. Under WPT capture,
  `SizingApplier.inflateForContentBox` passes a PERCENT size through untouched (SizingApplier.kt:254-256 doc, :288
  `else -> v`), and `fillMaxWidth/Height(fraction)` (:362 / :437) then makes the percentage the BORDER box. So #middle
  (`width/height: 100%; padding: 10px`) is drawn 200×100 border-box, its content box is 180×80, #inner fills that, and
  #inner's `overflow: hidden` clips the correctly sized 200×100 green down to 180×80.
- Fix, two units:
  - **U1** re-lands 5b333c8a unchanged.
  - **U2** is a new layout-time "fraction × base + band" for a content-box percentage size.
- U2's radius is exactly one box in the corpus (#middle of nested-border-radius-clip-3, both axes), inside U1's 14
  documents. The carrier set does not grow.
- On Android's own re-laid pixels the pair replays to **0.9999**.
- **GO-SMALL**: Android only, effort S, 2 units, 1 seam hunk (the old one).

---

## 1. Targets (cells at `wave54-final`, re-derived) and what the pictures show

| cell (`wave54-final`) | wave54-probe (HEAD + 5b333c8a, device) | role |
|---|---|---|
| `filter-effects/backdrop-filter-nested-border-radius-clip-3 android f 0.9574` | P 0.9668, **GEOMETRY WRONG** (the revert) | **the 0(g) target** (U2, needs U1) |
| `filter-effects/backdrop-filter-nested-border-radius-clip android P 0.9649` | P 1, GEOMETRY OK | U1, short box → faithful |
| `filter-effects/backdrop-filter-nested-border-radius-clip-2 android P 0.9689` | P 1, OK | U1 |
| `filter-effects/backdrop-filter-nested-border-radius-clip-4 android P 0.977` | P 0.9999, OK | U1 |
| `css-flexbox/abspos/abspos-autopos-htb-ltr android P 0.9966` | P 0.9967, OK | U1, red-ink DEGENERATE → faithful |
| `css-flexbox/abspos/abspos-autopos-vlr-ltr android P 0.9966` | P 0.9967, OK | U1 |
| `css-flexbox/abspos/abspos-autopos-vrl-ltr android P 0.9966` | P 0.9967, OK | U1 |
| `css-flexbox/abspos/abspos-autopos-htb-rtl android P 0.9869` | P 0.9966, WRONG (16, 88, 105, 187) = the iOS line | U1, report |
| `css-flexbox/abspos/abspos-autopos-vlr-rtl android P 0.9966` | P 0.9966, WRONG (16, 88, 105, 187) | U1, report |
| `css-flexbox/abspos/abspos-autopos-vrl-rtl android P 0.9966` | P 0.9966, WRONG (16, 88, 105, 187) | U1, report |
| `css-position/position-absolute-semi-replaced-stretch-button android f 0.9739` | f 0.9843 | U1, report (control chrome keeps it f) |
| `css-position/position-absolute-semi-replaced-stretch-input android f 0.4117` | f 0.4555 | U1, report |
| `css-position/position-absolute-semi-replaced-stretch-other android f 0.4396` | f 0.4679 | U1, report |
| `css-gaps/flex/flex-gap-decorations-006 android f 0.8223` | f 0.8223 (Δ 0) | U1 side reader (`vtext`), undirected |

Every iOS and web cell of these 14 tests is unchanged across wave54-open → wave54-probe → wave54-final. The only
exceptions are two web rows that are other lanes' carriers: semi-replaced-stretch-input / -other web, which moved on
wave-54 L6 RS. `nested-border-radius-clip-3` is web P 0.9921 / iOS P 0.9921, and both print GEOMETRY OK.

**What the pictures show** (looked at: gained-05 row 6 = ref | wave54-open | wave54-probe; zoomed sheets of the ref, the
wave54-final android / iOS / web captures and the wave54-probe android capture for clip-3, autopos-htb-ltr / -htb-rtl,
nested-clip and nested-clip-4). The colour runs come from `cbb-android.look.out.txt`, along row y = 80 / column x = 120:

| picture | row y = 80 | column x = 120 |
|---|---|---|
| ref (= web = iOS) | blue[16..25] grey[26..35] **green[36..235]** | blue[16..25] grey[26..35] **green[36..135]** |
| android base (wave54-final) | blue[16..25] grey[26..35] green[36..195] pink[196..215] grey[216..225] blue[226..235] | … green[36..95] pink[96..115] grey[116..125] blue[126..135] |
| android U1 only (wave54-probe) | blue[16..25] grey[26..35] green[36..215] **grey[216..225] blue[226..235]** | … green[36..115] **grey[116..125] blue[126..135]** |

What each picture shows:
- **The ref.** #middle OVERFLOWS #outer. Its border box is 220×120 from (26, 26), so its right and bottom padding bands and
  #outer's right and bottom padding all lie beyond #outer's padding-box clip at x 236 / y 136. The green runs straight to
  that clip edge.
- **Android with U1.** It has no fail ink. #middle's grey right and bottom bands are visible INSIDE #outer, with #outer's
  blue bands beyond them. So #middle is drawn as a 200×100 border box, ending at x 225 / y 125, not 220×120 ending at
  245 / 145. The green's rounded corners at (215, 115) are #inner's radius-25 corners: the green is #inner's clip.
- **Android base.** The pink L (196..215 / 96..115) is the backdrop source inside #inner's 180×80, which the 160×60
  backdrop does not cover.
- **iOS and web** paint #333 (inverted #ccc) at #inner's right-hand corners where the ref paints grey. That is their own
  0.9921, not this lane, and must-not-move.

The other U1 carriers, looked at side by side:
- autopos-htb-ltr android: base green 70×80 in a red L; probe green 100×100, = ref.
- autopos-htb-rtl android:
  - base: red with the green displaced right, plus a stray green strip;
  - probe: green with a 10-px red strip at the right (x 106..115), **pixel-identical in geometry to the iOS capture**.
- nested-clip and -4 android: base has a pink L on the left and bottom; the probe = ref.

## 2. Mechanism, the spec, and the arithmetic of exactly 20 px

**The test** (`tools/wpt/css/filter-effects/backdrop-filter-nested-border-radius-clip-3.html`):
- `#outer {position: relative; width: 200px; height: 100px; padding: 10px; border-radius: 10px; overflow: hidden}`;
- inside it `#middle {position: relative; width: 100%; height: 100%; padding: 10px; border-radius: 10px; overflow:
  hidden}`;
- inside that `#inner {position: relative; width: 100%; height: 100%; border-radius: 25px; overflow: hidden}`;
- inside #inner, `#backdrop-source {100% × 100%, pink}` and `#backdrop-filter {position: absolute; top: 0; left: 0;
  width: 100%; height: 100%; backdrop-filter: invert(1)}`.

The per-test IR (`wave54-final/sections/filter-effects/per-test-ir/wpt__filter-effects__backdrop-filter-nested-border-radius-clip-3.json`)
carries the same declarations:
- `0-171` (#outer): Width `{"type":"length","px":200}`, Height `{px:100}`, Padding* `{px:10}`;
- `0-172` (#middle): Width / Height `{"type":"percentage","value":100}`, Padding* `{px:10}`;
- `0-173` (#inner): Width / Height `{percentage 100}`, no bands;
- `1-175`: `Position ABSOLUTE`, Top / Left `{px:0}`, Width / Height `{percentage 100}`.

No component declares BoxSizing, so under WPT capture every box is `content-box`
(`SizingApplier.effectiveBoxSizing`, SizingApplier.kt:311-316).

**The spec.**
- css-ui-3 §3.1 (`box-sizing: content-box`): width and height size the CONTENT box, and padding and border are laid out
  outside it. The repo's comments cite this as css-sizing-3 §3.
- CSS 2.1 §10.2: a percentage width is "calculated with respect to the width of the generated box's containing block".
  §10.5 says the same for height against a definite containing-block height (#outer's 100 px is definite).
- CSS 2.1 §10.1: an in-flow box's containing block is its parent's content edge.
- css-position-3 §2.1: an absolutely positioned box's containing block is the padding box of the nearest positioned
  ancestor (#inner, no padding, so 200×100).
- CSS 2.1 §11.1.1 / css-overflow-3 §3: `overflow: hidden` clips at the padding edge.
- css-backgrounds-3 §5.3: that clip follows the border-radius curve.

**The reference arithmetic** (capture coordinates; #outer's border box starts at (16, 16)):

| box | border box | content box |
|---|---|---|
| #outer | 200 + 10 + 10 = 220 wide, 120 tall, x 16..235 | x 26..225 |
| #middle | 100% × 200 + 10 + 10 = **220**, 100% × 100 + 10 + 10 = **120**, x 26..245 | x 36..235 |
| #inner | — | 100% = 200×100 at (36, 36) |
| #backdrop-filter | — | 100% of #inner's padding box = 200×100 |

Visible after #outer's clip at x 236 / y 136: green **(36, 36, 235, 135)**.

**Compose with U1 only** (the probe picture):
- #outer: `inflateForContentBox` inflates the Exact px (:276) → frame 220×120, padding inside → content 200×100. Right.
- #middle: `rawW` is `LengthValue.Relative(100, PERCENT)`. `inflateForContentBox` returns it unchanged (:288,
  documented at :254-256, "a px band cannot be added to a fraction — needs a layout-time add"). `applyWidth` (:362) →
  `fillMaxWidth(1f)` against #outer's 200-px content constraint gives a 200-wide frame, and the padding is chained
  innermost (StyleApplier.kt:702 sizing → :758 overflow clip → :786 padding), so the content is 180. Same for the height
  (:437): 100 → 80.
- #inner: `fillMaxWidth / Height(1f)` → 180×80 at (36, 36), i.e. x 36..215.
- `#backdrop-filter` under U1:
  - `DynamicValueResolver.childContainingBlock` publishes #outer 200×100 (unsubtracted, content-box);
  - #middle publishes 100% × 200 = 200, also unsubtracted;
  - #inner publishes 200;
  - `resolveOutOfFlowPercentSizes` (ComponentRenderer.kt:1289 → :5018) → 200×100 px.
  - It is clipped by #inner's `overflow: hidden`, radius 25, to **(36, 36, 215, 115)**: exactly the probe line. Its
    greenPx 13784 ≈ 180×80 − 4 × (1 − π/4) × 25² = 13864, minus edge antialiasing.

**So the 20 px = the `contentBoxInflateX` / `contentBoxInflateY` of #middle**, computed by
`SizingExtractor.contentBoxInflation` (:219-243): PaddingLeft 10 + PaddingRight 10, and PaddingTop 10 + PaddingBottom 10.
It is dropped by the PERCENT pass-through.
- Right: 245 (ref #middle right edge) − 225 (Android #middle right edge) = 20.
- Inside the clip: 235 − 215 = 20 on x, and 135 − 115 = 20 on y.

**The base, for completeness** (neither unit): green 160×60 = 200 − 20 (#outer's band, subtracted by the frozen channel)
− 20 (#middle's band, subtracted again), and 100 − 20 − 20 = 60. #inner is 180×80 for the in-flow reason above. Pink
fail ink = 180×80 − 160×60 = 4800 minus the rounded corners → the measured 4360.

**The hypothesis "the nested clip's second border band", ruled on.**
- CONFIRMED in quantity: it is the second level's (#middle's) padding band, 10 + 10 per axis.
- REFUTED in locus: the band is not lost in the containing-block channel. U1 publishes 200×100 at every level and the
  abspos box is 200×100. It is lost on the in-flow percentage frame (SizingApplier's PERCENT arm).
- Two discriminators in the pictures:
  1. #middle's own grey right band is visible INSIDE #outer (216..225). A channel defect cannot move an in-flow box's
     frame.
  2. clip / -2 / -4 have no padded percentage box between #outer and the abspos, and they went to GEOMETRY OK under U1.
- The census confirms it: #middle of clip-3 is the corpus's only in-flow percentage box with a non-zero band (§5 B).

## 3. Ownership (disjoint from L1, L3–L7 as the wave lists them)

**Owned:**
- **U1** (re-land 5b333c8a; the two existing files are byte-identical to 5b333c8a's parent, so its hunks apply as
  committed):
  - NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt` (53 lines =
    `git show 5b333c8a:<path>`);
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/DynamicValueResolver.kt`: the defaulted
    parameter at :207-210 and the one condition before :238. The file is 663 lines; sha256 d6943e20… at HEAD equals
    5b333c8a^.
  - `runtimes/compose/src/test/java/com/styleconverter/runtime/core/variables/DynamicValueResolverTest.kt`: 5b333c8a's
    rows, with ONE change: the census pin's corpus path goes `wave53-final` → `wave54-final`.
    - The expected 14-document list is unchanged (§5 A re-derived it).
    - The 15 payload documents are byte-identical across wave53-final, wave54-final and wave54-probe (`cmp`, this
      planning).
- **U2** (new):
  - NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt` (≤ 120 lines);
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt` (561 lines, oversized): **two
    one-line `else if` call sites only**, after :123 and after :132 inside `applySizing`, plus at most one comment line at
    :254-256 pointing to the new file;
  - NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/sizing/PercentContentBoxBandTest.kt`.
- Lane dir `tools/titan/results/wave55-cbb-android/`.

**Seams** (delivered as patches, never edited):
- **seam 1: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` `RenderComponent`
  — the `val childContainingBlock = remember(…, wptCaptureModeForStretch)` block — passes `wptCaptureMode =
  wptCaptureModeForStretch` to `DynamicValueResolver.childContainingBlock` (+4 comment lines).**
  - Anchor :1950-1951 at 0d0d3a3d (sha256 2445d9ea2674576fc0946e3f630f48b88d48f0f9d8c98df3249ba67d7a026aa5). The hunk's
    context is unchanged since 5b333c8a.
  - It carries the patch-borne NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ChildContainingBlockSeamWiringTest.kt`,
    as in wave 54.
  - Delivered as `tools/titan/results/wave55-cbb-android/seam-1.patch`, re-cut from
    `tools/titan/results/wave54-oof-layout/seam-1.patch` (whose header names a stale :1930 hunk offset) against 0d0d3a3d.
- **No other seam.** U2 is reached through SizingApplier (owned): no `ComponentRenderer.swift`, `ComponentRenderer.tsx`
  or `extract-fixture.mjs` hunk, and no wire change.

**Read-only:** `SizingExtractor.kt`, `ExactWidthOverflow.kt` (U2 reuses `ExactWidthOverflow.measureSpec`),
`PercentSizeClamp.kt`, `StyleApplier.kt`, `LayoutFacade.kt`, `OverflowApplier`, `BackdropModifier.kt`,
`AbsposInsetStretch.kt`, `VerticalTextFlowLayout.kt`, and `ContentBoxRelativeInflationTest.kt`:
- Its pin `percent stays fractional - documented pass-through` (:176-184) stays literally true, because
  `inflateForContentBox` is untouched.
- Its "a layout-time add is future work" phrase goes stale. That is for the docs / L7 lane, not edited here.

**Collision check against the wave's lane list:**
- No other lane names `DynamicValueResolver`, `ContainingBlockBands`, `SizingApplier` or `nested-border-radius-clip`
  (grep over every other lane's census / probe file in `wave55-plan/` at writing: none).
- L3's ComponentRenderer.kt seam (PositionedParentFlowSlot) may sit near the positioned-ancestor channel, which starts
  ~:1979, 28 lines below seam 1. Re-cut at integration if their hunk touches :1940-1990.
- L6 (effects clip) must not list clip-3 (§9 R5).

## 4. Units (one commit each; revert order U2 → U1; **U1 never without U2**, U2 never without U1)

**U1 — CBB-android, re-landed (5b333c8a, the revert of 8deddc19).**
- The rule: `ContainingBlockBands.subtracts(properties, wptCaptureMode)`.
  - Under WPT capture, an effective content-box (`SizingExtractor` → `SizingApplier.effectiveBoxSizing`, one definition)
    publishes its declared size.
  - A declared border-box still subtracts.
  - The dark stage always subtracts, and breadcrumbs `ContainingBlock[dark-stage-content-box-bands]` on an explicit
    content-box.
- Mechanism: CSS 2.1 §10.1 / §10.2 plus css-ui-3 §3.1.
- Predicts:
  - its 6 gating rows to GEOMETRY OK (device-measured at wave54-probe);
  - the three autopos `*-rtl` android rows to the iOS line;
  - the three semi-replaced android f rows up;
  - 006 unchanged.
- **Kept only with U2**: BACKLOG 0(g) says "the unit comes back … only with that gating key OK". U1 alone is exactly the
  picture the wave-54 rule 4 refused: clip-3 P 0.9668, GEOMETRY WRONG.

**U2 — PercentContentBoxBand: the in-flow percentage of a content-box box gets its band at layout time.**
- The pure half:
  - `owns(raw, cfg, bandPx, clampOwnsAxis)` is true iff ALL of these hold:
    - `cfg.wptCaptureMode`;
    - `cfg.boxSizing == CONTENT_BOX`;
    - `raw` is `Relative(_, PERCENT)`;
    - `bandPx != 0f`;
    - the PercentSizeClamp lane does not own the axis (`pctW` / `pctH == null`).
  - `targetPx(fraction, maxPx, bandPx) = round(maxPx × fraction + bandPx)`.
  - The `(child, reported)` pair comes from `ExactWidthOverflow.measureSpec`: overflow measures at the target and reports
    the incoming max, so the parent does not grow.
  - Placement offset 0 (start-anchored, `placeRelative(0, 0)`).
  - An UNBOUNDED axis is identity: FillNode parity, since `fillMaxWidth` is a no-op there too.
- The modifier half: `Modifier.percentFillPlusBand(fraction, band, horizontal)` over `Modifier.layout`.
- Breadcrumbs (no silent fallthrough):
  - `Sizing[dark-stage-percent-content-box-band]` when the dark stage declares content-box + % + band (0 committed
    fixtures, §5 C);
  - `Sizing[percent-clamp-content-box-band]` when the PercentSizeClamp lane owns such an axis (0 in the corpus).
- The call sites, both inside `applySizing`:
  - :123 `if (pctW != null) …` / NEW `else if (PercentContentBoxBand.owns(rawW, config, config.contentBoxInflateX, false))
    r = r.percentFillPlusBand(…, horizontal = true)` / then the existing `else if (!minWCalcOwnsAxis) …`;
  - the twin at :132 with `rawH` and `contentBoxInflateY`.
- Mechanism:
  - css-ui-3 §3.1 + CSS 2.1 §10.2 / §10.5: border box = percentage × containing-block size + bands.
  - css-overflow-3 §3: a box larger than its container lays out at its size and overflows; the container's clip,
    #outer's `overflow: hidden`, hides it.
  - The chain order StyleApplier.kt:702 → :758 → :786 puts #middle's own clip INSIDE the size modifier, so it clips the
    220×120 box.
- Predicts:
  - clip-3 android GEOMETRY OK on both probes;
  - P ≈ 0.999;
  - nothing else in the corpus can move (§5 B: one box).

**Pins** (verbatim `wave54-final` per-test IR; every mutation executed red → restore → green, sha256-verified, as wave 54's
`mutate.sh`):
- U1 (restored, re-executed):
  - WPT rows: autopos container → (100, 100); #outer → (200, 100); #middle → (200, 100); an explicit BORDER_BOX under
    WPT still subtracts;
  - the dark-stage 300 → 276 and autopos-dark 70×80 rows;
  - the 14-document census on `wave54-final`;
  - the seam source pin.
  - Mutations: always subtract (RED 3/32 in wave 54), never subtract (RED 3/32), WPT ignores declared border-box
    (RED 1/32), drop the named argument (RED).
- U2:
  - P-U2-1 `targetPx` / `measureSpec` on #middle verbatim:
    - width: max 200, band 20, fraction 1.0 → child 220, reported 200;
    - height: max 100, band 20 → 120 / 100;
    - fitting row: max 400, fraction 0.5, band 20 → 220 / 220.
  - P-U2-2 `owns`:
    - #middle under WPT → true;
    - + `BoxSizing BORDER_BOX` → false;
    - dark stage with an explicit CONTENT_BOX → false + breadcrumb;
    - #inner (band 0) → false;
    - `MaxWidth` present → false + clamp breadcrumb.
  - P-U2-3: an unbounded axis → identity (null spec).
  - P-U2-4: placement offset is 0, never `(reported − child) / 2`.
  - P-U2-5: a source pin, SizingApplier routes both axes through `percentFillPlusBand` before `applyWidth` / `applyHeight`.
  - P-U2-6: the corpus census (local-only, `assume` on `wave54-final`): `owns` selects exactly `0-172` W and H of clip-3
    among the 122 percentage axes.
  - Mutations:

    | mutation | turns red |
    |---|---|
    | M-U2a `+ bandPx` → `+ 0f` | P-U2-1 (220 → 200) |
    | M-U2b report the child size | P-U2-1, reported |
    | M-U2c drop the `wptCaptureMode` clause | P-U2-2, dark-stage row |
    | M-U2d drop `bandPx != 0f` | P-U2-6, 2 → 122 axes |
    | M-U2e delete the call site | P-U2-4 / P-U2-5 |
    | M-U2f centre the placement (Compose's `requiredWidth`) | P-U2-4 |

- **May run** (after the opening gate releases the host; focused only):
  `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*DynamicValueResolver*' --tests
  '*ChildContainingBlockSeamWiring*' --tests '*PercentContentBoxBand*' --tests '*ContentBox*' --tests '*BoxSizing*'
  --tests '*SizingMinMaxClamp*' --tests '*SizingOverflowWidth*' --tests '*WptBoxSizingDefault*')`.

## 5. Carrier census (`cbb-android.census.mjs wave54-final` → `cbb-android.census.out.txt`)

**A — U1's radius.**
- Method: the block threaded twice (frozen / WPT) through all 1435 documents. A box is kept when its incoming block
  differs AND it reads that block:
  - `oof`: a % size or opposing-inset stretch on a moved axis, or auto margins between declared insets;
  - `spc`: a % margin / padding / inset;
  - `vtext`: text under a vertical writing mode;
  - `dyn`: a PERCENT original or `%` expression.
  This is a wire-level mirror of 5b333c8a's JUnit census.
- **Result: 33 boxes in 14 documents. They are IDENTICAL to `expectations.json`
  `probeDecisions.reverted[CBB-android].carriersWithdrawn.captures.android`**:

  | capture stems | boxes | block, frozen → WPT |
  |---|---|---|
  | `wpt__css-flexbox__abspos__abspos-autopos-{htb,vlr,vrl}-{ltr,rtl}` (6) | `oof` | 70×80 → 100×100 |
  | `wpt__filter-effects__backdrop-filter-nested-border-radius-clip` | `oof` | 180×80 → 200×100 |
  | `wpt__filter-effects__backdrop-filter-nested-border-radius-clip-2` | `oof` | 180×80 → 200×100 |
  | `wpt__filter-effects__backdrop-filter-nested-border-radius-clip-3` | `oof` | 160×60 → 200×100 |
  | `wpt__filter-effects__backdrop-filter-nested-border-radius-clip-4` | `oof` | 144×64 → 160×80 |
  | `wpt__css-position__position-absolute-semi-replaced-stretch-{button,input,other}` | `oof`, px-inset stretch | 144×94 → 150×100 and 344×94 → 350×100 |
  | `wpt__css-gaps__flex__flex-gap-decorations-006` | `vtext` | 296×296 → 300×300 |

- Per platform: Android captures **14**, iOS 0, web 0, wire 0 (the converter and the extractor are untouched).

**B — U2's radius ("does the corrected unit's radius differ?" — it adds no new document).**
- The corpus carries **122 percentage axes** (Width 76 + Height 46; no InlineSize / BlockSize / PERCENT-original size
  exists).
- Classes: **120 have a zero band** and are untouched. **2 are in-flow carriers**:
  `wpt__filter-effects__backdrop-filter-nested-border-radius-clip-3 0-172 W 100% band 20 base 200` and `… H 100% band 20
  base 100`, parent BLOCK.
- There are 0 border-box-declared, 0 out-of-flow and 0 min/max-lane axes.
- So U2's carriers: **Android 1 capture (clip-3), already a U1 carrier**. The union stays 14 Android captures, 0 iOS,
  0 web, 0 wire.

**C — the dark stage.** Committed non-WPT fixtures (`fixtures/**` minus `fixtures/wpt/`) with `box-sizing: content-box` +
a % size + a padding / border band: **0**. The WPT gate on U2 is belt-and-braces, and every committed baseline stays
byte-identical by census as well as by construction.

**Must-not-move:**
- every web and iOS cell of the 14 documents;
- `css-images/cross-fade-target-alpha` ×3 (android P 0.9957, the in-flow % control; not in A or B);
- every Android cell outside the 14 documents (including `css-multicol/abspos-autopos-contained-by-viewport-000/-001`);
- the fixture net: exit 0 with no ledger edit.
- `filter-effects/backdrop-filter-basic-blur` (web P 1 · ios P 0.9526 · android f 0.904) is ring-fenced: in neither
  radius, report-only if it moves.

## 6. Predictions (from `wave54-final`; floors on geometry-gated rows = the 0.95 pass threshold)

| cell | from → to | tier | floor · geometry | direction / unit |
|---|---|---|---|---|
| `filter-effects/backdrop-filter-nested-border-radius-clip-3 android` | f 0.9574 → **P ≈ 0.999** (replay on Android's own re-laid probe pixels 0.9999; siblings at device probe 1 / 1 / 0.9999) | MED-HIGH (U2's layout path is new on device) | **0.95 · gating U2 (two probe keys)** | up; U2 + U1 |
| `…-nested-border-radius-clip android` | P 0.9649 → P ≈ 1 (probe 1) | HIGH (device) | 0.95 · gating U1 | up; U1 |
| `…-nested-border-radius-clip-2 android` | P 0.9689 → P ≈ 1 (probe 1) | HIGH | 0.95 · gating U1 | up; U1 |
| `…-nested-border-radius-clip-4 android` | P 0.977 → P ≈ 0.9999 (probe) | HIGH | 0.95 · gating U1 | up; U1 |
| `css-flexbox/abspos/abspos-autopos-{htb,vlr,vrl}-ltr android` | P 0.9966 DEGENERATE → P 0.9967 faithful (probe) | HIGH | 0.95 · gating U1 | up; U1 |
| `…/abspos-autopos-htb-rtl android` | P 0.9869 → P 0.9966 (probe), stays GEOMETRY WRONG = the iOS line | HIGH (device) | report | up; U1 |
| `…/abspos-autopos-{vlr,vrl}-rtl android` | P 0.9966 → P 0.9966 (probe Δ 0; picture changes to the iOS line) | HIGH | report | flat; U1 |
| `css-position/position-absolute-semi-replaced-stretch-button android` | f 0.9739 → f ≈ 0.9843 (probe) | HIGH | report | up, stays f; U1 |
| `…-semi-replaced-stretch-input android` | f 0.4117 → f ≈ 0.4555 | HIGH | report | up, stays f; U1 |
| `…-semi-replaced-stretch-other android` | f 0.4396 → f ≈ 0.4679 | HIGH | report | up, stays f; U1 |
| `css-gaps/flex/flex-gap-decorations-006 android` | f 0.8223 → f 0.8223 (probe Δ 0) | MED | report, undirected (exempt from rule 2) | U1 |

**Totals.** Android +1 (1128 → **1129/1362**). Web and iOS +0. Lost 0, unmeasured-now 0, newly measured 0.

**Picture-correct with no change of verdict:** 6 rows in full (autopos ltr ×3, nested-clip, -2, -4). Each is claimed only
when its own geometry key prints `→ GEOMETRY OK` on the closing run.

**If U2 falls at the device probe,** U1 goes with it (revert order U2 → U1, BACKLOG 0(g)'s condition). All 14 rows then
stay at wave54-final, and the closing control reads them as must-not-move. The alternative is keeping U1 with clip-3
pre-registered DEGENERATE (P 0.9668 on a wrong picture). That is a decision against the letter of 0(g), for the
orchestrator only, recorded in the PLAN if taken.

## 7. Geometry probe — keys, classes, exact expect strings, and the fakes

**(a) Re-used unchanged: `python3 tools/titan/results/wave54-plan/compose-wpt-content-box-cb.geometry.py <run>`.**
Each key's line must END in the expect string. Where a `contains` string is given, the line must also contain it.

| key | class | expect (line ends in) | contains |
|---|---|---|---|
| `abspos/abspos-autopos-htb-ltr android` | gating → U1 | `GEOMETRY OK` | `'green': (16, 88, 115, 187)` |
| `abspos/abspos-autopos-vlr-ltr android` | gating → U1 | `GEOMETRY OK` | `'green': (16, 88, 115, 187)` |
| `abspos/abspos-autopos-vrl-ltr android` | gating → U1 | `GEOMETRY OK` | `'green': (16, 88, 115, 187)` |
| `backdrop-filter-nested-border-radius-clip android` | gating → U1 | `GEOMETRY OK` | `'green': (26, 26, 225, 125)` |
| `backdrop-filter-nested-border-radius-clip-2 android` | gating → U1 | `GEOMETRY OK` | `'green': (26, 26, 225, 125)` |
| `backdrop-filter-nested-border-radius-clip-4 android` | gating → U1 | `GEOMETRY OK` | `'green': (26, 26, 185, 105)` |
| **`backdrop-filter-nested-border-radius-clip-3 android`** | **gating → U2 (then U1)** | **`GEOMETRY OK`** | **`'green': (36, 36, 235, 135)`** |
| `abspos/abspos-autopos-{htb,vlr,vrl}-rtl android` | report | `GEOMETRY WRONG (green (16, 88, 105, 187) vs ref (16, 88, 115, 187))` (U1's measured residue = the iOS line) | — |
| the 10 web rows | control | `GEOMETRY OK` | — |
| iOS autopos-ltr ×3, nested-clip ×4 | control | `GEOMETRY OK` | — |
| iOS autopos-rtl ×3 | control | `GEOMETRY WRONG (green (16, 88, 105, 187) vs ref (16, 88, 115, 187))` (verbatim, as wave 54) | — |

**(b) New, supplementary: `/opt/homebrew/bin/python3.12 tools/titan/results/wave55-plan/cbb-android.geometry.py <run>`.**
It keeps (a)'s bbox rule and adds `bands:` (the colour runs along y = 80 / x = 120 equal the ref's ±1 px) and `corner:`
(green in #inner's bottom-right 20×20 square = the ref's 252 ± 8).

| key | class | expect |
|---|---|---|
| `backdrop-filter-nested-border-radius-clip-3 android` | gating → U2 (then U1) | `GEOMETRY OK` |
| `backdrop-filter-nested-border-radius-clip-3 web` | control | `GEOMETRY OK` |
| `backdrop-filter-nested-border-radius-clip-3 ios` | control | `GEOMETRY OK` |

**Executed calibrations** ((a): `cbb-android.reused-geometry.out.txt`; (b) and the fakes: `cbb-android.geometry.out.txt`):
- `wave54-final`:
  - every gating key FAILS (7 in (a), 1 in (b));
  - every control PASSES;
  - the report keys FAIL against the U1 expect: htb-rtl prints `(66, 88, 135, 167)`, vlr / vrl-rtl print
    `(36, 108, 105, 187)`.
  - This is the `--self-test` shape.
- `wave54-probe` (= HEAD + U1 on Android):
  - U1's 6 gating keys PASS;
  - **both U2 keys FAIL**, (a) `green (36, 36, 215, 115) vs ref (36, 36, 235, 135)`, (b) the same plus
    `bands … grey[216..225] blue[226..235]` and `corner 0 vs ref 252`;
  - the 3 report keys print exactly the U1 expect.
  - So on the old unit's own picture the gate names U2. The refs self-check OK; exit 0 on both runs.

**The fakes it must reject** (built from Android's own pixels by `cbb-android.fakes.py`; scored by `.replay-score.mjs`):

| fake | what it models | (a) re-used rule | (b) supplementary | gate metric |
|---|---|---|---|---|
| F1 wave54-probe capture | U1 alone (the reverted picture) | WRONG (36, 36, 215, 115) | WRONG (green; bands; corner 0) | **P 0.9668** |
| F2 wave54-final capture | neither unit | WRONG (36, 36, 195, 95) | WRONG | f 0.9574 |
| F3 `fake-centered.png` | #middle sized by Compose's stock `requiredWidth/Height`, which CENTRES the oversized box | WRONG (26, 26, 225, 125) | WRONG | f 0.9414 |
| F4 `fake-unclipped.png` | band right, #outer's clip missing (grey at x 236..245 / y 136..145) | **OK — blind** | WRONG (`bands … grey[236..245]`) | **P 0.9841** |
| F6 `fake-double.png` | band added twice (#inner 220×120 cut square by #outer's clip) | **OK — blind** | WRONG (`corner 369 vs ref 252`) | **P 0.9956** |
| U1+U2 replay `android-clip-3.png` | the predicted picture | OK | OK | P 0.9999 |

F4 and F6 would be degenerate passes that the re-used instrument accepts, which is why (b) gates U2 next to (a). This
follows the wave-54 lesson: a hole in a gate instrument is fixed before the probe that reads it.

## 8. Device probe (before the closing gate)

**One section: `filter-effects`**, on the lane tree with U1 + U2 and seam 1 applied, as an orchestrator window on a
quiet host:

```
tools/titan/gate-driver.sh wave55-cbb-probe --sections filter-effects --skip-fixture-net
```

It carries U2's only carrier and U1's four nested-clip carriers.

Reads and what they must show:
1. `python3 …/wave54-plan/compose-wpt-content-box-cb.geometry.py wave55-cbb-probe`: the 4 nested-clip android lines end
   in `GEOMETRY OK` with the `contains` strings above. The autopos rows print MISSING (not in the section) and are read at
   the closing gate.
2. `/opt/homebrew/bin/python3.12 …/wave55-plan/cbb-android.geometry.py wave55-cbb-probe`: the android row ends in
   `GEOMETRY OK`.
3. `node tools/titan/results/wave52-gate/cells.mjs 'nested-border-radius-clip' wave54-final wave55-cbb-probe`:
   - clip-3 android ≥ 0.95, expected ≈ 0.999;
   - clip / -2 / -4 android ≈ 1;
   - web and iOS identical.
4. The control: filter-effects web and iOS are 48/48 decoded-pixel identical to wave55-open. Android differs on exactly
   the 4 nested-clip stems.

U1's css-flexbox / css-position / css-gaps carriers already have device captures for exactly this code
(`wave54-probe`, tree diff = the unit). Their re-read is the closing gate's.

**A FAIL on read 1 or 2 reverts U2, then U1.**

## 9. Risks, and what could not be determined

- **R1. Compose drawing an oversized in-flow placeable.**
  - U2 relies on what the wave-12 `ExactWidthOverflow` path proved for the 472-px css-break container: a placeable
    measured beyond its reported size paints start-anchored and unclipped, and an ancestor's clip trims it.
  - Here the trimming ancestor is #outer's rounded `overflow: hidden`, and a backdrop two-pass (`BackdropModifier`) runs
    beneath.
  - If the backdrop layer is bounded by #middle's reported 200×100 rather than its placed 220×120, the green could stop
    at 225 or show F4 / F6 shapes. Only the device probe decides; (b) names the shape.
- **R2. The intrinsic pass** (PercentSizeClamp's wave-51 lesson). A `Modifier.layout` answers intrinsics by re-running the
  lambda on unbounded constraints, which takes U2's identity arm, so the intrinsic equals FillNode's. The single carrier
  sits under definite-size block boxes with no IntrinsicSize or table ancestor, so no other document can reach it
  (census B).
- **R3. Mutual dependency.** U2 alone is WORSE than the base on clip-3:
  - the frozen channel gives a 160×60 green inside a 200×100 pink #inner, ≈ 10 000 px of fail ink against 4360 today;
  - U1 alone is the refused degenerate pass.
  - Land and revert them as a pair, in the stated order.
- **R4. Seam overlap with L3.** The positioned-ancestor channel begins ~28 lines below seam 1. `git apply --check` both
  hunks at integration and re-cut if they touch :1940-1990.
- **R5. Cross-lane dependency on the overflow clip.** clip-3's U2 picture depends on #outer's `overflow: hidden` +
  radius clip (OverflowApplier / the border-radius clip).
  - L6's effects-clip work, as far as its census shows (`android-paint-order.census.mjs`: clip-path / drop-shadow),
    does not touch it.
  - clip-3 android must still be declared an L2 carrier only. If any lane edits OverflowApplier, clip-3 becomes a
    shared cell attributed at the A/B.
- **R6. The autopos `*-rtl` residue** (10 px on BOTH natives).
  - With U1 Android draws exactly the iOS picture: green x 16..105, red x 106..115.
  - 10 equals border-left 20 − border-right 10 of the flex container, which is suggestive, but the cause (the RTL
    static-position edge) was **not traced**.
  - Not this lane: queue it for the BACKLOG as a both-natives item, with these three rows and the iOS twins as evidence.
- **R7. Interpreter.** The geometry scripts need PIL. On this host `/usr/bin/python3` lacks it (the wave-54 records ran
  `python3` from a shell where it resolved to a PIL interpreter). `geometry-gate.py` invokes `python3` from the
  expectations `cmd`, so the orchestrator must run it with the PIL interpreter first on PATH.
- **R8. The opening gate.** `wave55-open` was still running when this was written.
  - Every "from" above is `wave54-final`.
  - If `wave55-open` diverges on any of the 14 Android carriers or the controls, the predictions are restated against it
    before landing.
  - The 15 payload documents are byte-identical wave53-final = wave54-final = wave54-probe (`cmp`); confirm against
    wave55-open as well.
- **What could not be determined without a build or device:**
  - (i) The exact constraint values at #middle (maxWidth 200 / maxHeight 100) are inferred from the probe picture (#middle
    drawn x 26..225 / y 26..125) and the code path, not observed in a layout dump.
  - (ii) That the U1 abspos box is 200×100 and is clipped by #inner, rather than sized 180×80, is inferred from the
    channel arithmetic. The probe picture cannot tell the two apart because #inner clips either way. The census line
    `1-175 [oof] 160x60->200x100` is the wire-level mirror, not a device read.
  - (iii) The 0.9999 replay assumes Android's corner antialiasing at the new geometry matches its own antialiasing at the
    old one. The three siblings' device scores (1 / 1 / 0.9999) are the support; the device probe is the test.

STATUS: COMPLETE
