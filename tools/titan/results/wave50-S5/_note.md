# wave-50 skeptic S5 — converter · web-harness · scorer instrument

Lane scope: B6 (converter `TextDecorationExpander.kt` + its two new tests),
B5 seam S1 (`apps/web-harness/src/sdui/UaBoxlessDisplay.ts` + the
`decorateStyles` hook), B12 (`tools/titan/degenerate-veto-probe.mjs` + the
applied `inject-wpt-block.mjs` seam), B1's validity oracle
(`provablyInvalidDeclaration` in `tools/titan/extract-fixture.mjs`).

Build systems this lane ran, and nobody else: root gradle (`:converter:test`,
`:converter:run`), vitest for `runtimes/web` + `apps/web-harness`. No devices,
no browsers. Every census below was written from scratch — no builder lane's
script was executed and trusted.

## Suite counts, executed

| suite | command | result |
|---|---|---|
| converter | `./gradlew :converter:test --rerun-tasks` | **526** pass / 0 fail (B6's claim of 526 holds; 514 before, +12 = 8 `TextDecorationShorthandExpansionTest` + 4 `ClearPresentationalHintTest`) |
| web runtime | `npm -w runtimes/web run test` | **1337** pass / 0 fail (docs match) |
| web-harness | `npm -w apps/web-harness run test` | **282** pass / 0 fail (docs say 276 — stale) |
| titan node | `node --test tools/titan/degenerate-veto-probe.test.mjs tools/titan/inject-wpt-block.test.mjs` | **133** pass / 0 fail (15 + 118, matches B12) |

## What reproduced exactly

* **B6's corpus census.** My own walk of all 1435 `per-test-ir` docs:
  exactly **one** `TextDecorationColor` lacking `srgb`
  (`text-decoration-shorthands-001`, `{"original":"auto"}`), and **zero**
  `auto`/`from-font` thickness wires (all 33 are `type:"length"`).
  `independent-censuses.json → textDecorationCensus`.
* **B6's PNG claim** (`green-ink-probe.mjs.txt`): the frozen ref for
  `-001` paints **48 px** of rgb(0,128,0)±8 on **row 85**; wave49-final web /
  android / ios each paint **0** — while all three cells PASS at 0.9998 /
  0.9990 / 0.9979. The sibling `-002` paints 4800 px on all four. The
  degenerate-pass is real and the isolation is right.
* **B6's `inherit` pin is honest** and its carrier count is right: exactly
  **4** `text-decoration: inherit` declarations in `tools/wpt/css/**`, none
  inside the 1435-test gate set.
* **`ClearPresentationalHintTest`'s converter claims**, all four, by CLI
  conversion (`adversarial-clear-fixture.json`): `left|right|both|none|
  inline-start` → typed `Clear`; `all|up|object|inherit|ALL` →
  `Generic {_unmapped:true}` passthrough. HTML Rendering's `br` rule set
  (`[clear=left i]{clear:left}`, `[right i]{clear:right}`,
  `[all i],[both i]{clear:both}`) is what the patch implements — the `all`→
  `both` fold is correct, not a shortcut.
* **B5's carrier set.** My own `meta.sourceTag` walk finds the **same 9
  tests with the same per-tag counts**, and **zero `rtc` carriers** corpus-wide.
  Their wave49-final cells match `carriers.json` byte for byte, and
  `passingCellsAtRisk: 7` is exactly right.
* **B5's mutation proof.** Emptying `UA_BOXLESS_DISPLAY` (copy → sha256 →
  edit → run → restore → sha256 verified `209913f5…`) turns **3 of 6** pins
  red, as claimed.
