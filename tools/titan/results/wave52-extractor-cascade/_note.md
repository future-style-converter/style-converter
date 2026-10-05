# wave-52 lane L5 · extractor-cascade — lane note

Plan: `tools/titan/results/wave52-plan/PLAN.md` §2 "L5", §3 (the `extract-fixture.mjs` seam row), §4 step 3, §5, §9.
Briefs: `cascade-and-splitter.md` §4 F1–F4 / §7 pins 1–11; `web-tail.md` §4 F-C/F-D/F-E / §7; `counters-and-multicol.md` T6.
Run of record: `wave51-fix`. Every cell below is `wave51-fix <section>/<test> <platform> <P|f> <ssim>` read through
`score-gate.mjs loadRun` (census.json → `differential`).

## FIX PASS (2026-10-05, after `skeptic.md`) — read this first

The skeptic's one must-fix (D1) was right: my F-E prediction for the natives was never measured and it was wrong.
- **What was wrong.** This note said F-E gives "0 flips alone" and moves "the 9 passing cssom families toward the ref".
  On BOTH native runtimes, HEAD paints an empty inside `<li>`'s marker in a zero-size overlay, so the three markers of a
  list stack on ONE band. Lane L6 measured that on Catalyst: band `[(16, 28)]` (`wave52-counters-and-lists/seam-4.verify.log`).
  The Compose twin is `ComponentRenderer.kt` `RenderListItemMarker`, whose Box "sizes itself from the item ALONE".
- **Now measured** (`fe-native-replay.mjs` → `fe-native-replay.json`, section "F-E replay" below). F-E alone takes all
  **18** passing native cssom cells **P → f**. The cause is the coverage-ratio veto: ink ratio 2.22–2.87, above the
  limit of 2. SSIM alone would have hidden it, because it RISES to 0.987–0.994. With L6's seam-3/seam-4, the same 18
  stay **P** (ratio 1.20–1.43, SSIM 0.986–0.994). Web is unaffected: all 11 passing web cells stay P.
- **Landing coupling, made mechanical** (section "Landing" below):
  - `seam-6-FE-li.patch` now lands in the SAME step as L6's `seam-3.patch` + `seam-4.patch`, or after them. It never
    lands before them or without them.
  - It now carries its own F-E pin (two files), so the tree's test file is green with seam-1…5 alone. That lets
    seam-1…5 keep PLAN §4 step 3, while seam-6 moves to step 5 with L6.
- **Hand-offs.** L6's hand-off is now recorded under Received, together with L1's and L11's extractor hunks. I checked
  that both compose with the chain in either order.
- **Nits.** N2, N3 and N4 are fixed (N4 in the test-file banner). Should-fixes D2–D4 and nit N1 are NOT in this pass;
  they are listed under "Open".

## Resume record (third start, 2026-10-05)

Found on resume: the +472-line wave52-L5 pin block in `tools/titan/extract-fixture.test.mjs`; `differential.sh` +
`differential-report.mjs`; six static differential stage JSONs. NOT found: any `seam-*.patch` or a note — the patched
extractor copies lived in the purged session scratchpad, so the implementation was LOST. Rebuilt from the briefs and the
surviving pins; the re-run differential is **byte-for-byte identical to the earlier start's at every one of the six
stages** (same changed documents, same property deltas) — the earlier JSONs are kept under `earlier-start/` as that record.
The seam file was clean (sha256 `eb9742dc…5f60` = HEAD) on resume and is clean now. F4 had not been started.

## What changed and why

### Seam — `tools/titan/extract-fixture.mjs`, ONE ordered patch set (never left edited in the tree)
Each patch applies on HEAD with the previous ones applied, in order; the header of each names its predecessors.
`git apply` of the chain on a HEAD copy reproduces the verified file exactly (sha256 `02780840…e307`).

