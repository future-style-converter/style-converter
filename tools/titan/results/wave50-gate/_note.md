# wave-50 gate — the orchestrator's gate artifacts

Wave 50 was built with **no device** (the host could not run the gate — see
`docs/BACKLOG.md` "Gate on a quiet host"); every builder prediction is a
corpus simulation or a PNG replay, and the seven skeptic lanes
(`tools/titan/results/wave50-S1..S7/`) re-derived each one independently.
This directory holds what the gate itself consumes and what the scorer
must know to attribute movement instead of debugging it.

## Files

| file | what it is |
|---|---|
| `watchlist.txt` | every cell a builder predicted (flip or margin gain) or named at risk, plus the ring-fence, in the format `node tools/titan/score-gate.mjs wave49-final <run> --watch tools/titan/results/wave50-gate/watchlist.txt` reads. Lane F1 appended the 13 tests skeptic S3 found missing from B2 hunk 1's blast radius. |
| `_note.md` | this file — the procedure, the expected exits, and the denominator rule below. |

The gate driver is tracked code since this wave: `tools/titan/gate-driver.sh`
(quiet-host refusal, detached adb, one emulator + one simulator, per-section
process-group watchdog with reprovision + one retry, column-count
verification, `BASELINE=1 ./test-all.sh --gate-set`, `--resume`). The scorer
is `tools/titan/score-gate.mjs` (per-cell diff; validated on history —
wave48-final → wave49-final reproduces corpus-v6.15's +32 / 0 exactly).

## Procedure (in this order)

1. `tools/titan/gate-driver.sh wave50-final` on a QUIET host (1-min load < 6,
   free+inactive > 2 GB after our processes stop; the driver refuses
   otherwise). 30 sections × 48 (css-cascade has 43), then the fixture net.
2. `node tools/titan/score-gate.mjs wave49-final wave50-final --json
   tools/titan/results/wave50-gate/score.json --watch
   tools/titan/results/wave50-gate/watchlist.txt`.
3. Open the PNG of every LOST cell and every headline GAINED cell next to its
   frozen ref before writing a sentence about it (retro A1#0: ~35% of PASS
   cells are visibly wrong renders).
4. Fixture net follow-ups (expected, not regressions — see below), then
   `UPDATE_BASELINE=1` only after LOOKING at each changed PNG.
5. Write `tools/titan/results/corpus-v6-16.json` from `score.json`'s
   `totals.cur` / `sections[*].cur`; `artifact` and `reproduce --run-id` MUST
   read `wave50-final`.

## What the gate is EXPECTED to report (attribute, do not debug)

**Denominator changes, never "lost".** `css-counter-styles/counter-suffix`
ios and android were `scoreExcluded: native-font-parity` in wave49-final and
retro R13 removed that exclusion, so wave 50 scores them for the first time
(skeptic S7's optimistic bound: iOS 0.9292 / Android 0.9044 — two new scored
FAILS). The scorer lists them under NEWLY MEASURED, not LOST; the corpus
note must say so.

**Fixture net (`BASELINE=1 ./test-all.sh --gate-set`):**

- exit 1 on **three Android baselines** — `Android__091_Button_Outline`,
  `Android__094_Input_Field`, `Android__105_Edge_DeepNesting` — is lane
  B11's placeholder-floor fix (applied in this tree): each must come back
  390×62 with ink 115×30 / 200×30 / 115×30 (they are 115×26 / 200×28 /
  115×26 today); 091's border box also widens 48→50 and 105's 46→50. A
  FOURTH changed Android PNG is B11's stop condition: revert the patch.
  After the PNG look, `UPDATE_BASELINE=1 ./test-all.sh fixtures/visual-test.json`
  for those three, then delete the six `android-harness-placeholder-floor`
  ledger lines in `tools/visual/cross-platform-expectations.json` — but
  RE-MEASURE first: S7 puts the post-fix iOS-Android pairs at 0.9540 /
  0.9998 / 0.9510 (105 clears the bar by 0.001; if it does not, that pair
  needs a NEW line naming the real divergence — iOS draws the placeholder
  label at half width, S7's open item — not a deletion).
- exit 1 on the **five iOS radius baselines** the retro pre-announced
  (BorderRadius_Uniform / _Pill / _Mixed, Edge_VeryLargeRadius,
  Edge_InsetRoundShadow) — retro R4's clip fix; PNG review, then
  `UPDATE_BASELINE=1` for the iOS column. Any exit 1 on an iOS component
  NOT in obligation #1's gate-watch list is a real regression.
- exit 5 on the **12 EXPECTED-STALE ledger lines** (10 `ios-borders-radius`,
  2 `android-effects-blur`) — DELETE each named line, nothing else.
- The stale iOS oracle waiver on `radius-overflow-transform.json`
  ROT_SelfRotate_ClippedChild was deleted in this tree by lane F2 (B8's
  6(d) fix makes iOS pass both oracle checks); if the gate still reports it
  stale, the fixture edit did not land.

**Corpus predictions, by confidence (skeptic S7's replays,
`tools/titan/results/wave50-S7/_note.md`):**

| cell(s) | prediction | S7 verdict |
|---|---|---|
| css-values/angle-units-001 ×3 | f→P | strongest in the wave (repaint makes web pixel-identical to the passing -002) |
| css-contain/contain-html-overflow-002 ×3 | f→P | plausible (IR differs from passing -001 by one token) |
| css-backgrounds/animations/background-color-animation-with-table1/3 web+iOS, table4 ×3 | f→P (+7) | plausible; B7 under-claimed table4 android |
| css-counter-styles/counter-style-at-rule/broken-symbols ios+android | f→P (+2, unclaimed by B4) | plausible |
| css-position/position-relative-006 android | f→P | plausible |
| css-masking/clip-path/clip-path-contentBox-1d / -1e android | f→P | plausible; 1e thin (0.010) |
| css-overflow/line-clamp/line-clamp-006 / -007 android | f→P | plausible, moderate (0.032 headroom vs p90 gap 0.031) |
| css-overflow/line-clamp/block-ellipsis-032 android | f→P | thin (0.033 headroom) |
| B3's five Android ::after cells | f→P | four plausible; counter-cjk-decimal thin (0.043 vs a 0.061 CJK gap) |
| css-images/gradient/gradient-hue-direction ×3 | web f→P certain; natives CONDITIONAL on the baked `<hr>` rule painting (fallback iOS 0.9491 / Android 0.9495) | mixed — read the capture height: 600 with a visible rule = flip |
| css-counter-styles/counter-suffix | web f→P (~0.98); natives stay f and are newly scored | mixed |
| css-text-decor/text-decoration-shorthands-001 ×3 | stay P, score up (green underline appears) | must be diffed, not assumed |
| css-writing-modes/ch-units-vrl-003/-004 web | stay P (0.9761, 0.026 headroom) under B5 seam S1 | one picture, one risk |
| css-tables/border-collapse-dynamic-col-001 ×3 | must not move (B5 seam S2 touches it only on the static path) | S2 must-watch |
| filter-effects/backdrop-filter-basic-blur ×3 | byte-identical | ring-fence |

Three of the 17 extractor-changed documents do NOT take the static path at
the gate (`css-pseudo/active-selection-057`,
`css-tables/border-collapse-dynamic-col-001` run post-load;
`css-counter-styles/counter-suffix` runs through the bidi + counter bakes) —
read counter-suffix's new `extract.log` line first; if its bake outcome
differs from `[bidi-bake: baked — 2 roots, 4 runs] [stale-runs dropped: 2]`,
B4's prediction for it must be re-derived before its cells are counted.

## What the gate REPORTED (run `wave50-final`, 2026-09-14 21:14 → 22:44 UTC, quiet host)

**Corpus (30 sections, every column 48/48/48 on the first attempt; css-cascade
43/43/43):** `node tools/titan/score-gate.mjs wave49-final wave50-final` →
web 1205/1379 → **1215/1379**, iOS 1081/1366 → **1089/1369**, Android
1058/1366 → **1077/1369**; per cell **40 gained / 3 lost / 6 newly
measured** (the two counter-suffix native cells and css3-counter-styles-008
+ counter-004 on both natives, all four retro-R13 unexclusions, all
scored f) / 46 movers (`score.json`). The watchlist verdicts are in
`score.txt`'s WATCHLIST block; the ring-fenced
filter-effects/backdrop-filter-basic-blur is score-identical on all three.

**The 3 LOST cells** — css-tables/height-distribution/
percentage-sizing-of-table-cell-children-003/-004/-006, Android — were
bisected ON DEVICE (`lost-cells-bisection.json`): the green
`overflow-y:auto; height:100%; min-height:100px` child of a `height:100%`
table cell renders 0-tall so the abspos z-index:-1 red square shows
(novelPx 10000, colorFailed). Four A/B runs on the same emulator, harness
and byte-identical IR: retro R2's percent-clamp lane disabled → still red;
every wave-50 Compose change reverted → still red; the wave-49 versions of
sizing/layout/table/scroll files → still red; the COMPLETE wave-49 Compose
runtime (226 files) → still red. Neither the retro's nor wave 50's Compose
code is the cause; the IR is identical; the harness app differs from wave
49 by comments only. Open, queued at the top for wave 51 (suspects: build
toolchain/dependency drift, the emulator system image or provisioning
flags, a capture-timing dependency the quiet host exposes). Siblings -005
(no overflow:auto) and -007 (same lane, no overflow) are byte-unchanged.

**Fixture net (`BASELINE=1 ./test-all.sh --gate-set`, rc 5):**
visual-test.json exit 5 (17 stale ledger lines — DELETED: 9
ios-borders-radius, the 6 android-harness-placeholder-floor, 2
android-effects-blur; Edge_InsetRoundShadow iOS-Android stays, it still
diverges) with 10 REGRESSION rows; composition-test.json 0;
filter-sepia-amounts.json **exit 4** (006_Sepia_Translucent iOS-Android /
iOS-web 0.9931 / Δpx 17.09 % / ΔE95 11.5; iOS fill measured rgb(95,117,129)
vs the oracle's rgb(90,92,95)); nested-transforms.json exit 5 (3 stale
Android waivers — retro R1's pivot fix confirmed — DELETED);
radius-overflow-transform.json **0** (B8's iOS clip-inside-transform fix
passes the oracle; its waiver was deleted before the gate);
transform-abspos-pivot.json 0; blend-isolation.json **exit 4**
(003_wrapper iOS-Android / iOS-web 0.9492 / Δpx 0.60 % / ΔE95 0);
opacity-blend.json 0. RESOLUTION of the two iOS exit-4s (post-gate, Catalyst
rasters proven byte-identical to the simulator captures): 003_wrapper was a
real iOS defect — SwiftUI's `.blendMode` propagates to every leaf drawing
op, so the blended box's glyphs blended against its OWN background;
`BlendModeApplier.swift` now chains `.compositingGroup()` first
(compositing-1 §3.1 / §5.1), pinned by `BlendGroupCompositingTests.swift`.
Sepia_Translucent was NOT a render change: the live iOS fill is
(95,117,129), exactly the src-over composite the 2026-08-28 ledger text
recorded; the retro (R13) had deleted its two ledger lines after re-scoring
the committed iOS PNG (#126, 2026-08-29), which reads (89,92,95) and never
matched the live render. Both ledger lines are RESTORED with the live
numbers (owner `ios-effects-filter-composite`) and iOS__006 is re-baselined
to the declared render; the composite fix stays open (BACKLOG 6(x²)).
The fixture net was NOT re-run on device after these edits (the host went
into daytime use and the quiet-host rule refused a second gate); the
ledger/oracle unit suites pass on the edited files, and the wave-51 opening
gate is the on-device confirmation — expected: every fixture exit 0.
Corpus provenance: the corpus captures predate the blend fix; its only
corpus carriers are two iOS cells at P 1.0000 (clip-path-blending-offset,
backdrop-filter-with-mix-blend-mode-same-element) — re-measure at wave 51.

**Baseline refresh (`UPDATE_BASELINE=1 ./test-all.sh fixtures/visual-test.json`
on the same device, every changed PNG measured against its committed
version AND against the web baseline, crops inspected):** 35 PNGs changed
— iOS ×21 (019/020/022/101/107 exactly as pre-announced; the 13 radius
shifts the gate-watch record listed; 046; 087/088 blend at ≤59 px),
Android ×13 (091/094/105 → 390×62 as B11 predicted, ink 115×30 / 200×30 /
115×30; 032_Filter_Blur retro R6; 016/017/018 borders toward web; 046;
097/098 R6 shadows; 089/093/096 at ≤2/255), web ×1
(046_Perspective_Rotate). 30 of 33 native PNGs moved TOWARD the web
render; the three that moved away: Android 098_Neumorphic_Light (7 → 94 px
vs web, max 48: a small HARD CORNER in the light glow where R6's border-box
knockout meets the rounded box — queued), iOS 098_Neumorphic_Light (3163 →
5810 px vs web: the retro R4 clip fix now REVEALS the already-ledgered iOS
shadow-σ divergence the old clip hid; both its ledger lines remain valid),
iOS 093/094 (±13 px, noise). 046_Perspective_Rotate moved on ALL THREE
from a trapezoid to an axis-aligned rectangle: the fixture declares
`perspective: 500px` as the element's OWN property, which css-transforms-2
§6 applies to the element's CHILDREN, so the rectangle is the spec-correct
render and the old three-way-agreeing trapezoid was a degenerate pass —
retro R1's DEFAULT_PERSPECTIVE removal, confirmed on device.
