# L1 · web-tail-colour-vt — lane note (wave 52, built 2026-09-25, fix pass 2026-10-05)

Brief: `tools/titan/results/wave52-plan/web-tail.md` §3-A/B, §4 F-A/F-B; plan `PLAN.md` §2 "### L1". Effort S, both fixes built.
Evidence run: `wave51-fix`. Every cell below reads `<section>/<test> <platform> <P|f> <ssim>` from the manifests.
No device, emulator, simulator, test-all.sh, feed-*.mjs or capture-browser-ref was run. The only "drive" is the headless-Chromium
bake probe (no fixture pair written). No seam file is left edited; one comment-only hunk is handed to L5 (below).

## Fix pass (2026-10-05) — the skeptic's findings (`skeptic.md`) and what was done

| # | severity | finding | done |
|---|---|---|---|
| 1 | must-fix | F-B blast radius claimed from an authorship predicate (9 stamps, 4 movers down) | `bake-probe.mjs` now derives its targets from the log (every solve-class bail, 18) and was run twice: **4 stamps, 12 cells**, 0 verdicts differing between runs; `census.mjs` takes the stamp verdict from the probe, never from authorship. Census / Predicted / Hand-offs rewritten below |
| 2 | must-fix | 4 watchlist lines labelled MOVERS DOWN | relabelled as NEGATIVE CONTROLS ("must NOT move under F-B"); `watchlist-check.mjs` with plan + additions → `unmatched 0` |
| 3 | should-fix | stale "bail leaves the fixture byte-identical" contracts | `view-transition-bake.mjs` banner (:88, :112) and the `viewTransitionBakeFixture` JSDoc rewritten; the seam line `extract-fixture.mjs:11645` delivered as `hunk-for-L5-1.patch` |
| 4 | should-fix | column-span hand-off to L12 assumed L12-B re-freezes it | reworded: 0 from either lane unless L12-B adds it to its re-freeze set |
| 5 | nit 4 | `ColorConversionTest.kt:140` said "~(0, 0.27, 0)" | now cites the measured g = 0.2129 |
| 6 | nit 5 | exact path of the new parser test | recorded under Files (it sits in `primitiveParsers/`, the package-correct home; no other lane owns it) |
| 7 | nit 7 | a sampled-null ring left no trace in the reason | the bail return now ends ` (frame-ring null not stamped)` when the ring was sampled and nothing written — the gate's own extract.log can now measure the stamp population. Leading token unchanged (CLI taxonomy key). Pinned + 3 mutations below |
| 8 | nit 6 | bail-path mint tagged `baked-view-transition-tree` | NOT changed: the mint is wave-41's step-2c body verbatim (pinned by the byte-identity test); the tag is component-level only and never reaches inject's `lossyReasons` on the bail path. Left as a recorded nit |

## What changed, and why

**F-A — `color(display-p3-linear …)` (and the two sibling linear spaces) now resolve to sRGB.**
`ColorParser.kt` `when (colorSpace)` gains three arms — `display-p3-linear`, `a98-rgb-linear`, `rec2020-linear` —
each calling a new `ColorConversion.{displayP3Linear,a98RgbLinear,rec2020Linear}ToSrgb`: the parent space's
primaries→XYZ(D65)→sRGB path WITHOUT the transfer decode (css-color-4 §10.2 "linear-light version of …"), then the
existing `.clamped()` simple clip. Before: the regex admitted the space name, the IR carried
`{type:"color", colorSpace:"display-p3-linear", values:[…]}` with `srgb` absent, and all three runtimes painted
nothing for the `.test` box. `prophoto-rgb(-linear)` is NOT added (no ProPhoto matrix exists; 0 corpus docs) — it
stays `srgb: null`, pinned as the runtime-dependent contract. No wire-shape change: `original` is untouched, `srgb`
is only added (pinned).

