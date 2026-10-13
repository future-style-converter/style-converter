TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-build-L2

# wave55-cbb-android — lane L2 (cbb-android) builder note

Lane contract: `tools/titan/results/wave55-plan/PLAN.md` §2 "### L2 · cbb-android"; brief `wave55-plan/cbb-android.md`;
generated contract `wave55-plan/expectations.json` `lanes["L2-cbb-android"]`, `wave55-plan/landing-manifest.json` seq 17–18
(group `cbb`). Base tree: `611c9f932bcfefa147a35f6c5f356d42709ddf84` (= dev 0d0d3a3d + the committed wave-55 plan and the
opening-gate records). Worktree: the `TREES:` line above (detached, provisioned per PLAN §0 before the first build:
`apps/android-harness/local.properties`, root `local.properties`, the `node_modules` link; plus a `tools/titan/runs` link to
the shared tree's gitignored runs so the local-only corpus pins EXECUTE instead of `assume`-skipping). Nothing was edited on
the shared tree outside this directory; no commit, checkout, stash or reset on the shared tree; no device, no Chromium.

## What changed and why (file:symbol)

**The picture** (looked at: `look/clip-3.ref.final.probe.png` and `look/l2-carriers.ref.final.probe.png`, built by `look.py`
from the frozen refs and the wave54-final / wave54-probe android captures — ref | wave54-final | wave54-probe, 2×):
- clip-3: the ref's green runs to #outer's clip edge (36,36)-(235,135) with #middle's grey band beyond it clipped; wave54-final
  android paints a 160×60 green inside a pink L (4360 px fail ink); wave54-probe (HEAD + the old unit) paints a clean 180×80
  green with #middle's grey right/bottom band and #outer's blue band visible INSIDE #outer — #middle drawn as a 200×100 border box.
- nested-clip / -4 and autopos-htb-ltr: final shows the pink / red L; probe = ref. autopos-htb-rtl: probe = the iOS line
  (10-px red strip at x 106..115) — the report rows, R6 (queued, not this lane).
So the brief's verdict holds on the pictures: U1 fixed the containing-block CHANNEL; the remaining 20 px per axis is #middle's
in-flow PERCENT frame drawn as a border box.

**U1 — CBB-android re-landed (5b333c8a, the revert of 8deddc19).** CSS 2.1 §10.1 / §10.2, css-ui-3 §3.1.
- NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt` —
  `ContainingBlockBands.subtracts(properties, wptCaptureMode)`: under WPT capture an effective content-box publishes its
  declared size; a declared border-box still subtracts; the dark stage always subtracts and breadcrumbs
  `ContainingBlock[dark-stage-content-box-bands]` on an explicit content-box. Byte-identical to `git show 5b333c8a:<path>`
  (sha256 f4bb43ff…).
- `…/core/variables/DynamicValueResolver.kt` — `childContainingBlock(properties, parent, wptCaptureMode: Boolean = false)`
  (the defaulted parameter) + the one condition `if (!ContainingBlockBands.subtracts(properties, wptCaptureMode)) return
  ContainingBlock(widthPx = w, heightPx = h)`; 5b333c8a's hunks as committed (the file at HEAD equals 5b333c8a^; result
  sha256 19b15add… = 5b333c8a's). 663 → 671 lines.
- `…/test/…/core/variables/DynamicValueResolverTest.kt` — 5b333c8a's five CBB rows + the CBB census, with: the census corpus
  path `wave53-final` → `wave54-final` (the payload documents are byte-identical: clip-3 `965c7730…`, autopos-htb-ltr
  `421c514a…` at wave53-final = wave54-final = wave55-open = wave54-probe); the provenance comments re-pointed to wave54-final;
  and **C8** — the row `CBB the dark stage breadcrumbs a declared content-box and still subtracts` (unset dark stage: 180, no
  crumb; declared content-box dark stage: 180×80 AND the crumb; the same declaration under WPT: 200, no crumb). 447 → 626 lines.
- seam: `seam-1.patch` — `…/core/renderer/ComponentRenderer.kt` `RenderComponent`, the `val childContainingBlock =
  remember(…, wptCaptureModeForStretch)` block: `.childContainingBlock(effectiveProperties, containingBlock, wptCaptureMode =
  wptCaptureModeForStretch)` (+4 comment lines; body byte-identical to `wave54-oof-layout/seam-1.patch`, re-cut on 611c9f93;
  8488 → 8492 lines) and the patch-borne NEW `…/test/…/core/renderer/ChildContainingBlockSeamWiringTest.kt` (assertions
  unchanged; its header now names this patch).

**U2 — PercentContentBoxBand (new).** css-ui-3 §3.1 + CSS 2.1 §10.2 / §10.5; css-overflow-3 §3.
- NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt` (191 lines):
  - `PercentContentBoxBand.owns(raw, cfg, bandPx, clampOwnsAxis)` — true iff WPT capture, effective content-box,
    `Relative(_, PERCENT)`, `bandPx != 0f`, and PercentSizeClamp not owning the axis; declines with the breadcrumbs
    `Sizing[dark-stage-percent-content-box-band]` (dark stage, declared content-box + % + band) and
    `Sizing[percent-clamp-content-box-band]` (the min/max lane owns the axis);
  - `fraction(raw)` (the fill branch's own `(value/100).coerceIn(0,1)`), `targetPx(fraction, maxPx, bandPx) =
    round(maxPx × fraction + bandPx)`, `measureSpec(fraction, bandPx, incomingMin, incomingMax)` (null on an unbounded axis
    = FillNode identity; otherwise `ExactWidthOverflow.measureSpec`: overflow measures at the target and reports the incoming
    max), `placementOffset(child, reported) = 0` (start-anchored, never requiredWidth's centring);
  - `PercentFillPlusBandModifier(fraction, bandCssPx, horizontal)` — a data-class `LayoutModifier` (the CalcSizeMinModifier
    shape, so the JVM chain pin can find it and read its inputs), and `Modifier.percentFillPlusBand(raw, bandCssPx, horizontal)`;
  - a named KNOWN LIMIT in the banner: `DynamicValueResolver.invalidWidthFallback` stands a guaranteed-invalid block `width`
    in as the wire's 100% (= `auto`); with a band this lane would draw 100% + band where CSS auto fits — 0 corpus carriers
    (the P-U2-6 census resolves every box with `blockLevelWidthAuto` on and still counts 122 axes / 2 owned).
- `…/sizing/SizingApplier.kt` `applySizing` (561 → 564 lines): one comment line and the two call sites
  `if (PercentContentBoxBand.owns(rawW, config, config.contentBoxInflateX, pctW != null)) r = r.percentFillPlusBand(rawW,
  config.contentBoxInflateX, horizontal = true)` and its `rawH` / `contentBoxInflateY` / `horizontal = false` twin; the two
  existing clamp lines (`if (pctW != null) …`, `if (pctH != null) …`) become `else if` (see D-a below).
- NEW `…/test/…/sizing/PercentContentBoxBandTest.kt` (199 lines): P-U2-1 … P-U2-6 (below).
- **C13.** The chain-order claim (sizing → #middle's own paint / overflow clip → padding inside StyleApplier's chain) is
  pinned BY SYMBOL and by behaviour, never by line: `P-U2-5 live chain` runs `StyleApplier.applyProperties` on #middle's
  verbatim properties and asserts the band nodes sit outside the `BackgroundElement` and the last `PaddingElement`.

**The mechanism on clip-3 (prediction, not observed):** #middle measures 220×120 (200 + 20, 100 + 20) and reports #outer's
200×100, start-anchored; its background, radius clip and overflow clip draw at 220×120; its padding leaves #inner a 200×100
content box at (36,36); #outer's rounded `overflow: hidden` trims the excess at x 236 / y 136 → green (36,36)-(235,135) = ref.

## Revert unit U1 — paths (one commit, with seam-1.patch)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt` (NEW) — unit-U1.patch
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/DynamicValueResolver.kt` — unit-U1.patch
- `runtimes/compose/src/test/java/com/styleconverter/runtime/core/variables/DynamicValueResolverTest.kt` — unit-U1.patch
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` — seam-1.patch (seam file)
- `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ChildContainingBlockSeamWiringTest.kt` (NEW,
  patch-borne) — seam-1.patch
- applyOrder: unit-U1.patch, seam-1.patch. revertOrder U2 → U1. Never landed or reverted without U2 (`pair`).

## Revert unit U2 — paths (one commit)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt` (NEW) — unit-U2.patch
- `runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt` — unit-U2.patch
- `runtimes/compose/src/test/java/com/styleconverter/runtime/sizing/PercentContentBoxBandTest.kt` (NEW) — unit-U2.patch
- needs U1; revertOrder U2 → U1. unit-U2.patch shares no path with U1 / seam-1 and applies on 611c9f93 alone too.

## Census (the lane's own, behavioural) — `census.sh` → `census.out.txt` (+ `census-check.py`)
- U1: `DynamicValueResolverTest` "CBB census" executed on the 1435 verbatim wave54-final per-test IR documents →
  `census-U1.junit.txt`: **33 boxes in 14 documents** (the frozen / WPT block threaded through every tree; kept when the
  incoming block differs AND the box reads it: oof / spc / vtext / dyn).
- U2: `PercentContentBoxBandTest` "P-U2-6 census" → `census-U2.junit.txt`: every component through the runtime's
  `ListStyleUaRule.apply` (UA list padding) and `DynamicValueResolver.resolve` (with `blockLevelWidthAuto` on for every box — a
  superset covering the invalid-width → 100% rewrite), then `SizingExtractor` under WPT: **122 percentage axes, 2 owned**
  (#middle 0-172 W and H of nested-border-radius-clip-3). The corpus has 0 var()-carrying sizes and 0 `media` / `selectors`
  folds (scanned), and no `ul` / `ol` / `menu` / `dir` with a % size.
- Patches' touched paths: 8, all under `runtimes/compose/` → web 0, iOS 0, wire 0.
- Cross-check vs `expectations.json` `lanes["L2-cbb-android"]`: captureCarriers android 14 / ios 0 / web 0 — **IDENTICAL**;
  wireCarriers 0 — **IDENTICAL**; revertUnits.U1.captures.android 14 — **IDENTICAL**; revertUnits.U2.captures.android 1 —
  **IDENTICAL**; planning census A (14 documents) and B (122 / 2) — **IDENTICAL**. **The census did not grow** (no plan
  regeneration need).

## Geometry probes (self-test on wave54-final; and the U1-only picture)
- `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave54-final --base wave54-open --lanes L2
  --self-test` → exit 0, `self-test: HOLDS — 0 violation(s), 0 self-check failure(s)`; 33 keys: gating 8 all FAIL today
  (7 in compose-wpt-content-box-cb.geometry.py + cbb-android.geometry.py clip-3), control 22 all PASS, report 3 FAIL
  (`geometry-gate.wave54-final.L2.selftest.txt`).
- The same gate on `wave54-probe` (HEAD + U1 on device; not a self-test): gating PASS 6 / FAIL 2 — exactly the two clip-3 keys
  (U2's), `green (36, 36, 215, 115) vs ref (36, 36, 235, 135)`, corners `[251,250,252,252]` missing, mask XOR 5591 px; the 3
  report keys print the U1 line; controls 22 PASS; revert rule 4 names U2 only (`geometry-gate.wave54-probe.L2.txt`). So on
  the old unit's own device picture the instruments name U2, and U1's six gating keys pass.

## Pins (verbatim wave54-final per-test IR) and their executed mutations
Runner `mutate.sh` (literals in `mutations/lit/<label>.{old,new}`; each run's red / green output and tally in
`mutations/<label>.{red,green}.txt*`, summary `mutations/<label>.summary.txt`; mutated-run census records
`mutations/<label>.red.percent-content-box-band-census.now.txt`). Every mutation: `MUTATING` written BEFORE the edit →
focused test RED → byte-exact restore → `RESTORED` (post == pre) → same test GREEN.

| pin | mutation (file) | RED (executed) |
|---|---|---|
| U1 WPT rows (container 100×100; nested clip 200×100 ×2) + census + C8 | M-U1a always subtract (ContainingBlockBands.kt) | 4 / 33 |
| dark-stage rows (300 → 276; autopos 70×80) + census + C8 | M-U1b never subtract | 4 / 33 |
| explicit BORDER_BOX under WPT still subtracts | M-U1c WPT ignores declared border-box | 1 / 33 |
| **C8** dark-stage breadcrumb row | M-U1-C8 delete the `markUnhandled` | 1 / 33 (the C8 row) |
| seam source pin (ChildContainingBlockSeamWiringTest) | M-U1d drop the named argument (ComponentRenderer.kt, seam applied) | 1 / 1 |
| P-U2-1 targetPx / measureSpec on #middle: 220/200, 120/100, fitting 220/220 | M-U2a `+ bandPx` → `+ 0f` | P-U2-1 |
| P-U2-1 (reported) | M-U2b report the child size | P-U2-1 |
| P-U2-2 owns (WPT true; border-box / band-0 / dark-unset false; dark content-box + crumb; MaxWidth + clamp crumb) | M-U2c drop the wptCaptureMode clause | P-U2-2 |
| P-U2-6 census (+ P-U2-2 #inner row) | M-U2d drop `bandPx == 0f` | P-U2-2 + P-U2-6 (owned **2 → 118**) |
| P-U2-5 source + live chain | M-U2e delete the width call site (SizingApplier.kt back to base on that axis) | both P-U2-5 rows |
| P-U2-4 start-anchored | M-U2f centre the placement `(reported − child) / 2` | P-U2-4 |
| P-U2-3 unbounded identity (extra) | M-U2g drop the unbounded identity | P-U2-3 |

M-U2d correction to the brief: it predicted "2 → 122 axes"; executed it is **2 → 118** — the 4 css-tables
height-distribution axes (`height: 100%; min-height: 100px`, band 0, the PercentSizeClamp carriers) stay out through the
clamp clause. The pin is red either way.

## Mutation log (appended live by mutate.sh: MUTATING before the edit, RESTORED after the byte-exact restore)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1a-always-subtract)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1a-always-subtract; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1b-never-subtract)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1b-never-subtract; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1c-wpt-ignores-border-box)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1c-wpt-ignores-border-box; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1-C8-no-breadcrumb)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt sha256 f4bb43ff3ce079d18eef4dfd513bb71b0f95d9cacdb464e8d38b0aa2bae79cb0   (label M-U1-C8-no-breadcrumb; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 7ddeb9b0befcb1b144a315ebb686e027e102228d30ce48c97ecd4f9d4f143a23   (label M-U1d-drop-named-argument)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 7ddeb9b0befcb1b144a315ebb686e027e102228d30ce48c97ecd4f9d4f143a23   (label M-U1d-drop-named-argument; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2a-band-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2a-band-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2b-report-child)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2b-report-child; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2c-wpt-clause-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2c-wpt-clause-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2d-band-test-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2d-band-test-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2f-centred)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2f-centred; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2g-unbounded-not-identity)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2g-unbounded-not-identity; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt sha256 e8f62e4926a01e131e28b5ded6c5228ab93a7af06ad32e0bea32be54a3e7b2a2   (label M-U2e-call-site-deleted)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt sha256 e8f62e4926a01e131e28b5ded6c5228ab93a7af06ad32e0bea32be54a3e7b2a2   (label M-U2e-call-site-deleted; post == pre)
(round 2 — the U2 mutations re-executed on the final PercentContentBoxBandTest.kt bytes, after its census asserts gained count messages)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2a-band-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2a-band-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2b-report-child)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2b-report-child; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2c-wpt-clause-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2c-wpt-clause-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2d-band-test-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2d-band-test-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2f-centred)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2f-centred; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2g-unbounded-not-identity)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 5ae8df6e5d87733f4286782c92029d3033939dc2b4ff3cd96e4af81bf16ca1bc   (label M-U2g-unbounded-not-identity; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt sha256 e8f62e4926a01e131e28b5ded6c5228ab93a7af06ad32e0bea32be54a3e7b2a2   (label M-U2e-call-site-deleted)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt sha256 e8f62e4926a01e131e28b5ded6c5228ab93a7af06ad32e0bea32be54a3e7b2a2   (label M-U2e-call-site-deleted; post == pre)
(round 3 — the U2 mutations re-executed on the FINAL PercentContentBoxBand.kt / PercentContentBoxBandTest.kt bytes: the test compacted to ≤ 200 lines, the banner gained the KNOWN LIMIT paragraph)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2a-band-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2a-band-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2b-report-child)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2b-report-child; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2c-wpt-clause-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2c-wpt-clause-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2d-band-test-dropped)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2d-band-test-dropped; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2f-centred)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2f-centred; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2g-unbounded-not-identity)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/PercentContentBoxBand.kt sha256 ad266adf5ff9ebc188f795d098afb8e1085ae95739581bf5fb8fb436723b26a9   (label M-U2g-unbounded-not-identity; post == pre)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt sha256 e8f62e4926a01e131e28b5ded6c5228ab93a7af06ad32e0bea32be54a3e7b2a2   (label M-U2e-call-site-deleted)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/sizing/SizingApplier.kt sha256 e8f62e4926a01e131e28b5ded6c5228ab93a7af06ad32e0bea32be54a3e7b2a2   (label M-U2e-call-site-deleted; post == pre)