* **B12's draw.** `node tools/titan/results/wave50-B12/draw-sample.mjs --run
  wave49-final` reproduces the **identical 100 cells in identical order**;
  population **3344** matches my own scorer census, which also reproduces
  corpus-v6.15 exactly (web 1205/1379, iOS 1081/1366, Android 1058/1366,
  4111 scored).
* **B12's arithmetic.** Recomputing the fire predicate from
  `veto-measurement.json`'s raw blocks: **0** disagreements with the stored
  `fired` flags; confusion TP 13 / FN 22 / FP 1 / TN 64; recall 0.3714
  (Wilson 0.2317–0.5366); FP 0.0154 (0.0027–0.0821); precision 0.9286; FP
  among differing cells 1/42 = 0.0238. **All seven post-hoc sweep rows
  recompute to the published figures.** Headline Wilson intervals
  (35/100 → 0.2636–0.4475; 33/100 → 0.2456–0.4269) also check out.
* **B12's disarm.** `degenerateVetoActive(true)` returns **false** with
  `TITAN_DEGENERATE_VETO` unset. Re-scoring the whole `css-tables` section
  through the live `diffComposedVsRef` (`rescore-probe.mjs.txt`): **144/144
  cells**, `wptPass` identical to the manifest for every scored cell, **zero
  drift on any stored metric key**, `degenerate` block present on all 144.
  (The only three deltas are the `scoreExcluded:true` row
  `absolute-tables-006`, whose manifest `wptPass` is `null` — a downstream
  stamp, not a scorer difference.)
* **B1's oracle blast radius.** My own sweep of **15 797** declarations
  across all 1435 corpus tests (`oracle-corpus-sweep.mjs.txt`) fires on
  **exactly 4** declarations, all in `css/css-values/angle-units-001.html`
  (`degree`/`gradian`/`radian`/`turns`) — B1's claim, independently.

## What did not hold — see the report's `defects`

1. `doc-staleness-check.sh` **fails** on this tree (converter 526 vs 514,
   tooling 2022 vs 1979, web-harness 282 vs 276, compose 3230 vs 3143,
   swiftui 2004 vs 1979, and `COVERAGE.md`'s dedicated-applier row).
2. The dedicated-`*Applier` Android count is now **44**, not 45
   (`MultipleShadowApplier.kt` was deleted this wave). CLAUDE.md, README.md
   and COVERAGE.md all still say 45; only COVERAGE.md is machine-checked.
3. `degenerateVetoActive` has **no pin in `inject-wpt-block.test.mjs`**,
   where its novel-ink precedent (lines 1884–1901) does.
4. The oracle mis-refuses three classes of VALID CSS
   (`oracle-probe.mjs.txt`): custom properties (`--x: 3bananas`),
   `unicode-range: U+0-7F`, and the css-speech semitone unit
   (`voice-pitch: 2st` — which the converter ACCEPTS and types as
   `VoicePitch`). Zero corpus carriers today.
5. The CSS-wide-keyword hole B6 pinned for `inherit` is wider than the pin:
   `initial`/`unset`/`revert`/`revert-layer` mis-expand the same way, and
   `revert-layer` is a **behaviour change introduced by this wave**
   (previously dropped outright; now `text-decoration-color: revert-layer`).
6. `UA_BOXLESS_DISPLAY`'s docstring mis-cites css-display-3 §2.4 for
   `display: ruby` (an inline-level box type, not an internal display type),
   and its `rb`/`rtc` entries have no measurement behind them.
7. B12's README §5.4 says the seam is "committed here unapplied" — it IS
   applied in this tree.

## Artifacts in this directory

| file | what |
|---|---|
| `independent-censuses.json` | all four censuses S5 rewrote from scratch, with `reproduce` recipes |
| `oracle-probe.mjs.txt` | the 30-declaration oracle probe (10 refused, 10 accepted, 10 adversarial) |
| `oracle-corpus-sweep.mjs.txt` | the 15 797-declaration independent oracle sweep |
| `rescore-probe.mjs.txt` | the live-scorer re-score of `css-tables` vs the manifest |
| `green-ink-probe.mjs.txt` | the rgb(0,128,0) PNG arithmetic on `text-decoration-shorthands-001/-002` |
| `degenerate-perf-probe.mjs.txt` | `computeDegenerate` vs `computeNovelInk` timing on the four largest corpus pairs |
| `probe-web-new-wire-shapes.test.ts.txt` | the deleted `_skeptic50` vitest probe for the NEW `{type:"auto"}` / `{type:"from-font"}` wires |
| `adversarial-*.json` | the converter-CLI fixtures (text-decoration, clear, oracle) |

Tree safety: two production files were mutated under copy→sha256→restore
(`apps/web-harness/src/sdui/UaBoxlessDisplay.ts` → `209913f5…`,
`tools/titan/degenerate-veto-probe.mjs` → `e9af3a8c…`); both verified
byte-identical afterwards and both suites re-run green. The one `_skeptic50`
probe file written into `runtimes/web/tests/` was deleted; its text is kept
here as `.txt`.