**F-B — the `::view-transition` backdrop reaches the composed canvas on a solve-class bail, when the MEASURED ring says so.**
`view-transition-bake.mjs`: step 2c of `applyViewTransitionBakePlan` is factored, body verbatim, into
`applyFrameRingStamp(fixture, stem, frameRing)` (null → 0 written, so the baked path is byte-identical — pinned by a
two-fixture deepEqual). New pure `isSolveClassBail(reason)` (the `snapshot solve failed for …`, `non-uniform-snapshot …`,
`missing snapshot solve …`, `isolation window … exceeds` classes, matched on the drive's own reason strings) and
`applyBailFrameRingStamp(fixture, stem, walk, bail, frameRing)` (0 unless `walk.active === true` AND solve-class AND a
non-null ring). In `driveViewTransitionBake`, the `if (bail)` branch samples the settled-page ring ONLY for a
solve-class bail on a clean drive (`ringSampled = isSolveClassBail(bail) && dirtyProbes.length === 0` — a re-drive must
start from an unmutated fixture) and stamps through the pure function. The outcome keeps `status: 'bailed'` and its
leading reason token (CLI taxonomy), and the reason grows ` (frame-ring rgb(…) stamped)` on a stamp or
` (frame-ring null not stamped)` when the ring was sampled and nothing was written (fix pass, nit 7); a bail that was never
sampled keeps the wave-45 return verbatim. extract-fixture.mjs prints the reason unchanged as `[vt-bake: bailed — …]`, so
no seam edit is needed. Never step 1 (`display: none` on live boxes), never the pseudo tree: the page keeps painting in
place; only the canvas colour is added. The ring is MEASURED (frameRingColor / padColorFor, the ref pipeline's own 8-point
rule), never read from the author sheet. **Authorship is not a stamp:** a group snapshot reaching the ring, or a backdrop
the settled page does not show, reads non-uniform or white → null → no stamp. Of the 9 solve-class bails that author a
backdrop, 5 measure null (below).

## Files (all in the L1 "own:" list)

- `converter/src/main/kotlin/app/irmodels/ColorConversion.kt` — three linear conversions (+ banner).
- `converter/src/main/kotlin/app/parsing/css/properties/primitiveParsers/ColorParser.kt` — three `when` arms.
- `converter/src/test/kotlin/app/irmodels/ColorConversionTest.kt` — 4 new pins (29 total, green); fix pass: one comment (nit 4).
- `converter/src/test/kotlin/app/parsing/css/properties/primitiveParsers/ColorParserLinearSpaceTest.kt` — NEW (the plan's
  "new ColorParser linear-space test beside it"; exact path recorded here, nit 5), 9 pins on the VERBATIM six
  `display-p3-linear-00N` values against each test's own `.ref` spelling and the ref PNG's centre pixel.
- `tools/titan/view-transition-bake.mjs` — factor + two pure helpers + the bail-path branch; fix pass: the sampled-null reason
  suffix and the three corrected byte-identical contracts.
- `tools/titan/view-transition-bake.test.mjs` — §8c: 8 pins (140 total, green); fix pass: the wiring pin covers the
  `ringSampled` gate and the not-stamped reason, plus a fix-pass mutation record.
- This directory: `census.mjs` / `census.json` / `census.log`; `bake-probe.mjs` / `bake-probe.json` / `bake-probe.log` (run 2) and
  `bake-probe.run1.json` / `bake-probe.run1.log` (run 1); `png-replay.mjs` / `png-replay.json` / `replay/*.png` (18 F-A + 15 F-B
  simulations + 6 settled-ring screenshots from the first cut); `watchlist-additions.txt`; `hunk-for-L5-1.patch`; `skeptic.md`.

## Pins and executed mutations (every restore byte-exact, sha-256 verified; records in the test headers)

| file | pin | mutation (executed) | result |
|---|---|---|---|
| ColorConversionTest.kt | `display-p3-linear green is CSS green` (±1/255), extremes, `linear twins differ …` | `displayP3LinearToSrgb` made to gamma-decode its inputs | g = 0.21289504620248018 (exp. ~0.50196) and linear == encoded == 0.2087… → 2 fail; restored 82856d07… |
| ColorParserLinearSpaceTest.kt | -001..-006 verbatim, alpha, wire shape, unknown space null | (A) parser arm aliased to `displayP3ToSrgb` · (B) arm deleted | (A) -001 g 0.2129, -006 r 0.1658 → 2 fail · (B) six `srgb` null → 6 fail; restored 45570db2… |
| view-transition-bake.test.mjs §8c | active solve bail stamps ONLY the ring; inactive → 0; white ring → 0; non-solve → 0; existing body-root overridden; refactor byte-identity; wiring | (M1) `walk.active` gate deleted · (M2) full `applyViewTransitionBakePlan` in place of the stamp | (M1) 1 fail (inactive pin) · (M2) 3 fail; restored 5bde8f73… (first cut) |
| view-transition-bake.test.mjs §8c (fix pass) | wiring: `ringSampled` gate, screenshot only under it, `(frame-ring … not stamped)` return | (M3) not-stamped return deleted · (V3) gate without `&& dirtyProbes.length === 0` · (M1) re-run on the new bytes | each 139 / 1 (M3, V3: the wiring pin; M1: `an inactive walk writes nothing`); restored acf8f897…0897c9 |

The skeptic also executed, on the first-cut bytes: `REC2020_TO_XYZ` in place of `P3_TO_XYZ` → 3 fail; deleting the solve-class
gate → 1 fail; `isSolveClassBail` accepting `^no-active` → 2 fail (`skeptic.md` §2).

Commands (fix pass, 2026-10-05): `./gradlew :converter:test --tests app.irmodels.ColorConversionTest --tests
app.parsing.css.properties.primitiveParsers.ColorParserLinearSpaceTest` (JDK 21) → 29 + 9, 0 failures (XML 16:00:48Z);
`node --test tools/titan/view-transition-bake.test.mjs` → 140 / 0. Final lane shas: view-transition-bake.mjs acf8f897…0897c9,
view-transition-bake.test.mjs d75c13c0…3f0d791, ColorConversionTest.kt 3b6a5b79…f0103bc; ColorConversion.kt 82856d07…, ColorParser.kt
45570db2…, ColorParserLinearSpaceTest.kt 704fed0c… unchanged from the skeptic's audit.

## Census (`census.mjs` over the 1435 per-test IR docs → `census.json` / `census.log`; stamps from `bake-probe.json`)

- F-A: `color()` spaces in the IR — srgb 40 decls (12 tests), display-p3 6, a98-rgb 4, xyz-d65 3, **display-p3-linear 6 decls /
  6 tests / 0 with `srgb`** (css-color/display-p3-linear-001..006), `--foo` 1 (custom space, still null). a98-rgb-linear,
  rec2020-linear, prophoto-*: **0 docs**. Blast radius of F-A = the 6 tests, 18 cells, nothing else carries the arm (the
  skeptic's own census agrees: 6 tests / 18 cells; the one raw-text extra, `css-images/gradient/display-p3-linear-gradient`,
  names the space only as an interpolation string, never the `color()` branch).
- F-B: 48 css-view-transitions sources · 29 author a backdrop · **18 solve-class bails** (9 author a backdrop, 9 do not) ·
  authorship candidates 9 · **STAMPS MEASURED 4 → 12 cells**, all four the targets (`fractional-box-with-{shadow,overflow-children}
  -{new,old}`), ring rgb(255, 182, 193) == the frozen ref ring → all 12 cells TOWARD the ref.
  The other 14 write nothing (fixture byte-identical, measured):
  - 5 backdrop authors whose ring was SAMPLED and read null: `capture-with-visibility-mixed-descendants`, `class-specificity`,
    `clip-path-larger-than-border-box-on-child-of-named-element`, `far-away-capture`, `column-span-during-transition-doesnt-skip`
    — negative controls.
  - 8 no-backdrop bails sampled null: animating-new-content(-subset), content-with-child-with-transparent-background,
    content-with-transform-{new,old}-image, element-with-overflow, inline-child-with-(composited-)filter.
  - 1 no-backdrop bail NOT sampled: `break-inside-avoid-child` (dirty crop probe on the drive and on its one re-drive, both
    runs — the clean-drive gate held, so the honest bail rode through).
  Body-roots: 0 in every target IR (the stamp MINTS `<stem>__body`, `display:none` + the ring, and touches no other component).

## Real drive (`bake-probe.mjs`: headless Chromium through the module's own drive on ALL 18 solve-class bails, no fixture written)

- Population derived from `wave51-fix/sections/css-view-transitions/extract.log` with the module's own `isSolveClassBail`
  (18 tests), not typed. Run twice (run 1 `bake-probe.run1.json`, run 2 `bake-probe.json`): 18 driven, 17 sampled, **4 stamped**,
  **0 verdicts differing** between runs; a stamp touched a non-body-root component 0 times; a non-stamp mutated the fixture 0 times.
  extract-fixture.mjs eb9742dc…25f60 (== HEAD) and view-transition-bake.mjs acf8f897… before and after each run.
- The four targets: `… (frame-ring rgb(255, 182, 193) stamped)`, changed component = the minted `<stem>__body`
  `{display:none, background-color: rgb(255, 182, 193)}` only, no `__vt` subtree.
- column-span: `isolation window 500x200 exceeds the 358x568 viewport (frame-ring null not stamped)`. The first cut's screenshot
  (`replay/ring__column-span….png`) shows why: the top three ring points read (1,129,1) — the `target` group's green snapshot
  reaches the top edge — the other five (255,192,203) → non-uniform → null. The plan's "its live ring is pink" is wrong in the
  direction that matters: F-B costs column-span ×3 nothing.
- Agreement with the skeptic's independent `sk-probe.mjs` (two runs, `skeptic.md` §5): the same 4 stamps; the 14 non-stamps
  likewise. This probe adds one distinction the reason suffix now exposes: `break-inside-avoid-child` was never sampled (dirty).

## PNG replay (`png-replay.mjs`, scored with `diffWebVsRef` + the gate's own veto/`computeWptPass` composition; every "before" reproduces the manifest verdict exactly)

- F-A (the `.test` box located on the web capture as the only capture≠ref region — 192×192 at (16,88) for -001/-002, 192×96 at
  (16,184) for -003/-004/-005, 192×64 at (16,88) for -006 — painted with the new arm's sRGB, which equals the ref's centre pixel
  exactly in all six: (0,128,0) · (0,0,0) · (255,255,255) · (0,255,0) · (255,255,0) · (114,137,73)):
  -001 web f 0.8775 C R → **P 1.0000**, ios f 0.8764 → P 0.9990, android f 0.8756 → P 0.9982 · -002 f 0.7977 → P 1.0000 / 0.9989 / 0.9981 ·
  -003 f 0.9484 C → P 1.0000 / 0.9990 / 0.9984 · -004 P 0.9548 → P 1.0000 / 0.9984 / 0.9973 (no colour veto: clip == ref) ·
  -005 P 0.9547 → P 1.0000 / 0.9990 / 0.9984 · -006 P 0.9568 → P 1.0000 / 0.9988 / 0.9980.
- F-B (every pure-white canvas pixel → the measured ring; 219 115 / 222 874 of 234 000 px recoloured, 0 box pixels):
  fractional-box-with-shadow-{new,old} web f 0.9763 C R → **P 1.0000**, ios 0.9749 → P 0.9980, android 0.9739 → P 0.9966;
  fractional-box-with-overflow-children-{new,old} web f 0.9757 C R Pr → **P 1.0000**, ios → P 1.0000, android → P 0.9996. ΔE mean 22.7 → ≤ 0.044.
  (The column-span pink-canvas simulation in `png-replay.json`, P 0.9879 → f 0.9743, is the cost IF a stamp landed; the drive
  measured that it does not, so it is a counterfactual, not a prediction.) The skeptic's own replay reproduced every number above.

## Predicted flips (against wave51-fix; confidence; falsifier)

- **+3 web +6 native** `css-color/display-p3-linear-001/-002/-003` (HIGH — replay P 1.0000 / ≥ 0.9981, colour exact to the
  8-bit pixel; falsifier: the natives paint `srgb` differently from the replay's flat fill — the sibling `display-p3-00N` cells
  already pass at these SSIMs, so unlikely).
- **+4 web +8 native** `css-view-transitions/fractional-box-with-{shadow,overflow-children}-{new,old}` (web HIGH, natives
  MED-HIGH — the real drive stamped all four, twice; replay P ≥ 0.9966; falsifier: a native canvas does not repaint from a
  `display:none` body-root mint — the wave-41 mint path already passes on `css-tags-paint-order(-with-entry)` / `css-tags-shared-element`).
- Movers up (P → P, picture-correctness): `display-p3-linear-004/-005/-006` ×3 (0.952–0.957 → ≥ 0.997).
- **Negative controls — 0 change from L1, measured:** `capture-with-visibility-mixed-descendants`, `class-specificity`,
  `clip-path-larger-than-border-box-on-child-of-named-element`, `far-away-capture`, `element-with-overflow` (in
  `watchlist-additions.txt`) and `column-span-during-transition-doesnt-skip` ×3 (already in `watchlist.txt`). No movers down.
- Sum: **+7 web / +14 native**, the measured F-B blast radius being exactly 4 tests / 12 cells (all toward the ref) and F-A's 6 tests /
  18 cells. This matches the plan's L1 row minus the column-span −3/+3 story, withdrawn by measurement.

## Hand-offs

- **To L5 (extract-fixture.mjs seam owner):** `hunk-for-L5-1.patch` — comment-only, `:11645`, states the one bail class that now
  writes the fixture. Cut on HEAD 7d9c22a7, `git apply --check` clean, verified under the seam lock (sha eb9742dc…25f60 before =
  after, `node --check` OK, `view-transition-bake.test.mjs` 140 / 0 with it applied). Fold into the ordered set at any position.
- **To L12 / orchestrator (calibration):** `column-span-during-transition-doesnt-skip` ×3 P 0.9879 — **0 change from L1 (measured),
  and 0 from L12-B unless L12-B adds it to its re-freeze set.** PLAN §2 L12 re-freezes "exactly the affected refs" (B10's 8 rows, the
  3 all-green refs, Fix B's 10 pairs) and sha1-checks every other frozen ref; L12's `body-margin-census.wave51-fix.json` has 0
  css-view-transitions rows. Do NOT pre-register a −3. Only IF it is added to the re-freeze would its ref interior turn pink while the
  capture stays white (P → f ×3 from the re-freeze alone, no L1 remedy in budget: that needs a window fitting the 500×200 spanner or
  a group-tolerant ring rule shared with capture-browser-ref).
- **To the orchestrator (BACKLOG paragraph, "Web tail residuals" 5):** F-A and F-B shipped; F-B's measured reach is the four
  fractional-box tests. The remaining web-tail cells of web-tail.md §2 belong to L5 (F-C/F-D/F-E) or wave 53 (§9 singletons). Open:
  the `prophoto-rgb(-linear)` arm (0 corpus docs); a shadow-aware overflow window (css-backgrounds-3 §7.1) so the fractional-box
  groups BAKE instead of bailing; the 5 backdrop authors whose settled ring reads null (white or non-uniform; column-span measured non-uniform).
- Received: none. Seam patches: none (the L5 hunk is a hunk for the seam owner, not an L1 seam patch).

## Not verified

- No device run: the three-platform verdicts are the replay's (same diff + same veto composition as the gate) and the real
  drive's stamp, not gate cells. The natives' canvas repaint from a MINTED body-root on a bail (live boxes still painting)
  is inferred from the wave-41 mint path passing elsewhere, not observed on a device.
- The probe ran on the shared mid-wave tree: `extract-fixture.mjs` was at HEAD, but modules it imports may carry other lanes'
  uncommitted edits. The stamp verdict depends only on the live page's settled ring and the drive, so this cannot move it; the
  static fixture bytes around it were not compared against a clean HEAD tree.
- `break-inside-avoid-child` was not sampled (dirty probe, both runs); with no author backdrop its ring is expected null even if a
  gate-time drive comes back clean — expected, not measured.
- Chromium's gamut mapping on -004 was not measured; the ref PNG's (0,255,0) equals the clip, so the replay clears the veto.
- The `--foo` custom colour space (1 doc) stays null by design; not opened.

STATUS: COMPLETE
