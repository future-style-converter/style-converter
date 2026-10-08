# Wave-53 family brief — `rtl-marker-bake` (BACKLOG 2(c⁴))

Gate of record `wave52-ship` (dev tip cdb8a845). All paths are relative to the gate worktree
`.claude/worktrees/trusting-bohr-bd6fbf`. Every pixel number below was measured with PIL on the 390×600 PNGs (frame
coordinates; the bake measures in the 358-wide ref content space, so bake x = frame x − 16). Every score was read with
`tools/titan/results/wave52-gate/cells.mjs` or re-derived with the gate's own `diffWebVsRef`. Nothing was built,
captured or run on a device. The opening gate was live, so no gradle, xcodebuild, vitest or puppeteer was run.
Files beside this brief:

- `rtl-marker-bake.census.mjs` writes `rtl-marker-bake.census.json`. It is a read-only census over the 1435 wave52-ship
  per-test IR docs, plus the carrier set and the replay numbers.
- `rtl-marker-bake.replay.py` and `rtl-marker-bake.replay-score.mjs` build the PNG replay and score it. The scorer
  reproduces the shipped 0.9818 / 0.9802 / 0.9547 exactly.

Ring-fenced: `filter-effects/backdrop-filter-basic-blur` is not bidi-baked, is not in any carrier set here, and is not touched.

## 1. Target cells

| cell | wave51-fix | wave52-calib | **wave52-ship** | review (cell-review.json) |
|---|---|---|---|---|
| css-counter-styles/counter-suffix ios | f 0.9285 | f 0.9285 | **P 0.9802** | DEGENERATE (both readers) |
| css-counter-styles/counter-suffix android | f 0.9051 | f 0.9051 | **P 0.9547** | DEGENERATE (both readers) |
| css-counter-styles/counter-suffix web | P 0.9818 | P 0.9818 | **P 0.9818** | not flipped (not reviewed). The picture is still wrong; see §2. |

The two native passes come from the wave-52 L6 T5 outside-marker hang on the LTR rows (arm `t5`, `ab-t5.txt`). The two
`dir=rtl` lists, a third of what the test asserts, still draw no marker. BACKLOG 0(b) says never to count these as fixed.

## 2. The picture (ref vs wave52-ship, ink bands at luma < 160)

Rows 1–8 (LTR lists, y21–204) are not part of this family. Web matches the ref there to the pixel. The natives are
close; iOS has its own residuals, listed at the end of this section. The defect is in rows 9–12, the two `dir=rtl` lists:

| rows y | ref | web | iOS | Android |
|---|---|---|---|---|
| 213-225 / 238-249 / 261-273 / 286-297 | text x103-127 + marker `.1` `.2` `.א` `.ב` at **x133-145** | marker at **x46-58** (left), text x103-127 | text x103-126, **no marker** | text **x151-175**, **no marker** |

- **Ref.** Each RTL item reads `foo .1`. The `<li>` border box ends at x128 (ol x16 + padding 48 + 64), and the marker
  hangs outside it on the right (the inline-start side in RTL). Its ink starts 5 px past the edge, which is the suffix
  space in visual-left position.
- **Web** paints the four RTL markers on the wrong side, in the LTR hang position (x46-58). Rows 9-12 have ink where the
  ref is blank and are blank where the ref has ink. It passes because the rest of the page is exact.
- **iOS.** The text is in the right place, but the marker is missing.
- **Android.** The marker is missing, and the text sits 48 px (one 3em padding) right of the ref.
- **iOS residuals in rows 1-8.** These are not part of this family and stay after the fix. The LTR Hebrew markers read
  `.א` / `.ב` with the period on the left: the iOS marker Text takes a first-strong base direction. The `一、` / `二、`
  markers carry a trailing gap (ink x29-49 vs ref x33-52). Both are iOS runtime defects in `lists/`.

## 3. The wire (`tools/titan/runs/wave52-ship/sections/css-counter-styles/per-test-ir/wpt__css-counter-styles__counter-suffix.json`)

