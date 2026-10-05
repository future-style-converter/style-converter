# Wave 52 · L8 vertical-wedges — skeptic review (executed repros)

Tree: shared `campaign/wave52` @ HEAD 7d9c22a7, 2026-10-05. Read: PLAN.md "### L8", §3, §5, §9; `_note.md`
(ends `STATUS: COMPLETE`); every file in this directory. No device. All mutations ran in a private
`git archive 7d9c22a7` export (+ the 22 `lane-files.txt` files copied from the tree + seam-1..4 + hunk-for-L4-1);
the shared tree was touched only by the two locked seam applications below (both SHA-OK, created files removed,
seams == HEAD afterwards; the 22 lane files hash-identical before/after the audit).

## Verdict

The lane's mechanisms are real, pinned and mutation-proof, its seams apply and stack, and its census numbers
reproduce — but ONE must-fix: Compose M-A measures **Inter** for every non-singleton, non-document family,
including `FontFamily.Default`, which is what the label paints (Roboto) for `font-family: Arial` / `system-ui` /
`fantasy` / any unavailable name. That is a product-runtime wrong-render regression versus HEAD (0 corpus cells).

## 1. Pins re-run

| run | result |
|---|---|
| in-tree JVM (`:runtime:testDebugUnitTest`, 6 classes) | 35/35 green (UprightOrthogonalBudget 3, VerticalRunIntrinsics 9, ChUnitMetrics 4, TableColumnWidths 4, VerticalInlineAxis 4, UAWidgetsResolve 11) |
| export JVM (HEAD + lane files + seams 1–4 + hunk-for-L4-1; `spacing.*`, `table.*`, the 4 above) | 209 run; all lane pins green incl. `ChUnitInlineAxisSpacingContextTest` 2/2 (the hunk); 1 failure = the skeptic repro below, by design |
| export Catalyst (7 suites incl. both seam raster pins) | 35/35 green |

## 2. Seam patches

- `git apply --cached --check` on a HEAD 7d9c22a7 index: seam-1/2/3/4 and hunk-for-L4-1 OK; seam-1+3 and seam-2+4 stack.
  `ma-exclude-ios.patch` does NOT apply on HEAD (by design: its stated base is the lane tree) — applies on the worktree ✓.
  hunk-for-L4-1 also applies on the worktree carrying L4's StyleApplier.kt edit ✓.
- Stacking against every other lane's current seam patch on the same file: seam-1 and seam-3 apply after each of
  L3/L4/L6(×2)/L9/L11's Compose patches; seam-2+4 apply after L6 seam-4+seam-2 (L6's required order), L3 seam-2,
  L11 seam-2, and L9 seam-2 applies after them. No other lane's patch touches `lineCountIsExact` / `verticalUpright*`.
- Under the lock, shared tree:
  - `ComponentRenderer.kt` + seam-1 + seam-3 → `compileDebugKotlin` executed (not up-to-date), lane JVM pins + `table.*`
    → BUILD SUCCESSFUL, exit 0; sha c4369165… before = after, SEAM==HEAD.
  - `ComponentRenderer.swift` + seam-2 + seam-4 → 8 suites / 56 tests green; `[L8] orange box (0,0,120,121)`,
    `[L8] green cell (0,0,120,120)`; sha d2afc70d… before = after, created raster-test files removed, SEAM==HEAD.

## 3. Mutations (independent; every one restored byte-exact, sha-256 verified)