| patch | fix | what |
|---|---|---|
| `seam-1-F3-oracle-filter.patch` | F3, BACKLOG 0(a‴) | `resolveLayeredCascade.resolveOne` gains the css-syntax-3 §2.2 candidate filter: a provably invalid candidate is set aside when another candidate in its window survives the oracle; an all-invalid window keeps the sort's own winner (byte parity, the exact `assignDeclaration` semantics). Set-asides that changed a pass's winner are logged once on `invalidShadowDrops` with the value that finally stayed in force. |
| `seam-2-F1-importance.patch` | F1, BACKLOG 0(a′) | `assignDeclaration` returns taken/refused; new `collapseDeclaration` (parseCss blocks AND `style=""`): a normal declaration never displaces a VALID important one (css-cascade-5 §6.1), and the flag follows the write taken (retires the wave-50 promotion defect). The `propsForElement` gate is widened to importance carriers (`r.important` / inline importance) so the cross-rule half is ranked by the layered resolver (unlayered rules share one rank → normal-vs-normal still falls to order). The layered resolver now KEEPS a non-revert-layer `all` (it used to drop every `all`; before the widening only `@layer` sheets reached it and none in the corpus declares one — without this the widening would have silently dropped `all: initial` from importance carriers). |
| `seam-3-FD-specificity.patch` | F-D (web-tail) | Exported `selectorSpecificity` / `compareSpecificity` (Selectors-4 §17: `:is/:not/:has` max-argument, `:where` zero, `:nth-child(… of S)`, `:host(S)`, `::slotted(S)`, legacy one-colon pseudo-elements as (c), null for malformed). parseCss stamps `specificity` on every rule (from the selector as written inside `@scope`, so the scoping root adds nothing). The unlayered merge sorts by specificity (stable → document order between equals) and re-emits keys in DOCUMENT first-appearance order (IR property order unchanged); pseudo buckets likewise; the layered `better` uses specificity inside one rank. |
| `seam-4-F2-splitter.patch` | F2, BACKLOG 0(a″) | ONE exported css-syntax-3 splitter `splitDeclarations` (paren depth, `<string-token>` with bad-string newline, unquoted `<url-token>` to its own `)`, escapes as pairs in and out of strings) at parseCss, `style=""`, @keyframes frames and @font-face (`splitDescriptors` retired). Oracle banner updated (tear repaired; `1e2deg` now read by AngleParser). |
| `seam-5-FC-colormix.patch` | F-C (web-tail) | Oracle rule R2: a value that is one `color-mix(…)` call with a TOP-LEVEL operand percentage outside [0,100] is provably invalid (css-color-5 §3.1 `<percentage [0,100]>` is grammar). Nested-function percentages and unbalanced calls prove nothing. |
| `seam-6-FE-li.patch` | F-E = T6 | `LIST_ITEM_TAGS = {li, summary}` exempt from the 100×100 empty-node placeholder (css-lists-3 §2/§3: a list item generates a ::marker box). **Two files since the fix pass**: the extractor hunks are byte-unchanged, and the F-E pin (M24/M25) is appended to `extract-fixture.test.mjs`. **Coupled to L6 seam-3/seam-4** (see "Landing"). |

### Non-seam (owned) files, edited in the tree
- `converter/…/primitiveParsers/AngleParser.kt` — F4, BACKLOG 0(e): `^([+-]?(?:\d+(?:\.\d+)?|\.\d+)(?:[eE][+-]?\d+)?)(deg|rad|grad|turn)$`
  with `IGNORE_CASE` (css-values-4 §5.3 exponent; units case-insensitive; a digit is still required after `.`, so `1.deg` stays null).
- `converter/src/test/…/primitiveParsers/AngleParserTest.kt` (new, 4 tests) — pin 9.
- `converter/src/test/…/primitiveParsers/ColorParserHslHueTest.kt` — pin 10 (`hsl(1.2e2deg, 75%, 50%)` is the green of hue 120, not red).
- `converter/src/test/…/InvalidDeclarationDropTest.kt` — pin 11 re-pointed: the scientific-notation gradients assert a typed
  `LinearGradient` with the resolved degrees (45 / 100 / 45 / 162 / 45) instead of `Raw`.
- `converter/…/longhands/background/GradientPrefixGuard.kt` — the stale `isNonAngleDimension` comment only (code unchanged).
- `tools/titan/extract-fixture.test.mjs` — the wave52-L5 pin block. FIX PASS: the F-E pin moved out of the tree file and
  into `seam-6-FE-li.patch`, so the tree holds 21 L5 pins and seam-6 adds the 22nd. The banner now says M1…M25 (nit N4)
  and states the coupling. Tree sha256 `1f78fcef…50db`; with seam-6 applied `8e5b9e0c…c2b9`. As first written (22 tests, kept from the earlier start and revised: mutation ids;
  the F-E `<span>` case replaced by `<section>` — a rule-less span is inline-merged before the placeholder branch, so that assertion
  could never hold; two load-bearing url-token cases; a new `all`-preservation pin; and the import-conditional-002 "VERBATIM" sheet
  CORRECTED — the earlier draft hand-wrote it with the red bare `@import` declined, but the resolver INLINES it (red `.test`, then the
  green `.test`, then the unresolved `supports(foo: bar)` statement, then `div`). Every VERBATIM pin (import-conditional-002, -024,
  revert-val-002, color-mix-percents-02, angle-units-001) was re-checked whitespace-normalised against
  `resolveImports(extractInlineStyle(stripComments(source)))` on the real WPT file: all five equal.

## Verification

### Pins and executed mutations
- FIX PASS counts (frozen `git archive HEAD` copy, tree test file copied in):
  - seam-1…5: 482 tests, 480 pass, 0 fail, 2 skipped.
  - seam-1…6, where the patch adds the F-E pin: 483 tests, 481 pass, 0 fail, 2 skipped.
  - HEAD's extractor: 19 of the 21 tree pins fail. Those are exactly the F1/F2/F3/F-C/F-D pins (TAP list checked). The 2
    that pass are the anchors.
  - `mutations.mjs` was re-run on the re-cut chain (copy with seam-1…6, both files): **25/25 RED as expected**, every
    restore sha-equal, baseline 0 failures, `allProven: true` (`mutations.json`). M24 → 1 red, M25 → 5 red.
- As first recorded: extractor pins: `node --test tools/titan/extract-fixture.test.mjs` → 481 pass / 0 fail / 2 skipped (frozen HEAD copy + the six
  patches); at HEAD's extractor 20 of the 22 L5 pins fail — the two that pass are anchors by design (the F3 all-invalid
  parity pin and the angle-units-001 regression anchor).