The bake root, the RTL `<ol class="dec">`, ends with
`{"type":"ListStyleType","data":"decimal"},{"type":"Direction","data":"LTR"},{"type":"Width","data":{"type":"length","px":160}},{"type":"Height","data":{"type":"length","px":48}},{"type":"BoxSizing","data":"BORDER_BOX"},{"type":"UnicodeBidi","data":"NORMAL"},{"type":"TextAlign","data":"LEFT"},{"type":"Position","data":"RELATIVE"}`.
It also keeps `{"type":"PaddingRight","data":{"original":{"v":3,"u":"EM"}}}` and `PaddingLeft` (the same value).

The first item, verbatim:
```
{"id":"counter-suffix__0__4__0-624","name":"counter-suffix__0__4__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"percentage","value":150}}},{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":48}},{"type":"Top","data":{"px":0}},{"type":"Width","data":{"type":"length","px":64}},{"type":"Height","data":{"type":"length","px":24}},{"type":"BoxSizing","data":"BORDER_BOX"}],"slot":{"parent":"counter-suffix__0__4-623"},"meta":{"sourceTag":"li","markerText":"1."}}
```
Its only child is the baked text run `counter-suffix__0__4__0__0-625`:
`Position ABSOLUTE, Left {px:39.36}, Top {px:2}, Width 24.64, Height 20, LineHeight 20px, WhiteSpace PRE, Direction LTR, TextAlign LEFT, …, "text":"foo"`.
The other three items are the same, with `markerText` `"2."`, `"א."`, `"ב."` (the hebrew `<ol>` is `counter-suffix__0__5-628`).

Census (§6): exactly **4** components in the corpus carry `meta.markerText` together with `Position ABSOLUTE|FIXED`
(verified). All 4 are these items, and they are also the only out-of-flow list items in the corpus. The wire therefore
has the marker string. What it lacks is a marker that any runtime can place on the right side.

## 4. Mechanism (traced)

1. `bidiBakeTrigger` fires on `dir="rtl"`. `inPageBidiWalker` (`tools/titan/bidi-bake.mjs:648`) marks the `<ol>`
   bidi-affected (`el.hasAttribute('dir')`). `selectBakeRoots` (:401) makes the `<ol>` the root. `planBidiBake` (:806)
   turns each `<li>` into an absolutely positioned **box** (`boxProperties` :442) and its text into a run (`runProperties` :594).
2. `rootProperties` (:509-531) rewrites the root to `direction: ltr; unicode-bidi: normal; text-align: left`. The header
   says "the reorder is already in the coordinates". The comment is true for the TEXT runs. It is false for the
   `::marker`: the walker collects TEXT NODES only (`collectText` → `charBoxes`, :671-726), a `::marker` is not a DOM
   node, and `fixtureHasPseudo` (:932) bails only on `_pseudo`. The marker's geometry is therefore dropped silently.
   This is the bake's one silent fallthrough. Its side was the one fact that still depended on the `direction` the bake
   retired.
3. `counter-style-bake.mjs` runs after the bidi bake (`extract-fixture.mjs:12059`). It stamps `_markerText` "1." on the
   now-absolute `<li>` (it does not look at `position`), which becomes `meta.markerText`. The log line is
   `[bidi-bake: baked — 2 roots, 4 runs] [counter-bake: baked — 10 markers, 2 declined]`.
4. **iOS**:
   - `ComponentRenderer.swift:774` `isOutOfFlow(li)` is true, so the item goes to the positioned overlay (:794).
   - The marker branch (:3800-3960, `markerPlacement` :4531) iterates in-flow children only, so no marker is drawn.
   - BACKLOG 2(c⁴) records the executed Catalyst repro.

   **Compose**:
   - `ComponentRenderer.kt:3518-3520` sends `ABSOLUTE` children to `RenderAbsoluteChild` before the
     `RenderListItemMarker` arm is reached, so no marker is drawn.