## revertStatesCompiled (`revert-states.sh` → `revert-states.out.txt`, per-state logs in `revert-states/`)
Each state rebuilt on a clean 611c9f93 checkout of the lane worktree from the RE-CUT patches (after the final cut), compiled,
and run through the PLAN §2 L2 "May run" focused suite:
revertStatesCompiled: A-full [unit-U1.patch seam-1.patch unit-U2.patch]: gradle rc=0, 111 tests, 0 failed, 0 skipped (GREEN) — revert-states/A-full.txt
revertStatesCompiled: B-revert-U2 [unit-U1.patch seam-1.patch]: gradle rc=0, 103 tests, 0 failed, 0 skipped (GREEN) — revert-states/B-revert-U2.txt
revertStatesCompiled: C-revert-U1 []: gradle rc=0, 96 tests, 0 failed, 0 skipped (GREEN) — revert-states/C-revert-U1.txt
revertStatesCompiled: D-U1-no-seam [unit-U1.patch]: gradle rc=0, 102 tests, 0 failed, 0 skipped (GREEN) — revert-states/D-U1-no-seam.txt
revertStatesCompiled: E-U1-U2-no-seam [unit-U1.patch unit-U2.patch]: gradle rc=0, 110 tests, 0 failed, 0 skipped (GREEN) — revert-states/E-U1-U2-no-seam.txt
revertStatesCompiled: F-U2-alone [unit-U2.patch]: gradle rc=0, 104 tests, 0 failed, 0 skipped (GREEN) — revert-states/F-U2-alone.txt
- B is both the revert chain's middle state and the landing moment between the U1 and U2 commits; C = 611c9f93.
- D / E prove rule 2b: the owned files compile without the seam patch (the defaulted `wptCaptureMode = false`).
- F (U2 alone) compiles but is never landed (R3: worse than the base on clip-3); it is compile evidence only.
- Final full state restored after the checks (A, byte-identical to the state every mutation ran against, `cmp` on all 8 files);
  `focused-suite.sh` → `focused-suite.final.txt`: 111 tests, 0 failures (DynamicValueResolverTest 33, PercentContentBoxBandTest 8,
  ChildContainingBlockSeamWiringTest 1, the rest unchanged). Neighbouring classes also green on the full state:
  SeamReachabilityTest 3, every `sizing.*` class, `core.variables.*`, StyleApplier* / OutOfFlowContainingBlock / PercentInset*.