| id | file | mutation | pin | result |
|---|---|---|---|---|
| C5-replay | VerticalRunIntrinsics.kt | `fallbackPx = null` | UprightOrthogonalBudgetTest | FAIL `expected 13 x 120 but was 55 x 72` SHA-OK |
| C6-replay | UAWidgetsResolve.kt | zero gate `false &&` | UAWidgetsResolveTest | FAIL `expected null, but was Spec(kind=RANGE…)` SHA-OK |
| SK1 (new) | UAWidgetsResolve.kt | `px == 0.0` → `== -1.0` | UAWidgetsResolveTest | FAIL (same) SHA-OK |
| SK2 (new) | TableBoxTree.kt | col harvest axis flag → `false` | TableColumnWidthsTest | FAIL `expected [120.0] but was [50.0]` SHA-OK |
| SK3 (new) | VerticalInlineAxis.kt | `uprightBudget` → `boundedPx` only | UprightOrthogonalBudgetTest | FAIL `13 x 120` vs `55 x 72` SHA-OK |
| SK4 (new) | ChUnitMetrics.kt | drop per-half rounding | ChUnitMetricsTest | FAIL `expected 24.0 but was 24.199219` SHA-OK |
| S5-replay | UAWidgetsResolve.swift | zero gate `false &&` | UAWidgetsTests, UAWidgetZeroBoxRasterTests | FAIL `628 ≠ 0 accent px`, `XCTAssertNil failed: Spec(kind: .range…)` SHA-OK |
| SKS1 (new) | ChUnitMetrics.swift | drop `.rounded()` on ascent/descent | ChUnitInlineAxisTests | FAIL `24.19921875 ≠ 24.0` SHA-OK |
| SKS2 (new) | VerticalTextFlowLayout.swift | `budgetPx(style:…)` → `return nil` | UprightChOrangeRasterTests (seam-2) | FAIL `orange height 25.0 ≠ 121.0 ± 1` SHA-OK |

All on assertions, none on compile errors (the lane's `_mutations-compose.log` lacks the assertion text; C5/C6 replays
confirm it was an assertion).

## 4. Census — skeptic's own script (`skeptic_census.py`, Python, independent of census.mjs)

| shape | lane | skeptic |
|---|---|---|
| docs | 1435 | 1435 |
| `"u":"CH"` docs / no FontFamily anywhere | 74 / 18 | 74 / 18 ✓ |
| upright-vertical ch carriers (own orientation) | 16 in 8 docs | 16 in 8 docs ✓ |
| M-C gate-admitted leaves (own orientation) | 14 docs, all failing natively | 14 docs, all failing on both natives ✓ (inherited orientation would add direction-upright-002) |
| col/colgroup with Width / UA-only | 5 / 4 | 5 ✓ (dynamic-col-001 cols carry `Display: TABLE_COLUMN`/`_GROUP` ✓) |
| widget with a 0px box | 1 | 1 ✓ |
| **Compose M-A movers** (sizing ch, family resolves to neither a generic singleton nor a document face) | 18 docs / 73 carriers (face-less) | 18 docs / 73 carriers ✓ |
| **iOS M-A movers** (W/H/min/max + padding/margin ch, design `default`, no document face — the gate as shipped) | 20 docs (18 + bidi-lines-001/002) | **21 docs / 76 carriers** — the 21st is `css-text/hyphens/hyphenate-character-005` (`font-family: sans-serif`, `margin-left: 10ch` @32px; iOS f 0.945, android f 0.9474, web P 0.974): `sans-serif` is design `default` on iOS, so SF 19.2 → Inter 20.19 px per ch (≈ +9.9 px margin). On NO watchlist (`watchlist-check` with that one line: matches 1 scored cell). |
| bold ch carriers | "every corpus ch carrier … is regular weight" | **1 bold**: text-decoration-inset-004 `h1` (FontWeight 700, `width: 15ch` @32px): Regular measures 302.8, the ref's Inter Bold is 323.7; wrap unchanged (`the ultra-quick` 231 / `+ brown` 339 bold) so no picture effect |

## 5. PNG check of every predicted-flip cell (wave51-fix captures vs the frozen htmlpins refs)

- `forms/input-range-zero-inline-size` ios/android: the only foreign ink is the two 129-wide slider rows (accent blue
  1484/1466 px); the green L stub is already pixel-identical to the ref ((16,68)-(31,87), 80 px). My own replay (erase
  the slider band, keep green) leaves 934 / 960 differing px, ALL in the text band y < 60 (AA). Flip plausible; iOS backed
  by a raster pin, Android not (lane says HIGH for both — Android is MED-HIGH at best).