5. **Web** renders a real `<ol>`, which now has `direction: ltr`, with an absolutely positioned `<li>`. Blink generates
   the outside `::marker` on the inline-start side of the inherited `ltr`, which is the left (x46-58). For the hebrew
   list the marker is the `<string>` `"א. "` (`runtimes/web/src/engine/lists/ListStyleTypeApplier.ts`,
   `RANGE_LIMITED_ADDITIVE_STYLES`), painted on the same wrong side. This is the failure a runtime fix would reproduce
   on the natives (BACKLOG 2(c⁴)), and web shows it today.
6. **Android +48 px.** The positioned-container `Box(Modifier.fillMaxSize())` (`ComponentRenderer.kt:3475/:3506`) is
   mounted inside the `<ol>`'s padding. Absolutely positioned children are therefore anchored at the CONTENT box, not
   the padding box. This is a documented approximation: `layout/position/PositionedParentFlowSlot.kt:74-76` says
   "insets resolve from the content-box corner … the same approximation the relative-parent Box branch already
   carries". So 48 + 48 + 39.36 puts the text at x151. The same offset shows on the two other padded bake roots:
   Android `bidi-lines-001` / `-002` runs sit +4 px right of ref/web/iOS (x33 vs x29, x36 vs x32). The size of that
   +4 is measured; how Android resolves `0.5ch` there was not traced.

## 5. The fix — two hunks, one file pair, no seam, no runtime code

Owns `tools/titan/bidi-bake.mjs` and `tools/titan/bidi-bake.test.mjs`, plus a probe script under
`tools/titan/results/wave53-rtl-marker-bake/`. No seam hunk is needed: `bidiBakeFixture(fixture, testRel)` keeps its
signature and its call site in `extract-fixture.mjs`. No runtime, converter or schema change is needed, because every
emitted property already rides the wire.

**Hunk M — bake the marker as positioned runs, like the text**

- **Probe first, on a quiet host.** For each kept list-item element (computed `display` contains `list-item`, there is
  a marker, `list-style-position` is outside or inside), take three things from Chromium:
  - the marker **string**, from `Accessibility.getPartialAXTree` on the `::marker` pseudo node (CDP
    `DOM.getDocument({depth:-1})` → `pseudoElements[pseudoType=marker]`);
  - the marker **box**, from `DOM.getBoxModel` on the same node (`DOMSnapshot.captureSnapshot` is an alternative source
    for both);
  - its **glyphs**: a hidden probe `<span>` placed at that box, carrying the string and the `getComputedStyle(li,
    '::marker')` font, colour, `font-variant-numeric`, `direction`, `unicode-bidi: isolate` and `white-space: pre`.
    It is fed through the walker's own `charBoxes` → `groupCharRuns`, which already trims edge whitespace and splits
    at level/adjacency boundaries.
- **Self-check.** The probe's advance must equal the box width within 0.5 px, or the test bails with
  `marker-probe-mismatch`.
- **Fallback if CDP does not surface the `::marker` box.** Place the box analytically on the inline-start border edge:
  RTL `left = li.right`, LTR `right = li.left`. Take its top from the item's first line run. This is cross-checked on
  the ref (RTL ink x133-145 against the edge at x128; LTR ref and web ink x46/47-58 against the edge at x64). State it
  in the header as a MODEL of Chromium, not its answer.
- **`planBidiBake`.** For a BOX (not a root, not hidden) with marker runs:
  - add `'list-style-type': 'none'` to the box props. Blink then stops generating the `::marker`. The counter bake
    skips the item: `'none'` is in `counter-style-table.mjs` `BULLET_STYLES`, so it is neither stamped nor counted as a
    decline. The natives get no `markerText`.
  - push the marker runs (owner = the `<li>`) with `runProperties` plus `font-variant-numeric` when it is not `normal`
    (it is `tabular-nums` from the UA `::marker` rule).
  - **split a run with no strong letter (digits and punctuation) into one run per grapheme.** `groupCharRuns` yields
    `"1."` as ONE rtl run, whose `.1` order would depend on each runtime's base-direction plumbing. That is unverified
    on SwiftUI, whose LTR `.א` bug shows it uses first-strong ordering. Single glyphs have no order to get wrong.
    `"א."` (strong R) stays one run with `direction: rtl`, which `needsExplicitRtl` already emits.