- **25 executed mutations M1–M25** (`mutations.mjs` → `mutations.json`): each one source edit of the patched extractor, whole
  suite run, every expected pin RED, byte-exact restore re-hashed to the starting sha256 every time; unmutated baseline 0 failures;
  `allProven: true`. Ids are named at each pin in the test file.
- Converter (`./gradlew :converter:test --tests AngleParserTest --tests '*ColorParserHslHueTest' --tests '*InvalidDeclarationDropTest'
  --tests '*GradientPrefixGuard*'`): 15 + 9 + 4 + 14 = 42 tests green. Executed mutations of `AngleParser.kt` (restored, sha256
  `07654926…7bec` before and after each):
  - F4-a drop the exponent group → 4 RED: AngleParserTest exponent + case rows (`45E0DEG`), HslHue pin 10, InvalidDeclarationDrop re-pointed pin;
  - F4-b drop `IGNORE_CASE` → 1 RED: AngleParserTest `units are ASCII case-insensitive`.
  A clean re-run after the restore is green.

### Seam verification (lock protocol, on the shared tree)
**FIX PASS re-verification of the re-cut chain** (`seam-verify-fixpass.log`, verbatim):
- Lock `tools/titan/runs/wave52-lock/extract-fixture.mjs` held 18:51:05 → 18:51:10.
- Hashes before: extractor `eb9742dc…5f60` (clean against HEAD), test file `1f78fcef…50db`.
- `git apply --check`, then apply, seam-1…5. Extractor `40fe4509…`. That is the **step-3 landing**:

  | suite | pass | fail |
  |---|---:|---:|
  | `extract-fixture` | 482 | 0 |
  | `extract-fixture-root-scope` | 6 | 0 |
  | `extract-fixture-col-placeholder` | 4 | 0 |
  | `extract-fixture-replaced-src` | 14 | 0 |
  | `extract-fixture-ua-hr` | 7 | 0 |
  | `extract-fixture-table-text` | 5 | 0 |
  | `post-load-extract` | 100 | 0 |
  | `attr-bake` | 53 | 0 |
  | `bidi-bake` | 50 | 0 |
  | `widget-appearance-bake` | 13 | 0 |
- Then seam-6 on top (extractor + pin). Extractor `02780840…e307`, unchanged from before the fix pass; test file
  `8e5b9e0c…c2b9`. That is the **step-5 landing**: `extract-fixture` 483 pass / 0 fail, and the nine sibling suites have
  the same counts as above, all 0 fail.
- Restore: `git show HEAD:… >` for the extractor, and the saved trimmed copy for the test file. Hashes after:
  `eb9742dc…5f60` and `1f78fcef…50db` (both equal). `git diff --quiet` clean. Lock released.
