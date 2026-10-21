# counter-suffix-rows — wave-55 lane brief L5 (BACKLOG obligations 0(j) + 0(n))

Tree `campaign/applier-campaign` at HEAD `0d0d3a3d` (the wave-54 merge). Evidence run: `tools/titan/runs/wave54-final`. The opening
gate `wave55-open` was running on this host while this brief was written, so nothing was built, captured or run on a device;
every script below only READS files. Every number comes from a look-up that was run, tagged:

- **[cells]** `node tools/titan/results/wave52-gate/cells.mjs 'css-counter-styles/' wave54-open wave54-final` →
  `counter-suffix-rows.cells.txt` (122 lines; all 48 tests of the section, both runs).
- **[census]** `python3 tools/titan/results/wave55-plan/counter-suffix-rows.census.py wave54-final` →
  `counter-suffix-rows.census.txt` (1435 per-test IR documents, all 30 sections, scorer idiom for cells).
- **[geom]** `python3 tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py <run> [base]` (the new probe, §7) →
  `counter-suffix-rows.geometry.wave54-final.out.txt` (base wave54-final) and `.wave54-open.out.txt` (no base).
- **[fakes]** `python3 tools/titan/results/wave55-plan/counter-suffix-rows.replay.py` → `counter-suffix-rows.geometry.replays.txt`
  and the verdict matrix `counter-suffix-rows.geometry.replays.matrix.txt` (pictures in `counter-suffix-rows-replay/<tag>/`).
- **[replay]** `node tools/titan/results/wave55-plan/counter-suffix-rows.replay-score.mjs` → `counter-suffix-rows.replay-score.out.txt`,
  scored with the gate's own composed metric (`tools/titan/inject-wpt-block.mjs` `diffWebVsRef`); its six calibration rows
  reproduce [cells] exactly (counter-suffix web 1 / ios 0.9873 / android 0.989; css3-counter-styles-005 web 1 / ios 0.993 /
  android 0.9759).
- **[look]** the PNGs, opened: `counter-suffix-rows-replay/look-rows1-9-4x.png` (ref / ios / android, x20-100 y14-232 at 4×, guide
  lines at x33 / x57 / x64), `look-rtl-rows9-12-5x.png` (x95-155 y205-305 at 5×), `look-005.png` (css3-counter-styles-005).
- **[font]** fontTools over the bundled faces `apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf` and
  `runtimes/compose/src/main/res/font/inter_regular.ttf` (identical advances; one-off, quoted in §2).
- **[spec]** section numbers read from the W3C TR pages of css-counter-styles-3, css-lists-3 and css-writing-modes-4 (ToC fetched).

**Summary.** At `wave54-final` both native counter-suffix cells pass DEGENERATE (`tools/titan/results/wave54-gate/final/cell-review.json`,
second reader agrees, `cell-review.second-reader.json:219-236`). Three separate native mechanisms paint three wrong things, and one
of them reaches three more documents than the BACKLOG names:

1. **iOS draws the Hebrew suffix before the letter** (rows 3–4 `.א foo` / `.ב bar`). SwiftUI's marker `Text` resolves its paragraph
   direction from the first strong character (UAX #9 P2/P3), and `א` is strong R; css-lists-3 §3.1.1 makes the `::marker` an
   isolate (`unicode-bidi: isolate`) whose base direction is the ITEM's `direction` (css-writing-modes-4 §2.2), here ltr. Compose
   already paints `א.` (its unspecified `TextDirection` follows the ltr layout direction). Census: exactly **2 markers in 1
   document** have a first-strong direction that disagrees with an ltr item.
2. **Both natives keep the CJK marker 4 px left** (rows 5–6). The 4 px is `ListMarkerRow.gapPt` / `gapDp` = 4, the natives' stand-in
   for the suffix's trailing space, which the counter bake trims from every suffix (`tools/titan/counter-style-table.mjs:19-27`).
   `cjk-decimal`'s suffix is `"、"` with no trailing space (css-counter-styles-3 §6.1), so the ref's marker ends flush at the
   content edge x64, and the natives' ends at x60. It is not the ideographic comma's advance: that is 1 em = 16 px on all three.
   The same gap also sits between the 34 INSIDE `、` markers of `cjk-decimal/css3-counter-styles-001/-004/-005` and their text.
3. **SwiftUI ignores `font-variant-numeric: tabular-nums` on a text run.** The RTL `1` run carries `FontVariantNumeric
   TABULAR_NUMS` on the wire, and iOS paints the proportional `1` (x138-141, 4 px of ink) where the ref and Android paint Inter's
   tabular `one.tf` (x138-145). The iOS applier is an identity (`FontVariantNumericApplier.swift:14`). Radius: **6 components, 1
   document**.

Plus a docs unit: **five** stale comments, not the two 0(n) names. The wave-54 re-true patch for the first one applies clean but
carries a false sentence of its own (§2.D).

No flip is predicted (every carrier passes today). Four P→P cells become picture-correct by replay and are geometry-gated. One is a
pre-registered −0.0018 mover (css3-counter-styles-005 android). No web unit, no wire change, no seam in `extract-fixture.mjs` or
`ComponentRenderer.tsx`.

## 1. Targets (re-derived at `wave54-final`) and what the pictures show

| cell | wave54-open | **wave54-final** | label at wave54-final |
|---|---|---|---|
| `css-counter-styles/counter-suffix ios` | P 0.9802 | **P 0.9873** | DEGENERATE, rows 3–6 (`expectations.json` `stayDegenerateEvenIfPass`) |
| `css-counter-styles/counter-suffix android` | P 0.9547 | **P 0.989** | DEGENERATE, rows 5–6 |
| `css-counter-styles/counter-suffix web` | P 0.9818 | **P 1** | FAITHFUL — control only |
| `css-counter-styles/cjk-decimal/css3-counter-styles-005 ios` | P 0.993 | **P 0.993** | in the U2 radius (inside `、` marker) |
| `css-counter-styles/cjk-decimal/css3-counter-styles-005 android` | P 0.9759 | **P 0.9759** | in the U2 radius |
| `cjk-decimal/css3-counter-styles-001` / `-004` ios + android | — | unscored (`scoreExcluded: native-font-parity`; ssim 0.9653 / 0.9039 and 0.7098 / 0.6715 in the manifest) | capture carriers only |
| `counter-style-at-rule/disclosure-styles ios` | f 0.6127 | **f 0.6127** | U1 reaches it; byte-identity predicted |

In css-counter-styles only counter-suffix's two native cells moved between `wave54-open` and `wave54-final` (M′) [cells].

**The pictures** ([look]; ink columns on the `dark` core read, sum < 300; row k's line box is y16+24(k−1) … 16+24k):

- **Rows 1–2 (decimal).** All three read `1. foo` / `2. bar`, with the content edge at x64 on all three. The ref's `1` is Inter's
  TABULAR one (x47-53, with the foot serif); BOTH natives paint the proportional one (x49-52). The ref's `.` is at x57-58, the
  natives' at x56-57.
- **Rows 3–4 (`hebrew`, ltr list).**
  - Ref `א. foo`: letter x46-53, then the period x57-58.
  - **iOS `.א foo`:** the period at x48, then the letter at x51-57; row 4 the same (x48 / x51-58).
  - Android `א. foo`: x46-53 / x57.
- **Rows 5–6 (`cjk-decimal`).**
  - Ref: `一` x33-46 and `、` x49-51, flush against `foo` at x64.
  - **iOS:** x29-42 / x46-48.
  - **Android:** x29-42 / x45-48.
  - On both natives the whole marker is 4 px left and leaves a visible gap before `foo` (zoom: the blue x33 guide).
- **Rows 7–8 (`korean-hangul-formal`).** `일, foo` / `이, bar` within 1 px on both natives. This is the Compose arm that the stale
  banner says does not exist (§2.D).
- **Rows 9–12 (dir=rtl, baked runs since M′).** `foo .1 / bar .2 / foo .א / bar .ב` on all three. Row 9's `1`: ref x138-144,
  Android x139-145 (tabular, foot visible), **iOS x138-141** (proportional, no foot; `look-rtl-rows9-12-5x.png`).
- **css3-counter-styles-005** (25 px, `list-style-position: inside`, the item's own text is `一、`). The ref paints `一、一、` with the
  second pair at x268. Both natives paint it at x271 (`look-005.png`), and the inside marker is in the same place (x217) on all
  three. Android's two lines also sit 2–4 px lower (y104 / y129 vs the ref's y102 / y125) and its CJK strokes are lighter. Those
  are out of family and not traced.

## 2. Mechanism (traced; file:line at 0d0d3a3d)

### A. iOS marker base direction (rows 3–4)

- **Spec.**
  - css-lists-3 §3.1.1: the UA rule `::marker { unicode-bidi: isolate; font-variant-numeric: tabular-nums; white-space: pre;
    text-transform: none }`. It is in §3.1.1, not Appendix A as `wave54-plan/rtl-marker-bake.md` §4 cites.
  - css-writing-modes-4 §2.2: under `isolate` the content "is ordered as if inside an independent paragraph with the base
    directionality specified by the box's `direction` property". Its control-code table maps isolate + ltr to `LRI U+2066 … PDI
    U+2069` and isolate + rtl to `RLI U+2067 … PDI U+2069`.
  - First-strong (P2/P3) is the rule for `plaintext` only.
- **UAX #9 on `א.`.**
  - `א` is R. The `.` is CS, and W6 turns it into ON.
  - With base ltr (level 0): the `.` sits between R and eos = L, which disagree, so N1 does not apply. N2 gives it the embedding
    direction L. The run reads `א` then `.`, which is the ref.
  - With a first-strong base (paragraph level 1): the `.` sits between R and eos = R, and N1 makes it R. The two-character RTL run
    reads `.` then `א`, which is the iOS capture.
  - The `.` is the only character whose order depends on the base direction.
- **Code.**
  - All three iOS placements build the marker `Text` from the bare string, at
    `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`:
    - :4602 the inside overlay;
    - :4626 the outside hang (`ListMarkerOutsideHangLayout`);
    - :4650 the HStack.
  - All three call `Text(ScriptFallbackFonts.annotate(markerText, …))`. Nothing sets a base direction.
  - The capture shows SwiftUI's `Text` taking the first-strong direction although the environment is ltr: no `Direction` is
    declared on that `<ol>` or any ancestor ([census]: `R/ltr 2`). So `.environment(\.layoutDirection)` is not a lever.
  - The `Text` is built from an `AttributedString`, and SwiftUI `Text` does not read an NSParagraphStyle base writing direction.
    The isolate controls in the string are the Text-level lever.
- **Compose is right by construction.**
  - Its marker style (`lists/ListMarkerTextStyle.kt:75`, `forItem`) sets no `textDirection`, and no Compose file sets one
    (`grep textDirection runtimes/compose/src/main` = 0).
  - An unspecified direction resolves from `LocalLayoutDirection`, so the paragraph is strict ltr here.
  - Measured: Android rows 3–4 x46-53 / x57.
- **Census [census (U1-ios)].**
  - Of 217 baked list-item markers in 34 documents: L/ltr 167, none/ltr 48, R/ltr 2.
  - The 2 are `counter-suffix__0__1__0` `'א.'` and `__0__1__1` `'ב.'`.
  - No baked marker sits under an RTL item: counter-suffix's dir=rtl lists are baked with root `Direction LTR`.
  - The only `<li>` under an inherited `direction: rtl` are the 6 inside items of `counter-style-at-rule/disclosure-styles`.
    They are unbaked, and iOS falls back to the `ul` disc `•`, whose natural direction (no strong character, so ltr) disagrees with
    the item's rtl. The rule below wraps them too. A single neutral glyph has no order, so their pixels are predicted identical.

### B. The CJK marker 4 px left (rows 5–6), and the same gap inline

- **Spec.**
  - css-counter-styles-3 §3.4: the `suffix` descriptor, initial `"\2E\20"` (full stop THEN a space).
  - §6.1 defines `cjk-decimal` with `suffix: "\3001"` and no space. U+3001 is also the suffix of §6.2 hiragana / katakana (-iroha),
    §6.4 cjk-earthly-branch / cjk-heavenly-stem, §7.1.1 japanese-* and §7.1.3 chinese.
  - Every other predefined suffix ends in U+0020: `". "`, korean `", "`, ethiopic `"/ "`, the bullets `" "`.
  - css-lists-3 §3.4: the marker string is the counter representation "followed by the suffix"; §3.5 places an outside marker
    before the item's start edge.
  - (The lane task cites "§3.1.2 suffix". §3.1.2 is the `fixed` system; the suffix descriptor is §3.4.)
- **The ref's arithmetic** (wire + [look]).
  - The ref span `一、` is right-aligned in a 3em box that ends at the content edge x64 (`counter-suffix-ref.html`).
  - `、` takes the 1-em box x48-64, and its ink sits low-left in it: x49-51 / 52.
  - `一` takes x32-48, ink x33-46.
  - The ref's marker therefore ends flush at x64. On the RTL rows the same flush rule is visible on the wire: the li's border box
    ends at root x112 (`counter-suffix__0__4__0` Left 48 + Width 64), and the `.` run starts at Left 116.3. That is the 4.3 px
    U+00A0 of the `"1. "` marker, and a cjk marker has none.
- **The natives' arithmetic.**
  - `ListMarkerOutsideHang.place` puts the marker at `markerX = −(markerWidth + gap)`
    (`StyleEngine/lists/ListMarkerOutsideHang.swift:81`; the Kotlin twin `lists/ListMarkerOutsideHang.kt` likewise).
  - `gap` is `ListMarkerRow.gapPt = 4` (`ListMarkerRow.swift:144`, read at `ComponentRenderer.swift:4618`) and
    `ListMarkerRow.gapDp = 4.dp` (`ListMarkerRow.kt:70`, read at `ComponentRenderer.kt:4482`).
  - The marker's end edge therefore sits at x60: `、` x44-60 (ink 45-48 / 46-49) and `一` x28-44 (ink 29-42). **Δ = 4 px = the
    gap**, exactly.
- **The gap's origin.** It is the stand-in for the trailing U+0020 that `counter-style-table.mjs:19-27` trims from every predefined
  suffix ("both native runtimes already own that trailing gap themselves"). The bundled Inter's U+0020 is 4.5 px at 16 px [font],
  and the ref's U+00A0 measures 4.3 px (above).
- **Why the stand-in is wrong here.** For the one suffix family with NO trailing space, the stand-in adds space the ref does not
  have.
- **The advance is not the cause.** The `一`→`、` ink pitch is 16 px on the ref and on Android, and 17 on iOS. All three use a 1-em
  ideographic comma. The task's "the ideographic comma's advance vs the ref's" is equal on all three; the whole 4 px is the gap.
- **Inline.** The same constant is the iOS HStack `spacing:` (`ComponentRenderer.swift:4646`) and the Compose Row
  `.padding(end = ListMarkerRow.gapDp)` (`ComponentRenderer.kt:4665`) for an inside marker on an item with text.
  css3-counter-styles-005 measures it: the item text is at x271 on both natives against the ref's x268 (3 px at 25 px; the 1 px
  difference from 4 is the glyph's left bearing at 25 px).
- **The key: the marker STRING, not the type.**
  - [census (U2)]: **36 baked markers end in U+3001, in 4 documents**:
    - counter-suffix: 2, outside;
    - css3-counter-styles-001: 9, inside;
    - css3-counter-styles-004: 23, inside;
    - css3-counter-styles-005: 2, inside.
  - [census (U2-table)]: **71 `<li>`** sit under a U+3001-suffix type WITHOUT a baked marker:
    - cjk-earthly-branch 201-203 and cjk-heavenly-stem 204-206;
    - name-case-sensitivity (hiragana).
  - Those 71 are painted from the native tables. iOS `ListMarkerText.swift:45-49` returns `"一."`-style strings with a `.` suffix,
    and the earthly / heavenly styles are not modelled at all. That is a different, wrong-glyph defect.
  - A rule keyed on the TYPE would move those rows' wrong glyphs. A rule keyed on the painted string ending in U+3001 reaches the
    36 and nothing else.
  - Author suffixes are trimmed the same way (`tools/titan/counter-style-author.mjs:121`, `replace(/ +$/, '')`). A space-less author
    suffix (`descriptor-suffix`'s `a` is `","`) cannot be told from `", "` on the wire. Its items are unbaked anyway: the document's
    `counter-reset` bails the bake. This is stated, not modelled.

### C. iOS `tabular-nums` on a run (row 9)

- **Spec.** css-fonts-4 `font-variant-numeric: tabular-nums` (OpenType `tnum`). The bake stamps it on every RTL marker run, which
  is the UA rule of css-lists-3 §3.1.1 carried as an authored property.
- **[font].** In Inter, `one` = 6.51 px and `tnum` → `one.tf` = 10.375 px. `period` = 4.61 px and `tnum` → `period.tf` = 4.297 px.
  The wire's run widths are `1` = 10.38 and `.` = 4.3 (`counter-suffix__0__4__3` / `__0__4__2`): Chromium measured the tabular
  glyphs.
- **iOS.**
  - `StyleEngine/typography/font-variant/FontVariantNumericApplier.swift:14` is `_ = cfg; _ = agg`. Its header claims "SwiftUI
    Text has no direct API". That is false: `Font.monospacedDigit()` (iOS 15+) selects fixed-width digits.
  - The run's Text takes `PlaceholderLabel.labelFont(for:)` (`ComponentRenderer.swift:5430-5539`). `small-caps` is composed there
    from `TextConfig.smallCaps` (:5537, threaded `TypographyAggregate.smallCaps` → `StyleBuilder.swift:505`). That is the model
    path for one more flag.
- **Compose is already right.** `ComponentRenderer.kt:6955-6964` folds `FontVariantApplier.buildFontFeatureSettings` (`"tnum" 1`,
  `FontVariantApplier.kt:88`) into the placeholder's `TextStyle`. Measured: Android row 9 x139-145.
- **Census [census (U3-ios)].** **6 components DECLARE `FontVariantNumeric`, all `["TABULAR_NUMS"]`, all in counter-suffix**: the
  root-owned marker runs `__0__4__2..5` and `__0__5__2..3`, two of them digits. Neither native inherits the property
  (`Renderer/InheritedText.swift:43` `inheritedTypes` has no `FontVariantNumeric`), so the reach is exactly these 6.

**Not this lane: the LTR markers' proportional `1`** (row 1, both natives). Neither native applies the UA `::marker` `tabular-nums`
rule to its marker `Text`. The reach is ≥ 13 baked digit markers in 7 documents ([census (Q)]) plus every native-table decimal
marker, uncounted. It shifts every `N.` marker's ink, so it is queued with that census and the probe's report key `[L]` (§7). It
is not folded into U3.

### D. The stale comments (0(n), re-derived: five sites, not two)

1. **`StyleEngine/lists/ListMarkerOutsideHang.swift:33-41`.**
   - The comment says "The corpus's RTL rows still lack markers for an UPSTREAM reason". That is false since M′ (`b312c933`): the RTL
     markers are root-owned baked runs.
   - It also calls the RTL hang side "the inline-END side". css-lists-3 §3.5 puts an outside marker before the item's START edge,
     which for an rtl item is the physical right.
   - The wave-54 re-true `tools/titan/results/wave54-rtl-marker-bake/hunk-for-orchestrator-1.patch` was executed with
     `git apply --check -v` at 0d0d3a3d: **clean**. The file's sha256 `b50d9bd1…d47e27` equals the patch's recorded base.
   - **But its new text is false in one sentence.** It says "The corpus's only RTL list rows (css-counter-styles/counter-suffix,
     dir=rtl) never reach this hang". [census (D)] finds the corpus's only `<li>` under an inherited `direction: rtl`: the 6 of
     `counter-style-at-rule/disclosure-styles`. They are `list-style-position: inside`. counter-suffix's dir=rtl items no longer
     inherit rtl (bake root `Direction LTR`).
   - The unit re-cuts the patch with that sentence corrected (proposed text in §4 D).
2. **`runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerOutsideHang.kt:36-41`**, the Kotlin twin. It carries
   both claims of #1: "hangs an `outside` marker on the inline-END side under `direction: rtl`" and "The corpus's RTL rows still lack
   markers for an UPSTREAM reason". Not in 0(n); found by grep.
3. **`StyleEngine/lists/KoreanHangulFormal.swift:35-58`**, the TWIN DIVERGENCE banner and its three-edit port list. It says Compose
   answers `1.`.
   - False since wave 52 lane L6 T1 (`cdb8a845`): `lists/KoreanHangulFormal.kt` exists, and the three arms are present
     (`ListStyleConfig.kt:47` `KOREAN_HANGUL_FORMAL`, `ListStyleExtractor.kt:182`, `ListStyleApplier.kt:54`).
   - Device evidence: `wave54-final` Android rows 7–8 read `일,` / `이,` at x41-52 against the ref's x42-53.
   - (The lane task's "false since U1-android" is not the commit; 0(n) and the git log both say wave 52.)
4. **`StyleEngine/lists/ListMarkerText.swift:10-14`**: "`.koreanHangulFormal` … has no Compose arm yet, so the two natives answer
   differently". False since `cdb8a845`.
5. **`StyleEngine/lists/ListMarkerStyle.swift:43-47`**: "It has NO Compose twin yet … (KoreanHangulFormal.swift's TWIN DIVERGENCE
   banner)". False since `cdb8a845`.

Hand-off, not owned (it lives in `tools/titan/`, L7's tree): `tools/titan/counter-style-table.mjs:21-23`'s SUFFIX NOTE says
"Compose renders `Text("$marker ")` with a 4dp end padding". That is false since wave 28 lane MC (`ComponentRenderer.kt:4524`,
"the trailing space is GONE"). After U2 it should also say that a U+3001 suffix has no stand-in gap.

## 3. Ownership (disjoint; lane dir `tools/titan/results/wave55-counter-suffix-rows/`)

**Owned — iOS (`runtimes/swiftui/`):**
- NEW `Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift` (≤ 120 lines; U1).
- NEW `Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerSuffixGap.swift` (≤ 80 lines; U2o-ios, consumed by U2i-ios).
- `Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerRow.swift`: the `gapPt` doc comment only (:131-143, points at
  ListMarkerSuffixGap; 230 lines, stays < 300).
- `Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift`: its body and header (U3).
- `Sources/StyleConverterRuntime/StyleEngine/typography/TypographyAggregate.swift`: +1 field `tabularNums` beside `smallCaps` :68
  (U3).
- `Sources/StyleConverterRuntime/Renderer/StyleBuilder.swift`: +1 `TextConfig` field beside `smallCaps` :117 and +1 line beside
  :505 (U3; 1543 lines, call-site-only per the size rule).
- Comment-only (D): `StyleEngine/lists/ListMarkerOutsideHang.swift`, `StyleEngine/lists/KoreanHangulFormal.swift`,
  `StyleEngine/lists/ListMarkerText.swift`, `StyleEngine/lists/ListMarkerStyle.swift`.
- Tests: NEW `Tests/StyleConverterRuntimeTests/ListMarkerBidiTests.swift`, NEW `ListMarkerSuffixGapTests.swift`, NEW
  `FontVariantNumericTabularTests.swift`. The existing `ListMarkerAbsposItemRasterTests.swift:308` (`gapPt == 4`) stays green
  untouched.

**Owned — Compose (`runtimes/compose/src/`):**
- NEW `main/java/com/styleconverter/runtime/lists/ListMarkerSuffixGap.kt` (≤ 80 lines; U2o-android, consumed by U2i-android).
- `main/java/com/styleconverter/runtime/lists/ListMarkerRow.kt`: the `gapDp` KDoc only (:60-69; 290 lines, stays < 300).
- `main/java/com/styleconverter/runtime/lists/ListMarkerOutsideHang.kt`: comment-only (D).
- NEW `test/java/com/styleconverter/runtime/lists/ListMarkerSuffixGapTest.kt`. The existing `ListMarkerRowTest.kt:209`
  (`gapDp == 4.dp`) stays green untouched.

**Owned — evidence:** `tools/titan/results/wave55-plan/counter-suffix-rows.*` and `counter-suffix-rows-replay/` (this brief's files),
and the lane dir.

**Seams (patches `tools/titan/results/wave55-counter-suffix-rows/seam-<n>.patch`, one per unit that needs a hunk; never direct
edits).** Base sha256 values at 0d0d3a3d:
- `ComponentRenderer.swift` `417853cefdc7627fadc21003efa735d99d47ed0c545b6c1747d7292403fd26f3` (6291 lines);
- `ComponentRenderer.kt` `2445d9ea2674576fc0946e3f630f48b88d48f0f9d8c98df3249ba67d7a026aa5` (8488 lines).

The seams:
- **seam 1** (U1-ios): `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`, the marker branch's call
  `markerPlacement(child: child, markerText: markerText,` (:3973) and `private func markerPlacement(` (:4561). The hunk:
  - adds a defaulted parameter `itemIsRTL: Bool = false`;
  - passes `itemIsRTL: ListMarkerBidi.itemIsRTL(own: child.properties, inherited: childInherited)` at the call;
  - binds `let painted = ListMarkerBidi.painted(markerText, itemIsRTL: itemIsRTL)` once;
  - swaps the string argument of the three `Text(ScriptFallbackFonts.annotate(markerText,` (:4602 / :4626 / :4650) to `painted`.
  - `.listMarkerSymbol(type:markerText:fontSizePx:)` keeps the RAW `markerText`: its disc / circle / square string match must not
    see the controls.
  - About 8 lines.
- **seam 2** (U2o-ios): `ComponentRenderer.swift` `markerPlacement` hang branch, `ListMarkerOutsideHangLayout(gap:` (:4618):
  `ListMarkerRow.gapPt` → `ListMarkerSuffixGap.points(forMarker: markerText)`. 1 line.
- **seam 3** (U2i-ios): `ComponentRenderer.swift` `markerPlacement` HStack branch, `spacing: ListMarkerRow.gapPt` (:4646) → the same
  call. 1 line.
- **seam 4** (U2o-android): `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`
  `RenderListItemMarker`, the `ListMarkerOutsideHang.Hang(gapPx = …)` argument (:4482):
  `ListMarkerRow.gapDp.roundToPx()` → `ListMarkerSuffixGap.gapDp(marker).roundToPx()`. 1 line.
- **seam 5** (U2i-android): `ComponentRenderer.kt` `RenderListItemMarker` Row marker modifier, `.padding(end = ListMarkerRow.gapDp)`
  (:4665) → `.padding(end = ListMarkerSuffixGap.gapDp(marker))`. 1 line.
- **seam 6** (U3-ios): `ComponentRenderer.swift` `PlaceholderLabel.labelFont(for:)`, after `if textConfig.smallCaps { f =
  f.smallCaps() }` (:5537): adds `if textConfig.tabularNums { f = f.monospacedDigit() }`. 1 line.

No seam in `tools/titan/extract-fixture.mjs` or `apps/web-harness/src/sdui/ComponentRenderer.tsx`. No owned file consumes a
symbol that only a seam creates: the seams consume the owned symbols `ListMarkerBidi`, `ListMarkerSuffixGap` and
`TextConfig.tabularNums`. So the shared tree compiles with the lane's unseamed edits at every moment.

**Read-only:** `ListMarkerOutsideHang` geometry (unchanged; only its `gap` input moves), `ListMarkerTextStyle.kt`,
`ScriptFallbackFonts.swift`, `InheritedText.swift`, `counter-style-bake.mjs` / `counter-style-table.mjs` /
`counter-style-author.mjs`, `bidi-marker-bake.mjs`, and every web file.

**Disjointness with the other six lanes**, as named in the task:
- L5's files are all under `StyleEngine/lists/`, `runtimes/compose/…/lists/` and `typography/font-variant/`, plus the three
  call-site-sized U3 lines.
- **One possible collision:** L1 (SwiftUI typography line boxes) may claim `StyleBuilder.swift` or `TypographyAggregate.swift`. U3
  adds one field to each (plus one line in StyleBuilder). If L1 owns either file, U3's three lines ride as a hunk the orchestrator
  applies after L1's unit in landing order, or L1 carries them. They are additive declarations, so a textual merge is trivial.
- No other lane names a lists file.
- L7 owns the `counter-style-table.mjs` SUFFIX NOTE hand-off (§2.D) and any spec prose.

## 4. Units (each its own commit, separately revertible; land order U2o before U2i)

| unit | paths in its commit | seam | predicts | revertOrder |
|---|---|---|---|---|
| **U1-ios** | `lists/ListMarkerBidi.swift`, `ListMarkerBidiTests.swift` | 1 | counter-suffix ios rows 3–4 read `א. foo` / `ב. bar` (letter x46-47, period x57 ±2, ink rows ±1); arm alone [replay] 0.9873 → 0.9897; disclosure-styles ios byte-identical | [U1-ios] |
| **U2o-ios** | `lists/ListMarkerSuffixGap.swift`, `ListMarkerSuffixGapTests.swift`, `lists/ListMarkerRow.swift` (doc) | 2 | counter-suffix ios rows 5–6 `一` at x33, `、` x50-52, flush; arm alone 0.9903 | [U2i-ios, U2o-ios] |
| **U2i-ios** | (seam only) | 3 | css3-counter-styles-005 ios item text x271 → x267/268; [replay] 0.993 → 0.9971; -001/-004 ios captures move (unscored) | [U2i-ios] |
| **U2o-android** | `lists/ListMarkerSuffixGap.kt`, `ListMarkerSuffixGapTest.kt`, `lists/ListMarkerRow.kt` (KDoc) | 4 | counter-suffix android rows 5–6 `一` at x33, `、` x49-52; [replay] 0.989 → 0.9924 | [U2i-android, U2o-android] |
| **U2i-android** | (seam only) | 5 | css3-counter-styles-005 android item text x271 → x267/268; [replay] 0.9759 → **0.9741 (down 0.0018, pre-registered)**; -001/-004 android captures move (unscored) | [U2i-android] |
| **U3-ios** | `font-variant/FontVariantNumericApplier.swift`, `typography/TypographyAggregate.swift`, `Renderer/StyleBuilder.swift`, `FontVariantNumericTabularTests.swift` | 6 | counter-suffix ios row 9 `1` ink x138-145; arm alone 0.9875 | [U3-ios] |
| **D** | comment-only: `ListMarkerOutsideHang.swift`, `ListMarkerOutsideHang.kt`, `KoreanHangulFormal.swift`, `ListMarkerText.swift`, `ListMarkerStyle.swift` | — | no pixel and no byte of any build output beyond comments; `grep -n "still lack markers for an\|NO Compose twin yet\|no Compose arm yet\|TWIN DIVERGENCE — stated" runtimes/` → 0 hits in those five files | [D] |

All three iOS arms together: [replay] **0.993**; U1 + U2o alone 0.9927.

**U1-ios — `ListMarkerBidi`** (css-lists-3 §3.1.1, css-writing-modes-4 §2.2, UAX #9 P2/P3/N1/N2).
- `itemIsRTL(own:inherited:)`: the item's `Direction` (own, else inherited) is `RTL`.
- `naturalIsRTL(_:)`: UAX #9 P2, the first character of Bidi_Class L / R / AL.
  - Swift exposes no Bidi_Class, so the file uses this approximation: the first scalar whose general category is a letter, or one
    of LRM / RLM / ALM, decides. It is R/AL iff it lies in the Unicode default R/AL blocks U+0590–08FF, FB1D–FDFF, FE70–FEFF,
    10800–10FFF, 1E800–1EFFF (DerivedBidiClass.txt's `@missing` ranges).
  - Digits (EN/AN) and punctuation are not strong, as in P2.
- `painted(_:itemIsRTL:)` returns the string UNCHANGED when `naturalIsRTL == itemIsRTL`: the isolate is then the identity, because
  P2/P3 already pick the item's direction. Otherwise it returns `LRI + s + PDI` (ltr) or `RLI + s + PDI` (rtl), the §2.2 table.
- The conditional keeps every other marker byte-identical by construction: 215 of 217 baked markers, and every native-table
  marker under an ltr item.
- The header records the one call site deliberately NOT wrapped, as a TODO (no silent fallthrough): `ownListMarker`
  (`display: list-item` on a non-`<li>`). Its corpus reach under rtl is 2 boxes in `css-cascade/all-prop-001`, unmeasured.

**U2 — `ListMarkerSuffixGap`** (css-counter-styles-3 §3.4 / §6.1 / §6.2 / §6.4 / §7.1.1 / §7.1.3; css-lists-3 §3.4 / §3.5).
- `points(forMarker:)` (Swift) and `gapDp(marker)` (Kotlin) return **0 when the painted marker string's last scalar is U+3001** and
  `ListMarkerRow.gapPt` / `gapDp` (4) otherwise.
- The header states the stand-in, the trimmed space it stands for (`counter-style-table.mjs:19-27`), why the key is the string and
  not the type (the 71 unbaked rows of §2.B), and the author-suffix limit (`counter-style-author.mjs:121`).
- The two twins are byte-parallel and carry the same pins.

**U3-ios — tabular digits on a label run** (css-fonts-4 `font-variant-numeric`; Apple `Font.monospacedDigit()`).
- `FontVariantNumericApplier.contribute` sets `agg.tabularNums = true` when the keyword list holds `tabular_nums` (the wire's
  `TABULAR_NUMS`, lower-cased by `FontVariantKeywordParse`) or `tabular-nums`. It sets false for `proportional_nums` and leaves the
  value unset otherwise.
- `StyleBuilder` mirrors it into `TextConfig.tabularNums` beside `smallCaps`. Seam 6 composes `.monospacedDigit()` on the label
  font.
- Declared-only, not inherited (§2.C). The TODO kept in the header: the container-level font in `TypographyApplier.swift:184` is
  not wired (no corpus carrier).

**Pins (each mutation executed by the lane: red → byte-exact restore → green, sha256 logged).**
- **U1 (`ListMarkerBidiTests`).**
  - Pure:
    - `painted("א.", itemIsRTL: false) == "\u{2066}א.\u{2069}"`;
    - `painted("1.", false) == "1."`, `("일,", false) == "일,"`, `("一、", false) == "一、"`;
    - `painted("•", true) == "\u{2067}•\u{2069}"`, `painted("א.", true) == "א."`;
    - `naturalIsRTL("١.") == false` (AN is not strong).
  - Raster (Catalyst `ImageRenderer`, the existing `ListMarker*RasterTests` pattern): `Text(painted("א.", false))` has the period's
    ink right of the letter's, and the bare `Text("א.")` has it left (documents the defect).
  - Mutations: `painted` returns its input → the first pure pin and the raster pin go red. Drop the conditional (always wrap) → the
    identity pins go red.
- **U2 (`ListMarkerSuffixGapTests` / `ListMarkerSuffixGapTest`).**
  - `("一、") == 0`, `("一〇、") == 0`, `("1.") == 4`, `("일,") == 4`, `("א.") == 4`, `("") == 4`.
  - Mutation: drop the U+3001 test → red.
  - The existing `gapPt == 4` / `gapDp == 4.dp` pins stay green (the constant does not change).
- **U3 (`FontVariantNumericTabularTests`).**
  - The applier on `["tabular_nums"]` / `["tabular-nums"]` gives true; `["proportional_nums"]` gives false; absent leaves it unset.
  - `StyleBuilder` over `[FontVariantNumeric ["TABULAR_NUMS"]]` gives `textConfig.tabularNums == true`.
  - Mutation: restore `_ = cfg; _ = agg` → red.
  - A raster width pin (`Text("1")` wider with the flag) only if the test bundle registers Inter. Otherwise the device key `[T]`
    is the evidence (§9 R3).
- **D.** The grep above, plus `git diff --stat` showing comment lines only.
- **Seams.** Each patch applies clean on the lane base, and its focused pins are green with it applied.
- **Lane self-test.** `geometry-gate.py wave54-final --base wave54-final --lanes L5 --self-test` must HOLD once §7's entry is in the
  wave-55 `expectations.json`. The probe's executed output already shows it: gating 6 FAIL, control 10 PASS, report 2 FAIL.

**Proposed D text** for `ListMarkerOutsideHang.swift:33-41` (the re-cut of hunk-for-orchestrator-1.patch; the Kotlin twin's :36-41
gets the same facts in its own words):

> ## RTL — SwiftUI's Layout engine mirrors every `place(at:)` x about the layout bounds under an RTL layoutDirection (probe-verified —
> see FloatBlockLayout.placedX), so the physical `-(w + gap)` handed in here lands at `width + gap`: the inline-START side of an rtl
> item, where css-lists-3 §3.5 puts an outside marker, with no code of its own. No corpus `<li>` both inherits `direction: rtl` and
> hangs outside (census `tools/titan/results/wave55-plan/counter-suffix-rows.census.txt` (D)): counter-suffix's two `dir=rtl` lists
> are bidi-baked (bake root `direction: ltr`, items absolutely positioned, markers baked as runs owned by the root since wave-54 M′,
> `tools/titan/bidi-marker-bake.mjs`), and the only rtl items left (counter-style-at-rule/disclosure-styles, 6 `<li>`) are
> `list-style-position: inside`. So this branch moves no corpus cell — stated, not hidden.

For `KoreanHangulFormal.swift:35-58`, `ListMarkerText.swift:10-14` and `ListMarkerStyle.swift:43-47`, the replacement says: "TWIN —
closed in wave 52 (lane L6 T1, cdb8a845): Compose `lists/KoreanHangulFormal.kt` is the byte-parallel twin (arms
`ListStyleConfig.KOREAN_HANGUL_FORMAL`, `ListStyleExtractor` `"korean_hangul_formal"`, `ListStyleApplier.getMarker`); wave54-final
counter-suffix android rows 7–8 read `일,` / `이,` at the ref's x." The three-edit port list is deleted (it is done).

## 5. Carrier census (`counter-suffix-rows.census.py` → `counter-suffix-rows.census.txt`)

**Capture carriers (file stems `wpt__css-counter-styles__<stem>.png`):**

| unit | ios | android | web |
|---|---|---|---|
| U1-ios | `counter-suffix` (moves); `counter-style-at-rule__disclosure-styles` (identity predicted) | — | — |
| U2o-* | `counter-suffix` | `counter-suffix` | — |
| U2i-* | `cjk-decimal__css3-counter-styles-001`, `-004` (unscored), `-005` | same three | — |
| U3-ios | `counter-suffix` | — | — |
| D | — | — | — |

The union is **iOS 5 stems · Android 4 · web 0**, all in css-counter-styles.

**Wire carriers: 0.** No converter, extractor or bake change. Every per-test IR document of the next run must be byte-identical to
`wave55-open`'s, and any wire diff is a leak. No component is added or removed, so there is **no id shadow** and nothing is
renumbered.

**Carrier rules, as code** (each a section of the census):
- U1: a painted marker whose UAX #9 natural direction ≠ the item direction (2 baked + the 6 rtl `<li>` of disclosure-styles).
- U2: a marker string ending in U+3001 (36 markers / 4 documents; 2 outside, 34 inside).
- U3: a component declaring `FontVariantNumeric` (6 / 1).

**Must not move** (byte-identical decoded pixels against `wave55-open`):
- the other 43 css-counter-styles captures on iOS and the other 44 on Android ([cells]: 74 scored native cells in the section, of
  which the carriers are the 4 counter-suffix / -005 cells, plus disclosure-styles ios as an identity carrier);
- all 48 web captures;
- every capture outside css-counter-styles. U1 is identity off its 2 + 6 items by construction, U2 off its 36 markers, and U3 off
  its 6 components.
- **The counter-suffix rows outside each unit's rows** ([K], §7):
  - ios: y0-63, y160-207, y232-599 (rows 1–2, 7–8, 10–12 and below);
  - android: y0-111, y160-599;
  - web: the whole capture.

## 6. Predictions (from `wave54-final`; re-read against `wave55-open`, whose opening gate must show 0 movers)

| cell | from → to | tier | floor · geometry | direction | units |
|---|---|---|---|---|---|
| `css-counter-styles/counter-suffix ios` | P 0.9873 → **P ≈0.993** ([replay] all three arms; U1 0.9897 · U2o 0.9903 · U3 0.9875 · U1+U2o 0.9927) | MED-HIGH | **0.95** · `[H]` ios (U1), `[C]` ios (U2o), `[T]` ios (U3) gating; `[K]` ios control | up | U1-ios, U2o-ios, U3-ios |
| `css-counter-styles/counter-suffix android` | P 0.989 → **P ≈0.9924** ([replay] U2o) | MED-HIGH | **0.95** · `[C]` android gating (U2o-android); `[K]` android control | up | U2o-android |
| `css-counter-styles/cjk-decimal/css3-counter-styles-005 ios` | P 0.993 → **P ≈0.997** ([replay] 0.9971) | MED | **0.95** · `[I]` ios gating (U2i-ios) | up | U2i-ios |
| `css-counter-styles/cjk-decimal/css3-counter-styles-005 android` | P 0.9759 → **P ≈0.974** ([replay] 0.9741) | LOW-MED | **0.95** · `[I]` android gating (U2i-android) | **down 0.0018, pre-registered** (horizontally right; Android's 2–4 px vertical offset and lighter CJK face dominate the SSIM) | U2i-android |
| `css-counter-styles/counter-suffix web` | P 1 → P 1, byte-identical | HIGH | `[H] [C] [T] [I] [K]` web controls | flat | — |
| `css-counter-styles/counter-style-at-rule/disclosure-styles ios` | f 0.6127 → f 0.6127, byte-identical | MED | — (read the picture if it moves) | flat | U1-ios |
| `cjk-decimal/css3-counter-styles-001`, `-004` ios + android | unscored → captures move (item text −3/−4 px) | — | — (not scored: `native-font-parity`) | — | U2i-* |

**Expected movement.**
- **0 flips, 0 lost, 0 newly measured, 0 unmeasured-now.**
- Four P→P "picture-correctness" cells. Each is claimed ONLY if its gating keys print `→ GEOMETRY OK` on a DEVICE capture, at the
  probe and again at the closing gate. A replay never earns the label (wave-54 lesson).
- At `score-gate`'s 0.01 mover threshold, only counter-suffix ios (+0.0057) is close; the others are below it. The direction column
  is what `adjudicate` reads.

**DEGENERATE retirement.**
- Retire `stayDegenerateEvenIfPass` for `counter-suffix ios` iff `[H]`, `[C]`, `[T]` and `[K]` ios all print OK at the closing gate.
- Retire it for `counter-suffix android` iff `[C]` and `[K]` android print OK.
- The remaining residue on both is the row-1 proportional `1` (`[L]`, report, queued) and 1-px period offsets. The wave-54 brief
  already ranked those glyph-level, "not counted" (`wave54-plan/rtl-marker-bake.md` §10).
- If any one of a cell's units is reverted, its DEGENERATE label stays.

## 7. Geometry probe — `tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py <run> <base>` (written, run)

**Why a new probe.** `wave53-plan/lists-bakes.geometry.py` (`suffix_rows`, y208-303) and `wave54-plan/rtl-marker-bake.geometry.py`
(`m_rows`, y205-300) read only the four RTL rows. So `lists-bakes … counter-suffix ios` printed PASS at wave54-final
(`tools/titan/results/wave54-gate/geometry-final.out.txt:11`, class report) on a picture whose rows 3–6 are wrong. That probe
CANNOT see the period order. This one reads rows 1, 3–6 and 9 and the inside document. It imports
`wave53-plan/geometry_common.py`, is pure PIL (8 PNGs, +6 with a base), and exits 1 if a ref row fails.

**Keys, classes and EXACT expect strings** (every expect string is `GEOMETRY OK`, matched with `line.endswith`; no `contains`):

| key | class | what it asserts |
|---|---|---|
| `[H] counter-suffix ios` | **gating → U1-ios** | rows 3–4: exactly one letter blob (≥ 5 px wide) and one period blob (≤ 3 × ≤ 4 px) in x30-63; the period's left > the letter's right; letter left and period left at the ref's ±2 (x46/47, x57); letter ink rows at the ref's ±1 (U1's vertical risk) |
| `[C] counter-suffix ios` | **gating → U2o-ios** | rows 5–6: ≥ 2 marker blobs in x20-59, the last ≤ 7 px wide (the `、`); marker left at the ref's x33 ±1; right edge at the ref's ±2 (x51); item text left at the ref's ±1 (x64/65) |
| `[C] counter-suffix android` | **gating → U2o-android** | same |
| `[T] counter-suffix ios` | **gating → U3-ios** | row 9: the digit glyph (≥ 7 px tall) in x136-150 is ≥ 7 px wide, left at the ref's ±2 (x138) |
| `[I] cjk-decimal/css3-counter-styles-005 ios` | **gating → U2i-ios** | both item lines (fixed windows y95-120 / y121-151): marker left at the ref's x217 ±1, item text left (first ink ≥ x255) at the ref's x268 ±1 |
| `[I] cjk-decimal/css3-counter-styles-005 android` | **gating → U2i-android** | same |
| `[H] counter-suffix web`, `[H] … android` | control | as `[H]` (both right today) |
| `[C] counter-suffix web` | control | as `[C]` |
| `[T] counter-suffix web`, `[T] … android` | control | as `[T]` |
| `[I] cjk-decimal/css3-counter-styles-005 web` | control | as `[I]` |
| `[K] counter-suffix web` / `ios` / `android` | control | the must-not-move zones of §5 decoded-pixel identical to `<base>` (`wave55-open`) |
| `[L] counter-suffix web` | control | row 1's `1` ≥ 7 px wide (tabular) |
| `[L] counter-suffix ios`, `[L] … android` | **report** — expected WRONG until the queued UA-marker `tabular-nums` unit lands | same rule |

**The `expectations.json` entry** (for the wave-55 plan builder):
```json
{"cmd": "python3 tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py <run> <base>",
 "expect": {"[H] counter-suffix web": "GEOMETRY OK", "[H] counter-suffix ios": "GEOMETRY OK", "[H] counter-suffix android": "GEOMETRY OK",
            "[C] counter-suffix web": "GEOMETRY OK", "[C] counter-suffix ios": "GEOMETRY OK", "[C] counter-suffix android": "GEOMETRY OK",
            "[T] counter-suffix web": "GEOMETRY OK", "[T] counter-suffix ios": "GEOMETRY OK", "[T] counter-suffix android": "GEOMETRY OK",
            "[L] counter-suffix web": "GEOMETRY OK", "[L] counter-suffix ios": "GEOMETRY OK", "[L] counter-suffix android": "GEOMETRY OK",
            "[K] counter-suffix web": "GEOMETRY OK", "[K] counter-suffix ios": "GEOMETRY OK", "[K] counter-suffix android": "GEOMETRY OK",
            "[I] cjk-decimal/css3-counter-styles-005 web": "GEOMETRY OK", "[I] cjk-decimal/css3-counter-styles-005 ios": "GEOMETRY OK",
            "[I] cjk-decimal/css3-counter-styles-005 android": "GEOMETRY OK"},
 "gating": {"[H] counter-suffix ios": "U1-ios", "[C] counter-suffix ios": "U2o-ios", "[C] counter-suffix android": "U2o-android",
            "[T] counter-suffix ios": "U3-ios", "[I] cjk-decimal/css3-counter-styles-005 ios": "U2i-ios",
            "[I] cjk-decimal/css3-counter-styles-005 android": "U2i-android"},
 "report": ["[L] counter-suffix ios", "[L] counter-suffix android"]}
```

**Executed** ([geom], `counter-suffix-rows.geometry.wave54-final.out.txt`, base wave54-final; exit 0, every ref row OK):

| key | ref | web | ios | android |
|---|---|---|---|---|
| `[H]` | OK | OK | **WRONG** (row 3 period x48-48 before the letter x51-57 (suffix on the wrong side: first-strong RTL base direction)) | OK |
| `[C]` | OK | OK | **WRONG** (row 5 marker left x29 vs ref x33±1 (suffix gap: the "、" must end flush at the content edge)) | **WRONG** (same, x29) |
| `[T]` | OK | OK | **WRONG** (digit ink x138-141 4 px wide vs ref 7 (proportional '1': tabular-nums not applied)) | OK |
| `[L]` | OK | OK | WRONG (x49-52) | WRONG (x49-52) |
| `[K]` | — | OK | OK | OK |
| `[I]` | OK | OK | **WRONG** (line y95 item text left x271 vs ref x268±1 (inside "、" marker not flush)) | **WRONG** (same) |

So on wave54-final: **gating 6/6 FAIL · control 10/10 PASS · report 2/2 FAIL**, which is the `--self-test` shape. With no base
(`.wave54-open.out.txt`), wave54-open (before M′) prints the same `[H]` / `[C]` / `[L]` / `[I]` verdicts, plus `[T]` WRONG (no digit
ink) on all three, because no RTL marker was painted before M′: 10 WRONG lines. So `[T]`'s web / android controls also guard M′.

**Fakes it must reject, and the fixed picture it must accept** ([fakes]; matrix `counter-suffix-rows.geometry.replays.matrix.txt`;
every probe exit 0, i.e. the ref self-check held). Each fake turns exactly its own key WRONG. Fakes built from the base pictures
also show that picture's existing WRONGs.

| replay | built from wave54-final pixels | result |
|---|---|---|
| `ok-all` | iOS rows 3–4 marker zone from Android; rows 5–6 marker +4 px on both natives; iOS row-9 digit from Android; -005 item text −4 on both | **every key OK** except `[L]` natives (report) |
| `H-broken` | Android rows 3–4 from iOS | `[H]` android WRONG (and `[K]` android WRONG: zone y0-111 differs) |
| `H-nodot` | ok-all, iOS periods whitened | `[H]` ios WRONG (row 3 marker blobs: 1 letter / 0 period) |
| `H-flush` | ok-all, iOS rows 3–4 marker +4 (order right, no gap) | `[H]` ios WRONG (row 3 letter left x50 vs ref x46±2) |
| `H-low` | ok-all, iOS rows 3–4 marker 2 px down (U1's metric risk) | `[H]` ios WRONG (row 3 letter ink y75-83 vs ref y73-81±1 (marker baseline moved)) |
| `C-shift8` | Android rows 5–6 marker +8 | `[C]` android WRONG (marker left x37) |
| `C-nocomma` | ok-all, Android `、` whitened | `[C]` android WRONG (marker blobs 1) |
| `C-space-only` | Android rows 5–6 marker AND text +4 (gap kept, row shifted) | `[C]` android WRONG (item text left x69 vs ref x64±1) |
| `I-wholeline` | ok-all, -005 iOS marker and text −4 | `[I]` ios WRONG (marker left x213 vs ref x217±1) |
| `K-leak` | ok-all, iOS row 1 nudged 1 px | `[K]` ios WRONG (zone y0-63 differs (68 px)) |

**What the wave-54 counter-suffix probes print after L5** (derived from the `ok-all` pixels, not executed: those scripts read only
run directories):
- `rtl-marker-bake.geometry.py [M] counter-suffix ios`:
  - iOS row 9's marker ink becomes x133/134-145, so `min 134 ±2` and `max 144 ±3` still hold.
  - `lists-bakes.geometry.py counter-suffix ios` likewise: the band right edge is 145 against the ref's 145.
  - Both verdicts stay OK.
- **Their `contains: "rows 0-207 identical to <base>"` breaks by design.** Rows 3–6 move on iOS and rows 5–6 on Android. The
  wave-55 plan must drop that clause from the four counter-suffix ios / android keys of those two probes, or replace it with this
  probe's `[K]`. Otherwise L5's units FAIL the wave-54 controls.
- Web keeps "identical", since web has no unit.

## 8. Device probe — section **css-counter-styles** (one section; every L5 carrier lives there)

Every unit positions or shapes glyphs on a native: U1 reorders the marker, U2 moves the marker or the item text, U3 swaps a glyph
advance. None of that is predictable from a replay (wave-54 lesson), so all six land behind ONE device probe before the closing
gate. The lane writes it under "## ORCHESTRATOR WINDOW REQUESTS" in its note.

- **[W1] Device probe** (orchestrator window, quiet host, about 2.5 min): `tools/titan/gate-driver.sh wave55-l5-probe --sections
  css-counter-styles --skip-fixture-net` on the lane tree with all six units committed. If other lanes share the tree, read it with
  the union carrier set.
- **Read, in this order:**
  1. `python3 tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py wave55-l5-probe wave55-open`. All 6 gating keys must
     print `→ GEOMETRY OK`, all 10 controls OK, and `[L]` natives WRONG.
  2. `node tools/titan/results/wave52-gate/cells.mjs 'counter-suffix|css3-counter-styles-005|disclosure-styles' wave55-open
     wave55-l5-probe`. Expected: counter-suffix ios ≈0.993 / android ≈0.992 / web 1; -005 ios ≈0.997 / android ≈0.974; every
     carrier ≥ 0.95 and P.
  3. The other 43 iOS / 44 Android / 48 web css-counter-styles captures decoded-pixel identical to `wave55-open`, plus the
     disclosure-styles iOS capture.
  4. Per-test IR of all 48 documents byte-identical to `wave55-open` (no wire unit).
  5. OPEN the counter-suffix PNGs. iOS rows 3–4 must read `א. foo` / `ב. bar`; rows 5–6 on both natives `一、foo` / `二、bar` flush,
     with no gap; iOS row 9's `1` must have the tabular foot. Then open the -005 pair. A label is written only after the look.
- **Decisions (pre-registered).**
  - A gating key not OK reverts ITS unit (U2i before U2o).
  - A `[K]` control FAIL is diagnosed in the picture. The prime suspect is U1 (the controls' fallback line box) if the moved zone
    holds a marker. Revert U1 first and re-read.
  - A capture change outside §5's carrier set stops the lane until its cause is found in the picture.
  - U2i-android is kept iff `[I]` android is OK and the cell is ≥ 0.95 (its −0.0018 is pre-registered, not a loss).
  - **U1 fallback arm, if `[H]` ios is WRONG or `H-low`-shaped:** re-cut `painted` to prefix U+200E LRM (P2 then finds a strong L
    at once) instead of the LRI…PDI wrap, re-run the Catalyst raster pin, then re-probe. If both fail, revert U1 and record CoreText's
    behaviour.

## 9. Risks, and what I could not determine

1. **U1: CoreText's handling of the isolate controls (MED).** P2 must skip LRI…PDI, and no fallback face may be resolved for the two
   default-ignorable scalars. Inter's cmap has neither U+2066/2069 nor U+200E/200F [font]. If CoreText resolves a fallback line box
   for them, the baseline-aligned marker moves. `[H]`'s ±1 ink-row check catches that (`H-low`). The Catalyst raster pin gives a
   device-free first read. Not executed here (read-only window).
2. **U1 reaches disclosure-styles ios** through the rtl branch (6 `•` markers wrapped in RLI…PDI). Byte-identity is predicted
   (MED). The painted-disc path keeps the raw string, so the symbol choice cannot change; only a metric change could.
3. **U3: `Font.monospacedDigit()` on the bundled `Font.custom("Inter")` (MED).** It must map Apple's number-spacing selector to
   Inter's OpenType `tnum` (present: `GSUB` lists `tnum`, `one → one.tf`). If it is a no-op on device, `[T]` stays WRONG and U3 is
   reverted. The explicit alternative is a `UIFontDescriptor` with `featureSettings` (`kNumberSpacingType` /
   `kMonospacedNumbersSelector`) wrapped as `Font(UIFont)`, as the italic path already does (`ComponentRenderer.swift:5521-5524`).
   The greedy pre-break's `measurementUIFont` does not carry the feature. Harmless for the 6 `white-space: pre` single-glyph runs;
   stated in U3's header.
4. **U2i-android lowers a passing cell by 0.0018** ([replay] 0.9759 → 0.9741). The SSIM is dominated by Android's 2–4 px vertical
   offset on that document and its lighter CJK face. Neither was traced; both are out of family. It is pre-registered; the probe
   decides.
5. **Ownership: `StyleBuilder.swift` / `TypographyAggregate.swift` may belong to L1** (§3). U3's three lines then ride after L1's
   unit.
6. **The wave-54 `rows 0-207 identical` clause** on the counter-suffix keys of `rtl-marker-bake.geometry.py` and
   `lists-bakes.geometry.py` FAILs by design under L5 (§7). The wave-55 plan builder must re-cut it.
7. **Docs drift.** New test files move the SwiftUI (2212) and Compose (3486) counts in `CLAUDE.md` / `docs/STATUS.md`
   (`doc-staleness-check.sh`). No new `*Applier` file, so `coverage-audit.mjs`'s `real` counts do not move. BACKLOG 0(j) and 0(n)
   are rewritten from this brief by the orchestrator, including the L7 hand-off (`counter-style-table.mjs:21-23`).
8. **Could not determine.**
   - Whether CoreText honours LRI in P2 on the device's iOS (no device; §8 decides).
   - Whether `monospacedDigit` reaches Inter's `tnum` (§8).
   - Why the 71 cjk-earthly-branch / cjk-heavenly-stem / hiragana `<li>` carry no baked marker. Only the census is certain: they are
     unbaked, and the native tables mis-paint them (`ListMarkerText.swift:45-49` `"."` suffix; earthly / heavenly unmodelled).
     Queued, not this lane.
   - Why Android's -005 lines sit 2–4 px low.
   - The 1-px period offsets on rows 1–4 (natives x56-57 vs ref x57-58; the stand-in gap is 4 against the ref's 4.3-px U+00A0, and
     Inter's `period` 4.61 vs the tabular `period.tf` 4.30 the ref's marker uses). Below every key's tolerance, not targeted.
   - Whether the queued UA-marker `tabular-nums` unit (row 1, both natives, `[L]`) is worth its radius (≥ 13 baked digit markers in
     7 documents plus every native-table decimal marker).
9. **Citations corrected while re-deriving.**
   - The suffix descriptor is css-counter-styles-3 §3.4, not §3.1.2.
   - The `::marker` UA rule is css-lists-3 §3.1.1, not Appendix A.
   - The Korean twin closed at `cdb8a845` (wave 52 L6 T1), not "U1-android".
   - The 0(n) list is five sites, not two.
10. **A stray write outside this directory, for the orchestrator.** `tools/titan/results/wave53-plan/__pycache__/geometry_common.cpython-312.pyc`
    (untracked, NOT gitignored) appeared during this planning window, at 12:56. Any importer of `geometry_common.py` writes it: this
    probe before it set `sys.dont_write_bytecode = True` (it does now, and its output is byte-identical, md5 `fbf74b34…`), or a
    sibling lane's probe (cbb-android, positioned-parent-slot, ua-heading-margin and flex-zero-gap-rules all import it). Never stage
    it. Delete it after the gate releases the host.

STATUS: COMPLETE