- A ROOT list item (the shape of `arabic-indic/css3-counter-styles-101..103`, an inside marker on an in-flow root) is
  NOT touched: every runtime paints its marker today.
- **Predicted wire.** Each of the 4 `<li>` gains `ListStyleType none` and loses `meta.markerText`. Six run components
  are added: `.` and `1`, `.` and `2`, `א.`, `ב.`. In li coordinates, row 1's runs sit at left ≈ 68.4 (= 64 + the
  suffix-space advance) to ≈ 82.6, top 2, height 20. The predicted log line is
  `[bidi-bake: baked — 2 roots, 10 runs] [counter-bake: baked — 6 markers, 2 declined]`.

**Hunk P — the bake root's padding is a spent input too (`rootProperties` + `applyBidiBakePlan`)**

- Emit `padding: 0`, deleting every `padding*` key first. The counter-suffix roots carry the shorthand `padding: "0 3em"`.
- Guard: keep the padding when the root's computed `background-clip` or `background-origin` is `content-box`, or
  `overflow` is not `visible`. None of the 6 padded roots trips the guard.
- **Why this is CSS-invariant.** The root has `box-sizing: border-box` with an explicit used width and height, and
  every descendant is absolutely positioned from the PADDING box (CSS 2.1 §10.1 item 4; css-position-3 containing
  block). The padding box is the border box minus borders, so zeroing the padding moves nothing on web or iOS.
- **What it changes on Android.** Android's content-box approximation becomes the padding box: the counter-suffix
  RTL text moves x151 → 103 and the marker lands at x133-145 instead of x181-193.
- **Without P, M is a loss on Android.** The replay of hunk M alone on Android scores **0.9466 (P → f)**. M must
  never land without P.

**Spec**:
- css-lists-3: the `::marker` box; `list-style-position: outside` puts the marker box outside the principal box on its
  inline-start side, which is the right side for `direction: rtl`; Appendix A UA `::marker { unicode-bidi: isolate;
  font-variant-numeric: tabular-nums; white-space: pre }`.
- css-counter-styles-3: the `suffix` descriptor (initial `". "`), §6 `decimal` / `hebrew`.
- css-writing-modes-4 `direction`, and UAX #9 (the isolated marker's levels).
- CSS 2.1 §10.1 (hunk P).

## 6. Blast radius (census: `rtl-marker-bake.census.json`)

- The changed code path is `bidi-bake.mjs`, which runs on exactly **17 bidi-baked docs** (`bidiBakeRootDocs`; the
  wire signature of `rootProperties` cross-checks against `fixtures/wpt/**` `_wpt.bidiBaked`).
- Those 17 docs have **40 scored wave52-ship cells, 31 P / 9 f**. The other three docs are unscored:
  `anchor-center-safe-rtl` and `attachment-local-positioning-3/-4` are `scoreEligible:false`, and `css3-counter-styles-102`
  is scored on web only.
- **Hunk M carriers.** Bake boxes that are list items: **4 components, 1 doc** (counter-suffix). The list items of the
  3 arabic-indic docs (9 + 24 + 2 = 35 `<li>`) are bake ROOTS and are excluded by construction.
- **Hunk P carriers.** Padded bake roots: **6 roots in 4 docs**:
  - `counter-suffix` (2 × 3em);
  - `bidi-lines-001` and `-002` (0.5ch each);
  - `anchor-center-safe-rtl` (2 × 10px, unscored).

`control-check` carrier set. Outside it, every composed capture of the run must be byte-identical:
- **allowed to change**:
  - `wpt__css-counter-styles__counter-suffix` on web, ios and android;
  - `wpt__css-text__bidi__bidi-lines-001`, `wpt__css-text__bidi__bidi-lines-002` and
    `wpt__css-anchor-position__anchor-center-safe-rtl` on **android only**.
