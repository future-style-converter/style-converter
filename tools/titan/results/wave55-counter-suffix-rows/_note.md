TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-reverify-L5
TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-fix-L5
TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-build-L5

# wave55 L5 counter-suffix-rows — builder lane note

Lane contract: `tools/titan/results/wave55-plan/PLAN.md` "### L5 · counter-suffix-rows" (brief
`counter-suffix-rows.md`; generated contract `expectations.json` lanes["L5-counter-suffix-rows"],
`landing-manifest.json` rows 21–25, 27, 28 and contingency row 30). Base tree 611c9f93 (= dev 0d0d3a3d + the
wave-55 plan + the opening-gate records). Built in the throwaway worktree on the `TREES:` line (provisioned per
PLAN §0: both `local.properties`, the `node_modules` symlink, Swift builds with `-derivedDataPath <wt>/.dd`). Nothing
was committed; the shared tree was written only in this lane dir. No device, simulator, emulator, Chromium,
gate-driver, test-all or feed step ran.

## What changed and why (file:symbol)

Three native mechanisms paint three wrong things on css-counter-styles/counter-suffix, and the second one also
spaces the 34 inside `、` markers of css3-counter-styles-001 / -004 / -005 (brief §2):

- **A — iOS draws the Hebrew suffix before the letter** (rows 3–4 `.א foo`). SwiftUI's `Text` takes its
  paragraph direction from the first strong character (UAX #9 P2/P3); css-lists-3 §3.1.1 makes `::marker` an
  isolate whose base is the ITEM's `direction` (css-writing-modes-4 §2.2). Fix:
  `runtimes/swiftui/…/StyleEngine/lists/ListMarkerBidi.swift` `ListMarkerBidi.painted(_:itemIsRTL:)` returns the
  marker UNCHANGED when its natural direction (`naturalIsRTL`, P2 with isolate runs skipped) equals the item's
  (`itemIsRTL(own:inherited:)` = `ListStyleUaRule.isRtl(InheritedText.merge(…))`, the package's one direction
  decoder), else wraps it in LRI/RLI … PDI. Seam 1 (`ComponentRenderer.swift` `markerPlacement`) gains
  `itemIsRTL: Bool = false`, binds `let painted` once and hands it to the three marker `Text`s;
  `.listMarkerSymbol` keeps the raw string; `ownListMarker` is the stated TODO (header).
  Reproduced device-free: Catalyst renders the bare `"א."` as period x10-12 / letter x16-33 (the defect) and the
  painted string as letter x9-26 / period x30-32 (`ListMarkerBidiTests`); through the REAL `markerPlacement`
  hang (`ListMarkerBidiSeamRasterTests`, the verbatim counter-suffix hebrew `<ol>` at 32 px): with seam 1 the
  period is after the letter (letter x80-96, period x101-104), without it before (x81-83 / x88-104, M6).
- **B — both natives keep the CJK marker 4 px left** (rows 5–6) and the inside `、` marker 3–4 px from its text:
  the 4 pt / 4 dp `ListMarkerRow.gapPt` / `gapDp` is the stand-in for the suffix's trimmed trailing space, and
  `cjk-decimal`'s suffix `"、"` has none (css-counter-styles-3 §3.4 / §6.1). Fix: `ListMarkerSuffixGap`
  (`…/StyleEngine/lists/ListMarkerSuffixGap.swift` `points(forMarker:)`; Compose
  `…/runtime/lists/ListMarkerSuffixGap.kt` `gapDp(marker)`, byte-parallel) — 0 when the RAW marker string's last
  scalar is U+3001, the stand-in otherwise (the key is the string, not the type: brief §2.B). Seams 2 / 4 feed it
  to the outside hang, seams 3 / 5 to the inside HStack spacing / Row end padding. Catalyst, through the real
  renderer: the counter-suffix cjk-decimal row's marker ink starts at x33 (the ref's x33; the stand-in control row
  x29), and the -005 inside item text at x267 (ref x268; control row and today's device x271).
- **C — SwiftUI ignored `font-variant-numeric: tabular-nums`** (row 9's proportional `1`). Fix:
  `…/typography/font-variant/FontVariantNumericApplier.swift` `contribute` sets `TypographyAggregate.tabularNums`
  (`tabular_nums` / `tabular-nums` → true + `touched`; `proportional_nums` → false; else unset);
  `Renderer/StyleBuilder.swift` mirrors it into `TextConfig.tabularNums` (+2 lines, beside `smallCaps`); seam 6
  composes `Font.monospacedDigit()` in `PlaceholderLabel.labelFont(for:)`. Declared-only (not inherited on either
  native). Catalyst with the harness Inter faces registered for one isolated test process
  (`probe-monospacedDigit.out.txt`): the verbatim `1` run inks x10-16 (7 px, Inter's `one.tf` with its foot)
  tabular vs x9-12 (4 px) proportional — the ref's 7 px vs today's iOS 4 px (`[T]`). The same probe shows the
  substitute face the UNREGISTERED test bundle resolves for `.custom("Inter")` ignores the modifier, which is
  why the seam raster pin uses `ui-rounded` (the system design path) — stated in that file.
- **D — comment-only.** The five stale sites of brief §2.D, plus the same false claims where they recur in the
  files D owns: `ListMarkerOutsideHang.swift` (the RTL paragraph — the wave-54
  `hunk-for-orchestrator-1.patch` re-cut with its false "never reach this hang" sentence replaced by the census
  fact; the `place` / Layout `gap` docs now name `ListMarkerSuffixGap`; the `placeSubviews` "end side" comment),
  `ListMarkerOutsideHang.kt` (the RTL paragraph, `@param gapPx`, `@param rtl`, the mirror comment),
  `KoreanHangulFormal.swift` (TWIN DIVERGENCE banner → "TWIN — closed in wave 52 (lane L6 T1, cdb8a845)", the
  three-edit port list deleted; device evidence re-measured: android rows 7–8 marker ink x41-57 vs ref x42-58),
  `ListMarkerText.swift` (header exception + the `.koreanHangulFormal` arm comment), `ListMarkerStyle.swift`
  (enum comment), and (C7) `tools/titan/counter-style-table.mjs` SUFFIX NOTE (Compose's `Text("$marker ")` is
  gone since wave 28; `StyleListApplier` → `ListStyleApplier`; a U+3001 suffix has no stand-in gap —
  ListMarkerSuffixGap). Every D sentence is true with U2o on the tree whether or not U2i is (U2i reverts alone).

## Revert units — exactly the paths each unit commit holds

### U1-ios (revertOrder [U1-ios]; landing row 21)
- `unit-U1-ios.patch`: NEW `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift`
  (158 lines), NEW `runtimes/swiftui/Tests/StyleConverterRuntimeTests/ListMarkerBidiTests.swift`.
- `seam-1.patch`: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` (the call's
  `itemIsRTL:` argument at :3973, the defaulted parameter + `let painted` at :4561, the three marker `Text`s at
  :4602 / :4626 / :4650; +18 −3) + patch-borne NEW
  `runtimes/swiftui/Tests/StyleConverterRuntimeTests/ListMarkerBidiSeamRasterTests.swift`.

### U2o-ios (revertOrder [D, U2i-ios, U2o-ios]; row 22)
- `unit-U2o-ios.patch`: NEW `…/StyleEngine/lists/ListMarkerSuffixGap.swift` (63 lines), NEW
  `…/Tests/StyleConverterRuntimeTests/ListMarkerSuffixGapTests.swift`, `…/StyleEngine/lists/ListMarkerRow.swift`
  (`gapPt` doc only, +6).
- `seam-2.patch`: `ComponentRenderer.swift` (:4618 `gap:` → `ListMarkerSuffixGap.points(forMarker: markerText)`,
  +4 −1) + patch-borne NEW `…/Tests/StyleConverterRuntimeTests/ListMarkerSuffixGapHangRasterTests.swift`.

### U2i-ios (revertOrder [U2i-ios]; row 23; consumes U2o-ios)
- no unit patch (seam only).
- `seam-3.patch`: `ComponentRenderer.swift` (:4646 HStack `spacing:` → the same call, plus the marker-row
  banner's "STILL DEFERRED … the gap is the fixed ListMarkerRow.gapPt" sentence at :3824-3827 re-trued; +6 −3) +
  patch-borne NEW `…/Tests/StyleConverterRuntimeTests/ListMarkerSuffixGapInsideRasterTests.swift`.

### U2o-android (revertOrder [D, U2i-android, U2o-android]; row 24)
- `unit-U2o-android.patch`: NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerSuffixGap.kt`
  (66 lines), NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/lists/ListMarkerSuffixGapTest.kt`,
  `…/runtime/lists/ListMarkerRow.kt` (`gapDp` KDoc only, +6; 296 lines).
- `seam-4.patch`: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`
  (:4482 `gapPx = … ListMarkerSuffixGap.gapDp(marker).roundToPx()` + its comment, fully qualified — no import
  hunk; +8 −3) + patch-borne `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt`
  (the C10 module line `"lists/ListMarkerSuffixGap.kt"` + 3 comment lines, cut on HEAD at the PINNED anchor after
  `"images/ImageCandidateChain.kt",` :167) + patch-borne NEW
  `runtimes/compose/src/test/java/com/styleconverter/runtime/lists/ListMarkerSuffixGapHangSeamTest.kt`.

### U2i-android (revertOrder [U2i-android]; row 25; consumes U2o-android)
- no unit patch (seam only).
- `seam-5.patch`: `ComponentRenderer.kt` (:4665 `.padding(end = …ListMarkerSuffixGap.gapDp(marker))`, plus
  RenderListItemMarker's KDoc "STILL DEFERRED … still the fixed [ListMarkerRow.gapDp]" sentence at :4201-4204;
  +6 −3) + patch-borne NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/lists/ListMarkerSuffixGapRowSeamTest.kt`.

### U3-ios (revertOrder [U3-ios]; row 27, after L1 NB-ios)
- `unit-U3-ios.patch`: `…/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift` (16 → 60; 71 after fix-r1's header paragraph),
  `…/StyleEngine/typography/TypographyAggregate.swift` (+3: the field and its 2-line doc; 241 lines),
  `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/StyleBuilder.swift` (+2 lines exactly, each with a
  trailing comment: the `TextConfig.tabularNums` field and the mirror), NEW
  `…/Tests/StyleConverterRuntimeTests/FontVariantNumericTabularTests.swift`.
- `seam-6.patch`: `ComponentRenderer.swift` (after `if textConfig.smallCaps { f = f.smallCaps() }` in
  `labelFont(for:)`: `if textConfig.tabularNums { f = f.monospacedDigit() }` + 4 comment lines; anchored by
  content) + patch-borne NEW `…/Tests/StyleConverterRuntimeTests/FontVariantNumericTabularRasterTests.swift`.

### D (revertOrder [D]; row 28, after U2o on both natives — C7)
- `unit-D.patch` (comment-only, 133 changed lines, 0 non-comment — `d-comment-only-check.out.txt`):
  `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerOutsideHang.swift`,
  `runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerOutsideHang.kt`,
  `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/KoreanHangulFormal.swift`,
  `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerText.swift`,
  `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerStyle.swift`,
  `tools/titan/counter-style-table.mjs`.

### U1-ios-LRM (CONTINGENCY — not a unit until landed by `land-units.sh --contingency L5:U1-ios-LRM`)
- `unit-U1-ios-LRM.patch` (cut ON TOP of unit-U1-ios + seam-1): `…/StyleEngine/lists/ListMarkerBidi.swift`
  (`painted` wraps in LRM … LRM / RLM … RLM instead of LRI/RLI … PDI; the `lri`/`rli`/`pdi` constants become
  `lrm`/`rlm`) and `…/Tests/StyleConverterRuntimeTests/ListMarkerBidiTests.swift` (the two wrap pins carry the
  marks). Delivered pre-emptively so the stage-1 trigger needs no fix lane; see "Contingency" below.

## Census (the builder's own, behavioural)

Script `census-l5.py` (written for this lane, independent of the brief's `counter-suffix-rows.census.py`: it models
both renderers' decisions — in-flow children only, baked-or-table marker string, ListMarkerResolver's fold with
unmodelled keywords keeping the running value, the overlay / hang / row placement, the item's own-else-inherited
`direction`) → `census-l5.out.txt` over all 1435 wave54-final per-test IR documents (= wave55-open's, wire
1435/1435):
- every iOS marker row: 451 (217 baked, of which 215 painted unchanged by U1; 234 table rows, all unchanged except
  the 6 rtl disclosure-styles bullets);
- U1-ios 8 components / 2 documents (counter-suffix `א.` `ב.` hang; disclosure-styles 6 rtl `•` rows);
- U2o 2 / 1 on each native (counter-suffix rows 5–6); U2i 34 / 3 on each native (-001 9, -004 23, -005 2);
  U2-overlay 0 (no U+3001 marker takes the inside overlay); U3-ios 6 / 1 (counter-suffix root-owned RTL runs);
- **cross-check against `expectations.json`: every unit's capture set identical on every platform;
  captureCarriers web 0 / ios 5 / android 4 identical; wireCarriers 0 identical. Census delta: grew 0, shrank 0 —
  no plan regeneration needed.** The same script over wave55-open (`census-l5.wave55-open.out.txt`) prints the
  identical census (its header line aside), as the opening gate's wire identity 1435/1435 requires.

## Geometry probe self-test (re-run on wave54-final)

- `geometry-gate.py wave54-final --base wave54-final --lanes L5 --self-test` → HOLDS, exit 0: gating 6 / 6 FAIL,
  control 16 / 16 PASS, report 2 / 2 FAIL (`geometry-selftest.base-wave54-final.out.txt`); `--base wave55-open`
  → identical, HOLDS (`…base-wave55-open.out.txt`).
- The probe read was the plan's ROUND-4 version, uncommitted on the shared tree while this lane ran (the orchestrator's
  parallel fix-r4: `counter-suffix-rows.geometry.py` sha256 bd2f001ae6a8ea5be86c1980bff9f2a65cf068ecab5726a8c4a6e84d130d3027,
  `expectations.json` 980542d01c1755007c5409a9b07d5d398508dcaf9687078b4437855502103839, `geometry-gate.py`
  39b9a0d7b1acd7df2af651d0c6fbd8f2c28530083e0ade9433956a6ccaacef59); re-run after delivery, byte-identical output,
  HOLDS. L5 changed nothing in the plan directory.
- **Observation for stage 1 (D39, the round-4 twin `ink:` reads):** `[H]` natives now compare rows 3–4's marker zone
  against the base's own two glyphs moved by whole px, and `[T]` ios compares row 9's digit window against ANDROID's
  tabular `1`. U1 re-lays the glyphs out (CoreText places the letter and the period at new, possibly fractional, x) and
  U3 draws CoreText's `one.tf`, not Android's raster, so a CORRECT iOS picture can differ from those twins by
  antialiasing alone. The plan already routes such a line to the stage-1 step-8 look (`stage1Decision`, D39); this lane
  flags that both iOS gating keys of its U1 / U3 are the ones exposed.
- The task's literal `--base wave54-open` form exits 1 with 5 control violations
  (`…base-wave54-open.out.txt`: `[T]` web / android and `[K]` ×3 read zones that wave-54 M′ moved between
  wave54-open and wave54-final). That is the base's age, not a probe defect: the plan's self-test is against
  wave54-final (PLAN preamble item 4) and the stage-1 read is against wave55-open.

## Pins and their executed mutations

| unit | pin | mutation (bracketed below) | red → green |
|---|---|---|---|
| U1 | `ListMarkerBidiTests` (pure: LRI/RLI wraps, identity rows, idempotence, P2 cases, item direction; raster: bare `.א` vs painted `א.`; isolate does not move the ink rows) | M1 `painted` returns its input; M2 always wrap | M1 4 red / 11; M2 5 assertions in 2 tests red; both green after restore |
| U1 seam 1 | `ListMarkerBidiSeamRasterTests` (verbatim hebrew `<ol>` through `markerPlacement`) | M6 seam-1 hunks reverse-applied | red (period x81-83 before letter x88-104) → green |
| U2 Swift | `ListMarkerSuffixGapTests` | M3 drop the U+3001 test | red (+ both seam rasters red) → green |
| U2o seam 2 | `ListMarkerSuffixGapHangRasterTests` (verbatim cjk-decimal `<ol>` + one ZWSP control row; precondition: the control paints byte-identical ink) | M7 seam-2 hunk reversed | red (both rows x29) → green (x33 / x29) |
| U2i seam 3 | `ListMarkerSuffixGapInsideRasterTests` (verbatim -005 list + control row) | M8 seam-3 hunks reversed | red (both x271) → green (x267 / x271); the hang pin stays green — the seams fail independently |
| U2 Kotlin | `ListMarkerSuffixGapTest` | M4 drop the U+3001 test | red → green |
| U2o seam 4 | `SeamReachabilityTest` module line (C10) | M10 seam-4 hunk only reversed → STILL GREEN (seam 5 also names the module); M10b ComponentRenderer.kt at base bytes → red `[lists/ListMarkerSuffixGap.kt]` | the M10 gap is real: repaired with `ListMarkerSuffixGapHangSeamTest` (M10': red → green) |
| U2i seam 5 | `ListMarkerSuffixGapRowSeamTest` (source-level, comments/strings stripped — the SeamReachabilityTest idiom) | M11 seam-5 hunks reversed | red → green |
| U3 | `FontVariantNumericTabularTests` (applier ×3, StyleBuilder over the verbatim `__0__4__3` properties) | M5 identity applier restored | 8 assertions in 4 tests red → green |
| U3 seam 6 | `FontVariantNumericTabularRasterTests` (`1111`, tabular wider) | M9 seam-6 hunk reversed | red (53 = 53) → green (68 vs 53) |
| U1-ios-LRM (contingency) | `ListMarkerBidiTests` with the LRM / RLM wrap pins, on FULL + `unit-U1-ios-LRM.patch` | M12 `painted` returns its input | 4 red / 11 → green |
| D | `d-comment-only-check.py` on `unit-D.patch` (0 non-comment lines, 0 stale phrases) | negative controls: the check exits 1 on `unit-U2o-ios.patch` (code) and on a unit-D copy with "the inline-END side" re-added | `d-comment-only-check.out.txt` |

The mutation log (every `MUTATING` line written BEFORE its edit, every `RESTORED` after the byte-exact restore,
post == pre) is the "## Mutation log" section at the end of this note, in execution order: 14 MUTATING / 14
RESTORED, every one paired (M1–M12 with M10' and M10b).

## Device-free evidence beyond the pins (Catalyst, `probe-monospacedDigit.{swift,out}.txt`)

A throwaway probe class (never delivered; its source is kept as `.swift.txt`), run ALONE because it registers the
harness Inter faces for its own test process:
- R3 (`monospacedDigit` on the bundled Inter): `.custom("Inter-Regular")` registered 9-56 → 11-87 with the modifier;
  the real label path on the verbatim `1` run 7 px tabular vs 4 px proportional. The unregistered `.custom("Inter")`
  substitute ignores it (11-65 both) — irrelevant on device, where the harness registers Inter.
- R1 / R2 (isolate controls in CoreText with Inter at 16 px): `א.` / `ב.` raw vs LRI-wrapped — box 15×20 / 14×20 and
  ink rows y6-15 unchanged (only the columns reorder); the rtl `•` raw vs RLI-wrapped — box 9×20, ink identical (the
  disclosure-styles identity prediction); `一、` unchanged by construction.
Catalyst is not the device: `[H]` / `[T]` / `[K]` on wave55-pre decide.

## revertStatesCompiled

**fix-r1:** every state below was re-run on the RE-CUT patches; the `revertStatesCompiled:` lines of record are in
"## Fix pass fix-r1" (the builder's lines are kept below as history, prefixed `(builder cut)`).

Each state = clean 611c9f93 checkout of the worktree + the kept patches in landing order (`git checkout -- .`,
`git clean -fd` on the owned dirs, `git apply`), then compile + the focused suites of the touched platform(s)
(Swift: the 7 L5 classes present + ListMarkerTests, ListMarkerOutsideHangTests, ListMarkerOutsideHangRasterTests,
ListMarkerAbsposItemRasterTests, ListMarkerInFlowItemRasterTests, ListMarkerEmptyItemTests,
ListMarkerEmptyItemRasterTests, ListMarkerSymbolTests, ListItemMarkerGateTests, FidelityWave2Tests; Compose:
`*ListMarker*`, `*SeamReachabilityTest*`, `*KoreanHangulFormal*`, `*ListStyle*`; node: counter-style-bake /
-author tests). A platform a state does not change is byte-identical to the full state's.
(builder cut) revertStatesCompiled: FULL [U1-ios, U2o-ios, U2i-ios, U2o-android, U2i-android, U3-ios, D]: Swift 113/113 green · Compose 99/99 green · node 44 pass / 0 fail / 1 skipped (the skip is pre-existing: the same count with 611c9f93's counter-style-table.mjs, state [all − D])
(builder cut) revertStatesCompiled: chain U1-ios → [all − U1-ios] (= the C12 dry-run state: U1-ios reverted with U2i-ios applied): Swift 102/102 green · Compose / node = FULL (unchanged platforms)
(builder cut) revertStatesCompiled: chain D (step 1 of both U2o chains too) → [all − D]: Swift 113/113 · Compose 99/99 · node 44/0/1 — green
(builder cut) revertStatesCompiled: chain D → U2i-ios → [all − D − U2i-ios]: Swift 112/112 green
(builder cut) revertStatesCompiled: chain D → U2i-ios → U2o-ios → [all − D − U2i-ios − U2o-ios]: Swift 106/106 green
(builder cut) revertStatesCompiled: chain U2i-ios → [all − U2i-ios]: Swift 112/112 green
(builder cut) revertStatesCompiled: chain D → U2i-android → [all − D − U2i-android]: Compose 98/98 green (compileDebugKotlin executed)
(builder cut) revertStatesCompiled: chain D → U2i-android → U2o-android → [all − D − U2i-android − U2o-android]: Compose 93/93 green
(builder cut) revertStatesCompiled: chain U2i-android → [all − U2i-android]: Compose 98/98 green
(builder cut) revertStatesCompiled: chain U3-ios → [all − U3-ios]: Swift 108/108 green
(builder cut) revertStatesCompiled: contingency [FULL + U1-ios-LRM]: Swift 113/113 green; its chain [U1-ios-LRM, U1-ios] reverse-applies in the export tree, and [FULL + U1-ios-LRM − U1-ios-LRM] is FULL (byte-compared)

Export-tree dry-runs (`patch-series-check.out.txt`): the 11 patches apply in landing order on 611c9f93 and
reproduce the worktree byte-for-byte; every revertOrder chain reverse-applies on the full series, including C12
(U1-ios reverted with U2i-ios applied) and the contingency's chain.

## Predictions per target cell (from wave54-final = wave55-open)

| cell | from → to | confidence | gating key |
|---|---|---|---|
| `css-counter-styles/counter-suffix ios` | P 0.9873 → P ≈0.993 (brief replay, all three arms; fix-r1: the replay grafted only row 9's `1` — U3's five other tabular glyphs are not modelled, ≤ 0.31 px each toward the ref's advances, not re-derived) | MED (fix-r1; was MED-HIGH) | **fix-r1 AMENDED:** as the plan stands, `[T]` ios and `[K]` ios print WRONG BY ZONE (U3's `period.tf` / `two.tf`; skeptic defect 1) and `[H]` ios prints WRONG `ink:` twin-only, all four ios keys also `residual:` — see "## Fix pass fix-r1" → "Stage-1 key predictions" and requests R1–R5; `[C]` ios OK on its own reads |
| `css-counter-styles/counter-suffix android` | P 0.989 → P ≈0.9924 | MED-HIGH (same arithmetic as iOS; no Compose raster exists) | `[C]` android OK; `[K]` android OK |
| `css-counter-styles/cjk-decimal/css3-counter-styles-005 ios` | P 0.993 → P ≈0.997 | MED (Catalyst x267 vs ref x268) | `[I]` ios OK |
| `css-counter-styles/cjk-decimal/css3-counter-styles-005 android` | P 0.9759 → P ≈0.974 (−0.0018, pre-registered `down-preregistered`) | LOW-MED | `[I]` android OK |
| `css-counter-styles/counter-style-at-rule/disclosure-styles ios` | f 0.6127 → f 0.6127, byte-identical | MED-HIGH (Catalyst: RLI-wrapped `•` box and ink identical) | — (read the picture if it moves) |
| `css-counter-styles/counter-suffix web` | P 1 → P 1 | HIGH (no web unit) | `[H] [C] [T] [I] [K] [L]` web controls OK |
| -001 / -004 ios + android | unscored (`native-font-parity`); item text −3/−4 px | — | — |

0 flips, 0 lost, 0 newly measured, 0 unmeasured-now; `[L]` natives stay WRONG (report: the queued UA-marker
`tabular-nums` unit); the wave-54 `[M]` / lists-bakes counter-suffix controls stay OK (PLAN C4). The
`stayDegenerateEvenIfPass` retirement (`degenerateRetirement`) is the closing gate's read, not claimed here.
**fix-r1:** the per-key stage-1 predictions for counter-suffix ios are superseded by the table in "## Fix pass fix-r1";
every other row above stands.

## Deviations from the plan's registry (for the orchestrator and the S1 skeptic)

1. **Patch-borne seam pins the registry does not list** (all NEW paths no other lane touches): the four iOS raster
   pins ride in seams 1 / 2 / 3 / 6 and the two Compose source pins in seams 4 / 5 — the wave-52
   ListMarkerOutsideHangRasterTests precedent (a renderer pin ships inside its seam patch, red without it by design).
   Without them, seams 1–6 had no executable proof of wiring device-free.
2. **Comment hunks inside seams** tied to the unit's own behaviour: seam 3 re-trues the marker-row banner's gap
   sentence (:3824-3827), seam 5 RenderListItemMarker's KDoc gap sentence (:4201-4204), seam 4 the comment above
   its own line. Each is ≥ 150 lines from every other lane's registered hunk; `land-units.sh --check` semantics
   (one seam file per seam patch) hold.
3. **M10 finding**: the C10 `SeamReachabilityTest` module line cannot fail on a seam-4-only revert while seam 5
   names the module — executed, recorded, repaired by `ListMarkerSuffixGapHangSeamTest` (seam 4).
4. **Contingency delivered early**: `unit-U1-ios-LRM.patch` is in the lane dir with its `patch:` line;
   `land-units.sh --check` treats it as informational ("applies on top of the series") until `--contingency`.
5. **Stale comment outside ownership, queued (not edited):**
   `runtimes/compose/src/test/java/com/styleconverter/runtime/lists/ListMarkerOutsideHangTest.kt:89`
   "RTL: the inline-END side" (+ its test name "hangs after the item's end edge") — the same false claim D fixed in
   the main file; a test-file comment, not in L5's `own:` list.
6. **ComponentRenderer.kt :4537** ("The gap is now the shared ListMarkerRow.gapDp alone", wave-28 history) is left
   as history.
7. **fix-r1 (skeptic nits 3 / 4, comment-only):** seam 4's patch-borne SeamReachabilityTest.kt comment no longer calls seam 4
   the ONLY caller; `FontVariantNumericApplier.swift`'s header states the run-wide `tnum` radius. `seam-4.patch` and
   `unit-U3-ios.patch` were re-cut; every other patch's hunks are the builder's (`fix-r1.recut.out.txt`).

## Size and count deltas
- Seams: `ComponentRenderer.swift` 6291 → 6317 (+26 over seams 1/2/3/6), `ComponentRenderer.kt` 8488 → 8496
  (seams 4/5), `SeamReachabilityTest.kt` 239 → 243.
- Over-300 files: `StyleBuilder.swift` 1543 → 1545 (+2, as allowed). Over-200 touched: `ListMarkerRow.kt` 290 → 296
  (doc), `ListMarkerRow.swift` 230 → 236 (doc), `TypographyAggregate.swift` 238 → 241. New files ≤ 200 lines (largest
  `ListMarkerBidiTests.swift` 184, `ListMarkerBidi.swift` 158).
- Test counts: SwiftUI +23 tests (ListMarkerBidiTests 10, ListMarkerSuffixGapTests 4, FontVariantNumericTabularTests
  4, ListMarkerBidiSeamRasterTests 1, ListMarkerSuffixGapHangRasterTests 2, ListMarkerSuffixGapInsideRasterTests 1,
  FontVariantNumericTabularRasterTests 1); Compose +6 (ListMarkerSuffixGapTest 4, …HangSeamTest 1, …RowSeamTest 1).
  No new `*Applier` file: coverage-audit counts do not move.

## ORCHESTRATOR WINDOW REQUESTS

**fix-r1 AMENDED** (skeptic defects 1 and 2). L5 still has no window of its own (PLAN §2 L5: "[W1] is stage 1"). The stage-1
read below is REQUESTED ON A REGENERATED PLAN. Before stage 1, the orchestrator lands R1–R5 of "## Fix pass fix-r1"
through `plan-build.py` → `plan-prose.py --fill` / `--check` and the probes-selfcheck / residual records:
- the U3 run boxes in the probe and the residual SPEC;
- the re-grafted ok-all replays;
- the `[H]` / `[T]` precedence.

As the plan stands now, the stage-1 rules would revert a correct U3-ios (`[T]` ios zone reason). They would also fire the LRM swap
and then revert a correct U1-ios (`[H]` ios twin-only).

1. **Stage 1** (the orchestrator's window, after `beforeEveryDeviceRun` and the landing).
   - The run:
     `tools/titan/gate-driver.sh wave55-pre --sections css-counter-styles,css-flexbox,css-grid,css-masking,css-text,css-text-decor,css-writing-modes,filter-effects,selectors --skip-fixture-net`
   - L5's read, base first:
     `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/counter-suffix-rows.geometry.py wave55-pre wave55-open`.
     Also `geometry-gate.py wave55-pre --base wave55-open --lanes L5 --sections css-counter-styles`, for the residual read.
   - **Expected (after R1–R5):**
     - `→ GEOMETRY OK`: `[C]` ios, `[C]` android, `[I]` ios, `[I]` android, `[K]` ios, `[K]` android and the web controls.
     - `[H]` ios and `[T]` ios print `→ GEOMETRY WRONG (ink: …)` and NOTHING else (twin-only). Each goes to the PLAN §7
       step-8 recorded look. Open the counter-suffix ios capture next to the ref and the ok-all model, and check:
       - rows 3–4 read `א. foo` / `ב. bar`, with the period right of the letter at the ref's x ±2 (H-low would show the
         marker's ink rows moved);
       - row 9's `1` has its foot (one.tf, ink ≥ 7 px wide);
       - rows 9–12's periods and row 10's `2` are at the ref's x ±1.
     - A right look sets `TWIN_RUN_DEFAULT = 'wave55-pre'` (a probe edit plus the step-6 chain). `[H]` / `[T]` ios then read
       OK from wave55-probe on.
     - The residual read is 0 / 0 / 0 on counter-suffix ios and android and on -005 ×2.
     - `[L]` counter-suffix ios / android stay WRONG (report).
   - The cells: `node tools/titan/results/wave52-gate/cells.mjs 'counter-suffix|css3-counter-styles-005|disclosure-styles' wave55-open wave55-pre`.
     - Expected: counter-suffix ios ≈0.993 (MED: U3's extra glyphs are not in the replay) / android ≈0.992 / web 1;
       -005 ios ≈0.997 / android ≈0.974. Every carrier is P and ≥ 0.95.
     - Every other css-counter-styles capture is decoded-pixel identical (disclosure-styles android: L6 may-move,
       |Δ| ≤ 0.002), and the per-test IR of the 48 documents is byte-identical.
   - **Expected if the plan is NOT regenerated (as it stands, HIGH):**
     - `[T]` ios prints `GEOMETRY WRONG (zone x0-135×y208-231 differs …)`;
     - `[K]` ios prints `GEOMETRY WRONG (zone y232-599 differs …)`;
     - `[H]` ios prints `GEOMETRY WRONG (ink: …)`;
     - every counter-suffix ios key carries `residual:` (≈89–92 px outside the declared zone).
   - **Decides:**
     - A GEOMETRY (non-`ink:`, non-`residual:`) reason on a gating key reverts ITS unit (U2i before U2o, D first on a U2o
       revert).
     - A twin-only or residual-only line goes to the recorded look (R4 (i)).
     - A `[K]` ios FAIL is diagnosed in the picture. Differing px only in rows 9–12 x130-150 → U3's glyphs (R4 (iv)); only in
       rows 3–4 x0-63 → U1.
     - U2i-android is kept iff `[I]` android is OK and ≥ 0.95.
     - OPEN the counter-suffix and -005 PNGs before any label.
2. **Conditional, only if `[H] counter-suffix ios` prints a GEOMETRY reason, or the step-8 look shows the H-low shape**
   (fix-r1: never on a twin-only `ink:` or `residual:` line, R4 (ii)), while `[C]` / `[T]` / `[K]` ios have no geometry reason.
   - Append the `probe-decisions/contingency.json` row (D25) and regenerate. Then run
     `tools/titan/results/wave55-plan/land-units.sh --contingency L5:U1-ios-LRM`, which lands `unit-U1-ios-LRM.patch`.
   - Then run the pre-registered re-probe
     `tools/titan/gate-driver.sh wave55-pre-r1 --sections css-counter-styles --skip-fixture-net`, with its own `hashRead` and
     records dir, and read it with the same probe against wave55-open.
   - **Expected:** `[H]` ios has no geometry reason. An `ink:` twin-only line is expected here too: LRM also reorders the
     glyphs. That line goes to the look.
   - **Decides:**
     - OK, or twin-only with a right look → keep U1-ios + U1-ios-LRM (revertOrder [U1-ios-LRM, U1-ios]).
     - A geometry reason again → revert U1-ios (after U1-ios-LRM) and record CoreText's behaviour.
3. No other window: no Chromium, no converter, no wire change (wire carriers 0), no A/B arm of L5's own.

## Not verified (honest)
- Nothing ran on a device or simulator: every picture claim above is Catalyst or a replay; the device keys decide.
- CoreText on the device's iOS vs Catalyst for the isolate controls (R1) and for `monospacedDigit` on Inter (R3):
  Catalyst says no line-box growth and a working `tnum`; the device may differ.
- Compose has no raster pin (JVM-only unit stage): U2o/U2i-android are pinned by the pure rule and source-level seam
  pins; `[C]` / `[I]` android are the only picture evidence.
- The -005 android −0.0018 (Android's 2–4 px vertical offset, lighter CJK face) is the brief's replay, not re-derived.
- The full suites were not run (single-writer sweep is the orchestrator's); only the focused suites above.
- Whether the round-4 twin `ink:` reads of `[H]` ios / `[T]` ios accept a correct CoreText picture (see the
  geometry section's D39 observation) — device-only. **fix-r1:** measured on ONE rasterizer (Catalyst FULL vs U1 reverted,
  the real probe): `[H]` ios's own-twin read rejects a correct reorder by 36 px, so the device line is predicted twin-only
  WRONG (`fix-r1.h-twin.measure.out.txt`); the device itself was not measured.
- fix-r1: the device pixels of U3's five extra glyphs (Catalyst and a device+Catalyst graft only); the score effect of
  those glyphs (not re-derived); the proposal R1 was run on the model and 12 built pictures plus the 10 committed replays,
  NOT through `probes-selfcheck.py` / `probes-teeth*.py` / the round-4 / round-5 fake sweeps (the orchestrator's
  regeneration step); the mutations M1–M4, M6–M8, M10, M10′, M11, M12 were not re-run (their code and pins are byte-
  identical to the skeptic's re-execution).

## Mutation log (bracketed in execution order; each MUTATING line was written BEFORE its edit and each RESTORED line after the byte-exact restore, post == pre; paths are relative to the worktree on the TREES: line — the absolute prefix was rewritten after the last restore to keep scratchpad paths out of the delivered note)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b — M1 (U1-ios): ListMarkerBidi.painted returns its input
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b
  M1 result: mutated → RED, 4 failures / 11 tests (ListMarkerBidiTests testHebrewMarkerUnderAnLtrItemIsLtrIsolated, testNeutralBulletUnderAnRtlItemIsRtlIsolated, testPaintedHebrewMarkerPaintsThePeriodAfterTheLetter; ListMarkerBidiSeamRasterTests testHebrewMarkerPaintsThePeriodAfterTheLetter: period x81-83 before the letter x88-104); restored → GREEN, 0 failures / 11 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b — M2 (U1-ios): ListMarkerBidi.painted always wraps (the natural==item conditional dropped)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b
  M2 result: mutated → RED, 5 assertion failures in 2 tests / 11 (testMarkersWhoseNaturalDirectionMatchesAreUnchanged ×4: "1.", "일,", "一、", rtl "א." all wrapped; testPaintedIsIdempotent: double isolate); restored → GREEN, 0 failures / 11 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerSuffixGap.swift sha256 58e30a1b33c633c8edd0c2c0024a6c2ac9a8d8da2c196348e427811a796ef307 — M3 (U2o-ios/U2i-ios): ListMarkerSuffixGap.points drops the U+3001 test (always the stand-in)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerSuffixGap.swift sha256 58e30a1b33c633c8edd0c2c0024a6c2ac9a8d8da2c196348e427811a796ef307
  M3 result: mutated → RED, 3 tests / 7 (ListMarkerSuffixGapTests testIdeographicCommaSuffixTakesNoGap ×2 assertions 4.0≠0.0; ListMarkerSuffixGapHangRasterTests: both rows' marker at x29 (the stand-in) instead of x33 vs x29; ListMarkerSuffixGapInsideRasterTests: both rows' text at x271 instead of x267 vs x271); restored → GREEN, 0 failures / 7 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 1eef5ec24bcfe71eb5b1b699d277b1c26f61eaeb87b33fac70392e8180006bef — M5 (U3-ios): FontVariantNumericApplier restored to the identity (_ = cfg; _ = agg)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 1eef5ec24bcfe71eb5b1b699d277b1c26f61eaeb87b33fac70392e8180006bef
  M5 result: mutated → RED, 8 assertion failures in 4 tests / 5 (testTabularNumsSetsTheFlag, testProportionalNumsClearsTheFlag, testStyleBuilderMirrorsTheVerbatimCarrierIntoTextConfig, FontVariantNumericTabularRasterTests: ink 53 = 53); restored → GREEN, 0 failures / 5 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M6 (U1-ios seam-1): the seam-1 hunks reverse-applied to ComponentRenderer.swift (markerPlacement paints the raw marker again)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
  M6 result: mutated → RED, ListMarkerBidiSeamRasterTests testHebrewMarkerPaintsThePeriodAfterTheLetter fails (row-1 marker groups [(81-83 period), (88-104 letter)] — the defect's `.א` through the real markerPlacement); the pure ListMarkerBidiTests stay green (they do not consume the seam); restored → GREEN, 0 failures / 11 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M7 (U2o-ios seam-2): the seam-2 hunk reverse-applied to ComponentRenderer.swift (the hang takes ListMarkerRow.gapPt again)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
  M7 result: mutated → RED, ListMarkerSuffixGapHangRasterTests testIdeographicCommaMarkerHangsFlush fails (both rows' marker at x29 — today's device picture; with the seam x33 / x29); the pure ListMarkerSuffixGapTests stay green; restored → GREEN, 0 failures / 6 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M8 (U2i-ios seam-3): the seam-3 hunks reverse-applied to ComponentRenderer.swift (the inside HStack spacing is ListMarkerRow.gapPt again)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
  M8 result: mutated → RED, ListMarkerSuffixGapInsideRasterTests testInsideIdeographicCommaMarkerTakesNoGap fails (both rows' item text at x271 — today's device x271; with the seam x267 / x271), while the hang pin (seam-2) stays green — the two seams fail independently; restored → GREEN, 0 failures / 3 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M9 (U3-ios seam-6): the seam-6 hunk reverse-applied to ComponentRenderer.swift (labelFont no longer composes monospacedDigit)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
  M9 result: mutated → RED, FontVariantNumericTabularRasterTests testTabularNumsWidensTheDigitRun fails (ink 53 = 53); the pure applier / StyleBuilder pins stay green; restored → GREEN (tabular 68 vs proportional 53), 0 failures / 5 tests
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerSuffixGap.kt sha256 8076fff4e9967b80b6e75d144aa0428a770d6cba33b82df82b0236f4abb99622 — M4 (U2o-android/U2i-android): ListMarkerSuffixGap.gapDp drops the U+3001 test (always the stand-in)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerSuffixGap.kt sha256 8076fff4e9967b80b6e75d144aa0428a770d6cba33b82df82b0236f4abb99622
  M4 result: mutated → RED, ListMarkerSuffixGapTest 'an ideographic comma suffix takes no gap' fails (1 / 5 tests); restored → GREEN, 0 failures / 5 tests
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M10 (U2o-android seam-4): the seam-4 hunk reverse-applied to ComponentRenderer.kt only (the hang takes ListMarkerRow.gapDp again; the patch-borne reachability line kept)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
  M10 result: mutated → still GREEN (8 / 8) — NOT a red: seam-5's Row padding still names ListMarkerSuffixGap, so the SeamReachabilityTest module line cannot tell seam 4 from seam 5 (a pin gap found by executing it). Repair: a patch-borne source pin for the hang hunk itself (NEW ListMarkerSuffixGapHangSeamTest.kt in seam-4.patch), re-run as M10' below; the reachability line's own negative control is M10b (ComponentRenderer.kt at base bytes). Restored → GREEN, 0 failures / 8 tests
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M10' (U2o-android seam-4): the seam-4 hunk reverse-applied to ComponentRenderer.kt only (the hang takes ListMarkerRow.gapDp again)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
  M10' result: mutated → RED, ListMarkerSuffixGapHangSeamTest fails (1 / 9; SeamReachabilityTest still green — seam-5 keeps the module reachable, as M10 showed); restored → GREEN, 0 failures / 9 tests
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M11 (U2i-android seam-5): the seam-5 hunks reverse-applied to ComponentRenderer.kt only (the Row pads by ListMarkerRow.gapDp again)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
  M11 result: mutated → RED, ListMarkerSuffixGapRowSeamTest fails (1 / 9); restored → GREEN, 0 failures / 9 tests
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M10b (C10 reachability line): ComponentRenderer.kt restored to its base bytes (no seam 4, no seam 5: nothing names ListMarkerSuffixGap)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
  M10b result: mutated → RED, 3 / 9 (SeamReachabilityTest 'every module … has a live call site': "unreachable from ScreenshotCaptureScreen (no live call site): [lists/ListMarkerSuffixGap.kt]"; ListMarkerSuffixGapHangSeamTest; ListMarkerSuffixGapRowSeamTest); restored → GREEN, 0 failures / 9 tests
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 50d00033b5f0f0498ff11fadf099126793cf341bc987e02af9b904c8276511cb — M12 (contingency U1-ios-LRM, on the full+LRM state): painted returns its input
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 50d00033b5f0f0498ff11fadf099126793cf341bc987e02af9b904c8276511cb
  M12 result: mutated → RED, 4 / 11 (the two LRM/RLM wrap pins, the painted raster pin, the seam-1 raster pin: period x81-83 before letter x88-104); restored → GREEN, 0 failures / 11 tests (full + unit-U1-ios-LRM state: 113 / 113 focused Swift tests green)

## Landing pre-check
`bash tools/titan/results/wave55-plan/land-units.sh --check --only L5` on the shared tree (read-only) →
`land-units-check.only-L5.out.txt`: rc 0, "0 error(s), 1 warning(s) — landable" (the warning is the 120-s freshness
guard on the files just written); each patch applies alone (seams 2/3/5/6 stacked as expected), the 11 apply
cumulatively on 611c9f93, 27 paths (13 created) clean at HEAD, every revert order reverse-applies (C12 included),
and the contingency applies on top.

## Delivered patches (sha256 of the file bytes, written after the final restore)
fix-r1: every patch was deleted and re-cut; the `patch:` lines of record are at the end of this note.

STATUS: COMPLETE
STATUS: PARTIAL — fix-r1 in progress

## Fix pass fix-r1 — execution log (mutations bracketed as they ran; the prose follows below this log)
Worktree: the first `TREES:` line (detached 611c9f93, provisioned per PLAN §0, Swift builds with `-derivedDataPath <wt>/.dd`). The
builder's 12 patches were verified against their `patch:` sha256 lines (12 / 12 OK), applied in landing order, and every patch
was then DELETED from the lane dir and re-cut (`fix-r1.recut.sh` → `fix-r1.recut.out.txt`). Paths below are relative to that
worktree.
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 d961bed609de3cf2146bdc735ac571265b2fb7516ceb63335c32cd7c07919b26 — M5' (U3-ios, fix-r1 bytes): FontVariantNumericApplier.contribute restored to the identity (_ = cfg; _ = agg)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 d961bed609de3cf2146bdc735ac571265b2fb7516ceb63335c32cd7c07919b26
  M5' result: mutated → RED, 8 assertion failures in 4 tests / 5 (FontVariantNumericTabularTests testTabularNumsSetsTheFlag ×4, testProportionalNumsClearsTheFlag, testStyleBuilderMirrorsTheVerbatimCarrierIntoTextConfig ×2; FontVariantNumericTabularRasterTests testTabularNumsWidensTheDigitRun: 53 is not greater than 53); the restore's green is the next run's FontVariantNumericTabularTests 4 / 4
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M9' (U3-ios seam-6): the seam-6 hunk reverse-applied to ComponentRenderer.swift (labelFont no longer composes monospacedDigit)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
  M9' result: mutated → RED, FontVariantNumericTabularRasterTests testTabularNumsWidensTheDigitRun (53 is not greater than 53); FontVariantNumericTabularTests 4 / 4 GREEN in the same run (the M5' restore verified); the restore's green is the next Swift run
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M10b' (C10 reachability line, fix-r1 comment bytes of seam 4): ComponentRenderer.kt at its 611c9f93 bytes (no seam 4, no seam 5: nothing names ListMarkerSuffixGap)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
  (M9' restore verified: the Swift run beside M10b' — FontVariantNumericTabularTests + …RasterTests 5 / 5 GREEN)
  M10b' result: mutated → RED, 3 / 9 (SeamReachabilityTest 'every module wave 49 added to the compose runtime has a live call site': unreachable from ScreenshotCaptureScreen (no live call site): [lists/ListMarkerSuffixGap.kt]; ListMarkerSuffixGapHangSeamTest; ListMarkerSuffixGapRowSeamTest; compileDebugKotlin executed); restored → GREEN, 0 failures / 9 tests

## Fix pass fix-r1 — defect → action, outputs

Inputs: `skeptic.md` (verdict MIXED: one must-fix, one should-fix, two nits). Every skeptic check was re-executed FIRST, in
the fix worktree, and reproduced exactly. The plan files were read at their CURRENT shared-tree bytes, which moved during the
lane (rounds 5–6): `counter-suffix-rows.geometry.py` bd2f001a… (unchanged since the builder), `expectations.json` 8fbfc83a…,
`geometry-gate.py` 09e35e92… (round 6 adds the RESIDUAL read, D40), `residual-read.py` 40211eb0…. The lane wrote no plan file.

### Defect 1 (must-fix) — U3-ios repaints more than row 9's digit: CONFIRMED; instrument + prediction fix, no code fix
- **Re-executed.**
  - The font fact, by an independent script: `fix-r1.inter-tnum.py` → `fix-r1.inter-tnum.out.txt`. Inter-Regular's GSUB `tnum`
    maps 61 glyphs, among them `one` → `one.tf`, `two` → `two.tf` and `period` → `period.tf` (advance 4.609 → 4.297 px at
    16 px). The Hebrew letters are not in Inter (fallback face, no `tnum`). Identical to `skeptic-inter-tnum.out.txt`.
  - The skeptic's Catalyst probe, re-run in the fix worktree (its source with the class renamed and two PNG writes added; run
    alone; never delivered). Readout identical: 160 px differ, all in rows 9–12 at x133-146. The with / without PNGs are
    pixel-identical to `skeptic-catalyst-u3-{with,without}.png`.
  - `skeptic-model-u3.py` re-run: the same three lines. `[T] ios` → WRONG (zone x0-135×y208-231, 10 px); `[K] ios` → WRONG
    (zone y232-599, 79 px); `skmodel2` `[T] ios` → `ink 13 vs wave54-final android`.
  - The wire's run widths: `.` 4.3 px (= `period.tf`), `1` / `2` 10.38 px (= `one.tf` / `two.tf`). Chromium applied `tnum` to
    the whole run, so U3 moves all six runs TOWARD the ref. U3's code is right; the plan's model of its radius is not.
- **Measured radius** (`fix-r1.u3-boxes.measure.py` → `.out.txt`).
  - U3's Catalyst delta lies entirely in six run boxes: rows 9–10 at x130-150 (period + digit) and rows 11–12 at x130-135 (the
    period of `א.` / `ב.`). There are 0 px outside them.
  - Two-mask XOR against the pre-`tnum` glyph at the best whole-px offset: the four period boxes read 2 / 2 / 2 / 0 (at INK_TOL),
    row 10's `2` box 16–19, row 9's `1` window 38–41.
  - Corpus glyph census (`fix-r1.u3-glyph-census.py` → `.out.txt`, wave54-final and wave55-open identical): 6 declaring
    components, 1 document, 6 glyphs repainted. The plan reads 1.
- **Round 6 compounds it** (`fix-r1.residual-u3.measure.py` → `.out.txt`, using residual-read.py's own `read_arrays`).
  - The installed counter-suffix ios zone is rows 3–6 x0-63 plus row 9's digit window: the same one-glyph model.
  - So a CORRECT U3 picture reads 89 px (device + Catalyst delta) or 92 px (Catalyst on / off) OUTSIDE it. `residual:` then
    fails EVERY counter-suffix ios key.
  - With the two U3 rects added, every one of those pictures reads 0 / 0 / 0. The U1-only Catalyst pair reads 0 under both.
- **Why no code change.** `Font.monospacedDigit()` turns `tnum` on per run. A digit-only feature still moves row 10's `2`, which
  lies inside `[K]`'s y232-599 zone. Leaving the periods proportional would keep them off the ref's tabular advances.
- **Lane actions.**
  - (a) The predictions are amended: the `css-counter-styles/counter-suffix ios` row, and "Stage-1 key predictions" below.
  - (b) The window request is amended (the section is rewritten in place).
  - (c) `FontVariantNumericApplier.swift`'s header now states the run-wide radius. This also closes nit 4. `unit-U3-ios.patch`
    is re-cut, comment-only.
  - (d) A plan-regeneration request R1–R5 with an EXECUTED proposal (below).
- **Census delta.**
  - The capture census is IDENTICAL to `expectations.json`: `census-l5.py` re-run, byte-identical on wave54-final and
    wave55-open. That is 1 U3 capture, carriers web 0 / ios 5 / android 4, wire 0.
  - The PIXEL radius inside U3's one capture GREW from 1 glyph to 6 glyphs in 4 rows. That is a plan-regeneration need for the
    probe zones, the residual SPEC and the standing replays (R1–R3). The carrier sets do not change.

### Defect 2 (should-fix) — rule precedence for twin-only `[H]` / `[T]` lines: a measurement raises the stakes
- `fix-r1.h-twin.measure.py` → `.out.txt` runs the REAL probe on a ONE-rasterizer pair: Catalyst FULL against Catalyst with
  U1-ios reverted, both through the verbatim IR. It mirrors stage 1's device-against-device read.
  - `[H] counter-suffix ios`'s geometry reads PASS: letter x46-53, period x57 = the ref's x.
  - Its own-twin `ink:` read gives 36 px → WRONG. A correct reorder moves each glyph to a new sub-pixel phase, and the
    anti-aliasing changes.
  - So at `wave55-pre`, `[H] ios` is predicted WRONG on a twin-only reason (MED-HIGH).
- Under the stage1Decision text as it stands, PLAN §7 step 7 (the LRM swap) runs BEFORE step 8 (the recorded look).
  - The LRM fallback also reorders the glyphs, so its re-probe is predicted twin-only WRONG again.
  - "Both fail → revert U1-ios" would then revert a correct unit.
- The lane cannot change the rule text. Request R4 (below) pre-registers the precedence.
- With defect 1 regenerated, `[T] ios` also fails only twin-only: `one.tf` against Android's raster, 13 px on the model and on
  the Catalyst pair.

### Nits
- **Nit 3 (seam-4 comment).** `SeamReachabilityTest.kt`'s patch-borne comment no longer says seam 4 is the only caller. It now
  says seam 5 calls it while U2i-android is on, and that ListMarkerSuffixGapHangSeamTest pins seam 4. `seam-4.patch` is re-cut,
  comment-only.
- **Nit 4 (U3 header).** The header now has a "Radius inside a run" paragraph, and the false "single-glyph runs" sentence is
  replaced. `unit-U3-ios.patch` is re-cut, comment-only.
- `fix-r1.recut.out.txt` lists the changed hunk lines; every one is a comment line.

### Re-cut, pins, revert states
- **Re-cut** (`fix-r1.recut.sh` → `fix-r1.recut.out.txt`; PLAN §8 step 3).
  - The 12 old patches were deleted and re-cut in landing order from a clean 611c9f93 worktree. A TEMPORARY index snapshots
    each step: no commit, stash or checkout of a branch.
  - The FULL state is byte-identical before and after: tracked diff, porcelain status, and every untracked runtime file's
    sha256.
  - Every hunk is identical to the builder's cut except in seam-4 and unit-U3-ios, which carry the comment fixes. The cumulative
    seam cuts' `index` lines carry 8-hex abbreviations where the builder's carried 7.
- **Pins.**
  - Every mutation whose mechanism or file bytes changed was re-executed and bracketed (log above):
    - M5′: the U3 applier reset to the identity. RED: 8 assertions in 4 tests.
    - M9′: the seam-6 hunk reversed. RED: the raster pin, 53 = 53.
    - M10b′: `ComponentRenderer.kt` at base bytes, which exercises the re-worded SeamReachabilityTest line. RED: 3 / 9.
    - Each was restored byte-exact (post sha256 == pre) and re-run GREEN.
  - The other mutations (M1–M4, M6–M8, M10, M10′, M11, M12) touch byte-identical code and pins. The skeptic re-executed them on
    the same bytes; they were not re-run here.
- **The executed proposal is itself an instrument.** It is proven red on 11 wrong pictures; see R1.

### Stage-1 key predictions (fix-r1; the counter-suffix keys — every other L5 key is unchanged from the table above)
| key | as the plan stands now | after R1–R4 | confidence | evidence |
|---|---|---|---|---|
| `[H] counter-suffix ios` (gating, U1-ios) | WRONG, `ink:` twin-only (own-twin XOR ≈36 px class) — and `residual:` from U3's boxes | WRONG `ink:` only → step-8 look → `TWIN_RUN_DEFAULT = 'wave55-pre'` → OK at wave55-probe / final | MED-HIGH | `fix-r1.h-twin.measure.out.txt` |
| `[T] counter-suffix ios` (gating, U3-ios) | WRONG on a ZONE reason (`x0-135×y208-231`: row 9's `period.tf`) — a geometry reason: the rule as written reverts U3-ios | WRONG `ink:` only (one.tf vs Android's '1', 13 px class) → look → OK from wave55-probe | HIGH / MED-HIGH | `skeptic-model-u3.out.txt`, `fix-r1.probe-proposal.out.txt` |
| `[K] counter-suffix ios` (control) | WRONG on a ZONE reason (`y232-599`: rows 10–12's periods and `2`) — "U1 first" names the wrong unit | OK | HIGH / MED-HIGH | same |
| `[C] counter-suffix ios` (gating, U2o-ios) | its own reads OK; `residual:` (U3's boxes, the whole cell) | OK | MED-HIGH | `fix-r1.residual-u3.measure.out.txt` |
| `[C]` / `[K] counter-suffix android`, `[I]` 005 ios / android, web controls, `[L]` | unchanged from the table above | unchanged | as above | — |
- **Scores.** counter-suffix ios is predicted P ≈0.993 as before, but at MED (was MED-HIGH). The brief's replay grafted only the
  `1`; the five other tabular glyphs move ≤ 0.31 px toward the ref's advances, and their score effect was NOT re-derived.

### Plan-regeneration request (the orchestrator regenerates through plan-build.py; the lane changed no plan file)
- **R1 — the probe** (`counter-suffix-rows.geometry.py`). Carve U3's six run boxes out of ios's base-identity zones and read
  them in `[T] ios`. EXECUTED as `fix-r1.probe-proposal.py` → `fix-r1.probe-proposal.diff.txt` (the edit, against probe
  bd2f001a…) and `fix-r1.probe-proposal.out.txt` (violations 0). The edit:
  - `ROW9_OUT_IOS` x0-129 / x151-;
  - `ZONES['ios']` skip rows 9–10 x130-150 and rows 11–12 x130-135;
  - `u3_boxes_check`: each period box holds ONE dot at the ref's x ±1 / rows ±1; row 10's `2` box holds ONE ≥ 7-px-tall blob at
    the ref's left and right ±1 / rows ±1, filling ≤ 60 %;
  - `t_ink` sums the five boxes with the digit window, each at its own best whole-px offset (round-4 "summed").

  It reads the pictures this way:
  - **base** (wave54-final): `[T] ios` WRONG on geometry (proportional `1`) and `[K] ios` OK, so the self-test holds.
  - **correct U3 model:** `[K] ios` OK. `[T] ios` WRONG `ink:` only (twin-only) without a twin run, and OK with the decided
    twin.
  - **11 wrong pictures** are rejected: periods erased / ±2 px, the `2` erased / +2 px / a block, a 2×2 blob, the `2` and a
    period doubled 1 px, and the row-11 letter erased (`[K]`).
  - **A 1-level single grey pixel passes with the decided twin.** That is the PLAN's NAMED 2-px floor, inherited, not widened.
  - **Every other key's verdict** is identical to the installed probe on every picture.
- **R2 — the standing L5 replays.**
  - `counter-suffix-rows-replay/ok-all/counter-suffix.ios.png` grafted only Android's `1`. Under R1, 7 of the 10 committed tags
    flip `[T] ios` to WRONG `ink:` (§R of the proposal output); the other 3 do not change.
  - Re-grafting Android's five other boxes into each tag's ios picture makes the proposal print exactly the installed probe's
    verdicts on the committed replays, for all 10 tags.
  - Then re-run `probes-selfcheck.py` (§L5, §T, §T3, §T4) with `probes-teeth*.py`, and fix-r6's §3 fixed-picture record. The
    `[K] ios` fakes whose ink lands in a carved box must now be caught by `[T] ios`.
- **R3 — the residual SPEC.** In `residual-read.py`, `SPECS['css-counter-styles/counter-suffix.html ios']` gains
  `('rect', (130, 208, 136, 304))` and `('rect', (136, 232, 151, 256))`. Measured 89 / 92 / 91 → 0 on the correct and
  re-grafted pictures; the U1-only pair stays 0. Then `fix-r6.zones.py --check`.
- **R4 — stage1Decision precedence** (defect 2), pre-registered before stage 1:
  - (i) An `[H] ios` / `[T] ios` line whose only reason is `ink:` (twin) or `residual:` goes to the step-8 recorded look FIRST.
    A right picture sets `TWIN_RUN_DEFAULT = 'wave55-pre'` (and, for `residual:`, the D40 `fix-r6.zones.py --also` record).
  - (ii) The LRM contingency (step 7) fires only on an `[H] ios` GEOMETRY reason (blob count, period before the letter, letter
    or period off the ref's x ±2 / rows ±1, letter fill) or on a look that shows the H-low shape.
  - (iii) A `[T] ios` geometry reason (digit width / position / rows / fill, a U3-box glyph, a row-9 zone) reverts U3-ios.
  - (iv) A `[K] ios` FAIL whose differing pixels all lie in U3's boxes is U3's, never U1's ("U1 first" applies to rows 3–4
    x0-63).
  - Without R1–R3, (i) also needs: "a `[T]` / `[K] ios` zone line whose differing pixels all lie in rows 9–12 x130-150 is
    decided by the look". This is the skeptic's alternative; the lane prefers R1–R3.
- **R5 — generated text.**
  - `stage1Decision`'s "all 6 gating keys OK" and the PLAN §2 L5 predictions' "GEOMETRY OK" for counter-suffix ios become
    "[H] / [T] ios twin-only WRONG at wave55-pre → step-8 look; OK from wave55-probe".
  - The probe's round-2 header sentence "U3-ios changes the digit's glyph, never the row's other ink" is false and is
    corrected.

### Evidence files of this pass (lane dir; every script runs read-only against the shared tree, each mirror in a temp dir)
- Scripts, each with its `.out.txt`:
  - `fix-r1.inter-tnum.py`: the Inter `tnum` glyph substitutions;
  - `fix-r1.u3-boxes.measure.py`: where U3 moves ink;
  - `fix-r1.u3-glyph-census.py`: the corpus glyph radius;
  - `fix-r1.residual-u3.measure.py`: the round-6 residual read on correct pictures;
  - `fix-r1.h-twin.measure.py`: `[H]` ios's twin read on one rasterizer;
  - `fix-r1.probe-proposal.py`: R1 / R2 executed, plus `.diff.txt`.
- Re-cut and revert states: `fix-r1.recut.sh` with `fix-r1.recut.out.txt`, and `fix-r1.revert-states.out.txt`.
- Geometry self-tests on the current plan bytes: `fix-r1.geometry-selftest.base-{wave54-final,wave55-open,wave54-open}.out.txt`.
- Catalyst pictures, all 390×600, scale 1, Inter registered:
  - `fix-r1.catalyst-{full,noU1}.png`, from the throwaway class kept as `fix-r1.render-probe.swift.txt`;
  - `skeptic-catalyst-u3-{with,without}.png`, re-rendered here pixel-identical. That re-render used the skeptic's
    `skeptic-probe-u3.swift.txt`, with the class renamed and two PNG writes added; it was never delivered.

### Geometry self-test (re-run on the CURRENT plan bytes: probe bd2f001a…, geometry-gate.py 09e35e92…, expectations 8fbfc83a…)
`geometry-gate.py wave54-final --base <b> --lanes L5 --self-test`, which now includes round 6's residual read:
- `--base wave54-final`: HOLDS, exit 0, 0 violations; the residual read is 8 / 8 OK.
- `--base wave55-open`: HOLDS, exit 0.
- `--base wave54-open`: exit 1 with 5 control violations, the same base-age effect the builder recorded (wave-54 M′ moved
  those zones between wave54-open and wave54-final).

### Census (fix-r1)
- `census-l5.py` re-run on wave54-final and on wave55-open gives output byte-identical to `census-l5.out.txt` /
  `census-l5.wave55-open.out.txt`.
- Every unit's capture set equals `expectations.json` lanes["L5-counter-suffix-rows"]: captureCarriers web 0 / ios 5 /
  android 4, wireCarriers 0. Capture-level delta: grew 0, shrank 0.
- The GLYPH / pixel radius of U3 inside its one capture grew: 6 glyphs in 4 rows, against the 1 the probe, the residual SPEC
  and the replay model. That is the plan-regeneration need R1–R3; the carrier sets do not change.

### revertStatesCompiled (fix-r1, on the RE-CUT patches; `fix-r1.revert-states.out.txt`)
Each state was built FORWARD (the kept patches on a clean 611c9f93 in landing order) and REVERSE (all 11, then the reverted ones
reverse-applied in revert order). Both built states are byte-identical in every state.
revertStatesCompiled: FULL [U1-ios, U2o-ios, U2i-ios, U2o-android, U2i-android, U3-ios, D]: Swift 113/113 · Compose 99/99 · node 44 pass / 0 fail / 1 skipped — green (run after the re-cut, and again as FINAL after every state and restore)
revertStatesCompiled: chain U1-ios → [all − U1-ios] (C12: U1-ios reverted with U2i-ios applied): Swift 102/102 green
revertStatesCompiled: chain D (step 1 of both U2o chains) → [all − D]: Swift 113/113 · Compose 99/99 (compileDebugKotlin executed) · node 44/0/1 — green
revertStatesCompiled: chain D → U2i-ios → [all − D − U2i-ios]: Swift 112/112 green
revertStatesCompiled: chain D → U2i-ios → U2o-ios → [all − D − U2i-ios − U2o-ios]: Swift 106/106 green
revertStatesCompiled: chain U2i-ios → [all − U2i-ios]: Swift 112/112 green
revertStatesCompiled: chain D → U2i-android → [all − D − U2i-android]: Compose 98/98 green (compileDebugKotlin executed)
revertStatesCompiled: chain D → U2i-android → U2o-android → [all − D − U2i-android − U2o-android]: Compose 93/93 green (compileDebugKotlin executed)
revertStatesCompiled: chain U2i-android → [all − U2i-android]: Compose 98/98 green (compileDebugKotlin executed)
revertStatesCompiled: chain U3-ios → [all − U3-ios]: Swift 108/108 green
revertStatesCompiled: contingency [FULL + U1-ios-LRM]: Swift 113/113 green; chain [U1-ios-LRM, U1-ios]: [FULL + LRM − LRM] == FULL and [FULL + LRM − LRM − U1-ios] == [all − U1-ios] (code-only snapshots byte-compared)
- Every count equals the builder's and the skeptic's.
- `d-comment-only-check.py unit-D.patch` on the re-cut unit-D: 6 files, 133 changed lines, 0 non-comment, 0 stale phrases.
- The worktree is left in the FULL state.

### Landing pre-check (fix-r1)
`bash tools/titan/results/wave55-plan/land-units.sh --check --only L5` was run read-only on the shared tree at HEAD 7566fb03
(the orchestrator's checkpoint and audit commits landed during this pass) → `fix-r1.land-units-check.only-L5.out.txt`.
- rc 0: "0 error(s), 1 warning(s) — landable". The warning is the 120-s freshness guard on the files just written.
- The 11 patches apply cumulatively on 7566fb03, and 27 paths (13 created) are clean at HEAD.
- Every revertOrder reverse-applies (C12 included), and the contingency applies on top.

## Re-verify pass reverify-r1 (independent re-verifier: records only — no patch byte and no plan file changed)
Worktree: the FIRST `TREES:` line (`wave55-reverify-L5`, detached 611c9f93, provisioned per PLAN §0, Swift with
`-derivedDataPath <wt>/.dd`). Evidence: `reverify-r1/` (`reruns.txt`, `catalyst-u3-probe.{out,swift}.txt`, `revert-states.txt`,
`mutations.txt`, `land-units-check.only-L5.txt`, the runner scripts as `.txt`). Shared-tree writes: this section, the `TREES:`
line and `reverify-r1/` only. No patch, no plan file, no `patch:` / `STATUS:` line was changed.

- **Patch bytes.** The 12 lane-dir patches equal their `patch:` sha256 lines (12 / 12) and the last `STATUS:` line is
  `STATUS: COMPLETE`. Applied in landing order on 611c9f93, they give 27 paths (13 created). Every path is byte-identical to the
  fix worktree's FULL state. Against the builder's FULL state (the skeptic worktree), only two files differ, and both
  differences are comments: `SeamReachabilityTest.kt` (nit 3) and `FontVariantNumericApplier.swift` (nit 4 and the radius
  paragraph).
- **Defect 1 (must-fix). The lane's part is FIXED; the plan's part is still OPEN.**
  - The defect is unchanged in code, by design. The skeptic's Catalyst probe, re-run on the current patches
    (`catalyst-u3-probe.out.txt`), prints the same readout line for line: 160 px in rows 9–12 at x133-146. The delta mask
    and its pixel values are identical to the skeptic's, and 0 px of it lies outside the six U3 run boxes.
  - The font fact re-runs byte-identical: `fix-r1.inter-tnum.py` and `fix-r1.u3-glyph-census.py` (6 glyphs).
  - **The plan has not been regenerated.** `skeptic-model-u3.py` was re-run through the CURRENT probe
    (`counter-suffix-rows.geometry.py` bd2f001a…, unchanged since the builder; residual-read.py 40211eb0…; expectations
    8fbfc83a…). Its output is byte-identical to the skeptic's:
    - `[T] counter-suffix ios → GEOMETRY WRONG (zone x0-135×y208-231 … 10 px)`;
    - `[K] counter-suffix ios → GEOMETRY WRONG (zone y232-599 … 79 px)`.
  - So the stage-1 rule as it stands still reverts a correct U3-ios. That stays true until the orchestrator lands R1–R4.
    HEAD 7566fb03 queues this regeneration as prelanding item 3, but that item names only the probe's `[T]` / `[K]` zones.
    It needs R3 (the residual SPEC) and R2 (re-grafting the 7 committed replays the R1 probe flips, then the selfcheck / teeth
    sweeps) as well.
  - The lane's actions were checked in the note: the predictions row is amended (MED), the fix-r1 stage-1 key table is in
    place, the window request is rewritten with both expected outcomes, the applier header is corrected, and R1–R5 are
    filed.
  - The executed proposal re-runs byte-identical: `fix-r1.probe-proposal.{out,diff}.txt` (violations 0) and
    `fix-r1.residual-u3.measure` (89 / 92 / 91 → 0).
- **Defect 2 (should-fix). The lane's measurement holds; the plan's part is still OPEN.**
  - `fix-r1.h-twin.measure` re-runs byte-identical: `[H]` ios's own-twin `ink:` is 36 px on one rasterizer.
  - expectations 8fbfc83a `stage1Decision` still says "[H] ios WRONG … → the LRM fallback" next to the D39 / D40 look rules,
    with no stated precedence. R4 is not yet pre-registered.
- **Nits 3 / 4: FIXED.** Both are comment-only, and the diffs were read. The new wording is accurate: seam 4's hang and
  seam 5's Row both call the module, and the U3 header states the run-wide `tnum` radius.
- **Census.**
  - `census-l5.py` (wave54-final, wave55-open) and `skeptic-census-l5.py` re-run byte-identical.
  - Against the CURRENT `expectations.json`, every unit's capture set is equal: captureCarriers 0 / 5 / 4, wire 0, grew 0.
  - U3's pixel radius inside its one capture is 6 glyphs. That is the open regeneration need (R1–R3).
- **Geometry self-test** (byte-identical to fix-r1's records):
  - `--base wave54-final`: exit 0, HOLDS (gating 6 / 6 FAIL, control 16 / 16 PASS, report 2 / 2 FAIL, residual 8 / 8 OK).
  - `--base wave55-open`: exit 0.
  - `--base wave54-open`: exit 1, with 5 control violations. This is the base-age effect the builder recorded, so the
    re-verify brief's `--base wave54-open … exit 0` cannot hold on this probe.
- **Pins.** RM1–RM3 (brackets below) reproduce M5′, M9′ and M10b′ on the current bytes.
  - FULL: Swift 113 / 113 (17 classes), Compose 99 / 99, node 44 / 0 / 1.
  - Rule 2b (all six seams reverted): Swift 108 / 108 (13 classes), Compose 97 / 97.
- **Landing check.** `land-units.sh --check --only L5` (read-only, run before this section was written) returned rc 0:
  0 errors, 0 warnings, landable.
- **PNGs looked at.** The ref, wave54-final iOS / Android counter-suffix, and the Catalyst with / without renders.
  - Ref row 9: the `1` has its foot. iOS row 9: the `1` has no foot. Android row 9: the `1` has its foot.
  - Catalyst-with draws the foot; catalyst-without matches the device.
  - The U3 move goes toward the ref.

revertStatesCompiled: reverify-r1 FULL: Swift 113/113 · Compose 99/99 · node 44 pass / 0 fail / 1 skipped — green (before the mutations and again as FULL-final)
revertStatesCompiled: reverify-r1 all − U1-ios (C12, U2i-ios applied): Swift 102/102 green
revertStatesCompiled: reverify-r1 all − D: Swift 113/113 · Compose 99/99 · node 44/0/1 green
revertStatesCompiled: reverify-r1 all − D − U2i-ios: Swift 112/112 green
revertStatesCompiled: reverify-r1 all − D − U2i-ios − U2o-ios: Swift 106/106 green
revertStatesCompiled: reverify-r1 all − U2i-ios: Swift 112/112 green
revertStatesCompiled: reverify-r1 all − D − U2i-android: Compose 98/98 green (compileDebugKotlin executed)
revertStatesCompiled: reverify-r1 all − D − U2i-android − U2o-android: Compose 93/93 green (compileDebugKotlin executed)
revertStatesCompiled: reverify-r1 all − U2i-android: Compose 98/98 green (compileDebugKotlin executed)
revertStatesCompiled: reverify-r1 all − U3-ios: Swift 108/108 green
revertStatesCompiled: reverify-r1 FULL + U1-ios-LRM: Swift 113/113 green; [FULL + LRM − LRM] == FULL and [FULL + LRM − LRM − U1-ios] == [all − U1-ios] (code snapshots)
- Every state was built forward and reverse, and the two builds are byte-identical (`reverify-r1/revert-states.txt`).
- The worktree is left in the FULL state (snapshot 95b372927a9a8b2e).

### reverify-r1 mutation brackets (written as they ran; paths relative to the first `TREES:` line, the re-verify worktree; a copy is in `reverify-r1/mutations.txt`)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 d961bed609de3cf2146bdc735ac571265b2fb7516ceb63335c32cd7c07919b26 — RM1 (= the fix's M5', U3-ios): FontVariantNumericApplier.contribute reset to the identity (_ = cfg; _ = agg)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 d961bed609de3cf2146bdc735ac571265b2fb7516ceb63335c32cd7c07919b26
  RM1 result: mutated → RED, 8 assertion failures in 4 tests / 5 (FontVariantNumericTabularTests testTabularNumsSetsTheFlag ×4, testProportionalNumsClearsTheFlag ×1, testStyleBuilderMirrorsTheVerbatimCarrierIntoTextConfig ×2; FontVariantNumericTabularRasterTests testTabularNumsWidensTheDigitRun: 53 is not greater than 53) — the fix's M5' reproduced; the restore's green is the RM2 run's FontVariantNumericTabularTests 4 / 4
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — RM2 (= the fix's M9', U3-ios seam 6): the seam-6 ComponentRenderer.swift hunk reverse-applied (labelFont no longer composes monospacedDigit)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
  RM2 result: mutated → RED, 1 failure / 5 (FontVariantNumericTabularRasterTests testTabularNumsWidensTheDigitRun: 53 is not greater than 53) while FontVariantNumericTabularTests stays 4 / 4 GREEN in the same run (the RM1 restore verified) — the fix's M9' reproduced; the restore's green is the final FULL Swift run
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — RM3 (= the fix's M10b', C10 reachability line under seam 4's re-worded comment): ComponentRenderer.kt at its 611c9f93 bytes (no seam 4, no seam 5: nothing names ListMarkerSuffixGap)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
  RM3 result: mutated → RED, 3 / 99 (SeamReachabilityTest 'every module wave 49 added to the compose runtime has a live call site': unreachable from ScreenshotCaptureScreen (no live call site): [lists/ListMarkerSuffixGap.kt]; ListMarkerSuffixGapHangSeamTest; ListMarkerSuffixGapRowSeamTest; compileDebugKotlin executed) — the fix's M10b' reproduced; restored → GREEN, 0 failures / 99
<!-- reverify-r1 brackets: end -->

## Delivered patches (fix-r1 re-cut; sha256 of the file bytes, written after the final restore)
patch: unit-U1-ios.patch sha256 cb4d2c4464c4139d19fc680da4c9113c7a49fe59ddd90390085f1fd5a79f15c3
patch: seam-1.patch sha256 c1c3c867c9ec05b4d0f994cf11d798cd4a20566b4e9f661ca37466afe96dd923
patch: unit-U2o-ios.patch sha256 9619a83568b2c828b7b42682fda2fafb072eed1a17a1a0889c576b40179f89e3
patch: seam-2.patch sha256 2ccddf4b092b6f31e56a83fc06d04612b47ec131a533d2a27d48eb51d24281cb
patch: seam-3.patch sha256 bab3a321fe7f8f014cf41fd38551639eb3da40d7bdc94f967ab0ee3edb63f763
patch: unit-U2o-android.patch sha256 76e64cef6609f93ad0165b95ad7a2b2c3942bbc2f6733be29dcc3c4be94bdada
patch: seam-4.patch sha256 cd0e5e6fe3d612a30d4127d90898301703c0426fc7544e8b4ef2687d141591b0
patch: seam-5.patch sha256 50b4282398f82a6a87e793bef7b91b72cb0282f8fe129bf116113ba3d996dbf0
patch: unit-U3-ios.patch sha256 7aafd3606f6eaad41af0be8e8941065e6c870e41d395f2808400c48c79a8ee56
patch: seam-6.patch sha256 636b56fd26082bdf4eefb8047263c0a9fdeb62a8e235b37339610fac448cf645
patch: unit-D.patch sha256 4b7ac2977f745e60524740934ba6b5e9a2db08dfa8784760345c1054291e1a58
patch: unit-U1-ios-LRM.patch sha256 eaaea484e9273925f6b607fef6459f05b26ca7935d68fcf7be639fe7dfc2cfa8

STATUS: COMPLETE