- `ch-units-vrl-003/-004` (refs and captures byte-identical pairwise): ref green 120×120 / blue 120×120 / orange 63×63.
  Predicted geometry (iOS raster-measured green 120×120, upright column 120×121; orange 5ch=63 Inter) reproduces the web
  picture (web P 0.9761) to within a 1-px row → plausible P on iOS; Android depends on seam-3's `widthIn(min)` reaching the
  td background and the Compose glyph line box (unmeasured) — lane's MED is fair. NOTE: the committed
  `replay-ch-units-vrl-003-*.png` is the WITHOUT-M-E variant (green still 6 px wide); the claimed with-M-E picture
  exists only as JSON (0.9683/0.9659).
- `ch-units-vrl-005/-006`: ref green 63×63; the fix leaves the green td 6×63 (orientation not inherited, disclosed), so
  even web reads only 0.9578; iOS replay 0.9513 at the measured 24.2 line box → MED-LOW is honest, Android LOW.

## 6. Ownership / rules

- Every changed file carrying an L8 marker is in `lane-files.txt`; none is another lane's (StyleApplier.kt carries no L8
  edit in-tree — the hunk is a patch). New `VerticalInlineAxis.{kt,swift}` are outside the plan's literal "own:" list
  (adjacent to owned VerticalTextFlow.{kt,swift}; no other owner) — not foreign, but the note's "no new file outside the
  ownership list" is inaccurate.
- New files ≤ 200 lines and commented; ChUnitMetrics.kt 109→243, .swift 155→216, TableBoxTree.swift 221→**316**,
  TableBoxTree.kt 454→533 (disclosed). Stale "NO iOS CALLER TODAY" on Swift `generatesNoBoxes` (columnChains now calls
  it); `import SwiftUI` now sits between `enum TableBoxTree`'s `///` doc line and the enum.
- No test-name carve-out in any non-comment line; ring-fence untouched; device-A/B hash sentence present for M-A (note +
  ma-exclude-ios header). Fixture net: gate-fixtures.txt carries no `ch`, no vertical/upright text, no widget → 0.

## 7. Defects

1. **MUST-FIX — Compose M-A measures Inter for `FontFamily.Default`.** `ChUnitMetrics.kt` `cacheName`/`resolveTypeface`
   route every non-singleton, non-document family to the Inter loader. `CssFontFamilyResolver.resolve` returns
   `FontFamily.Default` (deliberately NOT InterFontFamily) for a declared-but-unavailable list, and the label paints
   `textStyle.fontFamily ?: InterFontFamily` = Roboto. Executed (export, `SkepticChFamilyDefaultTest`):
   `resolve(["Arial"]) === FontFamily.Default` ✓, `resolve(["system-ui"]) === FontFamily.Default` ✓, then with the loader
   installed `cacheName(FontFamily.Default)` → `ComparisonFailure: expected:<[default]> but was:<[inter]>`. Product effect
   (seam-1 binds the loader on every render, not capture-gated): `font-family: Arial; width: 10ch` at 20px sizes 126 px
   against a ~111 px Roboto run — HEAD measured DEFAULT, matching the paint. 0 corpus carriers (census), so no gate cell moves.
   Fix: only `null` and `InterFontFamily` take the Inter loader; `FontFamily.Default` keeps "default"/Typeface.DEFAULT; pin it.
2. SHOULD-FIX — iOS M-A population mis-stated: the gate is design `default`, not "face-less"; add
   `css-text/hyphens/hyphenate-character-005 ios` to `watchlist-additions.txt` and correct the note/census.
3. SHOULD-FIX — Compose M-C fallback reads `LocalContainingBlock.heightPx`, which RenderComponent publishes on EVERY
   surface (`ComponentRenderer.kt:2004`, from `DynamicValueResolver.childContainingBlock`, not capture-gated);
   VerticalTextFlowLayout.kt's "Off the composed path neither is published, so the historical decline stands there byte
   for byte" is false, and the iOS twin gates the whole budget on `wptCaptureMode`. No committed baseline carries upright
   vertical text, so no fixture moves; still a twin divergence in the product. Gate the read on `LocalWptCaptureMode` or
   fix the claim.