- **must be byte-identical**:
  - web and ios of the three android-only docs above;
  - all three platforms of:
    - `wpt__css-counter-styles__arabic-indic__css3-counter-styles-101` / `-102` / `-103`;
    - `wpt__css-text__bidi__bidi-tab-001`;
    - `wpt__css-text__boundary-shaping__boundary-shaping-009`;
    - `wpt__css-text__hyphens__hyphenate-character-005`;
    - `wpt__css-writing-modes__bidi-plaintext-br-001`;
    - `wpt__selectors__dir-selector-change-003` / `-004`;
    - `wpt__selectors__dir-style-02a` / `-03a`;
    - `wpt__css-backgrounds__background-attachment-local__attachment-local-positioning-3` / `-4`;
  - every other capture in the corpus (the bake does not run there).

`control-check.mjs` hard-codes the wave-52 ch/li sets, so this list has to be fed to it.

## 7. Predictions

Replay = the ref's own marker pixels pasted at the predicted place, scored with `diffWebVsRef`. It is an upper bound
for the marker half.

| cell | wave52-ship | predicted (hunks M+P) | confidence |
|---|---|---|---|
| counter-suffix ios | P 0.9802 (DEGENERATE) | P ≈ 0.986 (replay 0.9888). The RTL rows gain `.1 .2 .א .ב` at x133-145. | HIGH stays P / MED magnitude |
| counter-suffix android | P 0.9547 (DEGENERATE) | P ≈ 0.985 (replay 0.9900). RTL text x151 → 103, markers at x133-145. | MED-HIGH |
| counter-suffix web | P 0.9818 | P ≥ 0.995 (replay 1.0000). Left-hung markers gone, right ones present. | HIGH P / MED magnitude |
| bidi-lines-002 android | P 0.9534 | P ≈ 0.975-0.98 (replay of a crude 4 px shift: 0.9818) | MED |
| bidi-lines-001 android | f 0.8934 | ≈ 0.95-0.96 (replay 0.9629): **f → P possible** | MED-LOW |
| anchor-center-safe-rtl android | unscored | moves (padding 10 px zeroed) | — |
| **M without P**, counter-suffix android | P 0.9547 | **f ≈ 0.947 (replay 0.9466) — pre-registered LOSS** | HIGH |

**Must not move** (byte-identical; HIGH unless stated):
- every §6 must-be-identical capture;
- web and ios of bidi-lines-001 and bidi-lines-002 (MED-HIGH for iOS: it anchors at the padding box today, so a zeroed
  padding should be invisible);
- counter-suffix rows y0-208 on every platform. The LTR lists are not baked; check this as a crop of pre vs post.

Web moving is by design. Its RTL rows are wrong today, and no bake-side fix can leave web byte-identical while fixing
the natives. The only web obligation is that P holds, and the replay says it rises.

## 8. Verification plan

**Unit pins (`node --test tools/titan/bidi-bake.test.mjs`)**

Each pin is built from the VERBATIM wave52-ship geometry: root rect x0 y192 w160 h48; li rects x48 y192/216 w64 h24;
text runs at +39.36 / +39.03. In bake coordinates the measured marker box starts at x112.

- **V1** `planBidiBake` on the counter-suffix walk with marker facts:
  - the li box props gain `'list-style-type':'none'`;
  - the marker runs' union left in li coordinates is ≥ 64 (RTL inline-start side);
  - the runs for row 1 are `['.', '1']` and those for row 3 are `['א.']` with `direction: 'rtl'`.

  Mutations: (m1) drop the `none` → red. (m2) place the marker at `li.left − w` → red. (m3) disable the per-grapheme
  split → `['1.']` → red.
- **V2** The arabic-indic-101 shape (li = root, inside marker) yields no marker run and no `list-style-type` change.
  Mutation: emit for roots too → red.
