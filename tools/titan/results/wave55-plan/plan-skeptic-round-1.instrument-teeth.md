# Wave 55 plan-skeptic round 1 — instrument-teeth

Skeptic stage `instrument-teeth`, round 1. Tree `trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0`
(unchanged before / after: `git rev-parse HEAD` both `640276a0f3245a6c90aa9922978e31f5caa1df73`; `git status` gained only
untracked files under `tools/titan/results/wave55-plan/`, mine plus the parallel skeptics'). I read no `wave55-open` capture.
Every result below is by execution. Scripts and full outputs are in `plan-skeptic-round-1.instrument-teeth.d/`.

- The fakes, fake runs and throwaway worktree lived under the session scratchpad.
- The `.d/lib.py` guard refuses to run inside the repository: copy the directory to a scratch dir to re-run it.

**Verdict: DEFECT-CONFIRMED.** The gate readers' refusal paths hold, `land-units.sh --check` is read-only, and
`ab-arms55.sh` writes its records after the last verified restore. The **geometry keys have no teeth against a wrong-but-plausible
picture**:

- 41 of the 43 gating keys PASS at least one fake built from their own fixed picture.
- 39 of those 41 pass a fake outside their declared tolerance.
- On a fake run where every gating key of a lane reads a wrong picture, `geometry-gate.py` **exits 0 in all six lanes**
  (§3). So revert rule 4 and closing rule R6 would call these pictures correct, and the `pictureCorrectTally` would count
  them.
- `probes-selfcheck.txt` only proves that each key fails on the PRE-fix picture. That does not show the key rejects a
  plausible wrong picture: wave-54 lesson "Every gating geometry probe the plan wrote passed some skeptic's fake", repeated.

## 1. Method

**Fake runs.** A fake run is `<scratch>/fakeruns/<id>/sections/…`: a tree of symlinks to `tools/titan/runs/wave54-final`
with only the faked captures replaced. Its run id is the relative path from `tools/titan/runs/`, because every probe
resolves `f'{ROOT}/tools/titan/runs/{run}/…'`.

**How each fake is read.** Through the REAL reader:

```
/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py <fake-run> --base wave54-final --lanes <L> --sections <secs> --json …
```

- The reader runs on the generated `expectations.json`, under the pinned interpreter.
- Each key's verdict comes from the gate's own JSON rows. No probe logic is re-implemented.

**The fixed (passing) picture each fake starts from.** Each one is derived from the wave-54 device captures:

| lane | fixed picture |
|---|---|
| L1 | `hyphen-ios-linebox-replay/<t>-ios-Rm.png` (built from the wave54-final iOS capture) |
| L2 U1 | the `wave54-probe` android capture (the old U1 on device) |
| L2 U2 | `cbb-android-replay/android-clip-3.png` |
| L3 | `positioned-parent-slot-replay/<doc>-android-U1U2/U1/U2.png` |
| L4 | `ua-heading-margin-replay/<test>-<plat>-C.png` (U1+U2) and `-U1.png` |
| L5 | `counter-suffix-rows-replay/ok-all/` |
| L6 | `android-paint-order-replay/<flat>-android-U2.png` and `backdrop-filter-clip-rect-android-U1.png` |

Every `fixed` row PASSes its gating key except L6 `[X] flex-…-safe-002`, whose own U2 replay FAILs; that was already known
(`probes-selfcheck.txt`). So every fake below is the passing picture with ONE defect added.

**The fake classes** (as the task named them): target ink removed, each run shifted ±2–3 px, segments shortened, and
count-preserving rearrangements.

## 2. The gating keys, family by family

Each family lists the targeted fakes its key ACCEPTS (`PASS`, the defect) and the fakes it rejects.

**Validation of every proposed fix.** Each fix below was executed, as a measurement, on:
- the fixed picture, which must stay OK;
- every accepted fake, which must turn WRONG;
- the ref or web rows.

The records are `fixcheck.out.txt`, `fixcheck2.out.txt`, `fixcheck3.out.txt` and `fixcheck4.out.txt`. No probe file was
edited (the probes are outside my write scope; the fix lane applies them).

### 2.1 L2 — `wave54-plan/compose-wpt-content-box-cb.geometry.py` (7 gating keys) and `cbb-android.geometry.py` (clip-3)

`fam_L2.out.txt`. The re-used rule has three parts:
- the strict-green bbox is the ref's ±1;
- the `contains` pins the exact bbox tuple;
- the fail-ink count is ≤ 2 % of the green mass.

Nothing reads the green MASS.

| key | fixed | ACCEPTED (PASS) | rejected |
|---|---|---|---|
| autopos-htb/vlr/vrl-ltr, nested-clip/-2/-4 android (U1 ×6) | PASS | `hollow` (1-px outline, greenPx 396 of 10000), `corners2` (TWO green pixels at the bbox corners, greenPx 2), `stripe30` (a 30-px white gap) | shift +2x, shift −3y, short3 |
| nested-clip-3 android, compose probe (U2) | PASS | `gut-cross` (greenPx 957 of 19384), `square-3-corners`, `stripe30` | shift +2x |
| nested-clip-3 android, cbb probe (U2) | PASS | `gut-cross` (row y80 / col x120 / outline / BR corner kept), `square-3-corners` (three radius-25 corners filled square; only the BR corner is counted) | shift +2x, stripe30 |

```
abspos/abspos-autopos-htb-ltr android {'green': (16, 88, 115, 187), 'greenPx': 2, 'failInkPx': 0} → GEOMETRY OK      (corners2)
backdrop-filter-nested-border-radius-clip-3 android {green (36, 36, 235, 135) greenPx 957 corner 254 row blue[16..25] grey[26..35] green[36..235]} → GEOMETRY OK   (gut-cross, cbb probe)
```

**FIX (validated, `fixcheck.out.txt` F1 / F2):**
- The rule needs a wave-55 copy, because the wave-54 file is restated and out of scope:
  `tools/titan/results/wave55-plan/compose-wpt-content-box-cb.geometry.py`. In it, add a branch after the bbox branch:

  ```python
  elif abs(gn - ref['greenPx']) > max(40, 0.005 * ref['greenPx']):
      why = f'green mass {gn} px vs ref {ref["greenPx"]} (hollow / cut / partial box)'
  ```

  Then point `lanes.L2-cbb-android.geometryProbes[0].cmd` at the copy in `plan-build.py` and regenerate.
- The measured spread across platforms is ≤ 26 px against the ref: web 19374 / ios 19376 / android-fixed 19384 vs ref
  19358. The fakes sit at 2 / 396 / 7000 / 16384 / 19846, so they separate.
- `cbb-android.geometry.py`: count all FOUR 20×20 corner squares `((36,36),(216,36),(36,116),(216,116))` against the ref's
  ±8. Measured: ref [251, 250, 252, 252], fixed [254, 254, 254, 254], square-3-corners [400, 400, 400, 254], gut-cross
  [0, 0, 0, 254]. Add the same green-mass rule.

### 2.2 L3 — `positioned-parent-slot.geometry.py` (7 gating keys)

All seven accept wrong pictures (`fam_L3.out.txt`).

| key | ACCEPTED | rejected |
|---|---|---|
| `[S] dir-style-02a android row6 / row7 / row9` | `gut` (only the 2-px dot and one 2×2 block at the right edge left), `mirror-words` (glyph order reversed inside the extent), `text+2x`, `text+3x`, `text−3x` | — |
| `[S] bidi-tab-001 android rtl-rows` | `gut` (only the outermost blue columns), `rtl+2x` | rtl−3x |
| `[W] anchor-position-multicol-nested-001 android stack` | `grown30` (squares 30×30), `slab` (one 180×100 green slab), `dup-stacked` (four flow-stacked duplicates added) | shift +1x |
| `[S] dynamic-offset-rtl-001 android` | `hollow` (a 1-px magenta frame, 36 px of 100) | shift +2x |
| `[W] dynamic-offset-rtl-002 android` | `shift+3y`, `shift−3y`, `wider+5`, `hollow`: the rule reads the LEFT edge only | — |

```
[S] dir-style-02a android row6 ink (17, 120, 153, 131) dot 4px → GEOMETRY OK            (gut: 9 ink px left of the ref's 684)
[W] anchor-position-multicol-nested-001 android stack cells [100, 100, 100, 100] → GEOMETRY OK   (slab)
[W] dynamic-offset-rtl-002 android magenta (26, 29, 35, 38) → GEOMETRY OK                  (box 3 px low)
```

**FIX (validated, `fixcheck.out.txt` F3b / F3d-e, `fixcheck2.out.txt`):**

1. **dir-style rows.** Cross-correlate the row's ink-column profile (ink px per column, rows y0..y0+20) with the ref's over
   shifts −6..+6. Require the best shift 0 ±1 and the peak ≥ 0.75.
   - Measured: fixed 0.920 @ 0; web 1.000 @ 0.
   - The fakes: gut 0.187, mirror 0.767 @ +5 and 0.681 @ −2, and text ±2 / ±3 at shift ±2 / ±3. All are WRONG.
2. **bidi-tab rtl-rows.** Each band's blue px within ±20 % of the ref band's, and the left edge ±1. The fixed picture
   holds at 248/269 @ x16/16; gut is 20/269 and rtl+2x is @ x18. Both WRONG.
3. **stack.** The 1-px ring outside each 10×10 cell has the WEB row's green count ±4. The `[W]` self-check row is web; the
   ref has green beside cells 2–3.
   - web [0, 0, 0, 0]; fixed [0, 0, 0, 0].
   - grown30 and slab [21, 21, 21, 21]; dup-stacked [10, 10, 10, 10]. All WRONG.
4. **dynamic-offset-rtl-001 `[S]`.** Magenta count = ref's ±5. Hollow gives 36: WRONG.
5. **dynamic-offset-rtl-002 `[W]`.** The FULL magenta bbox = the web row's ±1, and the count 100 ±5. The fixed picture
   holds at (26, 26, 35, 35) n 100. ±3y, wider+5 and hollow are WRONG.
6. Apply 4 and 5 to the `vrl-rtl` twins, which are tally entry #11.

### 2.3 L4 — `ua-heading-margin.geometry.py` (12 gating keys)

`fam_L4.out.txt`.

**The keys' blind spots:**
- `[M]` (005 / 006 / 014) reads ONLY band 1's top: the ref's ±3 when the 2em face is present, the base + margin ±2 when it
  is not.
- `[F]` reads the band count, the band heights, line 2's right edge and the band tops ±3. It never reads line 1's width.

| key | ACCEPTED (targeted fakes) | rejected |
|---|---|---|
| `[M] 005 / 006 / 014 ios + android` (6) | `C:heading+12x` (no x read); `C:line2-removed` (line 2 gone); 014 `C:line1-truncated` (line 1 cut to 40 %); `U1:heading+12x`; 014 `U1:line2-removed` / `U1:line1-truncated`; and within the declared tolerance `C:heading+3y`, `C:/U1:heading−2y` | `U1:heading+3y`, 005 / 006 `line1-truncated` |
| `[F] 014 ios + android` (2) | `C:line1-truncated` (bands [(130, 153, 17, 112), …] → OK); ±3 / ±2 y (tolerance) | +12x, line2-removed |
| `[F] 005 / 006 ios` (2) | only `C:heading+3y` / `−2y`, inside the declared ±3 | +12x, line2-removed, line1-truncated |
| `[M] 011 ios + android` (2) | none: the translation rule (every band = base + 21 ±1, x ±1, same count) held on every fake | all |

**FIX (validated, `fixcheck2.out.txt` L4 and `fixcheck4.out.txt`).** Generalise `verdict_011` to 005 / 006 / 014:
- **2em face present.** The first two bands = the ref's bands: tops / bottoms ±1, x0 / x1 ±3, same count.
  - With ±2 the C-arm fakes +3y, +12x, line2-removed and line1-truncated are all WRONG; −2y still passes, so tops ±1.
- **Today's face (U1 alone).** Every band = the wave54-final band + the margin (21 / 17) ±1, with x ±1.
  - The fixed picture is exact.
  - −2y, +12x, line1-truncated and 014 line2-removed are all WRONG.
- **Why ±1 matters.** `[F] 005 / 006 ios` are the `degenerateRetirementCheck` keys: `adjudicate.mjs` R6 RETIRES a
  degenerate 005 / 006 ios gain on them. A 3-px misplaced heading would therefore be counted as a gain.

### 2.4 L5 — `counter-suffix-rows.geometry.py` (6 gating keys)

`fam_L5.out.txt`, every row PASS. In each fake the erased ink is gone completely, not only its dark core: every non-white
pixel of the blob is erased (`look-L5b.png`: the 005 iOS line without its 、, and counter-suffix iOS rows 5–6 without their
ideographs).

| key | ACCEPTED (targeted fake) | what the picture shows |
|---|---|---|
| `[I] css3-counter-styles-005 ios / android` | `I-comma-removed`, `I-marker-block` | the marker's "、" erased; the marker ideograph replaced by a solid block |
| `[C] counter-suffix ios / android` | `C-ideograph-gutted` | rows 5–6's 一 / 二 replaced by a 1-px speck; the "、" and the item text kept |
| `[T] counter-suffix ios` | `T-1-bar7`, `T-1-down3` | row 9's '1' a solid 7-px bar; the '1' 3 px low |
| `[H] counter-suffix ios` | `H-letter-block`, `H-item-text-gone` | rows 3–4's א / ב solid blocks; the item text 'foo' / 'bar' erased |

- The tally keys `[K] counter-suffix ios` also PASS every iOS fake. The iOS zones exclude y64-159 and y208-231 entirely,
  item text included.

**FIX (validated, `fixcheck2.out.txt` L5 and `xpos_kzone.out.txt` (2)):**

| key | add | measured |
|---|---|---|
| `[I]` | ≥ 2 marker blobs (x < 255) per line, and the last one's right edge = the ref's ±2 | fixed (2, 249); comma-removed (1, 239): WRONG |
| `[C]` | the first marker blob's width = the ref's ±2 | 13 vs 13; gutted 0: WRONG |
| `[T]` | digit ink rows = the ref's ±1, and ink / bbox-area ≤ 0.6 | ref 0.39 / fixed 0.26 / bar 1.0 |
| `[H]` | letter ink / bbox-area ≤ 0.6 | ref 0.44 / fixed 0.33 / block 1.0 |
| `[K]` ios and android | the zone x64-389 × y64-159 (rows 3–6 item text) | ok-all vs wave54-final: 0 px differ on both natives; H-item-text-gone: 307 / 299: WRONG |

### 2.5 L6 — `android-paint-order.geometry.py` (7 gating keys)

`fam_L6.out.txt`.

**`[X]` ×6.** The rule is crossings ≤ 4 AND child fill ≥ 0.90 × ref. It does not check that the parent band EXISTS, nor
where the child is.

| fake | outcome |
|---|---|
| `border-erased` (every dark px white) | PASSES all six, including `safe-002`, whose own fixed replay FAILS (50 crossings). The only way that cell passes today's key is by losing the border. |
| `child+3x`, `child−3y` | PASS 5 / 6 |
| `child-scrambled` (the fill pixels moved to the canvas corner, count kept) | PASSES 6 / 6 |
| `child-shrunk8%` | PASSES safe-001 and rtl-001 |

**`[PO]`.** `navbar-sides-erased` PASSES: 531 of 851 navbar-band blue px were erased; only the 320 px the rule samples were
kept. `menus+1y` / `+2y` are rejected; `whole+1x` passes, inside the bbox ±1 tolerance.

**FIX (validated, `fixcheck3.out.txt`, `fixcheck.out.txt` F4b):**
- `[X]` adds three conditions. All six fixed replays print OK, and every fake prints WRONG.
  - The child-fill bbox = the BASE android capture's ±1 (U2 changes which paint wins, never where a box is).
  - Child fill ≥ 0.98 × the base's.
  - Dark (band) px ≥ 0.9 × min(base, ref) dark px. On the flex pair the base band is twice the ref's, because of the
    crossings.
- `[PO]` adds navbar blue (+ the overlap rows' red, minus the ref's) ≥ 0.95 × the ref's blue (866). Fixed 851: OK.
  Sides-erased 320: WRONG.

### 2.6 L1 — `hyphen-ios-linebox.geometry.py` (3 gating keys)

`fam_L1.out.txt`. The rule reads line CENTROIDS (rows only).

| key | ACCEPTED |
|---|---|
| `001 ios` | `text+3x`, `text−2x`, `g2l1+3x` (one line 3 px right) |
| `003 ios` | `hyph-removed` (every "•" erased), `right-half-cut`, x shifts |
| `004 ios` | `hyph-removed` (every "/−/" erased), `lines-2-3-swapped` ("ple/−/" ↔ "men/−/"), x shifts |

- The wave-54 `hyphenate-character.geometry.py` CONTROLS do catch `hyph-removed` on 003 / 004 ios. But a control FAIL
  never changes the gate's exit, and those controls are not in the tally entries #1–#2.
  - Combo L1: `control 15 (PASS 13 FAIL 2)`, exit 0.

**FIX (validated, `fixcheck2.out.txt` L1).** NB-ios moves line boxes, never glyphs. So every line's ink crop (x < 140) must
equal the SAME line of the base iOS capture (wave54-final = wave55-open), translated by whole rows only, with the same line
count and normalised correlation ≥ 0.97.
- Fixed: 1.000 on all three.
- hyph-removed 0.801 (003) / 0.316 (004); swapped 16 lines vs 15 (001 / 003) and 0.692 (004); every x shift ≤ 0.25. All
  WRONG.
- (001's hyph-removed is a no-op: 001 shows no hyphen.)
- Also add `hyphenate-character.geometry.py hyphenate-character-00{1,3} ios` to tally entries #1–#2 (§4).

## 3. One wrong picture per gating key, every lane at once: the gate exits 0

Command:

```
python3 -B combo.py   (gate-out/combo-L<n>.txt)
```

```
L1: geometry-gate exit 0; gating keys 3: PASS 3 FAIL 0
L2: geometry-gate exit 0; gating keys 8: PASS 8 FAIL 0
L3: geometry-gate exit 0; gating keys 7: PASS 7 FAIL 0
L4: geometry-gate exit 0; gating keys 12: PASS 12 FAIL 0     (011 ios / android on their FIXED picture; 005 / 006 ios via +3y, inside ±3)
L5: geometry-gate exit 0; gating keys 6: PASS 6 FAIL 0
L6: geometry-gate exit 0; gating keys 7: PASS 7 FAIL 0
```

The pictures:

| lane | wrong pictures |
|---|---|
| L1 | 001 shifted +3x; 003 / 004 with no hyphenate-character |
| L2 | six hollow boxes; clip-3 gutted |
| L3 | dir-style rows 6+7+9 all gutted in one picture; bidi gutted; stack slab; rtl-001 hollow; rtl-002 3 px low |
| L4 | 005 android line 2 gone; 006 android +12x; 014 line 1 truncated |
| L5 | the 、 erased; the ideographs gutted, the '1' a bar and the letters blocks in ONE iOS picture |
| L6 | every border erased; the navbar sides erased |

## 4. The picture-correct tally (honesty, read with the keys)

1. **#19–#22: grid `[X]`.**
   - grid-abspos-staticpos-align-self-001, justify-self-001, align-self-vertWM-001 and align-self-rtl-001 android are
     claimed picture-correct on `[X]` alone. `[X]` reads paint order only (§2.5).
   - The lane's OWN U2 replay leaves every abspos child off the ref: 22 / 26 / 26 / 26 children, all displaced > 2 px,
     worst 10 / 10 / 22 / 9 px (component match against the ref, executed: `xpos_kzone.out.txt`).
   - The yellow grid containers are in place: 11 / 11 and 13 / 13, displacement 0. So this is a self-alignment
     (static-position) residue, not a canvas offset. The teal squares sit at different alignments than the ref
     (`look-grid-abspos-staticpos-align-self-001.png`: ref | web | android today | the U2 replay).
   - These cells are P→P today at 0.9673 / 0.9667 / 0.9612 / 0.9646 (`cells.mjs`), with the position residue.
   - **They cannot be called picture-correct.** Either move them out of `pictureCorrectTally.full` into a "paint order
     corrected; static-position residue" label, or give them a position key, which their replay FAILs.
   - safe-001 / -002 are positioned right (bbox Δ0); only their paint order is at stake.
2. **#1–#2: L1.**
   - The entries list only the LINEBOX key. A picture with every hyphenate-character missing counts (§2.6).
   - Add the restated `hyphenate-character.geometry.py hyphenate-character-001 ios` / `-003 ios` control keys to the
     entries' `keys`.
3. **#12–#13: L5.** The `[K]` zone fix of §2.4.

## 5. The gate readers: refusal paths and blind paths (exit codes executed)

### `geometry-gate.py` (`gg_refusals.out.txt`)

| case | exit | |
|---|---|---|
| STALE (probeDecisions reverts NB-ios) | 2 | ✓ |
| cmd off the pinned interpreter | 2 | ✓ |
| gating key without keyCells | 2 | ✓ |
| gating capture removed (`L1-fixed` run − 003 ios) | 3 | ✓ |
| section absent from the run | 3 | ✓ |
| L1 fixed | 0 | ✓ |
| **`--sections css-txt` (typo)** | **0** | "0 keys — PASS 0" (NOT PROBED 230). A vacuous pass. |
| **`--lanes L9` (typo)** | **0** | "0 keys". A vacuous pass. |
| usage (no run id) | 1 | collides with exit 1 = "a gating key FAILed" |

`find_line` collisions: `keycollide.out.txt` shows `keys 230; matched by >1 line: 0; by none: 0`, and no key is a token
prefix of another. Clean.

### `probe-readout.mjs` (`rec/run-readout.out.txt`)

**Synthetic records.** All are `--movers 0` relabels of `score-open.movers0.json`, built by `rec/mk.mjs` and
`rec/mk2.mjs`. The "all-met" record moves every prediction row to its verdict at ≥ floor.

| case | exit | |
|---|---|---|
| usage | 2 | ✓ |
| ORDER | 2 | ✓ |
| NOT A `--movers 0` | 2 | ✓ |
| identity: the gating flips fire rule 3 | 1 | ✓ |
| all-met | 0 | ✓ |
| css-masking missing | 3 | ✓ |
| `--sections css-txt` | 3 | MISSING: good |
| complement +0.0001 | 1 | LEAK ✓ |
| may-move +0.002 | 0 | ✓ |
| may-move +0.0021 | 1 | ✓ |
| carrier lost | 1 | rules 1 + 2 + 3 ✓ |
| gating carrier unmeasured | 1 | rule 3 ✓ |
| **all-met record with `cur` = wave55-final read `--stage1`** | **0** | No check that `cur` is the stage's run. |
| **`--lanes L9`** | **0** | "recorded for another lane 67". A vacuous pass. |

**Latent gap.** A carrier with no prediction row that goes unmeasured-now is never read, although the header promises
exit 3. There is no live instance: the 5 such carriers (cjk-decimal 001 / 004 natives, clip-rect-zoom) are unscored on
their platform.

### `adjudicate.mjs` (`rec/run-adj.out.txt`)

| case | exit | |
|---|---|---|
| usage | 2 | ✓ |
| ORDER | 2 | ✓ |
| NOT A `--movers 0` (prev-side and cur-side) | 2 | ✓ |
| GEOMETRY PAIR | 2 | ✓ |
| all-met | 0 | ✓ |
| **all-met with `cur` = wave55-pre** | **0** | No `cur` = wave55-final guard. |
| **a `--sections`-narrowed geometry JSON of the right pair** | **0** (accepted) | Safe direction: an absent row is "not retired". |

Must-not-move / prediction cells: all 707 + 3352 must-not-move cells and every prediction cell are scored cells of the
4096-cell record (0 unresolved). R5's `if (!cell) continue` is not vacuous today.

### `control-check.mjs` (`run-cc.out.txt`)

Pre = wave54-final; post = fake runs. All of these are **exit 0, "CONTROL HOLDS"**:

| case | compared |
|---|---|
| **`--sections css-txt`** | 0 |
| **post without css-text** | 0 |
| **post without `css-text/ios-screenshots`** | ios 0 |
| **post missing one non-carrier capture** | android 47 |
| **post missing one `per-test-ir` document** | wire 47 |

Correct:

| case | exit | |
|---|---|---|
| usage / unknown run | 2 | ✓ |
| `--lane L9` | 2 | ✓ |
| identity | 0 | ✓ |
| non-carrier 1-px change | 1 | LEAK ✓ |
| carrier change | 0 | ✓ |
| ring-fenced change | 0 | printed RING-FENCED ✓ |

The R4 / R4b claims ("every other composed capture is decoded-pixel identical", "every `per-test-ir/*.json` byte-identical")
are not enforced for a capture or document that DISAPPEARS. At the closing gate, missing captures are caught by
adjudicate R2 / R3; a missing wire document is caught by nothing.

## 6. `land-units.sh --check` is read-only (`lu-check.out.txt`)

**Setup.** In a throwaway `git worktree add --detach <scratch>/wt-lu HEAD`, removed afterwards, I copied in the plan files
and delivered three synthetic L6 patches: U1 and U3 create files, U2 modifies `BordersFacade.kt`.

**Run 1: `--check --only L6`.**
- Exit 0.
- Each patch "alone: applies"; CUMULATIVE ok; REVERT DRY-RUNS `L6:U1 [U2 U1]`, `[U2]` and `[U3]` ok in the export tree.
- Snapshot identical: HEAD, index sha256 + mtime, refs, stash, `status --untracked-files=all`.
- **0 files newer than the pre-run stamp** in the work tree.

**Run 2: the same with three canaries.**
- The canaries:
  - an untracked loose copy at the path U3 CREATES;
  - `BordersFacade.kt` dirty;
  - a stray file in the lane dir.
- Exit 1. It names the dirty touched path, the loose copy ("exists on disk, but its first patch CREATES it") and the
  series stop.
- Snapshot identical; the canaries' sha256 are byte-identical before and after (`1b4c226b… 03cf8f58… ed4c9181…`); 0 newer
  files.

**Run 3: the REAL path (no `--check`) on the canary tree.**
- Exit 1: "the preflight found 4 error(s) — nothing was touched".
- Snapshot identical; the canaries are intact.

**Shared tree.** HEAD is unchanged. No tracked change; the only new files are untracked plan-dir files.

## 7. `ab-arms55.sh` writes its records after the restore (code path + execution)

**Code order.**
- `run_arm` (L168–194) writes only into `$STAGE`: L174, L177, L184, L189 (the hash script's in-tree `tee -a` lands on the
  arm branch).
- `restore_tree` (L196–210) runs `checkout -f` and then verifies HEAD, the branch, tracked-clean, the build-hashes.txt
  pre-arm sha256 and the branch removal; otherwise it exits 6 with the records left in the stage.
- `write_records` (L212–224) is called ONLY at L245, after the arm loop L239–244. `verify_records` follows at L246.
- The on_signal path writes nothing into the tree.

**Records.** `installed-build-hash.sh` writes only `tee -a build-hashes.txt`, which is tracked (`git ls-files`). That append
is discarded by the restore, as designed.

**Dry-run, executed in the throwaway (`ab-dry.out.txt`).**
- `arms-ok.txt --dry-run` exits 2: the throwaway is detached, and the refusal "detached HEAD: there is no branch to restore
  to" is correct. It still printed the revert check ("applies") and `gate-driver --plan`.
- `arms-bad.txt` exits 2 in both `--dry-run` and `--run`, with 6 refusals:
  - a duplicate `drop-U2` (the `abArms` collision D8 names);
  - `css-txt`;
  - the platform `windows`;
  - shas not child-first;
  - `deadbeef`;
  - detached HEAD.
- Snapshot identical; 0 newer files.

**Self-test.** I did not run the runner's `git init` self-test: it is not this stage's one permitted git command. Its
recorded result `ab-arms55.selftest.out.txt` shows the mutants M-L18, M-per-arm and M-dry RED.

## 8. Defects, ranked, with the exact fix

### must-fix

**M1 — The 7 gating keys of `compose-wpt-content-box-cb.geometry.py` (L2 U1 ×6 + clip-3) read the bbox, never the mass.**
- Fix: a wave-55 copy with the green-mass branch of §2.1; `plan-build.py` points the L2 cmd at it; regenerate.
- Add `hollow` / `corners2` / `stripe30` / `gut-cross` / `square-3-corners` as standing fakes in `probes-selfcheck.py`.

**M2 — `cbb-android.geometry.py` clip-3 counts one corner.**
- Fix: four corners ±8 plus the green mass (§2.1).

**M3 — All 7 gating keys of `positioned-parent-slot.geometry.py` accept gutted, mirrored, shifted, grown, hollow or
vertically displaced pictures.**
- Fix: the five additions of §2.2 (profile cross-correlation, band mass + left ±1, ring vs web, magenta count, full bbox
  vs web), also for the vrl-rtl twins.

**M4 — `ua-heading-margin.geometry.py` `[M]` 005 / 006 / 014 ×2 are x-blind and line-2-blind, and `[M]` / `[F]` 014 ×2 are
blind to line-1 truncation.**
- Fix: the band-list rules of §2.3: ref bands ±1 / ±3 with the face, and base + margin translation without it.

**M5 — All 6 gating keys of `counter-suffix-rows.geometry.py` accept a missing 、, a gutted ideograph, a bar for the '1'
and blocks for the letters; `[K]` ios / android omit the rows' item text.**
- Fix: §2.4.

**M6 — `android-paint-order.geometry.py` `[X]` ×6 pass a picture whose border is gone, whose child moved or whose child was
scrambled. `[PO]` passes with 62 % of the navbar band erased.**
- Fix: §2.5. The flex rule takes min(base, ref) dark px as its floor.

**M7 — `hyphen-ios-linebox.geometry.py` ×3 pass x-shifted lines, a missing hyphenate-character and swapped lines (004).**
- Fix: the base-line identity rule of §2.6.

**M8 — The tally claims picture-correctness the keys cannot certify.**
- #19–#22 grid `[X]`: relabel them out of `pictureCorrectTally.full`, or key them on position (§4).
- #1–#2: add the hyphenate-character controls to their keys.
- #12–#13: extend the `[K]` zone.
- Regenerate, `plan-prose.py --fill`, `--check`.

**M9 — `probes-selfcheck.py` proves only "FAILs on the pre-fix picture, PASSes the replay". PLAN §0 "Every gating geometry
key ships with its fakes" is not met by fakes of the plausible-wrong class.**
- Fix: make this note's fake classes a standing section of `probes-selfcheck.py`. For every gating key: target ink
  removed, ±2 / ±3 px per run, a segment shortened, and a count-preserving rearrangement, all from its own fixed picture.
- Exit 1 if any one PASSes.
- Re-run `geometry-gate.py --self-test`, with the restated `expect` strings re-derived where a new reason precedes an old
  one.

### should-fix

**S1 — `control-check.mjs` HOLDS on missing data.** The cases: a `--sections` typo, an absent post section, an absent
platform dir, a capture absent in post, a `per-test-ir` document absent in post.
- Fix: per listed section × platform, iterate the PRE files.
- A pre file absent in post is a counted LEAK ("absent after"); an absent wire document is a WIRE LEAK.
- Exit 2 if a `--sections` name is absent from either run, or a listed section compares 0 captures.

**S2 — `geometry-gate.py` exits 0 on a `--sections` / `--lanes` value that matches nothing.**
- Fix: exit 2 when a `--sections` entry is neither a key's section nor a run section, when `--lanes` matches no lane, or
  when 0 keys are in scope.
- Make usage exit 2 (today 1, which reads as "a gating key FAILed").

**S3 — `probe-readout.mjs` `--lanes L9` exits 0, with every row "recorded for another lane".**
- Fix: exit 2 on a `--lanes` prefix that matches no lane.

**S4 — The `[F]` / `[M]` band-top tolerance of ±3 lets a 3-px misplaced heading PASS `[F] 005 / 006 ios`.** Those are the
keys `adjudicate.mjs` R6 RETIRES a degenerate gain on.
- Fix: tops ±1. This is part of the M4 rule; if a device AA row forces ±2, record the measured value.

### nit

**N1 — `adjudicate.mjs` does not check `score.cur` (exit 0 on a `cur` = wave55-pre record).**
- Fix: add `--run` (default wave55-final) and refuse another `cur`.
- Refuse a geometry JSON whose `sections` is non-null.

**N2 — `probe-readout.mjs` does not check `score.cur`.**
- Fix: with `--stage1` it must match `/^wave55-pre(-r\d+)?$/`; without it, `wave55-probe`.
- Read unmeasured-now carriers in rule 1 (latent; no live instance).

**N3 — `ab-arms55.sh` has one `--against` per arms file.**
- Fix: refuse a file mixing arms of different `abArms.when` (the L4 probe-time arm vs the closing arms), or take a
  per-line against field.

## 9. Reproduce

Copy `plan-skeptic-round-1.instrument-teeth.d/` to a scratch dir, then:

```
nice -n 19 /opt/homebrew/bin/python3.12 -B fam_L{1..6}.py
nice -n 19 /opt/homebrew/bin/python3.12 -B combo.py fixcheck.py fixcheck2.py fixcheck3.py fixcheck4.py gg_refusals.py keycollide.py
nice -n 19 /opt/homebrew/bin/python3.12 -B cc_runs.py && bash run-cc.sh
(cd rec && node mk.mjs && node mk2.mjs && bash run-readout.sh && bash run-adj.sh)
```

For `lu-check.sh`, first create a throwaway `git worktree add --detach`, copy the plan dirs in and add the synthetic L6
patches (§6).