4. NIT — committed 003/004 replay PNG is the without-M-E picture.
5. NIT — "every corpus ch carrier … is regular weight" is false (text-decoration-inset-004, bold); harmless here.
6. NIT — file-size house rule (TableBoxTree.swift crossed 300); stale Swift comment; doc-comment/import order.
7. NIT — input-range Android labelled HIGH without a raster; M-E maps columns by cell index (colspan / `span` ignored silently).

Repro artefacts (scratch, not committed): `skeptic_census.py`, `sk-mutate-compose.sh`, `sk-mutate-swift.sh`,
`seamlock.sh`, export under `l8sk/`.

## Re-verify (2026-10-05, after the fix pass)

Tree: shared `campaign/wave52` @ HEAD 7d9c22a7. Fresh isolated export `l8rv/export` = `git archive 7d9c22a7` + the 22
`lane-files.txt` files copied from the tree (sha-256 recorded before, re-checked after: all unchanged) + seam-1 + seam-3 +
hunk-for-L4-1 (each `git apply --check` OK on that base, then applied). Repro artefacts (scratch, not committed):
`l8rv/SkepticChFamilyDefaultTest.kt` (in the export only), `rv-mutate.sh`, `xfail.py`, `rv_census.py`, `*.log`.

### Must-fix 1 — Compose M-A measured Inter for `FontFamily.Default` → **FIXED (confirmed by execution)**

- Code: `ChUnitMetrics.kt` `paintsInter(f) = f == null || f == InterFontFamily`; `cacheName` takes "inter" only on
  `paintsInter && loader != null`; `resolveTypeface` sends only `paintsInter` to the loader, else `Typeface.DEFAULT`. For
  `FontFamily.Default` that is HEAD's key ("default") and face (`Typeface.DEFAULT`) exactly (`git show HEAD:` lines 56-92).
- Paint premise re-checked: config family (`TypographyExtractor:318`) and label TextStyle family (`TextStyleApplier:566`)
  both come from `CssFontFamilyResolver.resolve`; the label paints `textStyle.fontFamily ?: InterFontFamily`
  (`ComponentRenderer.kt:6637-6638, 6660-6661`). So measurement = paint for null (Inter), InterFontFamily (Inter),
  Default (Roboto), generics (their Typeface).