- **V3** `rootProperties` and `applyBidiBakePlan` on the verbatim `counter-suffix__0__4` fixture node (`padding: "0 3em"`)
  give `padding: '0'` and no other `padding*` key. A root with computed `background-clip: content-box` keeps its padding.
  Mutation: remove the line → red. Remove the guard → red.
- **V4** Cross-bake: `applyBidiBakePlan`, then `counterBake.bakeCounterStyles(fixture, html)` on counter-suffix, gives
  `stamped 6, declined 2` (today 10 / 2), and no `_markerText` on the 4 RTL items. Mutation: omit the `none` → stamped
  10 → red.
- **V5** Probe self-check: a box/advance mismatch > 0.5 px returns `{ bail: 'marker-probe-mismatch' }`.

**Quiet host, before the closing gate**

- **(a) Probe.** Run the probe script on counter-suffix. Acceptance: the strings are `"1. " "2. " "א. " "ב. "`; the
  row-1 marker box starts at x112±0.5 (bake coordinates); the glyph runs land at frame x133-145.
- **(b) Extraction.** Re-extract the 17 bidi-baked tests with `--bidi-bake`, then diff the fixtures:
  - only counter-suffix (the 4 li, 6 new runs, 2 root paddings) and the padding keys of bidi-lines-001/-002 and
    anchor-center-safe-rtl change;
  - the other 13 fixtures stay byte-identical.
- **(c) Convert.** Run `:converter:run` on counter-suffix: the IR carries the new runs with existing property types only.
- **(d) Replay.** Re-run `rtl-marker-bake.replay*.{py,mjs}` after (a) with the measured box.

**Probe sections for the closing gate**: css-counter-styles, css-text, css-writing-modes, selectors, css-anchor-position
and css-backgrounds (all 17 carrier docs).
- `watchlist.txt` lines: `css-counter-styles/counter-suffix`, `css-text/bidi/bidi-lines-001 android`,
  `css-text/bidi/bidi-lines-002 android`.
- Run `control-check` with the §6 set.
- counter-suffix ios and android must be re-reviewed against the picture, not the score: rows 9-12 must show `foo .1`
  with ink x133-145.

## 9. Risks and recommendation

1. **The CDP `::marker` box and AX name are unverified.** They could not be run while the gate was live. The analytic
   inline-start-edge fallback is backed by the ref (x128 → x133) and by the LTR web/ref rows (x64 → x58). If neither
   the AX name nor `DOMSnapshot` yields the string, take it from a dry run of `bakeCounterStyles` on a CLONE: it is the
   same string the natives already paint, and that bake already bails on `::marker` and `reversed` documents.
2. **M without P is a pre-registered Android loss** (replay 0.9466). Ship both or neither.
3. **Hunk P's web/iOS invariance is argued, not captured.** A leak on bidi-lines web/iOS is a finding. The fallback is
   P-narrow (only roots that host a marker run), which gives up the bidi-lines Android gains.
4. **The natives may ignore `font-variant-numeric` on a run.** A proportional `1` inside a tabular box would be off by
   ≤ 1-2 px. LOW.
5. **The iOS cell keeps the out-of-family residuals** (LTR `.א` period order, cjk trailing gap). They cap it below
   ≈ 0.99 and belong to a lists runtime lane.
6. **A comment to hand over (no binary change).** `StyleEngine/lists/ListMarkerOutsideHang.swift:33-41` says "inline-END
   side" (it is inline-start) and "no `meta.markerText` reaches them" (false: the item is out of flow; the wire has
   the string). The orchestrator should re-true it, and BACKLOG 2(c⁴), when this lands.

**Recommendation: GO.** This is a small lane (S–M, about half a day after the gate). It owns two files, has no seam and
no runtime code, and turns 2 DEGENERATE passes into picture-correct ones. It fixes web's wrong-side markers, and as a
measured side effect hardens bidi-lines-002 android and possibly flips bidi-lines-001 android.
