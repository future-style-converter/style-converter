# Wave 52 · L8 vertical-wedges — lane note

Plan: `tools/titan/results/wave52-plan/PLAN.md` §2 "L8", §3, §4 step 5, §9 C4. Brief: `wave52-plan/vertical-wedges.md`
§3 M-A/M-B/M-C/M-E/M-G (M-F belongs to L3). Run of record: `wave51-fix`. Nothing here ran on a device.

This lane was started twice before (usage-limit kills). On resume the tree held the earlier self's M-A/M-B/M-C/M-G
runtime edits, `VerticalInlineAxis.{kt,swift}` + tests and `census.{mjs,json}`; no note, no seam patch, no
verification. Kept all of it after reading it; fixed one real defect (below); finished M-A's Compose install,
the M-B call site, the iOS M-C seam, M-E on both twins, every pin, every mutation, the replay and this note.

## Fix pass (skeptic.md, 2026-10-05)

| # | skeptic finding | resolution | proof |
|---|---|---|---|
| 1 MUST | Compose M-A sent EVERY non-singleton, non-document family to the Inter loader — `FontFamily.Default` too, which the label paints as Roboto (`font-family: Arial` / `system-ui` / `fantasy`): 10ch @20px = 126 px around a ~111 px run, a product regression vs HEAD | `ChUnitMetrics.kt`: new `paintsInter(f)` = `f == null \|\| f == InterFontFamily`; only those take the loader (`cacheName` "inter", `resolveTypeface` → `defaultMeasuringTypeface()`); Default and every other family measure `Typeface.DEFAULT`, keyed "default" — HEAD's measurement, the face they paint. seam-1 unchanged (stays ungated: with the split, product measurement now equals paint on every surface) | pin `ChUnitMetricsTest › a FontFamily-Default label keeps the face it paints` on VERBATIM wave51-fix payloads (`["Arial"]` bidi-plaintext-br-001, `["system-ui"]` disclosure-styles, the 8-name harness stack) — `cacheName(Default) == "default"` with the loader installed (the skeptic's exact repro) + a counting loader proves the ROUTE (0 calls for Default, 1 for null). Mutations C8 / C9 / C11 / C1b all FAIL on assertions, SHA-OK (`_mutations-fix.log`) |
| 2 should | iOS M-A population mis-stated ("face-less"): the gate is design `default` — `sans-serif` too | new `census-family.mjs` → `census-family.json` models both natives' RESOLVED family (twins of `CssFontFamilyResolver` / `FontFamilyExtractor`): **iOS movers 21 docs / 76 carriers** (the 21st = `css-text/hyphens/hyphenate-character-005`, `["sans-serif"]`); **Compose movers 18 docs / 73 carriers, all face-less (`null`), 0 InterFontFamily, defect population (Default) 0** — reproduces the skeptic. `hyphenate-character-005 ios` added to `watchlist-additions.txt` (watchlist-check: 8 lines, unmatched 0). Swift ChUnitMetrics comments now say "design default"; iOS has no twin of defect 1 (its label paints Inter for EVERY design-default family, `ComponentRenderer.swift` `fontDesign == .default` → `.custom("Inter")`) | census re-run; watchlist-check |
| 3 should | Compose M-C fallback read `LocalContainingBlock.heightPx`, published on EVERY surface — the note's "off the composed path neither is published" was false; iOS gates on `wptCaptureMode` | `VerticalTextFlowLayout.kt`: new pure `uprightFallbackBudgetPx(wptCaptureMode, cb, icb)` — null unless `LocalWptCaptureMode` (the Swift twin's gate); comments corrected. Capture paths unchanged (composed + inbox both set the flag), product/dark stage back to HEAD's decline | pin `UprightOrthogonalBudgetTest › the fallback is WPT-capture-only, like the Swift twin` (product → null → 55×72 decline; capture → ICB 568 / definite 300); mutation C10 FAILS (expected null, was 300.0), SHA-OK |
| 4 nit | committed 003/004 replay PNG was the without-M-E picture | `png-replay.mjs` also writes `replay-<stem>-<plat>-withME-LB24.2.png` (003/004/007/008 × 2); re-run reproduced `png-replay.json` byte-identically and every prior PNG unchanged | `cmp` |
| 5 nit | "every corpus ch carrier … is regular weight" false | ChUnitMetrics.kt comment corrected (text-decoration-inset-004's bold h1, wrap unchanged) | — |
| 6 nit | stale Swift "NO iOS CALLER TODAY"; `import SwiftUI` between doc line and enum; file sizes | `TableBoxTree.swift` comment now names `columnChains` as the caller; import moved above the doc comment. Sizes NOT split (below) | Swift export 82/82 green |
| 7 nit | input-range Android HIGH without a raster; M-E maps columns by cell index (colspan / `span` ignored silently) | Android confidence lowered to MED-HIGH. colspan / `<col span>`: the IR v2 wire carries NEITHER (0 corpus components with `meta.attrs.colspan`/`span`; colspan-004's colspans exist only in its HTML), so the runtime has nothing to breadcrumb against — a wire change (v3-gated), not a runtime one | corpus scan |

Fix-pass runs: isolated HEAD export (HEAD + every Compose lane file + hunk-for-L4-1 + seam-1 + seam-3):
`_fix-export-jvm.log` 49/49 green (8 classes incl. `ChUnitInlineAxisSpacingContextTest` 2/2, `CssFontFamilyResolverTest` 10/10);
mutations `_mutations-fix.log` (script `mutate-fix.sh`). Shared tree: `_fix-intree-jvm.log` (compileDebugKotlin
executed, 47/47 green, 7 classes); seam-1 re-verified under the lock with the fixed ChUnitMetrics
(`_fix-seam-1-verify.log`: compileDebugKotlin executed, exit 0, sha c4369165… before = after, SEAM==HEAD). Swift
export (HEAD + every Swift lane file + seam-2 + seam-4, re-synced to the tree byte-for-byte): `_fix-xexport-swift.log`
82/82 green. No seam patch changed in this pass.

## What changed and why

| mech | spec | Compose | SwiftUI |
|---|---|---|---|
| M-A — ch measures the face the label paints | css-values-4 §6.1.1 "in the font used to render it" | `spacing/ChUnitMetrics.kt`: the two families the label paints as Inter — null (face-less) and InterFontFamily — measure Inter Regular through an installable loader (`paintsInter`, fix pass); `FontFamily.Default` and every other family keep `Typeface.DEFAULT` (what they paint). `bindInterFace(context)` installs the loader from the renderer (**seam-1**) — the static chain holds no Context. No loader → historical DEFAULT, logged once | `StyleEngine/spacing/ChUnitMetrics.swift`: the default design (face-less, `sans-serif`, any non-generic name — all painted as Inter) measures `UIFont(name: "Inter")` when registered (the label bridge's own lookup), SF otherwise; memo key `inter`/`default` |
| M-B — ch along the inline axis | css-values-4 §6.1.1 "advance width or height, whichever is in the inline axis"; css-writing-modes-4 §5.1 | `measure(…, inlineAxisUpright = false)` (defaulted — every caller compiles): upright → round(ascent)+round(descent). Call site `StyleApplier.buildSpacingContext` is L4's file → **hunk-for-L4-1.patch** (PLAN §9 C4: ships in L8's PR after L4 merges) | `zeroAdvancePx(…, inlineAxisUpright:)`; call site in the StyleBuilder ch block (owned) |
| shared decision | — | `typography/text/VerticalInlineAxis.kt` (new, pure): `chAdvanceIsVertical`, `orthogonalBudget`, `uprightBudget` | `StyleEngine/typography/writing/VerticalInlineAxis.swift` (twin) |
| M-C — upright run's §7.3.1 budget | css-writing-modes-4 §7.3.1 (definite ancestor block size, else the ICB) | `VerticalTextFlowLayout.kt` resolves `LocalContainingBlock.heightPx` → `LocalComposedViewport.heightPx`, gated on `LocalWptCaptureMode` by `uprightFallbackBudgetPx` (fix pass — the iOS twin's gate), and hands it to `uprightFlowMeasurePolicy(fallbackBudgetPx)` (`VerticalRunIntrinsics.kt`) — no seam needed | `VerticalTextFlowLayout.swift`: `VerticalUprightGate.budgetPx(style:viewport:wptCaptureMode:)` (own definite height → containing block → ICB; nil outside capture); a bounded proposal still wins. **seam-2** passes it through PlaceholderLabel AND vetoes the WPT N×L line-box pin for a run that will plan (without the veto the planned column was clamped back to 25 px) |
| M-E — `<col width>` harvested | css-tables-3 §2.1 / §3.2 | `table/TableBoxTree.kt`: `columnChains` / `columnWidthsPx` (UA-only col/colgroup, the `rowsOf` drop rule; col ch measured along the col's own axis); **seam-3** gives each cell `widthIn(min = column width)` in `RenderTableContent` | `StyleEngine/table/TableBoxTree.swift`: same harvest + `cellColumnWidths` + the `\.tableCellColumnWidths` environment key; **seam-4** publishes it from the table plan and folds it into each cell's unset `min-width` |
| M-G — zero used box paints no UA atom | css-sizing-3 §5.1 / CSS 2.1 §10.2 | `widgets/UAWidgetsResolve.kt`: `resolve` → null when the merged list's used Width or Height is exactly 0px (`hasZeroUsedBox`), logged once | `StyleEngine/widgets/UAWidgetsResolve.swift` twin |

Defect fixed on resume: the earlier self appended `fallbackBudgetPx` AFTER `onDecline`, breaking every trailing-lambda
caller of `uprightFlowMeasurePolicy` (`VerticalRunIntrinsicsTest` did not compile). Reordered (default param before the
lambda). Comments that claimed `text-orientation` inherits were corrected: it is in NEITHER native's inherited set
(Compose `INHERITED_PROPERTY_TYPES`, iOS `InheritedText.inheritedTypes`) — twin-identical; see "not verified".

NOT built (stated, not hidden): M-G's second clause ("size the atom to a declared px box otherwise"). Every post-load
widget wire carries px Width/Height, so it would move every passing widget cell; its census was not taken, it is not
needed for the target flip, and the replay shows the target passes without it. Left for a lane that can price it.

## Seam patches and hand-offs (all in this directory)

| file | target | verified |
|---|---|---|
| `seam-1.patch` | Compose `ComponentRenderer.kt` ~:1351, 1 hunk (bindInterFace before applyProperties) | `git apply --check` on HEAD ✓; in-tree under the lock (`_seam-1-verify.log`: compileDebugKotlin ran, 6 JVM classes green, SHA-OK) ✓; HEAD-export with hunk + seam-3 ✓ |
| `seam-2.patch` | iOS `ComponentRenderer.swift` 4 hunks (call site ~4170, PlaceholderLabel var, line-box-pin veto ~4908, flow call ~4970) + new `UprightChOrangeRasterTests.swift` | HEAD ✓, HEAD+seam-4 ✓; in-tree under the lock (`_seam-2-verify3.log`: orange 120×121, SHA-OK; `_seam-2-verify-full.log`: the full lane pin list, 81 Catalyst tests green, SHA-OK; `_seam-2-verify-final.log`: the same with the FINAL patch, whose raster pin is tightened to 121 ± 1 — 81 green, SHA-OK) ✓; Swift HEAD-export with seams 2+4: 82 tests green (`_xexport-swift-run1.log`) |
| `seam-3.patch` | Compose `RenderTableContent` 2 hunks | HEAD ✓, HEAD+seam-1 ✓; in-tree under the lock (`_seam-3-verify.log`) ✓; HEAD-export ✓ |
| `seam-4.patch` | iOS 3 hunks (@Environment, table-plan publish ~1638, cell min-width fold ~1058) + new `UprightColTableRasterTests.swift` | HEAD ✓, HEAD+seam-2 ✓; in-tree under the lock (`_seam-4-verify.log`: green cell 120×120, SHA-OK; `_seam-4-verify-full.log`: 81 Catalyst tests green, SHA-OK) ✓; Swift HEAD-export ✓ |
| `hunk-for-L4-1.patch` | `StyleApplier.kt` `buildSpacingContext` (passes `inlineAxisUpright`) + new `ChUnitInlineAxisSpacingContextTest.kt` (red without the hunk, so ONE commit) | applies on HEAD ✓ and on the shared tree carrying L4's backface edit ✓; HEAD-export: compiles, 2/2 green ✓. **L4: this edits your file's `:805-830` region in L8's PR after you merge (PLAN §9 C4); 270 lines from your hunk.** |
| `ma-exclude-ios.patch` | the M-A exclude-direction build for the device A/B (iOS); Compose's exclude-direction = seam-1 not applied | applies on the lane tree ✓; NEVER committed |

No seam patch touches a line of another lane's §3 hunk. Landing: §4 step 5 (after L4). Seam-1/3 are independent
of each other, as are seam-2/4.

Log hygiene: `_seam-2-verify3.log` / `_seam-4-verify.log` ran the raster pin only (a zsh unsplit argument list);
the `-full` re-runs carry the whole list. `_seam-2-verify.log` / `-verify2.log` are the FIRST seam-2 cut, whose
raster pin failed at 120×25 — that failure is how the WPT line-box pin clamp was found and vetoed. `_jvm-run1.log`
is an aborted invocation (no `timeout` binary).

Isolated verification: because twelve lanes share the tree, every mutation ran in a private `git archive HEAD` export
(scratch) carrying only this lane's files + patches (`lane-files.txt` lists them): shared files were never mutated.

## Census (`census.mjs` → `census.json`, 1435 docs / 30 sections; letters W/I/A = passing)

- M-A/M-B: `"u":"CH"` 74 docs, 18 with no FontFamily anywhere (brief: 74/18 ✓). Per carrier: 79 face-less ch carriers
  in 20 docs — 18 docs carry it in a SIZING slot (both natives measure it), 2 only in padding (`css-text/bidi/bidi-lines-001
  [WIa]`, `-002 [WIA]`): iOS measures padding/margin ch, Compose only sizing → **2 extra iOS at-risk cells** the brief missed.
- M-A by RESOLVED family (fix pass, `census-family.mjs` → `census-family.json`, the gates as shipped): 169 ch carriers.
  Compose sizing slots resolve to `monospace` 77 / `null` 73 — **movers (null + InterFontFamily) 73 carriers in 18 docs**;
  `FontFamily.Default` (the defect's population) **0**. iOS size+padding+margin slots: design `monospaced` 83 / `default` 76 —
  **movers 76 carriers in 21 docs** = the 20 face-less docs + `css-text/hyphens/hyphenate-character-005` (`["sans-serif"]`,
  `margin-left: 10ch` @32px, ≈ +9.9 px; iOS f 0.945 today — added to the watchlist). 11 corpus docs carry a family that
  resolves to `FontFamily.Default` on Compose (bidi-plaintext-br-001 `Arial`, disclosure-styles `system-ui`, line-through-
  vertical `Times`, boundary-shaping-001..008 `test` (woff, declined on Android)) — none measures ch, so the fix moves no cell.
- M-B runtime-faithful (inherited WritingMode, OWN TextOrientation): 16 carriers in the 8 ch-units-vrl docs, all failing
  natively (brief's inherited-orientation count: 18).
- M-C: brief 10 upright-leaf + 5 mixed-CJK docs ✓; runtime-faithful (the gate's exact rule): 14 docs, all failing on both
  natives (direction-upright-002's leaves are not gate-admitted under own-only orientation).
- M-E: col/colgroup with Width 5 docs ✓; UA-only (what the runtime harvests) 4 — `border-collapse-dynamic-col-001`
  declares `display: table-column` and is excluded by construction (pinned on both twins).
- M-G: 1 doc (the target); the gate fires for any widget tag — still 1 doc.
- Fixture net (non-WPT, M-A/M-G are not capture-gated): no fixture uses `ch`, no widget fixture declares a 0 width/height → 0.

## PNG replay (`png-replay.mjs` → `png-replay.json`, `replay-*.png`)

Scorer recipe re-implemented from `inject-wpt-block.mjs` (fitToRefFrame + ssim.js fast + overflow-ink, colour-mass and
coverage vetoes). Sanity: re-scoring every current capture reproduces the manifest SSIM AND verdict/vetoes exactly
(e.g. ch-units-vrl-005 iOS 0.9159 f cF cov). Boxes erased and repainted at the predicted geometry; line box LB =
24 (Chromium) / 24.2 (iOS, measured by the Catalyst raster pin: 120×121) / 25 (calibrated WPT line-height — Compose unmeasured).

| cell | now | predicted (LB 24 / 24.2 / 25) |
|---|---|---|
| ch-units-vrl-003/-004 ios | 0.8396 f cF cov | 0.9712 P / **0.9683 P** / 0.9557 P (with M-E; without M-E 0.92 f cF) |
| ch-units-vrl-003/-004 android | 0.8404 f cF cov | 0.9687 P / 0.9659 P / 0.9532 P |
| ch-units-vrl-005/-006 ios | 0.9159 f cF cov | 0.9529 P / **0.9513 P** / 0.9438 f |
| ch-units-vrl-005/-006 android | 0.9103 f | 0.9505 P / 0.9489 f / 0.9414 f |
| ch-units-vrl-007/-008 ×2 | 0.9159 / 0.9103 f | ≈0.912 / 0.910 f (web itself f 0.917: td own-upright 5ch = 120 tall, as Chromium) |
| ch-units-vrl-001/-002 ×2 | 0.84 f | ≈0.875 f (td orientation not inherited — see below) |
| forms/input-range-zero-inline-size ios / android | 0.9747 / 0.9743 f cov | **0.9993 P / 0.9989 P** (the green L stub already matches the ref's (16,68) 16×20) |

## Predicted flips (confidence · falsifier)

- input-range-zero-inline-size ios: **+1, HIGH**; android: **+1, MED-HIGH** (fix pass: no Compose raster — the JVM pin
  proves `resolve` → null, not the painted frame) — the fail is the coverage veto from the slider ink; the Catalyst
  raster pin shows 0 accent / 0 red px with the verbatim wire. Falsified if Compose's box path still paints something
  for the 0-wide input, or `blockLead` shifts the rows.
- ch-units-vrl-003/-004 ios + android: **+4, iOS MED-HIGH / Android MED** — iOS raster pins measure the green td 120×120
  and the blue/orange upright column 120×121; Android's Compose consumer (`widthIn(min)` via itemModifier) and its
  glyph line box are not raster-verifiable on the JVM. Falsified if the Compose cell's background does not span the
  min width, or its glyph line box is ≥ 26.
- ch-units-vrl-005/-006 ios: **+2, MED-LOW** (0.9513 at the measured line box — a 0.0013 margin). android: **+2, LOW**
  (passes only if Compose's upright glyph box is 24, not 24.2/25).
- 001/002, 007/008: no flip (rises / flat). direction-upright-*, available-size-011, forms/*-appearance-native-vertical*:
  movers, no flip claimed.

## At risk (all on the plan's watchlist or in `watchlist-additions.txt`; `unmatched 0`)

M-A (both natives, sizing ch): hyphens-auto-001 I A · hyphens-none-shy-on-2nd-line-001 I A · hyphens-out-of-flow-002 I ·
hyphens-punctuation-001 I · hyphens-span-002 I A · text-decoration-inset-004 I A (10). M-A iOS only (padding ch):
bidi-lines-001 I · bidi-lines-002 I (2, ≈ +0.15 px per side). M-A iOS only (declared `sans-serif`, design default —
fix pass): hyphenate-character-005 ios (failing today, f 0.945; a mover, ≈ +9.9 px margin). M-E: border-collapse-dynamic-col-001 — excluded by
construction (pins on both twins). M-B/M-C/M-G: no passing cell in their populations.

M-A is the device A/B of PLAN §4 step 5: include-direction = the lane tree; exclude-direction = seam-1 not applied
(Compose) + `ma-exclude-ios.patch` (iOS). Each run must record the installed `base.apk` sha1 / `.app` hash.

## Pins (verbatim per-test IR) and EXECUTED mutations

Mutation logs: `_mutations-compose.log` (C1–C7), `_mutations-swift.log` (S1–S7), `_mutations-me.log` (K-ME, S-ME,
S4-SEAM, S2-SEAM) — every one FAILS-AS-EXPECTED on an assertion (not a compile error), restored, SHA-OK.

| pin | mutation → failure |
|---|---|
| `ChUnitMetricsTest` (JVM) | C1 `cacheName` Inter arm removed → "default"≠"inter"; C2 upright branch → x-advance → null≠24; FIX PASS (verbatim `["Arial"]` / `["system-ui"]` / harness-stack payloads): C8 pre-fix gate → expected "default" was "inter"; C9 Default routed through the loader → calls expected 0 was 1; C11 InterFontFamily dropped from `paintsInter` → expected "inter" was "default"; C1b arm removed on the fixed line → same |
| `VerticalInlineAxisTest` / `VerticalInlineAxisTests` | C3/S3 orientation clause dropped; C4/S7 ICB fallback → null |
| `UprightOrthogonalBudgetTest` (JVM measure policy, 005 orange) | C5 `fallbackPx = null` → decline 55×72 ≠ 13×120; FIX PASS C10 capture gate dropped → expected null was 300.0 |
| `ChUnitInlineAxisSpacingContextTest` (in hunk-for-L4-1) | C7 flag → false → null≠24 |
| `UAWidgetsResolveTest` (28-prop verbatim range wire) / `UAWidgetsTests` | C6/S5 zero gate off → RANGE spec |
| `UAWidgetZeroBoxRasterTests` (Catalyst raster, verbatim div+range) | S5 → 628 accent px |
| `ChUnitInlineAxisTests` (Catalyst, Inter registered from the harness bundle) | S1 SF default → 60.19≠63; S2/S6 → 12.6≠24; S4 → 60×25≠13×120 |
| `UprightChOrangeRasterTests` (in seam-2, verbatim 005 orange) | seam not applied / S2-SEAM veto off → 120×25 |
| `TableColumnWidthsTest` / `TableColumnWidthsTests` (verbatim 003/004/dynamic-col) | K-ME / S-ME harvest → null |
| `UprightColTableRasterTests` (in seam-4, verbatim 003 table) | S4-SEAM fold off → green 6×120 |

Test runs: in-tree JVM `_jvm-run3.log` (8 classes green), in-tree Catalyst `_swift-run1.log`, locked seam runs above,
HEAD exports `_export-jvm-run1/2.log`, `_xexport-swift-run1.log`.

## Not verified

- No device: every flip above is a replay prediction. Compose: no raster of the M-E cell width, the upright glyph line
  box (24 / 24.2 / 25), or `bindInterFace`'s real `Resources.getFont` load (JVM has no Context — the call compiles and is
  exercised only on device). The Compose M-A switch is live only once seam-1 lands.
- `text-orientation` is not inherited on either native (pre-existing, twin-identical): a `td` under an upright `tr`
  (001/002) measures the x-advance. Making it inherit is a seam change with an unmeasured blast radius on every upright
  descendant run — not done.
- 007/008: per spec (and Chromium) the td's own upright `height: 5ch` is 120; the ref expects 63 — no runtime fix exists.
- House-rule deviations: `census.mjs` 224 and `png-replay.mjs` 214 lines (results scripts); `TableBoxTree.kt` 533 /
  `.swift` 318 lines (owned files grown by M-E; the Swift twin is past the ~300 split line and was NOT split this pass —
  a split re-cuts every M-E pin and seam-4's context for a nit); `ChUnitMetrics.kt` 270 / `.swift` 221;
  `VerticalTextFlowLayout.kt` 236. Correction of an earlier claim: `VerticalInlineAxis.{kt,swift}` (+ tests) are NEW
  files outside the plan's literal "own:" list (adjacent to the owned VerticalTextFlow pair; no other lane owns them).
- Fix-pass M-A: no device — the Inter-vs-DEFAULT split is pinned on the key and the loader ROUTE; the real
  `Resources.getFont` load and Typeface.DEFAULT advance are device-only, as before.
- Brief's Web ch-units cells: untouched (no web file in this lane).

## Hand-offs

Delivered: hunk-for-L4-1 (L4 told above). Received: none. M-F (baked guard) is L3's. Orchestrator: land seam-1..4 and
hunk-for-L4-1 with this lane's commit (each patch names its prerequisites), run the M-A device A/B with hashes.

## Blocked by

Nothing — the shared tree compiled for this lane's files at every run.

STATUS: COMPLETE