- Original repro, unchanged assertion, export: `SkepticChFamilyDefaultTest` 4/4 green — `cacheName(FontFamily.Default)`,
  `cacheName(resolve(["Arial"]))`, `cacheName(resolve(["fantasy"]))` == "default" with the loader installed; null and
  InterFontFamily == "inter"; a counting loader is called 0× for Default / Arial (horizontal and upright) and 1× for
  InterFontFamily. Export JVM run (spacing.*, table.*, the lane's pins, CssFontFamilyResolverTest): 222 tests, 0 failures, exit 0.
- In-tree pins (shared tree, fresh timestamps 18:59:29): ChUnitMetricsTest 5, UprightOrthogonalBudgetTest 4,
  VerticalRunIntrinsicsTest 9, VerticalInlineAxisTest 4, UAWidgetsResolveTest 11, TableColumnWidthsTest 4,
  CssFontFamilyResolverTest 10 → 47/47 green, exit 0.
- Seam-1 under the lock (`wave52-lock/ComponentRenderer.kt`, free; file == HEAD sha c4369165… before): seam-1 + seam-3
  applied, `bindInterFace` present, `:runtime:compileDebugKotlin` EXECUTED + ChUnitMetricsTest / UprightOrthogonalBudgetTest /
  table.* / CssFontFamilyResolverTest → BUILD SUCCESSFUL exit 0; restored with `git show HEAD:`, sha c4369165… after = HEAD,
  lock removed.
- Independent mutations (export; each restored, sha-256 verified — ChUnitMetrics.kt daa48eb45e7a…, VerticalTextFlowLayout.kt
  6f85975fea6e…, identical to the lane's `_mutations-fix.log` pre-sha):

| id | mutation | result |
|---|---|---|
| RV-M1 | `cacheName`: drop `paintsInter(fontFamily) &&` (the pre-fix gate) | FAIL — `expected:<[default]> but was:<[inter]>` (lane pin + skeptic repro) SHA-OK |
| RV-M2 | `resolveTypeface`: always `defaultMeasuringTypeface()` | FAIL — loader calls `expected:<0> but was:<1>` (both) SHA-OK |
| RV-M3 (new) | `paintsInter` also true for `FontFamily.Default` | FAIL — `expected:<[default]> but was:<[inter]>`, route `0 vs 1` SHA-OK |
| RV-M4 | `paintsInter`: drop the InterFontFamily clause | FAIL — `expected:<[inter]> but was:<[default]>` (harness stack) SHA-OK |
| RV-M5 | M-C `uprightFallbackBudgetPx`: `if (!wptCaptureMode)` → `if (false)` | FAIL — `expected:<null> but was:<300.0>` SHA-OK |

All on assertions, none on compile errors.

- Census (own script `rv_census.py`, structural `u == "CH"` walk, family resolved own → nearest ancestor via
  `slot.parent`, twin of `CssFontFamilyResolver` incl. the Android-loadable document-face gate): 1435 docs; Compose
  sizing-ch carriers: monospace 77 (53 docs), **null 73 (18 docs)**, InterFontFamily 0, **Default 0**. Post-fix movers
  18 docs / 73 carriers = the lane's `census-family` numbers; the pre-fix / post-fix gates differ only on the Default
  population, which is 0 → the fix moves no corpus cell and changes no predicted flip. 11 docs declare a family resolving
  to Default somewhere, none on a sizing-ch carrier. No non-WPT fixture carries a `ch` value → fixture net 0.
- iOS: no twin defect — `ComponentRenderer.swift:5239-5256` paints `.custom("Inter")` for every design-default family
  (Arial / sans-serif included), so iOS measuring Inter for design default matches its paint. The fix pass's Swift edits
  are comment-only (`ChUnitMetrics.swift` M-A comments; `TableBoxTree.swift` doc comment + import order) — diffed
  against the first-skeptic snapshot; the lane's Swift export run `_fix-xexport-swift.log` reads 82/82, TEST SUCCEEDED.

### Regression sweep

- Fix-pass delta vs the first-skeptic snapshot: exactly 6 files changed (ChUnitMetrics.kt, VerticalTextFlowLayout.kt,
  ChUnitMetricsTest.kt, UprightOrthogonalBudgetTest.kt, ChUnitMetrics.swift, TableBoxTree.swift), all in `lane-files.txt`.
  Every changed file in the tree carrying an L8 marker is in `lane-files.txt` (22/22, 0 foreign); `StyleApplier.kt`
  carries no L8 edit (the hunk stays a patch).
- Should-fix 3 (M-C fallback now capture-gated): the gate is pinned (RV-M5) and both capture paths still provide the flag
  (`ScreenshotCaptureScreen.kt:582` composed `true`, `:620` inbox `inboxMode`) → the 005/006 Android budget is unchanged
  under capture; product / dark stage back to HEAD's decline.
- Should-fix 2: `css-text/hyphens/hyphenate-character-005 ios` is in `watchlist-additions.txt`.
- Nit 4: `replay-ch-units-vrl-003-ios-withME-LB24.2.png` measured — green (16,184)-(135,303) 14400 px = the frozen
  htmlpins ref exactly; blue 120×121 and orange 63×63 one row lower (the disclosed 24.2 line box).
- Sizes: ChUnitMetrics.kt 270 lines (pre-existing file, under the ~300 split line), VerticalTextFlowLayout.kt 236. New
  code commented line-by-line; no test-name carve-out; no silent fallthrough (DEFAULT-for-Inter still logs once).
- Residual (not a defect of the fix, pre-existing in the lane's design): `cacheName` reads the volatile loader outside the
  memo lock, so a loader install racing a measurement could memoize one stale advance; installs happen once per process
  (`bindInterFace` idempotent), so no practical effect.

### Verdict

Must-fix 1 is fixed and pinned (lane pin + the skeptic's original repro both green; four independent mutations of the
split each fail on assertions). No regression found. No must-fix remains.