## Predictions (from wave54-final = wave55-open; floors 0.95 on gating rows; PLAN §2 L2 table, restated with my confidence)
| cell | from → to | geometry | unit | confidence |
|---|---|---|---|---|
| `filter-effects/backdrop-filter-nested-border-radius-clip-3.html android` | f 0.9574 → P ≈0.999 | GEOMETRY OK on both probes (`'green': (36, 36, 235, 135)`) | U1+U2 | MED (the brief's MED-HIGH; U2's oversized in-flow placeable under #outer's rounded clip and the backdrop two-pass is first executed on device — R1) |
| `…-nested-border-radius-clip.html android` | P 0.9649 → P ≈1 | GEOMETRY OK `(26, 26, 225, 125)` | U1 | HIGH (device: wave54-probe, same Android bytes) |
| `…-nested-border-radius-clip-2.html android` | P 0.9689 → P ≈1 | GEOMETRY OK `(26, 26, 225, 125)` | U1 | HIGH |
| `…-nested-border-radius-clip-4.html android` | P 0.977 → P ≈0.9999 | GEOMETRY OK `(26, 26, 185, 105)` | U1 | HIGH |
| `css-flexbox/abspos/abspos-autopos-{htb,vlr,vrl}-ltr.html android` | P 0.9966 DEGENERATE → P 0.9967 faithful | GEOMETRY OK `(16, 88, 115, 187)` | U1 | HIGH |
| `…/abspos-autopos-htb-rtl.html android` | P 0.9869 → P 0.9966 | report: `GEOMETRY WRONG (green (16, 88, 105, 187) vs ref (16, 88, 115, 187))` (the iOS line) | U1 | HIGH |
| `…/abspos-autopos-{vlr,vrl}-rtl.html android` | P 0.9966 → P 0.9966 (picture → the iOS line) | report, same line | U1 | HIGH |
| `css-position/position-absolute-semi-replaced-stretch-button.html android` | f 0.9739 → f ≈0.9843 | — | U1 | HIGH |
| `…-semi-replaced-stretch-input.html android` | f 0.4117 → f ≈0.4555 | — | U1 | HIGH |
| `…-semi-replaced-stretch-other.html android` | f 0.4396 → f ≈0.4679 | — | U1 | HIGH |
| `css-gaps/flex/flex-gap-decorations-006.html android` | f 0.8223 → f 0.8223 (undirected) | — | U1 | MED |
Totals: android 1128 → 1129 / 1362 (clip-3 f → P); web, iOS +0; lost 0. Must-not-move: every web / iOS cell of the 14
documents; every Android cell outside them (incl. `css-images/cross-fade-target-alpha` ×3, `css-multicol/abspos-autopos-
contained-by-viewport-000/-001`); the fixture net (no committed fixture declares content-box + % + band — census C — and the
dark stage never owns, never stops subtracting). The ring-fenced `filter-effects/backdrop-filter-basic-blur` is in neither
radius; report only.

## Decisions against (or beyond) the brief / plan — for the S1 skeptic
- **D-a (call-site order).** The brief puts the band lane AFTER the clamp lane (`if (pctW != null) … else if (owns(…, false))
  …`). There the clamp argument is false by construction, so `Sizing[percent-clamp-content-box-band]` could never fire — a dead
  breadcrumb, i.e. a silent fallthrough for a WPT content-box % + min/max + band axis. The band lane is asked FIRST with
  `clampOwnsAxis = pctW != null`, and the existing clamp line becomes `else if`. Routing is identical whenever `owns` is true
  (it requires `pctW == null`); on the 4 css-tables clamp carriers `owns` declines at the band test before the clamp test, so no
  breadcrumb there. SizingApplier stays at the planned +3 lines (2 call sites + 1 comment); the two `if` → `else if` edits are
  one-line conditions (PLAN §0 allows them in an oversized file).
- **D-b (the one comment line)** sits at the call site, not at :254-256. The :254-256 KDoc phrase ("a px band cannot be added
  to a fraction — needs a layout-time add") stays literally true of `inflateForContentBox` but is now stale as a design note;
  it joins C9 (ContentBoxRelativeInflationTest's "future work") for the closing docs pass.
- **D-c** M-U2d is 2 → 118, not 2 → 122 (above). **D-d** an extra mutation, M-U2g. **D-e** P-U2-5 has a live-chain half
  (and a dark-stage control) beside the source pin. **D-f** the modifier is a data-class `LayoutModifier`, not a
  `Modifier.layout {}` lambda. **D-g** the C8 row also asserts the WPT counterpart (200, no crumb).
- **D-h** the fraction keeps fillMax*'s `coerceIn(0, 1)`: a % above 100 stays clamped (pre-existing limit, documented in
  `fraction`'s KDoc); 0 corpus carriers (all owned axes are 100%).
- **D-i** the KNOWN LIMIT in PercentContentBoxBand's banner (invalid-width → 100% emulation + band), 0 carriers.
- **D-j** DynamicValueResolverTest: besides the census path, three provenance comment lines re-pointed to wave54-final and a
  two-line "re-landed in wave 55" note; the rows themselves are 5b333c8a's.
- **D-k** the seam hunk is byte-identical to wave 54's; the patch-borne test's header comment names the wave-55 patch.
- The brief's "P-U2-1 … P-U2-6" numbering is kept; the U2 test file is 199 lines, PercentContentBoxBand.kt 191.

## Risks and what is NOT verified (honest)
- R1 (the main one): Compose drawing #middle's 220×120 placeable under its 200×100 reported box, inside #outer's rounded
  `overflow: hidden`, with BackdropModifier's two-pass on #inner's abspos child. JVM tests prove the arithmetic, the routing and
  the chain composition — not the device draw. If the backdrop layer is bounded by #middle's REPORTED box, the green could stop
  at x 225 or show the brief's F4 / F6 shapes; `cbb-android.geometry.py` names them. Only stage 1 decides.
- The constraint values at #middle (max 200 / 100) are inferred from the wave54-probe picture and the code path.
- The censuses run the runtime's property pipeline only in part (ListStyleUaRule, DynamicValueResolver with a superset context,
  SizingExtractor); renderer-side rewrites not replayed (AllReset, resolveOutOfFlowPercentSizes, L4's UA margins) can only
  remove % axes or bands, never add one.
- Not run with other lanes' patches (none delivered when this lane finished): L6 U2 (lands before L2, C13) adds StyleApplier
  lines; L2 cites StyleApplier by symbol and the P-U2-5 live chain asserts only "band nodes outside BackgroundElement and the last
  PaddingElement" (L6 moves the BORDER band; #middle has none). The landing's `--check` and the S1 skeptic re-run it combined.
- The corpus pins (CBB census, P-U2-6) are local-only and `assume`-skip where `tools/titan/runs/wave54-final` is absent (CI).

## ORCHESTRATOR WINDOW REQUESTS
L2 has no window of its own (PLAN §2 L2). Its reads ride the planned windows, on the landed tree with the union carriers:
1. **Stage 1 (`wave55-pre`; filter-effects + css-flexbox are in its 9 sections).** After `beforeEveryDeviceRun`, the launch and
   the hash read of `expectations.json` `stage1Probe`, the L2 slice of the stage-1 read-out:
   `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave55-pre --base wave55-open --lanes L2 --sections filter-effects,css-flexbox`
   - expected: every L2 GATING key PASS — compose-wpt-content-box-cb.geometry.py: the 4 nested-clip android lines end in
     `GEOMETRY OK` and contain `'green': (26, 26, 225, 125)` (clip, -2), `(26, 26, 185, 105)` (-4), `(36, 36, 235, 135)` (-3);
     autopos-{htb,vlr,vrl}-ltr android `GEOMETRY OK` with `'green': (16, 88, 115, 187)`; cbb-android.geometry.py clip-3 android
     `GEOMETRY OK`; the 3 report keys print `GEOMETRY WRONG (green (16, 88, 105, 187) vs ref (16, 88, 115, 187))`; the 22
     web / iOS controls PASS; exit 0.
   - decision: ANY L2 gating FAIL (U1's or U2's) reverts **U2 then U1** — the pair (PLAN §2 L2, D14); a clip-3 FAIL whose line
     shows `bands … grey[236..245]` (F4, unclipped) or `corner 369` (F6, double band) is R1 and is recorded with the picture.
   `node tools/titan/results/wave52-gate/cells.mjs 'nested-border-radius-clip' wave55-open wave55-pre`
   - expected: clip-3 android ≥ 0.95 (≈0.999); clip / -2 / -4 android ≈1 (≥ 0.95); web and iOS identical to wave55-open.
   - decision: a gating floor miss → revert rule 1/2 per `probe-readout.mjs` (the pair). The stage-1 `controls` read must show
     filter-effects web / iOS decoded-pixel identical and Android moved only on the union carriers (L2 nested-clip ×4, L6 clip-rect /
     -zoom) and L6's may-move stems.
2. **Stage 2 (`wave55-probe`, 26 sections)** adds L2's css-position / css-gaps / css-images / css-multicol rows: semi-replaced
   ×3 android up-or-stay (stay f); flex-gap-decorations-006 android Δ≈0 (undirected); cross-fade-target-alpha ×3 and
   abspos-autopos-contained-by-viewport-000/-001 android must-not-move (|Δ| ≤ 0.002). Decision: rule 5 (a leak) names U1 → the pair.
3. **Closing gate:** all 14 L2 rows re-read with the same geometry keys; `pictureCorrectTally` counts the 6 U1 rows (autopos-ltr
   ×3, nested-clip, -2, -4) only when each key prints `→ GEOMETRY OK`; clip-3 is claimed picture-correct only with BOTH clip-3
   keys OK.
4. **Landing check (no device):** after L6 U2 and L4's seams land, re-run `focused-suite.sh`'s command on the landed tree
   (the S1 skeptic's sweep) — expected 111 green L2-suite tests incl. `P-U2-5 live chain`.

## Lane-dir inventory
patches `unit-U1.patch`, `seam-1.patch`, `unit-U2.patch` (headers in `headers/`, cut by `cut-patches.sh`); scripts
`mutate.sh`, `revert-states.sh`, `census.sh` + `census-check.py`, `focused-suite.sh`, `look.py`; records `census.out.txt`,
`census-U{1,2}.junit.txt`, `revert-states.out.txt` + `revert-states/`, `focused-suite.final.txt`,
`geometry-gate.wave54-final.L2.selftest.txt`, `geometry-gate.wave54-probe.L2.txt`, `mutations/`, `look/`.

## Delivered patches (sha256 of the file bytes, written after the final restore)
patch: unit-U1.patch sha256 addb9c053ece63237e5a2aea4dd9080dcd6029c706253d147299a4c2df593bf5
patch: seam-1.patch sha256 219705956c8fd07f188a3386c5e403e64936f49f23ec1b95d4e57592c77b1c48
patch: unit-U2.patch sha256 7773cef3f865c60462ec25f8bebe78c52855f681e0148b2b8c8161d6f08ac37d

STATUS: COMPLETE
