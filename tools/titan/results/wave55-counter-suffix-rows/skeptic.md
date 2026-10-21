TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-skeptic-L5

# wave55 L5 counter-suffix-rows — SKEPTIC report

Scope: the builder's `_note.md` (STATUS: COMPLETE), its 12 patches, PLAN §0 / §2 L5 / §3 / §4, `expectations.json`
lanes["L5-counter-suffix-rows"] (sha256 980542d0…, the round-4 bytes on the shared tree) and `landing-manifest.json`.
Everything below was EXECUTED in my own detached worktree off 611c9f93 (the `TREES:` line; provisioned per PLAN §0, Swift
builds with `-derivedDataPath <wt>/.dd`). Nothing was committed; the lane's patches and `_note.md` were not edited; the only
shared-tree writes are this file and the `skeptic-*` evidence files in this lane dir. No device, simulator, emulator,
Chromium, gate-driver, test-all or feed step ran.

## Verdict: MIXED

The code, the pins, the census, the patch series and the revert states hold — every claim I re-ran reproduced exactly. One
prediction does not: **U3-ios moves pixels that the plan's own base-identity zones forbid**, so on a CORRECT stage-1 picture
the gating key `[T] counter-suffix ios` and the control `[K] counter-suffix ios` print GEOMETRY WRONG (executed on a modeled
picture through the real probe). The lane predicted both OK at MED-HIGH. Without a plan regeneration (or a pre-registered
decision) the stage-1 rule "a gating key not OK reverts ITS unit" reverts U3-ios although its picture moves toward the ref.

## Defects (ranked)

