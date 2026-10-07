# Wave 53 — gate record

## Opening gate `wave53-open` (2026-10-06 23:06 → 2026-10-07 02:00 UTC for the corpus; `tools/titan/gate-driver.sh wave53-open`)

Tree: `campaign/applier-campaign` = `neworigin/dev` cdb8a845 (wave 52 squash-merged; exactly the content of `wave52-ship`), plus
the committed wave-53 plan (1f5112cc — nothing the gate executes). **First gate on the new toolchain**: macOS 27.0.1 (26A434),
Xcode 27.0 (27A266a); simulator runtimes iOS 26.0 / 26.2; Chrome for Testing 151.0.7922.47 (unchanged). Installed builds MATCH
the ones built from the tree (`build-hashes.txt`).

### What the corpus run measured

- **29 of 30 sections OK on the first attempt**, every column full (48/48/48; css-cascade 43/43/43).
- **`css-view-transitions` TIMED OUT twice** (attempt 1 at 00:09 → 00:59, attempt 2 after a reprovision at 01:03 → ~01:53): both
  attempts spent the 50-minute watchdog in Step 1 (extract) — the view-transition bake's CDP drives hit `Runtime.callFunctionOn
  timed out` (protocolTimeout 300 s) and re-drove once on `fractional-box-old` and `fractional-box-with-overflow-children-new`
  (wave52-ship had ONE re-drive in this section, the known dirty-crop one); attempt 2 reached 37 of 48 tests. The cause is the
  host, not the code: the machine was swapping throughout (`vm.swapusage` 5.5 → 6.5 GB used of 6–7 GB, compressor 7 GB) while the
  1-minute load sat near 1 — the driver's quiet-host check looks at load and free+inactive memory, both of which passed. The
  booted iOS 26 simulator alone holds ~4 GB resident under Xcode 27 (3973 MB measured at 01:30), four Chrome-for-Testing
  processes ~1.7 GB, the emulator 0.6 GB, Firefox 0.75 GB. The section is re-run with `--resume` once the pressure is lower
  (§ below); no code is changed for it.
- `score-gate.mjs wave52-ship wave53-open` over the 29 sections (`score-open.txt` / `.json`): **0 gained / 0 lost / 0 movers
  (|Δ| ≥ 0.005) / 0 newly measured**; unmeasured-now 137 = the missing section's cells. At |Δ| ≥ 0.00005 (a finer read, not the
  record): **22 web cells moved by ±0.0001, 0 iOS, 0 Android, none by ≥ 0.001.** Up to that noise the pipeline reproduced
  `wave52-ship` on the new host.

### The cross-host web rasterization, measured (control-check calibration 3)

`control-check.mjs wave52-ship wave53-open --sections CSS2,css-lists,css-counter-styles,css-display,css-contain`
(`control-calib-crosshost.json`): iOS and Android **240 / 240 byte-identical each**; the per-test IR **240 / 240 byte-identical**
(the extractor and Chrome produce the same wire on the new host); web **236 of 240 byte-different, and all 236 differ in
PIXELS** — 0 are pure re-encodes. The planner's sample ("236 byte-different, pixel-identical in 9 of 9 decoded") was wrong on the
pixels: the differences are small anti-aliasing / glyph-rasterization deltas — pixel counts 1–10 px (21 captures), 11–50 (30),
51–200 (139), > 200 (46); max channel delta ≤ 8 (7), 9–16 (69), 17–32 (140), > 32 (20). The largest are CJK and Bengali
counter-style glyph tests (`css3-counter-styles-117` 3948 px, delta 44; `cjk-decimal-004`, `cjk-heavenly-stem-205`,
`cjk-earthly-branch-202` ≈ 2600 px, delta 97) — the text rasterizer changed with the OS, not the layout. `counter-suffix` web:
129 px, max delta 27, as the planner measured. **Scores barely notice**: the 22 scored web movers are ±0.0001 and only 6 of them
fall in these 5 sections.

Consequences, recorded for the lanes and the closing gate:
- Across the host change, a byte control on web is meaningless and a pixel control reports every capture — so **every control
  this wave is same-host (`wave53-open` → a later run on this machine)**; whether web capture is byte- or pixel-deterministic
  run-to-run on THIS host is measured by the first same-host probe (`wave53-hh-probe`, plan §4 step 2) before any lane lands.
- `control-check.mjs`'s "re-encoded" class (byte-different, pixel-identical) was exercised by NO calibration — nothing on this host
  produced such a file. It stays as the intended reading of a harmless encoder change; if the same-host probe shows web captures
  byte-identical, the class is simply never used.
- No OS, Xcode, simulator-runtime, emulator-image or Chrome-for-Testing update between `wave53-open` and `wave53-final`
  (plan §8 risk 1). Recorded here: see the header line.

### Control-check calibrations (plan §6; the tool is `control-check.mjs`, generalised from wave 52's)

| pair | expected | measured |
|---|---|---|
| `wave52-preview` → `wave52-ship` | 0 changed, 0 re-encoded, wire 0 | ✓ 1435/1435 identical on all three; wire 1435/1435 identical — HOLDS |
| `wave52-calib` → `wave52-final` | leaks (747 under the byte rule) | 773 capture leaks (web 166 · iOS 333 · Android 274, pixel-changed) + 79 wire documents outside the plan's carriers — FAILS, as it must |
| `wave52-ship` → `wave53-open` (5 sections) | web re-encoded + changed = 236 with `counter-suffix` changed; iOS/Android 0/0 | web 236 changed (0 re-encoded), `counter-suffix` 129 px / Δ27 among them; iOS 0/0, Android 0/0; wire 0 — FAILS by design (cross-host), the "re-encoded ≈ 235" expectation was wrong, see above |

The wire control (per-test IR byte identity outside the plan's wireCarriers) can fail (79 on calibration 2) and holds on the
other two pairs. `adjudicate.mjs` (reads `expectations.json`) fails R1/R2/R4/R5 on the wave-52 record and holds on nothing yet —
its first real reading is the closing gate.

### Fixture net (second launch, `--skip-corpus`, 02:01 → 02:17 UTC)

`BASELINE=1 ./test-all.sh --gate-set`: **exit 0 on all 9 gate fixtures** on the new toolchain (`gate-driver/fixture-net.log`) —
the 390 committed baselines and the 10-line ledger hold under macOS 27 / Xcode 27 as well; `all-then-color` still gate-only
(obligation 0(e): seed its baselines after looking, in a device-idle window). Swap during the net: 6.5 → 7.1 GB used of 8 GB with
Chrome and the emulator already gone; the booted simulator held 3.7 GB resident.

### `css-view-transitions` re-run

- 02:18 UTC: the seed simulator was shut down and re-booted to shed its 3.9 GB resident (swap 7.1 GB used of 8 GB at that
  point, Chrome and the emulator already gone). Swap did not move (7.0 GB after); the boot storm — 229 CoreSimulator processes
  — pushed the 1-minute load to 17.9, and the first `--resume` launch at 02:19 was REFUSED by the driver's own quiet-host check
  (exit 2, the right outcome; its vacuous hash lines were struck from `build-hashes.txt`).
- 02:25 → 03:26 UTC, the second `--resume` launch (after waiting for load < 5): its attempt 1 timed out again at 50 minutes
  in Step 1; after the reprovision, attempt 2 ran the whole section in **8.5 minutes, 48/48/48, one re-drive** (the known
  dirty-crop one on `break-inside-avoid-child`, exactly as `wave52-ship`). Same tree, same captures within minutes of each other:
  the slowness is a wedged renderer state under memory pressure that a fresh browser escapes, not a property of the tests.
  Installed builds MATCH (`build-hashes.txt`; the APK/.app digests differ from the 23:11 launch only because HEAD moved from
  cdb8a845 to the plan commit 1f5112cc between them — no source changed, Gradle/Xcode embed the build identity).
- **`wave53-open` is complete: 30/30 sections.** `score-gate.mjs wave52-ship wave53-open --watch …/wave52-plan/watchlist.txt`
  (`score-open.txt` / `.json`, re-scored over all 30): **0 gained / 0 lost / 0 movers (|Δ| ≥ 0.005) / 0 newly measured / 0
  unmeasured-now over 4096 cells** — web 1229/1372 · iOS 1119/1362 · Android 1111/1362, the corpus-v6.18 numbers exactly.
  At |Δ| ≥ 0.00005: 22 web cells moved by ±0.0001, none in css-view-transitions, no iOS or Android cell. Obligation 0's
  requirement ("0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now against wave52-ship and fixture net exit 0
  on all 9") is MET; the toolchain change (macOS 27 / Xcode 27) moved nothing a score can see, and the natives' captures are
  byte-identical. In css-view-transitions the cross-host control reads: iOS 48/48 and Android 48/48 identical, wire 48/48
  identical, web 6 captures pixel-changed by small amounts (the same rasterizer effect as the five calibration sections).

## Same-host probe `wave53-hh-probe` (2026-10-07 11:15 → 11:29 UTC; `gate-driver.sh wave53-hh-probe --sections css-cascade,css-counter-styles,css-flexbox,css-text --skip-fixture-net`)

Tree: the shared tree with every lane's unseamed edits (L1–L5) — planned as L5's hygiene probe, it measured the union of the
lanes' pre-seam radii as well (PLAN §10 item 7). Host after stopping our processes: load1 3.70, free+inactive 3410 MB. **Four
sections OK on attempt 1** (css-cascade 43/43/43, the others 48/48/48), ~2.5 min a section.

`control-check.mjs wave53-open wave53-hh-probe` (union carrier set, `control-hh-probe-union.json`):

| platform | compared | identical | re-encoded | changed | of which carriers |
|---|---:|---:|---:|---:|---:|
| web | 187 | 186 | 0 | 1 | 1 (`counter-suffix`) |
| iOS | 187 | 186 | 0 | 1 | 1 (`counter-suffix`) |
| Android | 187 | 178 | 0 | 9 | 9 (`counter-suffix`, `bidi-lines-001/002`, the six hyphens tests) |
| wire | 187 | 169 | — | 3 content-changed (all carriers) + 15 renumbered | — |

**CONTROL HOLDS.** Two readings for the closing gate:

- **Web capture is byte-deterministic run-to-run on this host** (186/187 identical bytes; the one difference is a carrier). The
  "re-encoded" class is therefore unused on same-host pairs, and a web decoded-pixel change at `wave53-final` is a render
  change, not an encoder effect — R4/R7's web rule stands as pre-registered.
- **The wire control gained a "renumbered" class** after this run first reported 15 leaks: the 15 `cssom-*-setter{,-invalid}`
  documents after `counter-suffix` in `tests.list`, each identical to its twin once the component-id counter is stripped and
  each shifted by exactly +6 = `counter-suffix` 23 → 29 components. The extractor numbers a section's components with one
  running counter; a carrier that gains components renumbers every later document's ids. The class is admitted only when the
  uniform shift equals the running component-count delta of the content-changed documents before it (the explaining carrier is
  printed per row); the pre-fix output (15 WIRE LEAK) is the classifier's mutation record. `expectations.json` →
  `wireRenumbering`, PLAN §10 item 6.

Per-lane controls on the same pair (for the record, not as evidence): `--lane L1-lists-bakes` FAILS on L2's six hyphens
Android captures; `--lane L5-harness-hygiene` (empty carrier set) FAILS on L1+L2's 11 captures and 3 wire documents — on a
shared-tree probe a per-lane control can only hold for the union; lane isolation is the revert units' job.

## Probe `wave53-probe` (2026-10-07 13:42 → 14:39 UTC; 20 sections, `--skip-fixture-net`; tree 7843a523 = the S1 fix pass + docs)

Host after stopping our processes: load1 5.29, free+inactive 5657 MB (swap 12.3 of 13.3 GB used — it did not matter: no
view-transition section in the probe). **20 / 20 sections OK on attempt 1, every column full** (~2.5–3 min a section).
Installed builds MATCH (`build-hashes.txt`, 13:46 UTC). Full read-out: `probe/` (score, controls, geometry, prediction cells).

### What the score and the controls say (`probe/score-probe.txt`, `probe/control-union.txt`)

- `score-gate.mjs wave53-open wave53-probe --watch …/watchlist.txt --movers 0.005`: **gained 14 · lost 0 · movers 17 ·
  newly measured 0 · unmeasured-now 1427** (= the 10 sections the probe did not run). At |Δ| ≥ 0.002: 19 movers.
- Union control **HOLDS**: web 949/955 identical + 6 carriers · iOS 944 + 11 · Android 937 + 18 · wire 5 content-changed
  (all carriers) + **25 renumbered** (10 css-lists documents +1 after counter-reset-reversed-nested — U1's shadow, as the fix
  pass derived — and the 15 cssom documents +6 after counter-suffix — U2's) · 0 leaks. Per-lane controls fail on the other
  lanes' carriers, as a shared-tree run must.
- Every gating prediction met its floor (17 of 17 measured). The gains, cell by cell, are in `probe/prediction-cells.txt`.
- `adjudicate.mjs` on the partial run: R1 ok; R2/R3 fail on the 10 unprobed sections (informational); R5's "moved 7" were
  the 7 must-not-move cells in those sections (unmeasured-now) — not movement.

### Geometry (the probes the plan reads instead of SSIM)

| probe | result |
|---|---|
| lists-bakes | nested web/ios/android **OK**; counter-suffix web/ios **OK**; **counter-suffix android WRONG** (rtl row 1 right x126 vs ref x144; rows 1–3 right edges 126/134/134 vs 144/144/145) |
| soft-hyphen | every target OK on web/ios/android (span-001, out-of-flow-001/-002); the span-002 self-check anchor OK ×4. `hyphens-out-of-flow-002 web` prints WRONG (box 4 height 26 vs ref 46) — **pre-existing**: f 0.9411 on wave52-ship, wave53-open and the probe alike; a web-runtime defect outside L2, queued |
| canvas-root | display-contents-root-background ×3 + background-attachment-margin-root-001/-002 ×3 — 12 / 12 OK |
| float-avoid | bfc-floats-001/-002 + display-flow-root-002 ×3 — 12 / 12 OK |
| display-table-body 006 | web OK, ios OK, **android WRONG** (square x24-63 vs ref x24-43 — 40 px wide) |

### The revert rules fired twice (pictures looked at, both)

1. **L3 B-android** (`276757ea` → revert `d773ff6a`): the pre-declared probeGatedRevert row. The Android picture shows an
   extra empty outlined cell beside the black square (the anonymous table forest draws a second cell); score 0.9944 → 0.9906
   (rule 2 as well). B-web and B-ios stay (006 web P 1, ios 0.9992, squares at y56-75).
2. **L1 U2** (`a8ffd1c6` → revert `b0edb788`): rule 4 on the geometryGating row counter-suffix android. The Android picture:
   the baked RTL markers land on the wrong rows — "foo" bare, "bar ·", "foo ·", "bar .א", then a lone "ב." (ref: "foo .1 /
   bar .2 / foo .א / bar .ב") — while SSIM rose 0.9547 → 0.9793: SSIM rewarding a wrong picture, which is exactly why the
   geometry probe gates. iOS (0.9873) and web (1) were picture-correct, but the unit is one commit (M never lands without P;
   P alone was never pre-registered) and pNarrowFallback does not apply (no bidi-lines / anchor leak; it keeps the
   counter-suffix rows). Withdrawn with it: bidi-lines-001 android f 0.8934 → P 0.9629 and bidi-lines-002 android +0.028
   (hunk P's gains). U1 stays (nested ×3 OK).

Both decisions are pre-registered for the closing gate in `expectations.json` → `probeDecisions` (`plan-build.py`):
`adjudicate.mjs` withdraws the six predictions from R4 and holds their cells to must-not-move (R5); `control-check.mjs`
withdraws the units' carriers (7 capture stems, 4 wire stems), so a change on them at `wave53-final` is a leak again.
Proof the amended rules can fail: re-read against the probe itself (which HAD both units) they report 12 must-not-move
violations and 7 capture + 4 wire leaks — exactly the reverted content (`probe/adjudicate-probe.txt` was the pre-amendment
reading).

What the closing gate must now show vs `wave53-open`: the 8 remaining gains (margin-root-001/-002 ×3, bfc-floats-002 ×2,
display-contents-root-background ×3 = 11 — minus none; bidi-lines-001 android withdrawn), the DEGENERATE→faithful movers of
L2/L4/L3-A/B-web/B-ios, 0 lost, and every withdrawn cell back at its wave53-open value.
