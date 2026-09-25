# web-tail — wave 52 plan brief (the web failing tail, clustered by mechanism)

Evidence base: `tools/titan/runs/wave51-fix/sections/<section>/manifest.json` (all 30 sections, read-only, 2026-09-25),
the `screenshots/` (web) captures, the frozen refs under `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
white-black-ink-font-lh-imgpad-htmlpins/`, the section `per-test-ir/` docs and `extract.log`s. Every target PNG named in §2
was opened with the Read tool and its capture/ref difference measured with PIL (pixel counts below are those measurements).
Beside this file: `web-tail.failing-cells.json` (the regenerated 164-row web tail, sorted by ssim, with every veto flag) and
`web-tail.census.json` (the four carrier censuses of §5). Cell citations read `wave51-fix <section>/<test> <platform> <P|f> <ssim>`.

**Regenerated tail:** 1435 web-ref cells, 1379 score-eligible, **164 failing, 52 at ssim ≥ 0.93** (matches the task's numbers).
A cell passes only when `ssim ≥ 0.95` AND none of the presence / colour / coverage-ratio vetoes fired
(`tools/titan/inject-wpt-block.mjs:627-656`; the novel-ink and degenerate vetoes are OFF by default, so the `N`/`D` flags in the
JSON never fail a cell). That matters for ranking: several 0.98–0.999 cells fail on a VETO, not on SSIM (§2 says which).

## 1. Queue items covered (docs/BACKLOG.md)

- **"Ranked queue (wave 51+)" › 5. "Web tail residuals"** — the whole item; specifically its sub-items (b) "`none` colour components
  need an IR wire shape", (c) "gradient-eval-predefined-color-spaces: script-templated test extracts zero styled components",
  (f) "color-mix in lch needs a static mixer", and the un-numbered rest of the tail this brief regenerates. (b)/(c) are NOT
  re-targeted: (c) is a blank-capture presence fail the absence-only lane owns; (b) is 0.8199 with 43 % ref-dropped ink, not a near miss.
- Touches **"Ranked queue" › 0(a′)/(a″)** only at a seam: F-C and F-D below edit the same `provablyInvalidDeclaration` /
  `propsForElement` code the `cascade-and-splitter` brief owns (§8 names the coordination).

## 2. Target cells, cheapest first (each PNG pair opened; "cap" = web capture, sizes 390×600 unless stated)

| # | cell (web) | ssim / veto | visible defect in the cap | what the frozen ref shows |
|---|---|---|---|---|
| A1 | wave51-fix css-color/display-p3-linear-003 web f 0.9484 | ssim<0.95, no veto | grey (128,128,128) canvas; ONE white 192×96 box at (16,88); the 192×96 `.test` box below it at y184–279 is canvas-grey — 18 432 px of missing white | one white 192×192 square (the `.ref` and `.test` boxes fused) on the grey body |
| A2 | wave51-fix css-color/display-p3-linear-001 web f 0.8775 | C veto | `.test` 192×192 box unpainted (white canvas) | solid #008000 square |
| A3 | wave51-fix css-color/display-p3-linear-002 web f 0.7977 | C veto | same box unpainted | solid black square |
| B1 | wave51-fix css-view-transitions/fractional-box-with-shadow-new web f 0.9763 | C+R veto | two green 100×51 boxes with a 7 px darkgreen spread shadow at (16,31) and (16,96) on a WHITE canvas — 219 115 px white where the ref is (255,182,193) | the identical two boxes on a lightpink canvas edge to edge |
| B2 | …/fractional-box-with-shadow-old web f 0.9763 | C+R | identical to B1 | identical |
| B3 | …/fractional-box-with-overflow-children-new web f 0.9757 | C+P+R | two green boxes with darkgreen abspos children on white — 222 874 px white vs lightpink | same boxes on lightpink |
| B4 | …/fractional-box-with-overflow-children-old web f 0.9757 | C+P+R | identical to B3 | identical |
| C1 | wave51-fix css-color/color-mix-percents-02 web f 0.9514 | C veto | a 224×160 purple (175,92,174) block at (16,88): rows t1–t5 painted, rows t6–t7 (y248–311, 14 336 px) WHITE | one 224×224 purple square — all seven rows purple |
| D1 | wave51-fix css-cascade/import-conditional-002 web f 0.9990 | C veto | 100×100 RED square at (16,88) under the prose | 100×100 GREEN square, "no red" in bold |
| D2 | wave51-fix css-cascade/import-conditional-001 web f 0.9990 | C veto | same red square (plus "FAIL" text) | same green square |
| E1 | wave51-fix css-counter-styles/cssom/cssom-pad-setter web f 0.9821 | R veto | markers "1." "2." "3." at y≈40/140/240 — rows 100 px apart | "001." "002." "003." at y≈40/60/80 — 20 px rows |
| E2 | wave51-fix css-counter-styles/cssom/cssom-prefix-suffix-setter web f 0.9844 | R veto | "1." "2." "3." 100 px apart | "(A)" "(B)" "(C)" 20 px apart |

Also opened, cited in §4 as residuals: `css-view-transitions/html-becomes-fixed web f 0.9969` (cap: #acf 60×60 "A" box on WHITE;
ref: same box on a uniform #eeeeee canvas — 230 400 px differ by exactly (17,17,17)), `css-cascade/scope-implicit-006-print web f
1.0000` (cap RED 100×100 at (16,70), ref GREEN; ssim 1.0 because the grey-structure is identical, only the colour veto fires),
`css-view-transitions/column-span-during-transition-doesnt-skip web P 0.9879` (cap AND frozen ref: green 375×200 band on WHITE).

## 3. Mechanism, with file:line

**A — `color(display-p3-linear …)` has no sRGB normalisation.** `converter/…/primitiveParsers/ColorParser.kt:96` the `color()`
regex admits any `[\w-]+` space (the IR proves it: per-test-ir `…display-p3-linear-003.json` component 3 carries
`BackgroundColor.original = {type:"color", colorSpace:"display-p3-linear", values:[1,1,1]}` and **no `srgb`**); `:246-259` the
`when (colorSpace)` table lists `srgb`, `srgb-linear`, `display-p3`, `a98-rgb`, `rec2020`, `xyz*` and `else -> null // … runtime-dependent`.
A null `srgb` paints nothing on all three runtimes (the web capture's box is canvas-grey). `converter/src/main/kotlin/app/irmodels/
ColorConversion.kt:507-510` already has `displayP3ToSrgb` = `mul3(P3_TO_XYZ, srgbGammaToLinear(r|g|b)) → xyzToSrgb`; the
`-linear` space is the same matrix WITHOUT the transfer decode (css-color-4 §10.2 "display-p3-linear … linear-light version of display-p3").
Same shape for `a98-rgb-linear` (`:516-519`), `rec2020-linear` (`:531-534`) and `prophoto-rgb-linear` (no ProPhoto matrix exists yet).

**B — a snapshot-solve bail drops the `::view-transition` backdrop.** `tools/titan/view-transition-bake.mjs:2257-2274`
`probeSnapshotOverflow` sees 624 px (shadow) / 225 px (abspos children) of ink outside the 101×51 group window
(`VT_OVERFLOW_INK_TOLERANCE_PX = 4`, `:408`) and records a solve error; `:2288` `planViewTransitionBake` turns it into a bail
(`:1497`); `:2296 if (bail) return { status: 'bailed' … }` returns BEFORE `:2303-2314` (ring screenshot + `frameRingColor`) and
`:2316 applyViewTransitionBakePlan`, whose step 2c `:1729-1757` is the ONLY writer of the body-root `background-color` the three
composed canvases paint from (`apps/web-harness/src/ui/ComposedCaptureGallery.tsx:117-131 resolveCanvasBackground`; native
twins `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`,
`apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift`). The extract.log line for each of B1–B4 reads
`[vt-bake: bailed — snapshot solve failed for one/new|old: snapshot overflows its 101x51 box (624px|225px of ink outside it)]`.
The page then paints in place — which for these four IS the frozen state's box content (the `-new` files show `::view-transition-new(*)
{opacity:1}` over an unchanged DOM, the `-old` files the old snapshot of the same DOM; `::view-transition-group(root){opacity:0}`
hides the root, so the author's `html::view-transition { background: lightpink }` backdrop is what the viewport shows). Only the
canvas colour is lost — the measured diff is 100 % canvas, 0 box pixels.

**C — an INVALID `color-mix()` shadows a valid declaration.** `css-color-5 §3.1` grammar is
`color-mix(<color-interpolation-method>, [ <color> && <percentage [0,100]>? ]#{2})` — `purple 125%` and `purple 9999%` are
parse-time invalid, so `.t6/.t7 { background-color: color-mix(in lch, purple 125%, plum 125%) }` must be IGNORED and the earlier
`.negative-test { background-color: rgb(68.4898% 36.015% 68.3102%) }` stays in force (css-syntax-3 §2.2). The extractor's guarded
write `tools/titan/extract-fixture.mjs:962-975 assignDeclaration` only refuses what `provablyInvalidDeclaration` (`:905-921`) proves,
and that oracle has ONE rule (R1, unknown dimension units) — so the color-mix overwrote the rgb; the IR carries
`{type:"color-mix", percent1:125, percent2:125}` / `9999` for components 6–7 (per-test-ir `…color-mix-percents-02.json`), the
web runtime emits it verbatim and Blink rejects it → transparent → the two white rows. Converter twin: `ColorParser.kt:538-547
parseColorWithPercent` accepts any `\d+(\.\d+)?%`.

**D — `propsForElement` has no specificity.** `extract-fixture.mjs:6237` "Last-write-wins matches CSS cascade for same-specificity
rules"; `:6318-6337` the unlayered path merges `buckets['']` in document order; `:5221-5223` admits "propsForElement has no
specificity — it is last-write-wins in document order". import-conditional: the wave-38 resolver (`:565-610`) correctly inlines
`support/test-green.css` (`.test { background: green; color: green }`, specificity 0,1,0) in the `@import`'s place and declines the
two traps; then the local `div { …; background: red }` (0,0,1) is LATER in the sheet and overwrites `background`. Proof in the IR:
`…import-conditional-002.json` component 1 = `BackgroundColor red` + `Color green` — `color` survived because `div{}` never declares
it. Rule objects (`:1213-1218 { selector, props, layerName?, important? }`) carry no specificity today.

**E — the 100×100 "empty node" placeholder on `<li>`.** `extract-fixture.mjs:10995-11028` stamps `{width:100px,height:100px}` on
an element with no matched rule, no inline style, no own text and no kept children; `INTRINSIC_WIDGET_TAGS :4683` exempts form
controls only. Per-test-ir `…cssom-prefix-suffix-setter.json`: three `sourceTag:'li'` components, each exactly `Width 100 / Height 100`.
A `<li>` is `display: list-item` (css-lists-3 §2): it ALWAYS generates a `::marker` box, so it is never content-less; its UA box is
one line-height tall. That is the 100 px row pitch in E1/E2. NB the E cells FAIL on the coverage-RATIO veto (`R`), i.e. marker ink
("1." vs "001."/"(A)"), which E does not touch: the marker text is decimal because the web harness "deliberately does NOT read"
the marker bake and "renders a real <ol> and lets Blink synthesise ::marker" (`tools/titan/counter-style-bake.mjs:30-34`) while the
IR has no channel for `@counter-style` rules — `list-style-type: foo` reaches Blink with no `@counter-style foo`.

## 4. Proposed fixes (CSS-correct behaviour + spec section)

- **F-A (converter, S).** `ColorParser.kt:246-259`: add `"display-p3-linear" -> ColorConversion.displayP3LinearToSrgb(…)`,
  `"a98-rgb-linear"`, `"rec2020-linear"` (and `"prophoto-rgb"`/`"prophoto-rgb-linear"` if the D50→D65 Bradford path at `:551` is
  reused); `ColorConversion.kt` next to `:507`: `displayP3LinearToSrgb = xyzToSrgb(mul3(P3_TO_XYZ, r, g, b))` — no gamma decode
  (css-color-4 §10.2, linear-light predefined spaces). Keep `.clamped()` (simple clip, stated at `:242-244`).
- **F-B (harness, S).** `view-transition-bake.mjs:2296`: when `bail` is a solve-class reason (`snapshot solve failed …`,
  `non-uniform-snapshot …`, `isolation window … exceeds`) AND `walk.active === true`, take the `:2303-2314` ring sample anyway and
  apply ONLY step 2c (factor `:1729-1757` into `applyFrameRingStamp(fixture, stem, frameRing)`), never step 1 (`display:none`) or the
  pseudo tree. Log `[vt-bake: bailed — … (frame-ring rgb(255, 182, 193) stamped)]`. Correct per css-view-transitions-1 §"::view-transition":
  the pseudo is `position: fixed; inset: 0` in the top layer, its author background paints UNDER every group and reaches the viewport
  edge; the ring is MEASURED off the frozen live page (`frameRingColor :1368-1379` returns null for a white ring), so a visible root group
  yields no stamp. The later, full fix is a shadow-aware overflow window (css-backgrounds-3 §7.1: extent = |offset| + blur + spread)
  so the groups bake instead of bailing; F-B is its honest subset.
- **F-C (harness, S).** `extract-fixture.mjs:905-921`: rule R2 — if `/^color-mix\(/i` and any top-level `<percentage>` argument of
  the two colour operands is `< 0 || > 100`, return `"color-mix percentage outside [0,100] (css-color-5 §3.1 <percentage [0,100]>)"`.
  Nothing else changes: `assignDeclaration` already keeps the earlier valid value and logs `invalidShadowDrops`. Converter twin
  (no cell impact): `parseColorWithPercent` returns `null`/Named passthrough for the same range so natives never receive it.
- **F-D (harness, M).** Compute Selectors-4 §17 `(a,b,c)` per rule at `:1213-1218` (from `parseCompound`'s ids/classes/attrs/pseudos
  and type selectors; `:where()`/`*` = 0; `:is()/:not()/:has()` = most specific argument), then in `:6318-6337` stable-sort
  `buckets['']` (and each pseudo bucket) by specificity BEFORE the last-wins merge; in `resolveLayeredCascade :1260-1305` use it as the
  tie-break inside a rank. css-cascade-5 §6.1 sorting: origin+importance → context → layer → **specificity** → order of appearance.
  `!important` handling is the cascade-and-splitter lane's F1; F-D must land after or with it (same loop).
- **F-E (harness, S).** `:11028`: add a `LIST_ITEM_TAGS = new Set(['li', 'summary'])` exemption beside `INTRINSIC_WIDGET_TAGS`
  (css-lists-3 §2/§3: list-items generate a marker box). Zero flips alone (the E cells fail on marker ink); it is the prerequisite for
  **F-F (M–L, not cheapest)**: serialise `CSSCounterStyleRule.cssText` from the settled page in `post-load-extract.mjs` (the CSSOM
  setters are reflected in `cssText`) into a `_wpt.counterStyleRules` sidecar the web harness injects as a `<style>`; natives unchanged.

## 5. Blast radius — how a builder censuses, and who is at risk (`web-tail.census.json`)

- **F-A** — grep test sources for `color\(\s*(display-p3|a98-rgb|prophoto-rgb|rec2020|srgb)-linear`: **6 tests, all css-color/
  display-p3-linear-00{1..6}**; in the IR grep `"colorSpace":"display-p3-linear"`. At risk: -004/-005/-006 (`web P 0.9548/0.9547/0.9568`,
  natives 0.952–0.956) — they pass TODAY with the `.test` box unpainted. -005 (sRGB yellow) and -006 (in-gamut moss green) become exact →
  up. **-004 is out-of-gamut** (`color(display-p3-linear 0 1 0)` vs `.ref` `lab(86.6% -106.5 102.9)`): both go through `.clamped()`
  clipping, Chromium gamut-maps (css-color-4 §13.2) — the two rectangles will match each other (as the ref demands) but their colour may
  sit a ΔE off the ref's; MEDIUM-LOW risk of tripping the colour veto (`WPT_COLOR_FAIL_DELTA_E_MIN = 2.3`, inject-wpt-block.mjs:820).
- **F-B** — the census joins `::view-transition\s*{[^}]*background` in the 48 css-view-transitions sources with each test's
  `[vt-bake: …]` log reason: **29 author a backdrop; 8 of them bail on a solve-class reason while active**: B1–B4, capture-with-
  visibility-mixed-descendants (f 0.9215, ref is 390×732 — frame mismatch, no flip), class-specificity (f 0.8773), clip-path-larger-
  than-border-box-on-child-of-named-element (f 0.9067) and **column-span-during-transition-doesnt-skip `web|ios|android P 0.9879`**.
  That last one is the at-risk cell: its root group is `visibility: hidden` and the backdrop pink, so the live ring reads PINK — but the
  frozen ref PNG is WHITE although `column-span-…-ref.html` says `html { background: pink }`: the erased-ref sub-mechanism the
  absence-only brief's T3 names (`capture-browser-ref.mjs:490-491`, fixed by `tools/titan/results/wave50-B10/capture-browser-ref-body-
  background.patch`). **Under the current frozen refs F-B costs these 3 cells; after B10's re-freeze it is what makes them pass.** Land
  F-B with/after the re-freeze, or accept −3/+3 and say so. Non-active bails (`no-active-transition`, `transition-not-frozen`,
  `root-not-captured`) are excluded by the `walk.active` gate — element-stops-grouping-after-animation (P 0.9639 ×3) is untouched.
- **F-C** — grep sources for `color-mix\(` with any percentage `< 0` or `> 100`: **1 test, 2 declarations** (color-mix-percents-02);
  IR shape to grep: `"type":"color-mix"` with `percent1|percent2` outside 0–100. Nothing else moves.
- **F-D** — NOT grep-able from the IR. The census must be a DIFFERENTIAL run of the static walker with and without the sort over all
  1435 tests, diffing the emitted fixtures (the recipe BACKLOG 5(g) records for root-scope: "exactly 2 documents change, 1433
  identical"), then reading each changed test's cells. Known member of the class besides D1/D2: selectors/has-style-sharing-003
  (`:5221`, `:has(> .a) .b {green}` before `.b {purple}`), today rescued by the post-load route. Until that census runs F-D is a
  proposal; no at-risk list can honestly be written.
- **F-E** — IR shape: a component with EXACTLY two properties `Width {px:100}` + `Height {px:100}`, empty text, `meta.sourceTag`
  present. Corpus: 44 tests / 88 stamps; by tag `li` 51, untagged 21, `slot` 6, `section` 5, others 1 each. `li`: **15 tests, all
  css-counter-styles/cssom/*-setter(-invalid)**; 9 pass on all three (0.975–0.988) and move TOWARD the ref (20 px rows) — expected up;
  falsifier: any of the 27 cells drops below 0.95.

## 6. Predicted flips (web unless stated; confidence; what falsifies)

| fix | flips | confidence | falsifier |
|---|---|---|---|
| F-A | display-p3-linear-001/-002/-003 web (+ ios/android ×3 each = 9 cells) | HIGH (in-gamut values; the ref is a solid square the converter then paints exactly; natives read `srgb`) | the converter's `xyzToSrgb` for P3 primaries lands > 1/255 off #008000 on -001; or -004 trips the colour veto (at-risk, not a flip) |
| F-B | fractional-box-with-shadow-new/-old, fractional-box-with-overflow-children-new/-old web ×4 (+8 native, MEDIUM-HIGH) | HIGH — the measured diff is 100 % canvas colour, box pixels already identical | the ring sample of the FROZEN page reads white (the isolation sheet removed too late, or the transition already finished at sample time) → null → no stamp; or the natives' body-root twins do not repaint the pad |
| F-C | color-mix-percents-02 web (+2 native, MEDIUM) | HIGH (web) — rows t1–t5 already match; the two rows become the rgb purple | Blink accepts `125%` as valid (then the ref would not be uniform purple — it is) |
| F-D | import-conditional-001/-002 web ×2 (+4 native, HIGH) | HIGH for these 2 (green/red swap is the only diff) | the differential census shows passing cells whose green depends on the current mis-order |
| F-E | 0 | — | any of the 27 passing cssom cells drops below 0.95 |

Sum claimed: **+10 web / +14 native**, at-risk: column-span ×3 (F-B, ordering with B10), display-p3-linear-004 ×3 (F-A, MEDIUM-LOW).

## 7. Pins a builder must write (each with the mutation that fails it)

- **F-A** `converter/src/test/kotlin/app/irmodels/ColorConversionTest.kt` (beside `:105`): `displayP3LinearToSrgb(1,1,1)` → (1,1,1);
  `(0,0,0)` → (0,0,0); `(0.0383, 0.2087, 0.0156)` → (0, 0.502, 0) ± 1/255. Plus a `ColorParser` pin: `color(display-p3-linear 1 1 1)`
  yields non-null `srgb`. **Mutation:** route `display-p3-linear` through `displayP3ToSrgb` (gamma-decoding) → the (0.0383,…) case
  comes out ≈(0, 0.27, 0) and fails; drop the `when` arm → `srgb` null fails the parser pin.
- **F-B** `tools/titan/view-transition-bake.test.mjs` §8: (i) a bail outcome with `walk.active=true` and a solve error stamps
  `body-root.background-color` from a pink ring AND leaves every component's `display` undefined; (ii) same with `active=false` writes
  nothing; (iii) white ring → nothing (extends `:1231-1246`). **Mutation:** remove the `active` gate → (ii) fails; call the full
  `applyViewTransitionBakePlan` instead of the 2c stamp → (i)'s no-`display:none` assertion fails.
- **F-C** `tools/titan/extract-fixture.test.mjs`: `assignDeclaration({bg: 'rgb(68% 36% 68%)'}, 'background-color', 'color-mix(in lch,
  purple 125%, plum 125%)')` keeps the rgb and pushes one `invalidShadowDrops` entry naming §3.1; `… purple 50%, plum 50%` and
  `color-mix(in srgb, red 100%, blue)` overwrite. **Mutation:** `> 100` → `>= 100` fails the 100 % case; deleting R2 fails the first.
- **F-D** `extract-fixture.test.mjs`: `propsForElement` on `<div class=test>` with `[.test{background:green}, div{background:red}]` →
  green; `[div{red}, .test{green}]` → green; `[div{red}, div{green}]` → green (order preserved at equal specificity);
  `[#x{red}, .test{green}]` on `<div id=x class=test>` → red. **Mutation:** remove the sort → case 1 fails only.
- **F-E** `extract-fixture.test.mjs` beside `:3337`: `buildComponents('<body><ol><li></li></ol></body>')` → the `li` component's
  `properties` is `{}`; the existing "rule-less LEAF `<div>` still IS a placeholder" pin (`:3337-3346`) stays green.
  **Mutation:** add `div` to `LIST_ITEM_TAGS` → `:3337` fails; drop `li` → the new pin gets `{width:'100px',height:'100px'}`.

## 8. File ownership and seams

- F-A: `converter/src/main/kotlin/app/parsing/css/properties/primitiveParsers/ColorParser.kt`,
  `converter/src/main/kotlin/app/irmodels/ColorConversion.kt`, `converter/src/test/kotlin/app/irmodels/ColorConversionTest.kt`
  (+ a new ColorParser test). No harness or runtime file.
- F-B: `tools/titan/view-transition-bake.mjs`, `tools/titan/view-transition-bake.test.mjs`. **Shared seam, read-only for this lane:**
  the body-root `background-color` channel — `apps/web-harness/src/ui/ComposedCaptureGallery.tsx:117` and the two native twins named in
  §3-B; no change there. **Ordering seam:** lane B10's `capture-browser-ref-body-background.patch` + ref re-freeze (column-span).
- F-C / F-D / F-E: `tools/titan/extract-fixture.mjs` + `tools/titan/extract-fixture.test.mjs`. **Conflict:** the cascade-and-splitter
  lane edits `:905-1000` (oracle, its F3 candidate filter) and `:6318-6350` (its F1 importance gate). F-C is one new rule inside
  `provablyInvalidDeclaration`; F-D rewrites the same merge loop as their F1 — ONE lane must own `propsForElement`, or F-D waits.

## 9. What I could not verify (no gradle / node / device allowed)

- No converter, node or device run: F-A's sRGB numbers are derived from the existing P3 matrix path, not executed; the natives'
  canvas repaint for F-B and their color-mix rendering for F-C are inferred from their current scores tracking web's to ±0.002.
- F-D's blast radius (the differential walker census) was not run; the two D cells are proven by the IR, the at-risk set is not.
- Whether Blink under puppeteer rejects `color-mix(... 125% ...)` is inferred from the white capture + the IR carrying `percent1:125`,
  not from a console log.
- The coverage-ratio arithmetic behind the E cells' `R` veto is read from the flags, not recomputed.
- The remaining ≥0.93 web tail was clustered by PNG diff but not root-caused per cell: shadow-DOM presence fails (absence-only lane),
  caret-shape ×2 (no caret paints in a capture), html-becomes-fixed (post-load serializer `post-load-extract.mjs:847-864` returns only
  `headInner`/`bodyOuter`, so the script's `<html class=f>` is lost — S–M, 3 cells, not claimed), scope-implicit-006-print (`extractInlineStyle
  :198-204` collects a `<template shadowrootmode>` `<style>` as a document sheet; 13 corpus carriers, 7 passing web cells at risk — needs
  shadow-scoped rules, M), colspan-004 (web f 0.999 while natives PASS — web-runtime table attributes), at-color-profile-001 (ICC
  profile: unpassable), and the hyphenation / text-decoration-inset / table / flex-gap singletons at 0.93–0.95, each its own mechanism.