### 1. MUST-FIX (before stage 1) — U3-ios's pixel radius is wider than the [T] / [K] ios zones allow
- **What.** `Font.monospacedDigit()` makes CoreText apply the face's OpenType `tnum` to the WHOLE run, and Inter's `tnum`
  substitutes punctuation as well as digits (61 substitutions; `skeptic-inter-tnum.out.txt`): `period` → `period.tf`
  (advance 4.609 → 4.297, ink x 1.25..3.36 → 1.12..3.23) and `two` → `two.tf` (ink 1.20..8.62 → 1.48..8.89). U3-ios's six
  carriers are the `.` runs of rows 9–10, row 9's `1`, row 10's `2` and the `א.` / `ב.` runs of rows 11–12. So U3 changes
  row 9's period (x133-135, INSIDE `ROW9_OUT` x0-135 × y208-231, which `[T]` reads against the base) and rows 10–12
  (inside `[K]` ios's y232-599 base-identity zone). The plan's premise "U3-ios changes the digit's glyph, never the row's
  other ink" (`counter-suffix-rows.geometry.py` round-2 header; the ok-all replay grafted only Android's digit) is false,
  and the lane inherited it: its probe (`probe-monospacedDigit.*`) measured only `1` / `1111`.
- **Evidence (executed).**
  - Catalyst, the REAL label path, the VERBATIM wave54-final counter-suffix per-test IR, harness Inter faces registered,
    every L5 patch applied — with vs without the 6 `FontVariantNumeric` declarations (`skeptic-probe-u3.{swift.txt,out.txt}`):
    160 px differ, all in rows 9–12 at x133-146 (row 9 13+ px incl. the period, row 10 48 px, rows 11 / 12 8 / 10 px at
    x133-135 = the periods). Per run: `.`, `1`, `2`, `א.`, `ב.` each render differently with the flag. Catalyst "without"
    reproduces the device geometry of rows 9–12 (period x133-134, proportional `1` x138-141).
  - The real probe on a modeled stage-1 picture (`skeptic-model-u3.py` → `skeptic-model-u3.out.txt`: the wave54-final iOS
    capture with exactly U3's Catalyst delta grafted, probe copied byte-identical into a private mirror):
    `[T] counter-suffix ios … → GEOMETRY WRONG (zone x0-135×y208-231 differs from wave54-final (10 px))` and
    `[K] counter-suffix ios … → GEOMETRY WRONG (zone y232-599 differs from wave54-final (79 px))`.
  - The ref really uses the tabular glyphs (wire: the `.` runs are 4.3 px = `period.tf`), so the move is toward the ref:
    an instrument / prediction defect, not a code defect.
- **Fix.** (a) Lane: amend the note's prediction rows (`[T]` / `[K]` ios WRONG-by-zone at stage 1 as the plan stands) and its
  window request; file a plan-regeneration request. (b) Plan (orchestrator, `plan-build.py` + the probe, regenerated with
  `plan-prose.py --fill/--check`): carve the six U3 run boxes (rows 9–12, x≈130–150) out of `ROW9_OUT` and `ZONES['ios']`,
  and read them with a ref-anchored glyph rule instead (row 9–12 period ink at the ref's x ±1, row 10's `2` tabular), with a
  fake for the class in the standing set; or pre-register in `stage1Decision` that a `[T]` / `[K]` ios line whose ONLY
  differing pixels lie in those six run boxes is decided by the step-8 look, never by rule. Narrowing the unit cannot fix
  it: `tnum` is per run, and a digits-only gate would still move row 10's `2` (and leave the periods off the ref).

### 2. SHOULD-FIX (orchestrator / plan, pre-registration) — the twin `ink:` reads of `[T]` ios and `[H]` ios reject Catalyst's correct glyphs
- `skeptic-model-u3.out.txt` `skmodel2` (U3's delta grafted inside the digit window only, i.e. the picture the plan assumes):
  `[T] counter-suffix ios … ink 13 vs wave54-final android → GEOMETRY WRONG` — CoreText's `one.tf` vs Android's raster.
- `[H]`: Catalyst's correctly ordered marker zone (letter x46-53, period x57 = the ref's x) grafted onto the device capture
  reads `ink 47 vs wave54-final android → GEOMETRY WRONG` (mirror probe run; INCONCLUSIVE for the device: part of the 47 px
  is Catalyst-vs-device rasterization).
- The lane flagged this exposure (its D39 observation), so this is not a lane honesty defect. The gap is the rule text:
  `stage1Decision` says "[H] ios WRONG … → the LRM fallback re-cut" AND "an [H] / [T] native line whose only reason is the
  twin `ink:` … is decided by the recorded look". With defect 1 unfixed, `[T]` ios's reason is a zone (not twin-only), so the
  look route does not apply. Fix: state which rule wins for `[H]` ios (twin-only reason → look first, LRM only on a
  geometry reason), and fix defect 1 so `[T]` ios can only fail twin-only.

### 3. NIT — `seam-4.patch` SeamReachabilityTest comment
"the string-keyed marker gap whose ONLY caller is the renderer seam (seam-4)" is false once seam 5 lands (two callers), and the
lane's own M10 (reproduced) shows the line cannot fail on a seam-4-only revert. Reword ("whose callers are seams 4 / 5;
ListMarkerSuffixGapHangSeamTest pins seam 4 itself"). Comment-only; touches a patch-borne line.

### 4. NIT — `FontVariantNumericApplier.swift` header understates the reach
"Harmless for the 6 carriers (`white-space: pre` single-glyph runs)": two of the six are two-glyph runs (`א.` / `ב.`), and
the header never says that `tnum` also swaps the period (`period.tf`) — the fact behind defect 1. One sentence.

### Disclosed deviations I checked and accept (not defects)
Patch-borne seam pins (4 iOS rasters, 2 Compose source pins) and the comment hunks in seams 3 / 5 are outside §3's table but
inside `deliveryContract` (one seam file + its patch-borne test files); `land-units.sh --check` (full, all lanes) accepts
them; each new path is touched by no other lane. The queued stale comment in `ListMarkerOutsideHangTest.kt:89` is
outside L5's `own:` and is correctly left alone.

## Repros (executed; outputs)

**(a) Pins — every mutation replayed, bracketed, red → byte-exact restore → green.** Pre-mutation sha256 values equal the
builder's (my worktree = the builder's FULL state: ComponentRenderer.swift 2d31f256…, ComponentRenderer.kt 748963a1…).
Batched where the red sets are provably disjoint; each restore re-verified green in the next run.
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerSuffixGap.swift sha256 58e30a1b33c633c8edd0c2c0024a6c2ac9a8d8da2c196348e427811a796ef307
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 1eef5ec24bcfe71eb5b1b699d277b1c26f61eaeb87b33fac70392e8180006bef
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerSuffixGap.swift sha256 58e30a1b33c633c8edd0c2c0024a6c2ac9a8d8da2c196348e427811a796ef307
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/font-variant/FontVariantNumericApplier.swift sha256 1eef5ec24bcfe71eb5b1b699d277b1c26f61eaeb87b33fac70392e8180006bef
      M1+M3+M5 result: mutated → RED (log swift-mA): M1 ListMarkerBidiTests 3/10 (testHebrewMarkerUnderAnLtrItemIsLtrIsolated, testNeutralBulletUnderAnRtlItemIsRtlIsolated, testPaintedHebrewMarkerPaintsThePeriodAfterTheLetter) + ListMarkerBidiSeamRasterTests (period x81-83 before letter x88-104); M3 ListMarkerSuffixGapTests (2 assertions 4.0≠0.0) + HangRaster (both rows x29) + InsideRaster (both rows text x271); M5 FontVariantNumericTabularTests 3 tests / 7 assertions + TabularRaster (53 = 53); 16 failures / 23 tests
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M9 (seam-6 hunk reverse-applied)
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 074f227c4e6dabfed388b180d63926a5bae1b5a3f4d2c8f8353cc5b058d4353b
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
      M2 result: mutated → RED, 5 assertions in 2 tests / 10 (testMarkersWhoseNaturalDirectionMatchesAreUnchanged ×4: '1.' '일,' '一、' rtl 'א.' wrapped; testPaintedIsIdempotent double isolate); seam rasters stay green under always-wrap. M9 result: mutated → RED, FontVariantNumericTabularRasterTests 53 = 53; FontVariantNumericTabularTests 4/4 and ListMarkerSuffixGapTests 4/4 GREEN in the same run (the M5 / M3 restores verified green). 6 failures / 23 (log swift-mB)
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M8+M6 (seam-3 then seam-1 ComponentRenderer hunks reverse-applied)
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
      M8+M6 result: mutated → RED, 2 failures / 23 (log swift-mC): M6 ListMarkerBidiSeamRasterTests (period x81-83 before letter x88-104) while the pure ListMarkerBidiTests stay 10/10 green (also the M2 restore verified); M8 ListMarkerSuffixGapInsideRasterTests (both rows' text x271) while the hang pin ListMarkerSuffixGapHangRasterTests stays green (seam 2 still on: the seams fail independently); FontVariantNumericTabularRasterTests green (the M9 restore verified)
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2 — M7 (seam-2 ComponentRenderer hunk reverse-applied)
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 2d31f256facef30a4ac7a568f0ff44a8c9300eba39e47db43afc1aee9f0894c2
      M7 result: mutated → RED, 1 failure / 8 (log swift-mD): ListMarkerSuffixGapHangRasterTests both rows' marker x29; ListMarkerSuffixGapInsideRasterTests green (seam 3 still on); ListMarkerBidiSeamRasterTests green (the M6 restore verified)
    MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerSuffixGap.kt sha256 8076fff4e9967b80b6e75d144aa0428a770d6cba33b82df82b0236f4abb99622 — M4 (gapDp always the stand-in)
    MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M11 (seam-5 ComponentRenderer.kt hunks reverse-applied)
    RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListMarkerSuffixGap.kt sha256 8076fff4e9967b80b6e75d144aa0428a770d6cba33b82df82b0236f4abb99622
    RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
      M4+M11 result: mutated → RED, 2 / 88 (log compose-mA): ListMarkerSuffixGapTest 'an ideographic comma suffix takes no gap' (M4) and ListMarkerSuffixGapRowSeamTest (M11); SeamReachabilityTest and ListMarkerSuffixGapHangSeamTest green (seam 4 still names the module)
    MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M10/M10' (seam-4 ComponentRenderer.kt hunk only reverse-applied)
    RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
      M10/M10' result: mutated → RED, 1 / 88 (log compose-mB): ListMarkerSuffixGapHangSeamTest; SeamReachabilityTest stays GREEN (the builder's M10 finding reproduced: seam 5 keeps the module reachable); compileDebugKotlin executed
    MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d — M10b (ComponentRenderer.kt at its 611c9f93 bytes)
    RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 748963a1b9a41ef6be5347b4f3c28d73dabd7104aadedbd5488483722381125d
      M10b result: mutated → RED, 3 / 88 (log compose-mC): SeamReachabilityTest 'every module … has a live call site' (lists/ListMarkerSuffixGap.kt unreachable), ListMarkerSuffixGapHangSeamTest, ListMarkerSuffixGapRowSeamTest
    MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 50d00033b5f0f0498ff11fadf099126793cf341bc987e02af9b904c8276511cb — M12 (FULL + unit-U1-ios-LRM; painted returns its input)
    RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerBidi.swift sha256 50d00033b5f0f0498ff11fadf099126793cf341bc987e02af9b904c8276511cb
      M12 result: mutated → RED, 4 / 11 (log swift-mE): testHebrewMarkerUnderAnLtrItemIsLtrIsolated, testNeutralBulletUnderAnRtlItemIsRtlIsolated, testPaintedHebrewMarkerPaintsThePeriodAfterTheLetter, ListMarkerBidiSeamRasterTests (period x81-83 before letter x88-104)

Final FULL state after every restore: Swift 113 / 113 green (17 classes: the 7 L5 + the 10 existing list / fidelity
suites), Compose 99 / 99 (`*ListMarker*`, `*SeamReachabilityTest*`, `*KoreanHangulFormal*`, `*ListStyle*`), node
counter-style-bake + counter-style-author 44 pass / 0 fail / 1 skipped — the note's exact counts.

**(b) Census — my own script** (`skeptic-census-l5.py` → `skeptic-census-l5.out.txt`, written from the renderer sources:
iOS ComponentRenderer.swift :3837-3875 marker decision, Compose :3549-3610 `liKeepsItsMarker`, type fold own > parent > UA,
inherited `ListStylePosition` / `Direction`, ListMarkerBidi's rule re-implemented AND cross-checked against the real
`unicodedata.bidirectional` (0 disagreements on all baked markers); native tables grepped for U+3001 at 611c9f93: 0 hits).
1435 documents; marker rows iOS 217 baked + 234 table, Android the same; U1-ios 8 rows / 2 documents (counter-suffix `א.`
`ב.`; disclosure-styles 6 rtl `•`, inside); U2o 2 / 1 per native; U2i 34 / 3 per native (-001 9, -004 23, -005 2); overlay
0; U3-ios 6 / 1; no unbaked `hebrew` item and no `list-style-type` string ending in U+3001 anywhere; `FontVariantNumeric`
declared only in counter-suffix. **Every unit's capture set equals `expectations.json` on every platform; captureCarriers
web 0 / ios 5 / android 4 identical; wire 0. Under-reported: none.** The same script over wave55-open prints the identical
census (header aside). The CAPTURE radius holds; the PIXEL radius of U3 inside its one capture does not (defect 1).

**(c) Cells** (`node tools/titan/results/wave52-gate/cells.mjs … wave54-final` and `… wave55-open`): counter-suffix
ios P 0.9873 / android P 0.989 / web P 1; css3-counter-styles-005 ios P 0.993 / android P 0.9759 / web P 1;
disclosure-styles ios f 0.6127 (android f 0.6255, web f 0.6454); -001 / -004 natives unscored (web P 0.9933 / 0.9878).
Identical at wave55-open. Every number the note cites resolves.

**(d) PNGs opened** (ref / iOS / Android, wave54-final). counter-suffix: iOS rows 3–4 read `.א foo` / `.ב bar` (period x48,
letter x51-57); rows 5–6 marker ink x29 on both natives vs the ref's x33 with a visible gap before `foo`; row 9 iOS `1`
x138-141 (no foot) vs ref x138-144 / Android x139-145; rows 7–8 korean within 1 px on all three. -005: marker x217 on
all three; item text x268 ref vs x271 on both natives; Android's lines 2–4 px low with lighter strokes. All as the note
describes.

**(e) Code review.** No silent fallthrough found (`proportional_nums` → false and tested; `normal` → unset; the
`ownListMarker` TODO breadcrumb is in ListMarkerBidi's header); new files ≤ 200 lines (largest ListMarkerBidiTests 184,
ListMarkerBidi 158); over-300 files get call sites / a defaulted parameter only (StyleBuilder +2, ComponentRenderer.swift
6291 → 6317, .kt 8488 → 8496); no scratchpad pointer and no probe leftover in any patch (`print(` lines are the tests'
logged measurements); every patch path is inside `own:` or patch-borne; seam headers name 611c9f93 and the true base
sha256 (swift 417853ce…, kt 2445d9ea…, SeamReachabilityTest 7a1b061f…, all recomputed). **Rule 2b executed**: the four
unit patches + D with NO seam patch compile and pass (Swift 60 / 60 focused, Compose 86 / 86). **D is comment-only by an
independent string-aware stripper** (all 6 D files + both ListMarkerRow docs CODE-IDENTICAL to 611c9f93; negative control:
TypographyAggregate.swift reads CODE-CHANGED). D's factual claims re-checked: the only rtl `<li>` are disclosure-styles'
6, all `inside`; the Compose twin symbols (`KOREAN_HANGUL_FORMAL`, `typeFromKeyword` "korean_hangul_formal",
`ListStyleApplier.getMarker`) exist; `Placeable.place` is not mirrored. The seam-2 / seam-3 gap reads the RAW `markerText`,
so a U1-wrapped U+3001 marker (… PDI) still gets gap 0 — correct.

**(f) Revert states — every one re-run on my worktree** (`git checkout -- .` + `git clean` on the owned dirs + the kept
patches applied in landing order):
- FULL: Swift 113 / 113 · Compose 99 / 99 · node 44 / 0 / 1 (as noted)
- all − U1-ios (C12: U2i-ios applied): Swift 102 / 102
- all − D: Swift 113 / 113 · Compose 99 / 99 · node (bake + author + counter-bake) 72 / 0 / 1
- all − D − U2i-ios: Swift 112 / 112;  all − D − U2i-ios − U2o-ios: Swift 106 / 106;  all − U2i-ios: Swift 112 / 112
- all − D − U2i-android: Compose 98 / 98;  all − D − U2i-android − U2o-android: Compose 93 / 93;  all − U2i-android: Compose 98 / 98
- all − U3-ios: Swift 108 / 108
- FULL + U1-ios-LRM: Swift 113 / 113 (and M12 red 4 / 11 on it)
- Byte compares: FULL + LRM with [U1-ios-LRM, U1-ios] reverse-applied == the forward all − U1-ios state; FULL + LRM − LRM ==
  FULL (whole diff + untracked set identical).
Every count equals the note's `revertStatesCompiled:` lines.

**(g) Patch set.** All 11 landing patches apply on 611c9f93 in landing order; `bash tools/titan/results/wave55-plan/
land-units.sh --check` (full, all seven lanes, read-only) → "0 error(s), 1 warning(s) — landable" (the warning is L4's
gitignored logs): L5's seams stack on L4 (swift) / L4-L2-L3 (kt), seam 6 applies after L1 NB-ios, every L5 revertOrder
reverse-applies on the landed series, the contingency applies on top. Each unit patch's path list equals the note's unit
section and the manifest's `unitPatch` / `seams`. All 12 `patch:` sha256 lines match the file bytes.

**(h) Honesty.** No flip is claimed; nothing is labelled picture-correct by replay. Two over-statements: the `[T]` / `[K]`
ios "OK" predictions (defect 1), and counter-suffix "picture-correct" remains bounded by `[L]` — rows 1–2 still paint the
proportional `1` / `2` on both natives (the note says so). The geometry self-test re-ran: `geometry-gate.py wave54-final
--base wave54-final --lanes L5 --self-test` → HOLDS, exit 0 (gating 6 / 6 FAIL, control 16 / 16 PASS, report 2 / 2 FAIL).
The wave-54 `[M]` and lists-bakes counter-suffix ios controls stay OK on my U3 model.

## What I could not check
- Device / simulator behaviour (out of scope by rule). Defect 1 rests on Catalyst CoreText + the registered Inter face; the
  device uses the same CoreText and registers the same face, but the device capture is the evidence.
- Whether `[H]` ios's twin `ink:` read accepts the device's reordered glyphs (my Catalyst-graft model is inconclusive).
- The combined landed tree (other lanes' code compiled together with L5) — S1's job; I ran only `land-units.sh --check`'s
  textual series and revert dry-runs.
- The brief's replay SSIM numbers were not re-derived (they do not model the period / `2` change either).