- Composition with the other lanes' extractor hunks, in a scratch HEAD copy: L1 `hunk-for-L5-1.patch` (comment-only)
  and L11 `seam-3.patch` (`--include` the extractor; its pin file is L11's).
  - Order A: seam-1…5, L1, L11, seam-6.
  - Order B: seam-1…6, L1, L11.
  - Both give the same extractor (`831aa02d…`), `node --check` OK, and `extract-fixture` 481 pass / 0 fail.

First verification (before the fix pass): `mkdir tools/titan/runs/wave52-lock/extract-fixture.mjs` taken 17:31:49 → sha256 before `eb9742dc…5f60` (clean) → `git apply`
seam-1…6 (all applied; patched sha `02780840…`) → `extract-fixture` 483 pass / 0 fail, `extract-fixture-{root-scope,col-placeholder,
replaced-src,ua-hr,table-text}` 6/4/14/7/5 pass, `post-load-extract` 93 pass, `attr-bake` 53, `bidi-bake` 50, `widget-appearance-bake` 13,
all 0 fail → restored with `git show HEAD:…` → sha256 after `eb9742dc…5f60` (equal), `git diff --quiet` clean → lock released 17:31:54.

### Corpus census (`census.mjs` → `census.json`, over the 1435 wave51-fix per-test IR docs + sources)
- F-E: 88 placeholder stamps in the wire — `li` 51 (15 docs, all `css-counter-styles/cssom/`), untagged 21, `slot` 6, `section` 5, 1 each other.
- F-C: wire color-mix operand outside [0,100] = 2 components in 1 doc (`color-mix-percents-02` t6/t7); sources: 1 test.
- F4: exponent-form angles in sources 0, in the wire 0; upper-case angle units in `<style>` 0 → zero corpus movement (proved by the differential).
- F1: 10 sources carry `!important`. F2: 2 sources have `;` inside `url(` (the css-pseudo first-letter pair).
- F3: **15** sources use `@layer` / `revert-layer`. The first count of 13 missed `revert-layer-009` and
  `revert-layer-012`, which carry `revert-layer` in `style=""` (skeptic N2). This count is descriptive only; the
  differential is the authority, and it moved 0 documents at the F3 stage.

### Extract+convert differential (`differential.sh`; base = HEAD extractor; one converter jar per comparison)

Apparatus: each side is a `git archive HEAD` copy of `tools/titan` (+ `tools/visual`) with only `extract-fixture.mjs` swapped in,
`WPT_DIR` = the repo corpus, the 1435-test union of the 30 wave51-fix `tests.list`, `build-combined-fixture` per section, one
converter jar (a snapshot of `converter/build/install/converter/lib`, `converter.jar` sha256 `c47341d9…1a58`), `split-combined-ir`.
Both sides extract 1435/1435 on the static path; with POST_LOAD_EXTRACT=1 both sides extract 1434 ok + the same 1 FAIL
(`css-pseudo/first-letter-of-html-root-refcrash`, also FAIL in wave51-fix's own extract.log) and both report post-load
277 extracted / 49 bailed / 9 declined — exactly wave51-fix's counts.

**POST_LOAD_EXTRACT SET** (`differential-postload-base-vs-s6.json`, base vs all six patches): the SAME 22 documents change, with
property deltas identical to the static run except two pixel-inert differences — `color-mix-percents-02` (component-id ordinal
only) and `override-in-shadow-dom` (in the gate this test is post-load extracted: its `<li>` carry computed 318×20 either way, and
the only change is the ORDER of `Position` vs `Width/Height` in the bag — same properties, same values). So in the gate path
override-in-shadow-dom is a byte mover, not a picture mover.

BIDI_BAKE / VT_BAKE were not set in the differential, although the gate sets them. The earlier claim here, that "neither reads the
cascade code L5 changed", was wrong (skeptic N3): both bakes re-enter `extractFixture` for their static pass, and
`bidi-bake.mjs:1089` calls `parseCss`. The effect is still inert in practice, for two reasons:
- The bakes post-process the same static fixture that the differential measured.
- None of the 22 changed documents is bidi- or VT-baked (the wave51-fix manifest flags are 0 for all of them).

**F4 (converter)** — a second differential over the same extracted fixtures: one jar built from a HEAD copy of `converter/`, one
from the same copy + L5's `AngleParser.kt` (jars `dda81ba0…` vs `a24e59d8…`): **0 of 1435 per-test IR documents change, static
and post-load fixtures alike** (`differential-F4-converter-{static,postload}.json`) — the zero-carrier census, proved on bytes.

STATIC path (POST_LOAD_EXTRACT unset):
Cumulative stages (`differential-static-stage-{1..6}-*.json`), 1435 per-test IR docs / 2870 fixtures compared:

| stage | changed docs | new this stage (brief predicted) |
|---|---:|---|
| F3 | 0 | 0 (predicted 0) |
| F1 | 2 | `flex-gap-decorations-024` (predicted) + **`css-cascade/revert-val-002`** (UNPREDICTED: `display: block !important` vs later `display: revert`) |
| F-D | 5 | `import-conditional-001/-002` (predicted) + **`css-cascade/important-prop`** (UNPREDICTED: `.square{color:#00f}` beats later `div{color:red}`) |
| F2 | 5 | 0 — brief predicted 2 (the css-pseudo first-letter pair): their `::first-letter` bag is dropped before emission, so no byte moves |
| F-C | 6 | `color-mix-percents-02` (predicted) |
| F-E | 22 | 15 `css-counter-styles/cssom/*` (predicted) + **`counter-style-at-rule/override-in-shadow-dom`** (UNPREDICTED 16th) |

Every changed document, its delta and its cells (`census.json` → `differential`):

| doc | IR delta | wave51-fix web / ios / android |
|---|---|---|
| css-cascade/import-conditional-001 | BackgroundColor red → green | f 0.999 / f 0.9974 / f 0.9966 |
| css-cascade/import-conditional-002 | BackgroundColor red → green | f 0.999 / f 0.9974 / f 0.9966 |
| css-cascade/important-prop | Color red → #00f (the `FAIL` text) | P 0.999 / P 0.9973 / P 0.9966 |
| css-cascade/revert-val-002 | Generic `display: revert` → Display BLOCK | P 0.999 / P 0.9974 / P 0.9967 |
| css-color/color-mix-percents-02 | t6/t7 BackgroundColor color-mix(125%/9999%) → rgb(174,91,174) | f 0.9514 / f 0.9503 / f 0.9496 |
| css-gaps/flex/flex-gap-decorations-024 | ColumnRule 9px DOTTED blue → 10px SOLID pink | P 0.9901 / P 0.99 / P 0.99 |
| css-counter-styles/counter-style-at-rule/override-in-shadow-dom | 3 `li` lose Width/Height 100 | f 0.9816 / f 0.9808 / f 0.9805 |
| css-counter-styles/cssom/* (15 docs) | 3–6 `li` each lose Width/Height 100 | name ×2 P 0.9755/0.9756/0.976; additive ×2 P 0.9884/0.9887/0.9884; fallback ×2 P 0.9879/0.9876/0.9878; range ×2 P 0.9882/0.9878/0.988; symbols-invalid P 0.988/0.9876/0.9878; negative ×2 P 0.984 / f 0.9849 / f 0.9848; pad ×2 f 0.9821/0.9819/0.982; prefix-suffix ×2 f 0.9844/0.9848/0.9849 |

### PNG replay (`png-replay.mjs` → `png-replay.json`; scorer = HEAD `diffWebVsRef`, frozen refs)
The IR delta painted onto the wave51-fix capture, scored against the ref; residual = pixels off by >32 on any channel,
test area (y ≥ 80) vs the caption band (harness-wide font noise, untouched by L5):
- import-conditional-002 ×3: red square → green; test-area residual 10000 → **0** px; SSIM unchanged (0.999 / 0.9974 / 0.9967 — the
  cells fail on the colour veto, not SSIM). -001 ×3: 9921 → **99** px (a 1-px edge row of the square).
- color-mix-percents-02: two more 32-px purple rows; web 0.9514 → **1.0000** (residual 14336 → 0), ios 0.9503 → 0.9988, android 0.9496 → 0.9981.
- flex-gap-decorations-024: the 10-px gap x 66–75, y 16–125 painted pink under the green row rule; 0.99 → **1.0000** on all three (460 → 0 px).
- revert-val-002 web: the 100×100 #outer (x 16–115, y 88–187) fills green; 8460 → **0** px (today it PASSES at 0.999 while showing a
  mostly RED box — a degenerate pass that F1 makes honest). ios/android already paint the whole box green (no red pixel): unchanged.
- important-prop: not replayed — the bytes move the `FAIL` text red → blue, but the capture's red 10-px border and visible text come
  from the un-baked `@keyframes override` (border-color/color green), which L5 does not touch; picture stays wrong either way.
  (The skeptic replayed it with HEAD's `diffWebVsRef`: SSIM 0.999 → 0.9966 / 0.9973 → 0.995 / 0.9966 → 0.9946, colorComposite
  barely moves — holds P.)

### F-E replay — FIX PASS (`fe-native-replay.mjs` → `fe-native-replay.json`)
**Method.**
- The F-E delta only removes a placeholder; the glyphs themselves do not change. So each scenario MOVES the wave51-fix
  capture's own marker ink bands, crops ±2 rows, and composites overlapping glyphs by darkest pixel.
- Each prediction is scored through HEAD's `diffComposedVsRef`: SSIM plus the presence, colour and coverage-ratio vetoes,
  giving `wptPass`. The scorer is a `git archive HEAD` copy, because the tree's copy carries L12's edit.
- Apparatus check: re-scoring the untouched capture reproduces every one of the 45 cssom cells' wave51-fix verdict, and
  its SSIM to within 0.0006 (`apparatusReproduces: true`).

**Scenarios.**
- `feAlone`: natives with HEAD's renderer. Zero-size overlay, so a list's 3 markers go on its first band, and the second
  list of `name-setter*` sits one 16-px gap below. The geometry is L6's Catalyst raster `[(16, 28)]`.
- `feAloneGap32`: the same, with the gap doubled (a sensitivity check).
- `feSeam34`: natives with L6's seam-3/seam-4. One 16-px marker line per item (L6's raster pitch).
- `feWeb`: web, with rows at the ref's 20-px pitch.

| cells (wave51-fix) | scenario | SSIM | coverage ratio ref/cap | verdict |
|---|---|---|---|---|
| 18 native P (additive, fallback, name, range ×2 each, symbols-invalid; ios + android), today 0.9756–0.9887 | `feAlone` | 0.9868–0.9939 | **2.22–2.87 (> 2)** | **18 P → f** (coverageRatioFailed ×18; colour / presence 0) |
| same 18 | `feAloneGap32` | 0.9867–0.9939 | 2.22–2.87 | 18 P → f (the gap does not matter) |
| same 18 | `feSeam34` | 0.9862–0.9938 | 1.20–1.43 | **18 P → P**, SSIM up |
| 12 native f (negative / pad / prefix-suffix ×2) | all three | 0.9876–0.9906 | 2.13–7.21 | stay f (ink deficit; their glyphs need L6 T7's `markerText`) |
| 11 web P (9 families + negative ×2), today 0.9755–0.9884 | `feWeb` | 0.9920–0.9977 | — | 11 P → P, SSIM up |
| 4 web f (pad / prefix-suffix ×2) | `feWeb` | 0.9898–0.9919 | — | stay f (coverage ratio; L6 T3/T7) |

**Reading.**
- F-E on its own is a **−9 iOS / −9 Android** regression, and SSIM alone would hide it.
- It is +0 / +0 when it lands with L6's seam-3/seam-4.
- The margin on the closest cell (additive, ios: ratio 2.22) would need about 11 % more ink than the measured overlapped
  union of the three glyphs, and the overlap geometry is the runtime's own (all three markers at the same origin).
- Caveats:
  - The Android pitch under seam-3/4 is assumed equal to the iOS one; the coverage verdict does not depend on pitch.
  - The 5 `-invalid` families also get L6 T7's baked `markerText` (different glyphs). That combined picture is L6's
    prediction, not this replay's.

## Predicted flips (with confidence)
- import-conditional-001 / -002: web f → P ×2 (HIGH), ios/android f → P ×4 (HIGH — natives paint the IR colour; replay residual 0 / 99 px).
- color-mix-percents-02: web f → P (HIGH, replay 1.0000), ios/android f → P (MED; replay 0.9988 / 0.9981).
- flex-gap-decorations-024 ×3: P → P ≈1.0, `novelInkFailed` clearing (MED — falsified if a native draws the solid rule wider than the gap
  or keeps a dotted style).
- revert-val-002 web: P → P (degenerate → honest; HIGH); natives byte-change only.
- F-E (15 cssom + override-in-shadow-dom). **AMENDED in the fix pass.** The old claim — "0 flips alone; the 9 passing
  cssom families move toward the ref" — was unmeasured and WRONG for the natives (skeptic D1).
  - **Web:** 0 flips; the 11 passing web cells move toward the ref (SSIM 0.976–0.988 → 0.992–0.998 in the replay). Confidence
    MED-HIGH: the web harness is Chrome, the same browser that drew the ref.
  - **Natives, F-E landed WITH L6 seam-3/seam-4 (the only sanctioned landing):** 0 flips from F-E itself. The 18 passing
    native cells stay P with SSIM up (0.986–0.994). Confidence MED-HIGH: the replay matches L6's iOS raster pitch; the
    Android pitch is not measured.
  - **Natives, F-E ALONE:** **18 P → f** (−9 iOS, −9 Android) on the coverage-ratio veto. Confidence HIGH: L6's Catalyst
    raster gives the one-band geometry, the replay uses the gate's own scorer, and every one of the 18 fails with
    margin ≥ 0.22.
  - This is why seam-6 must never land without L6's pair.
  - override-in-shadow-dom: picture-inert in the gate (post-load extracted; only the key order moves).
- Totals claimed: **HIGH +3 web, +2 iOS, +2 Android** (import-conditional ×2 per platform, color-mix web); **MED +1 iOS, +1 Android**
  (color-mix natives). Matches PLAN §2 L5 ("+2 web, +4 natives HIGH; color-mix +1 web HIGH, +2 natives MED").
  F-E adds 0 to these totals ONLY under the "Landing" coupling. Landed alone it would subtract 9 iOS and 9 Android.

## At risk (watched)
- `css-cascade/important-prop ×3 P 0.999/0.9973/0.9966` — text colour red → blue inside an already-wrong picture (MED-LOW risk of a
  colour-veto flip; both colours are novel ink against the green ref).
- The layered css-cascade statics (`revert-layer-0*`, `layer-media-toggle`): unchanged by the static differential (0 deltas).
- `selectors/has-style-sharing-003` (the web-tail brief's known F-D shape): NO delta in either the static or the post-load
  differential — its `:has()` rule never matches statically, and post-load re-derives the bag from the live page.
- **The 18 passing native cssom cells — AT RISK until L6 T7's native half (seam-3/seam-4) lands with seam-6.** They are:
  `cssom-{additive-symbols-setter, additive-symbols-setter-invalid, fallback-setter, fallback-setter-invalid, name-setter,
  name-setter-invalid, range-setter, range-setter-invalid, symbols-setter-invalid}` on ios and android, wave51-fix P
  0.9756–0.9887. P → f is certain if seam-6 lands alone (replay above); predicted P (↑) with L6's pair. Listed by exact
  name in `watchlist-additions.txt` (the plan's `css-counter-styles/cssom/` prefix line already covers them; the
  explicit lines carry the label).
- All already on `wave52-plan/watchlist.txt` (L5 block); the one unpredicted document not covered,
  `counter-style-at-rule/override-in-shadow-dom`, is in `watchlist-additions.txt`. `WATCH=watchlist-additions.txt
  watchlist-check.mjs` → 21 lines, 21 cells, unmatched 0.

## Landing (FIX PASS — the atomic F-E coupling, PLAN §4)
1. **Step 3 (L5's PR), as planned.** Apply seam-1…seam-5 IN ORDER, plus L5's tree files (the test file and the 5
   converter files). Verified green: `extract-fixture` 482/0 and the nine sibling suites 0 fail
   (`seam-verify-fixpass.log`). On the static differential this is exactly stage 5: 6 documents change
   (`differential-static-stage-5-FC.json`), and not one cssom document is among them.
2. **Step 5 (L6's landing), NOT step 3.** Apply `seam-6-FE-li.patch` in the SAME commit / PR / gate as L6's
   `seam-3.patch` + `seam-4.patch` and the L6-owned files they need (`lists/ListMarkerEmptyItem.{kt,swift}` + tests).
   The patch carries its own pin, so the test file stays green at every commit.
3. **Order inside step 5 is free.** L6's pair applied FIRST is inert on the cssom cells, because their `<li>` still carry
   `Height 100` and `ListMarkerEmptyItem.isEmpty` refuses a declared block size. seam-6 first, in a DIFFERENT gate or PR
   from L6's pair, is the forbidden state.
4. **If L6's seam-3/seam-4 are dropped, deferred or reverted, withhold seam-6 too.** Its F-E pin goes with it. The
   alternative "land all of L5 at step 5 with L6" is equally safe.
5. L1's `hunk-for-L5-1.patch` and L11's `seam-3.patch` compose with either ordering (verified above).

## Hand-offs

Delivered:
- **Orchestrator** — the six seam patches above, to apply IN ORDER, split as described in "Landing": seam-1…5 at step 3,
  and seam-6 at step 5 with L6's seam-3/seam-4.
  - After seam-1…5, `node --test tools/titan/extract-fixture.test.mjs` must print 0 fail. It is 19 red on HEAD's extractor
    by design.
  - After seam-6 it must also print 0 fail, with one more test (the F-E pin rides in the patch).
  - L5 is not the only lane delivering text for this seam file: L1's comment-only hunk and L11's seam-3 also patch it
    (see Received).
- **L6 (counters-and-lists)** — F-E (`seam-6-FE-li.patch`) is the prerequisite for T7: after it, the 51 cssom `<li>` (15 docs) and
  the 3 in `counter-style-at-rule/override-in-shadow-dom` carry EMPTY bags instead of 100×100. FIX PASS: and it now lands in
  L6's step, coupled to L6's seam-3/seam-4 (the seam-6 header says so first thing). Observation only (L6's file, not
  touched): `tools/titan/counter-style-descriptors.mjs:18` cited `css-values-4 §3.2` (NO-SUCH-SECTION) — L6 has since fixed it
  to `§4.2`.
- **Orchestrator, `docs/BACKLOG.md`** (orchestrator-owned; paste at ship time) — replace the tails of ranked item 0:
  - (a′) → "**CLOSED in wave 52 (lane L5 F1).** In-block: importance is compared before order and the flag follows the write taken
    (`collapseDeclaration`). Cross-rule: the `propsForElement` gate routes importance carriers through the layered resolver. The
    'zero corpus carriers' claim was the in-block twin only — the cross-rule half had TWO static carriers:
    `css-gaps/flex/flex-gap-decorations-024` (blue dotted rule → solid pink) and `css-cascade/revert-val-002` (`display: block
    !important` over a later `display: revert`)."
  - (a″) → "**CLOSED in wave 52 (lane L5 F2)**: one css-syntax-3 splitter (`splitDeclarations`) at rule bodies, `style=""`,
    @keyframes and @font-face. Zero corpus bytes moved (the css-pseudo first-letter pair's bag is dropped before emission)."
  - (a‴) → "**CLOSED in wave 52 (lane L5 F3)**: the candidate filter inside `resolveOne`; zero corpus movement."
  - (e) → "**CLOSED in wave 52 (lane L5 F4)**: `AngleParser` reads the exponent and case-insensitive units; `hsl(1.2e2deg, …)` is
    green, not red; zero corpus carriers (census: 0 sources, 0 wire docs)."
  - New residual for item 5 / 0: "Specificity now orders the per-ELEMENT cascade (wave 52 L5 F-D); `propsForBodyRoot`'s
    html/body/`*` root-scope merge still uses document order inside each scope — not measured, no known carrier."

Received (FIX PASS; the original "none" was wrong — skeptic D1):
- **L6** (`wave52-counters-and-lists/_note.md` §2 item 2, §6, §8): "seam-3/4 must land WITH L5's seam-6-FE-li". Without
  them F-E collapses every native cssom row onto one band, and the 18 passing native cssom cells are at P→f risk. The
  evidence is the Catalyst raster `[(16, 28)]` in `seam-4.verify.log`.
  - **Acted on:** I measured it (the F-E replay above: 18 P → f alone, 18 P with the pair). The coupling is now the
    first thing in the seam-6 header and in "Landing", the F-E pin moved into seam-6, and the 18 cells are on the watchlist.
- **L1** (`wave52-web-tail-colour-vt/hunk-for-L5-1.patch`): a comment-only hunk at the `vt-bake` call (`:11642`), asking
  me to fold it into my set or have the orchestrator apply it after.
  - **Not folded**, so the verified chain hashes stay stable. The orchestrator applies it after L5's set.
  - Checked: it applies on seam-1…5 and on seam-1…6, `node --check` OK, and the extractor pins stay 0 fail.
- **L11** (`wave52-all-reset-postload-colour/seam-3.patch`): `uaLinkProps` `:10054`, declared independent of L5's hunks.
  - Checked: its extractor part applies in either order relative to seam-6. Together with L1's hunk, both orders give
    the same file (`831aa02d…`).

## Seam re-verification after the last test-file edit
Re-run of the lock protocol at 17:38:22–17:38:24 (after the import-conditional-002 pin correction): sha256 before `eb9742dc…5f60`,
chain applied (`git apply --check` then apply, all six), `extract-fixture` 483 pass / 0 fail and the nine sibling suites 0 fail,
restored, sha256 after `eb9742dc…5f60`, `git diff --quiet` clean, lock released. `mutations.mjs` re-run on the final test file:
25/25 RED as expected, every restore sha-equal, `allProven: true`.

## Not verified (honest)
- No device, simulator or gate run (rule 4): every flip above is a PREDICTION backed by the IR differential and a pixel simulation,
  not a runtime render. The replay paints the IR delta by hand; it cannot catch a runtime that mis-draws a solid column rule.
- The census and the differential are now re-derived independently. The skeptic's own scripts (`skeptic.md` §5) reproduce
  0/2/5/5/6/22 static, the same 22 documents post-load, and the F4 zero carriers.
- F-E on natives (fix pass) is a PIXEL SIMULATION scored with the gate's scorer, not a runtime capture of the full composed
  canvas.
  - The one-band geometry is a runtime measurement for iOS only: L6's Catalyst raster of the verbatim cssom-pad `<ol>`,
    whose re-executed mutation turned it red with one band.
  - For Android it rests on reading the code, the Compose overlay Box that "sizes itself from the item ALONE"; Compose has
    no JVM raster.
  - I did not re-run L6's Catalyst probe myself (the `ComponentRenderer.swift` lock was held by another lane at
    18:47); I cite its log.
  - The `feSeam34` Android pitch (16 px) is assumed. The coverage verdict does not depend on it.
- Converter: only the four focused classes ran (42 tests), not the whole `:converter:test` — the module also carries L1's in-flight
  `ColorParser.kt` / `ColorConversion.kt` edits; the F4 byte-identity proof used jars built from a HEAD copy, not the shared tree.
- `differential.sh` was edited (post-load mode, completeness guard) WHILE the first post-load run was in flight; that run's `run`
  wrapper stopped at the old, too-strict guard after extraction, and both post-load sides were then converted by hand with the final
  script (`differential.sh convert`). Extraction itself was not affected.
- `important-prop ×3`: the text-colour change (red → blue `FAIL`) was not replayed; predicted to hold P on the basis that both colours
  are equally novel against the green ref, not measured.
- The differential report JSONs and `mutations.json` record absolute scratchpad paths for their sides; the scratchpad is purgeable —
  re-run `differential.sh` / `mutations.mjs` to reproduce (both are deterministic on a fixed HEAD; `setup` freezes `git archive HEAD`,
  so re-run at 7d9c22a7 or note the new HEAD).
- `important-prop ×3`: the skeptic has since replayed it (it holds P), but the lane did not measure it.

## Open (skeptic should-fixes and a nit — NOT in this fix pass, whose brief was the one must-fix)
- **D2** `selectorSpecificity` (seam-3) throws on a selector whose trimmed text ends in a lone backslash
  (`.foo\ { … }`): `readIdent` reads `m[0]` of a null match.
  - The one-line fix is `i += m ? m[0].length : 1`, plus a pin and its mutation.
  - No corpus carrier: 1435/1435 extracted on both sides.
  - Re-cutting seam-3 re-hashes stages 3–6, so it needs its own review.
- **D3** F4 makes non-finite angles reachable. `rotate(1e999deg)` gives `_serializationError` (Infinity).
  - Fix: `AngleParser` returns null when the number or the converted degrees are not finite, plus a pin and its mutation.
  - No corpus carrier.
- **D4** pin gaps (the skeptic's surviving mutants):
  - SK1: R2 with only the SECOND colour operand out of range.
  - SK8: the splitter's bad-string newline.
  - SK4: a `:not(#id)` specificity case.
- **N1** `keepAll` in `resolveLayeredCascade` is dead logic (SK6 is an equivalent mutant); its comment says it is needed.
- **O1** (orchestrator) this directory is untracked. The six seam patches are the only copy of the extractor
  implementation; commit it.

STATUS: COMPLETE
