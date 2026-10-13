# Wave 55 plan-skeptic round 2 — instrument-teeth

Skeptic stage `instrument-teeth`, round 2. Tree `trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0`.
The instruments read, by sha256 prefix:

| file | sha256 |
|---|---|
| `expectations.json` | `a1fdca71…` (the fix-r1 regeneration) |
| `geometry-gate.py` | `9c516fe4…` |
| `probe-readout.mjs` | `73b7b0f8…` |
| `../wave55-gate/adjudicate.mjs` | `2c48748d…` |
| `../wave55-gate/control-check.mjs` | `7cd662c2…` |
| `../wave55-gate/ab-arms55.sh` | `b5e1e7b1…` |
| `land-units.sh` | `98b0b07b…` |
| `probes-teeth.py` | `3e3dddcd…` |

**How this was run.**
- No `wave55-open` capture was read. Every fake is derived from `wave54-final`, `wave54-probe` or the briefs' replays.
- The only git state commands were `git worktree add --detach <scratch>/wt-lu HEAD` and its `git worktree remove`.
- Shared-tree snapshot before and after (`git --no-optional-locks status --porcelain --untracked-files=all` sha256, HEAD,
  index sha256 + mtime): **identical**.
- Every result below comes from execution. The scripts and full outputs are in `plan-skeptic-round-2.instrument-teeth.d/`.
  Scratch paths are redacted to `<scratch>`. The `.py` files refuse to run inside the repository: copy the dir to a
  scratch dir first.

**Verdict: DEFECT-CONFIRMED.**
- Every fix-r1 instrument-teeth fix holds against the round-1 fakes. The standing set re-runs at 84 cases, 0 violations,
  43 / 43 (§1).
- But the rules were tuned to exactly those fakes. New fakes of the same four classes (target ink removed, a run moved
  2–3 px, a segment shortened, a count-preserving rearrangement), built from each key's own fixed picture and read
  through the real `geometry-gate.py`, are **accepted by 37 of the 43 gating keys** (§2).
- With one wrong picture per gating key, `geometry-gate.py` **exits 0 on L2, L3, L4 and L5** (§3).
- The gate readers' refusal paths hold, with four exit-code holes (§4).
- `land-units.sh --check` is read-only (§5).
- `ab-arms55.sh` writes its records only after the last verified restore (§6). Its new `--against` check reads the wrong
  block and ignores MISMATCH (§6).
- PLAN §0, lines 220–223, says every gating key rejects "the plausible wrong pictures of its own fixed picture". That is
  not true at `a1fdca71`.

## 1. Re-check of every round-1 defect marked fixed

`probes-teeth.py --scratch <scratch>/teeth-rerun` (`teeth-rerun.out.txt`) printed:

```
84 cases; violations 0; gating keys with ≥ 1 rejected fake 43 / 43        exit 0   (74.7 s)
```

| round-1 id | fix-r1 claim | re-checked by execution | holds? |
|---|---|---|---|
| M1 / M2 L2 mass, four corners | wave-55 copy + green mass; cbb four corners ±8 | standing set L2 U1 / U2 rows "as pre-registered" | yes, for those fakes (§2.2 has new ones) |
| M3 L3 | profile xcorr, band mass, ring, magenta count, full bbox | standing set L3 rows | yes, for those fakes (§2.3 has new ones) |
| M4 / S4 L4 | band list vs ref ±1 / translation + margin | standing set L4 rows | yes, for those fakes (§2.4) |
| M5 L5 | glyph reads, `[K]` item-text box | standing set L5 rows | yes, for those fakes (§2.5) |
| M6 L6 | `[X]` vs base, `[PO]` whole navbar | standing set L6 rows | yes, for those fakes (§2.6) |
| M7 L1 | glyph identity vs base | standing set L1 rows; and four NEW L1 fakes (§2.1) are all rejected | **yes** |
| M8 tally | grid → `paintOrderOnly`; entries 1–2 + hyphenate-character keys; `[K]` zone | `expectations.json` `pictureCorrectTally` (18 full + 4 paintOrderOnly) | yes; `[K]` ios still has a row-9 hole (§2.5) |
| M9 standing set | `probes-teeth.py` §T | re-run above | yes as built; it covers gating keys only (M6 below) |
| S1 control-check | iterates pre and post; refuses empty scopes | `cc/cc.out.txt` (§4.4) | yes; a vanished CARRIER still HOLDS (S3) |
| S2 geometry-gate scopes | exit 2 on an unknown or empty scope | `gg/gg-refusals.out.txt` G4 / G5 / G6 | yes; a value-less flag exits 1, and `--lanes L1,` widens (S5) |
| S3 probe-readout `--lanes L9` | exit 2 | `rec/readers.out.txt` | yes; a value-less `--lanes` crashes with exit 1 (S5) |
| N1 / N2 / N3 | queued | re-run (§4) | still open (N1, N3 below) |

## 2. Round-2 fakes against every gating key (`r2fakes.py`, `r2fakes.L*.out.txt`)

**Method.**
- Each fake is ONE defect on the key's own fixed picture: the same replays `probes-teeth.py` uses, or the `wave54-probe`
  android capture for L2 U1.
- It is written into a fake run: a symlink tree of `wave54-final` under the scratch dir.
- It is read by `geometry-gate.py <fake-run> --base wave54-final --lanes <L> --sections <s> --exp expectations.json`
  under the pinned interpreter.
- "ACCEPTED" means the key's own row printed PASS.
- `nonoop.out.txt` proves no fake is a no-op: every targeted capture differs from its source by 12 or more px. The zeros
  there are the untouched companion document of a two-document L5 case.

### 2.1 L1 `hyphen-ios-linebox.geometry.py` (3 keys): 11 / 11 reads rejected

The fakes:
- `one-hyph-removed`: group 1 line 1's hyphenate-character only.
- `g1l2-last-cell-cut`: 8 px off one line.
- `g3l2+1x`: one line moved 1 px.
- `all+1x`: every line moved 1 px.

Every one prints `LINEBOX WRONG (glyphs …)`. The glyph-identity rule has teeth. One caveat is in S2.

### 2.2 L2 (8 keys): 19 / 19 reads ACCEPTED

| fake | keys | probe line |
|---|---|---|
| `fail-ink-sliver`: red (abspos) or pink (clip) 2 × 100 px beside the right box | U1 ×6 | `{'green': (16, 88, 115, 187), 'greenPx': 10000, 'failInkPx': 200} → GEOMETRY OK` |
| `notch7`: a 7 × 7 white hole in the "filled" box | U1 ×6 | mass 9951 inside ±50 |
| `radius-moved`: TL arc filled square, BR radius grown so the mass stays | clip, -2, -4 | `greenPx 19369 … → GEOMETRY OK` |
| `c3-pink-sliver-off-scanlines`: pink 2 × 40 at x236-237 y36-75 | clip-3 compose + cbb | `failInkPx 80 → OK`; cbb `corner [254 ×4] row blue[16..25] grey[26..35] green[36..235] → OK` |
| `c3-notch-off-scanlines`: a 7 × 7 hole clear of row 80 / col 120 / corners | clip-3 compose + cbb | `greenPx 19335 → OK` on both |

- The fail-ink tolerance `max(2, 2 % of the green mass)` is 200 px on the abspos boxes and ≈ 390 px on the clips.
- It has **no measured basis**. Fail ink is 0 on every passing picture: ref, `wave54-probe` android, `wave54-final` web and
  ios, the clip-3 replay (`fixcheck.L2L3.out.txt`, `fixcheck9.clip3.out.txt`).
- The sliver is the exact failure picture `stayDegenerateEvenIfPass` records for the RTL siblings ("green x16-105, red
  x106-115").

### 2.3 L3 `positioned-parent-slot.geometry.py` (7 keys): 18 / 18 reads ACCEPTED

| key | accepted fakes | why it passes |
|---|---|---|
| `[S] dir-style-02a android row6 / row7 / row9` | `text+2y`, `text-2y`, `text-vflip` (glyphs upside down) | the column profile and the dot do not depend on y (`ink (17, 122, 153, 133) dot 4px profile 0.92@+0 → GEOMETRY OK`) |
| `[S] bidi-tab-001 android rtl-rows` | `rtl-run-order-mirrored` ("0■" ↔ "■0" inside each RTL run's extent), `yellow-boxes-erased` | extent, left edge and blue mass are all unchanged by a mirror; nothing reads the boxes |
| `[W] anchor-position-multicol-nested-001 android stack` | `red-shown` (a 10 × 10 red square), `other-squares-erased` | the rule reads the 4 cells and their rings only; the test says "only green below, and no red" |
| `[S] dynamic-offset-rtl-001 android`, `[W] -002 android` | `parent-erased` (the yellow positioned parent gone), `parent+3x` | only the magenta child is read (`magenta (86, 26, 95, 35) n 100 → GEOMETRY OK`) |
| `[S] dynamic-offset-vrl-rtl-001 android` (report key; a tally entry) | the same two parent fakes (`vrl.out.txt`) | the same rule |

- Blanking is this lane's DOCUMENTED failure mode: the brief's PLAIN arm "blanks dir-style-02a's rows 6 and 9".
- A mirrored run is the canonical RTL failure.
- One fake is **withdrawn**. `rtl-runs+2y` was also accepted, but the band's blue-row reads are confounded by the
  neighbouring line (`bluebox.out.txt`), so I do not claim that picture as wrong.

### 2.4 L4 `ua-heading-margin.geometry.py` (12 keys): 46 / 46 reads ACCEPTED

| fake | keys | probe line |
|---|---|---|
| `line1-mirrored` and `line1-thirds-swapped` (count-preserving), C arm (2em face) and U1 arm (today's face) | every `[M]` 005 / 006 / 014 ×2 and every `[F]` | U1 arm: `[M] text-decoration-inset-005 ios bands [(130, 151, 16, 191)] → GEOMETRY OK` |
| `011 band1-mirrored`, C and U1 arms (`l4_011.out.txt`) | `[M] 011 ios / android` | `[(158, 172, 57, 184), …] → GEOMETRY OK` |
| `line1-clipped-10px` (segment shortened, C arm) | all 10 face-present keys | `[F] text-decoration-inset-005 ios bands [(134, 173, 16, 326), …] → GEOMETRY OK` (Δx1 −10, inside the ±12) |

- Every L4 read is a list of row bands with their x-extents. No read looks at the glyphs inside a band.
- `adjudicate.mjs` R6 RETIRES a degenerate 005 / 006 ios gain on `[F]` PASS (`degenerateRetirementCheck`). A mirrored or
  clipped heading would therefore be counted as a gain.
- Measured (`fixcheck4.L4.out.txt`): the C-arm replays are the ref's own pixels (Δx1 +0, column profile 1.000). So the
  face-present tolerances (left ±3, right ±12) have never met a device face. Web's own face sits at Δx1 −8 / +2 / −8.

### 2.5 L5 `counter-suffix-rows.geometry.py` (6 keys): 14 / 16 reads ACCEPTED

| fake | key | result |
|---|---|---|
| `C-ideographs-swapped-5-6` (rows read 二、foo / 一、bar: wrong counter values) | `[C]` ios + android | ACCEPTED |
| `C-ideograph+3y` | `[C]` ios + android | ACCEPTED |
| `H-letters-swapped-3-4` (ב. foo / א. bar) | `[H]` ios | ACCEPTED |
| `H-period-3y` | `[H]` ios | ACCEPTED |
| `T-row9-text-erased` (row 9 keeps only its digit) | `[T]` ios **and** `[K]` ios | ACCEPTED (`[K] counter-suffix ios zones y0-63,y160-207,y232-599,x64-389×y64-159 → OK`) |
| `T-row9-text-3x` | `[T]` ios **and** `[K]` ios | ACCEPTED |
| `T-row9-text-erased`, `T-row9-text-3x` | `[K]` android | rejected (163 / 273 px) |
| `I-line1-ideograph-is-line2s` | `[I]` ios + android | ACCEPTED |
| `I-line1-marker+3y` | `[I]` ios + android | ACCEPTED |

The iOS `[K]` zones skip y208-231 entirely, so `counter-suffix ios` (tally entry, keys `[H] [C] [T] [K]`) counts as
picture-correct with row 9's item text gone.

### 2.6 L6 `android-paint-order.geometry.py` (7 keys): 4 / 5 reads ACCEPTED

`one-child-moved-3px` moves ONE child 3 px inside the children's union bbox, so the bbox is unchanged:

| key | result |
|---|---|
| `[X] grid-abspos-staticpos-align-self-001 / justify-self-001 / align-self-vertWM-001 / align-self-rtl-001 android` | ACCEPTED (`crossings 0 \| child fill 1000 (ref 1056) base 914 \| bbox (19, 30, 158, 230) … → GEOMETRY OK`) |
| `flex-…-safe-001` | rejected: the move created 40 crossings |
| `flex-…-safe-002` | not attacked: its own replay FAILs (known) |
| `[PO]` | no round-2 fake found that passes it |

The union-bbox read cannot see one of 22–26 children moving.

**Tally of §2.** Gating keys that accept at least one round-2 fake: L1 0/3 · L2 8/8 · L3 7/7 · L4 12/12 · L5 6/6 · L6 4/7.
That is **37 / 43**.

## 3. One wrong picture per gating key, a whole lane at once (`combo2.py`, `combo2.out.txt`)

```
L2: geometry-gate exit 0; gating keys 8: PASS 8 FAIL 0 UNMEASURED 0     (six fail-ink slivers; clip-3 pink sliver off the scanlines)
L3: geometry-gate exit 0; gating keys 7: PASS 7 FAIL 0 UNMEASURED 0     (rows 6 +2y / 7 vflip / 9 −2y in ONE picture; bidi mirrored; stack red; both parents erased)
L4: geometry-gate exit 0; gating keys 12: PASS 12 FAIL 0 UNMEASURED 0   (005/006/014 natives clipped 10 px; 011 on its fixed C picture)
L5: geometry-gate exit 0; gating keys 6: PASS 6 FAIL 0 UNMEASURED 0     (letters swapped + ideographs swapped + row 9 erased in ONE picture; 005 marker +3y)
```

So revert rule 4 and closing rule R6 would call every one of these pictures correct.

## 4. The gate readers: refusal paths, `--sections`, exit codes

### 4.1 `geometry-gate.py` (`gg/gg-refusals.out.txt`, `gg/g14-perkey.out.txt`)

| case | exit | |
|---|---|---|
| G1 no run id | 2 | usage ✓ |
| G4 `--sections css-txt` | 2 | REFUSED unknown scope ✓ |
| G5 `--lanes L9` | 2 | ✓ |
| G6 `--sections css-cascade` (a run section with no key in L1) | 2 | "0 keys in scope" ✓ |
| G8 `--section css-text` (typo'd flag) | 2 | usage ✓ |
| G9 STALE (probeDecisions reverts NB-ios) | 2 | ✓ |
| G10 off-interpreter cmd | 2 | ✓ |
| G11 a gating key without keyCells | 2 | ✓ |
| G12 an unknown run | 3 | UNMEASURED 27 ✓ |
| G13 `--base wave55-nope` | 3 | UNMEASURED ✓ |
| G16 L1 fixed | 0 | ✓ |
| **G2 `--sections` with no value / G3 `--lanes` with no value** | **1** | `IndexError` traceback. Exit 1 means "a gating key FAILed". |
| **G7 `--lanes L1,`** | **1** | the empty entry is a prefix of every lane: the read widens to all lanes (43 keys) and names `L3 U2` for revert |
| **G15b L1 `text+3x` with `--base` = the run itself** | **0** | (G15a with base wave54-final: exit 1) |
| **G14b L6 `child+3x` with `--base` = the run** | 1 | **5 of 6 `[X]` keys PASS** (only safe-002's known 50 crossings fail) |

Base-relative reads lose their teeth when `--base` is the run itself (or any run with the same picture): L1 glyphs, L6
`[X]`, and the L3 / L5 `[C]` / `[K]` controls.

### 4.2 `probe-readout.mjs` (`rec/readers.out.txt`; records from round 1's `mk.mjs` / `mk2.mjs`)

**As required:**
- usage → 2; ORDER → 2; NOT A `--movers 0` → 2.
- identity `--stage1` → 1 (rule 3 fires 9); all-met → 0.
- `css-masking` missing → 3; `--sections css-txt` → 3 (MISSING).
- must-not-move leak → 1; may-move inside the band → 0; may-move outside it → 1.
- carrier lost → 1 (rules 1 + 2 + 3); gating carrier unmeasured → 1.
- `--lanes L9` / `''` / `L1,` → 2; `--lane L1` → 2 (usage).

**Holes:**
- `--lanes` with no value → **exit 1** (`TypeError: Cannot read properties of undefined (reading 'split')`).
- `--exp` with no value → exit 1 (TypeError).
- `--sections` with no value or `''` → the run's full list (a widening, harmless).
- **The all-met record with cur = wave55-final, read `--stage1` → exit 0** (round-1 N2, still open).

### 4.3 `adjudicate.mjs`

**As required:**
- usage → 2; ORDER → 2; NOT A `--movers 0` → 2.
- all-met → 0; GEOMETRY PAIR (wave55-pre) → 2.
- `--geomtry` (typo'd flag) → 2 (usage).
- `--geometry` with no value → read as no geometry, so nothing is retired (the safe direction).

**Holes:**
- `--exp` with no value → exit 1 (TypeError).
- **The all-met record with cur = wave55-pre → exit 0** (round-1 N1, still open).

### 4.4 `control-check.mjs` (`cc/cc.out.txt`; pre = wave54-final, post = symlink runs)

**As required:**
- usage → 2; identity → 0 (96 / 96).
- `--sections css-txt` → 2; `--sections css-text,` → 2; `--lane L9` → 2.
- carrier wire document gone → 1 (WIRE LEAK, absent after: hyphenate-character-001 is a capture carrier, not a wire
  carrier).
- ring-fenced capture gone → 1; a new capture → 1 (absent before).
- the three platform dirs gone → 1; the `per-test-ir` dir gone → 1.
- a non-PNG capture → 1 (sharp throws: the conservative direction).
- the bidi-lines-002 android 1-px nudge → 0 as `may-move` (D21, by design) and 1 with `--lane L1`.

**Holes:**
- **A carrier capture that vanished** (`hyphenate-character-001` ios) → `changed 1 (carriers 1)`, **CONTROL HOLDS, exit 0**.
- **A new capture named `wpt__css-text__bidi__002.png`** → invisible: the `pngs()` filter drops every `__\d+\.png`. There
  are 0 such stems among the 12915 captures of wave54-final today.

### 4.5 Latent gap: an unmeasured carrier with no prediction row (round-1 N3)

There are 81 carrier cells; 5 have no prediction row, and **0** of those 5 are scored on wave55-open. So there is still
no live instance.

## 5. `land-units.sh --check` is read-only (`lu-check2.sh`, `lu-check2.out.txt`)

**Setup.**
- Throwaway `git worktree add --detach <scratch>/wt-lu HEAD`, removed afterwards.
- Plain copies of the untracked plan / gate files.
- A synthetic L6 delivery:
  - `unit-U1.patch` creates `BehindContentBorder.kt`;
  - `unit-U2.patch` modifies `BordersFacade.kt`;
  - `unit-U3.patch` creates the test;
  - `_note.md` with `TREES:`, three `patch: <name> sha256 <64 hex>` lines and `STATUS: COMPLETE`.
- Each run is bracketed by a snapshot (HEAD, index sha256 + mtime, refs, stash, worktrees, `status
  --untracked-files=all`) and a `find -newer` sweep.

| run | exit | what it printed | snapshot | newer files | export dirs left |
|---|---|---|---|---|---|
| A `--check --only L6` (clean) | 0 | cumulative ok, three revert dry-runs ok, "landable" | IDENTICAL | 0 | 0 |
| B the same, with four canaries (untracked loose copy at U1's CREATED path; `BordersFacade.kt` dirty; a stray in the lane dir; a stray at the tree root) | 1 | 5 errors: "exists on disk, but its first patch CREATES it", "MODIFIED in the working tree", the series STOPS | IDENTICAL | 0 | 0 |
| C the REAL path (no `--check`) on the canary tree | 1 | "the preflight found 5 error(s) — nothing was touched" | IDENTICAL | 0 | 0 |
| D a stale `unit-U9.patch` the ORDER table does not know | 1 | "not a patch the ORDER table names" + "names no 'patch: unit-U9.patch sha256' line" | IDENTICAL | 0 | 0 |
| E `unit-U2.patch` re-cut after the note | 1 | "sha256 … differs from the note's …" | IDENTICAL | 0 | 0 |
| F `STATUS: PARTIAL — killed` | 1 | "a lane lands only on STATUS: COMPLETE" | IDENTICAL | 0 | 0 |
| G restored | 0 | landable | IDENTICAL | 0 | 0 |

- The canary sha256s are byte-identical before and after B and C (`fb654593… 03cf8f58… ed4c9181… d3d3e36f…`).
- The canaries were then removed with plain `rm` / `cp`, never git.

## 6. `ab-arms55.sh`: the records come after the restore; the `--against` check

**Code order (read).**
- The arm loop (L258–263) calls `run_arm` (steps 1–5, writing only into `$STAGE`), then `restore_tree` (`checkout -f`,
  then a VERIFY of HEAD, branch, tracked-clean, the build-hashes.txt pre-arm sha256 and the branch removal; `exit 6`
  otherwise).
- `write_records` (L264) is reached only after the LAST verified restore. `verify_records` follows it (L265).
- `on_signal` writes nothing into the tree.
- So the order holds as claimed. I did not re-run the runner's `git init` self-test: it is not the one git command
  allowed here. Its record, `ab-arms55.selftest.out.txt`, shows M-L18 / M-per-arm / M-dry RED.

**Dry runs in the throwaway** (`ab-dry2.out.txt`; the status / HEAD / refs snapshot was identical across every run):

| run | exit | |
|---|---|---|
| AB1 `--against wave55-open` | 2 | the only arm-independent refusal is the detached throwaway HEAD (expected). It prints "wave55-open ran tree a2a37a8e". |
| **AB2** a `wave55-pre` block naming this HEAD whose ios line reads `MISMATCH` (built digest MISSING) | 2 (for the dirty record and the detached HEAD only) | **"against: wave55-pre ran tree 640276a0 — no code difference"**: an against run whose installed build did not match its tree is ACCEPTED |
| **AB3** `wave55-final` with a corpus-launch block on wave-53's tree `7cce3b22` (code differs: 64 files) FOLLOWED by a net-launch block naming HEAD | 2 (same) | **"against: wave55-final ran tree 640276a0"**: the check reads `tail -1`, i.e. the NET-launch block, not the corpus launch that produced the scored captures |

- `build-hashes.txt` was restored byte-identical with a plain `cp`.
- AB3 is not hypothetical. `wave55-open`'s own blocks already differ: the corpus launch names HEAD `0d0d3a3d`, the net
  launch names `a2a37a8e`, and the net block reads `ios MISMATCH` (the race).
- `tail -1` therefore reads exactly the block the gate note calls "not evidence".

## 7. Defects, ranked, with the exact fix

### must-fix

**M1 — L3: all 7 gating keys and the vrl-rtl-001 tally key accept plausible wrong pictures. Combo exit 0.**

Edit `positioned-parent-slot.geometry.py` and add these reasons:

1. **dir rows.** Add a row profile: ink px per row over rows `y0−4 … y0+23`, cross-correlated with the ref's over ±4. The
   best shift must be 0 ±1.
   - Measured: web `(1.0, 0)`, fixed `(0.996, 0)`.
   - Fakes: +2y → +2, −2y → −2, vflip → −3 (`fixcheck.L2L3.out.txt`).
   - Alternative: the 2-D ink-crop correlation at (0, 0) ≥ 0.75 (fixed 0.845–0.865; every round-1 and round-2 fake
     ≤ 0.528).
2. **bidi rtl-rows.** Per RTL band, the blue column profile's correlation with the ref's at shift 0 must be ≥ 0.80.
   - Measured: web 0.999, ios ≥ 0.85, fixed ≥ 0.904; mirrored ≤ 0.701.
   - Each yellow box's bbox (y60-155, y160-255) must equal the ref's ±1. Fixed: `(16,66,136,149)` vs the ref's
     `(16,66,135,149)`. Erased: None.
3. **stack.** Strict red px must equal 0 (ref / web / ios / fixed: 0; the fake: 100). Green px outside the four cells
   (with their ±2 ring) must be within ±10 % of web's. Web, ios and fixed are all 300; the erased fake is 0.
4. **dynamic-offset `[S]` and `[W]` (and vrl-rtl-001 / -002).** The yellow parent's bbox must equal the ref's ±1, and
   yellow + magenta inside it must equal the ref's ±1 %.
   - Ref, web, ios and fixed all read `((16, 16, 115, 115), 10000)`.
   - The erased fake reads None; `+3x` reads `(19, 16, 118, 115)`.

**M2 — L2: all 8 gating keys accept a fail-ink sliver, a hole, or a mass-preserving radius move. Combo exit 0.**

Edit `compose-wpt-content-box-cb.geometry.py` (and the cbb clip-3 key the same way):
- Replace the mass reason with a mask reason: `|green(capture) XOR green(ref)| ≤ 40`, pixelwise on the same canvas.
- Lower the fail-ink bound to `≤ 2` px. Measured fail ink is 0 on every passing picture.
- Measured XOR (`fixcheck.L2L3.out.txt`, `fixcheck9.clip3.out.txt`):

| picture | XOR px |
|---|---|
| web / ios vs the ref (passing) | ≤ 25 |
| fixed android (passing) | ≤ 28 |
| notch (fake) | 49–77 |
| radius-moved (fake) | 283–339 |
| clip-3 notch (fake) | 72 |

- The XOR reason subsumes the bbox ±1 and mass reasons. Keep the bbox printed first, so the plan's `contains` strings
  still match.

**M3 — L5: all 6 gating keys and `[K] ios` (a tally key) accept wrong counter glyphs, vertical drift and an erased row 9.
Combo exit 0.**

Edit `counter-suffix-rows.geometry.py`:

1. **`[C]`.** Per row 5 / 6, the first marker blob's stroke count (row-runs of `min channel < 200` ink in its columns)
   must equal the ref's (1 / 2), and its ink rows must equal the ref's ±1.
   - Ref `(1,(123,124)) / (2,(142,152))`; ios and android fixed agree.
   - The swapped fake reads (2, 1); `+3y` reads rows 126 / 145 (`fixcheck6.L5L6.out.txt`).
2. **`[H]`.** Each row's letter column profile must correlate higher with the ref letter of the SAME row than with the
   other row's.
   - Fixed: row 3 `[0.949, 0.795]`, row 4 `[0.532, 0.852]`. Swapped: inverted (`fixcheck8.L5H.out.txt`).
   - The period's ink rows must equal the ref's ±1 (fixed exact; `−3y` reads 77 vs 80).
3. **`[K]` ios.** Add the zones `(0, 208, 136, 232)` and `(151, 208, None, 232)` to `ZONES['ios']`.
   - The ok-all replay differs from wave54-final in row 9 only at x137-145 (65 px), so the new zones read 0.
   - Fakes: 161 / 266 px (`fixcheck5.L5.out.txt`).
4. **`[I]`.** Per line, the first marker blob's stroke count must equal the ref's (1 / 2), and its ink rows must equal
   the BASE capture's ±1, not the ref's.
   - Android sits 2–4 px below the ref on both base and fixed: `104-105 / 128-145` vs the ref's `101-103 / 125-142`.
   - Fakes: android stroke 2; ios rows 109 vs 102; `+3y` rows 105 / 107.

**M4 — L4: all 12 gating keys accept count-preserving rearrangements of the heading. Combo exit 0. R6 would retire a
mirrored 005 / 006 ios heading as a gain.**

Edit `ua-heading-margin.geometry.py` and its cmd:

1. **Today's-face branch and `verdict_011`.** Each band's ink crop (rows `t−3 … b+3`) must equal the base capture's same
   band translated by the margin, with correlation ≥ 0.97 (the L1 glyph rule).
   - Fixed: 1.000. Fakes: 0.387–0.646 (`fixcheck4.L4.out.txt`).
   - The probe then needs the base capture. Add `--base <base>` to `lanes.L4…geometryProbes[0].cmd` in `plan-build.py`;
     today it reads the hard-coded wave54-final `BASE_BANDS`.
2. **Face-present branch.** Line 1's column profile must correlate with the ref's at the best shift |s| ≤ 12 at ≥ 0.95.
   - Web: 0.999. Mirrored: ≤ 0.771. Thirds swapped: ≤ 0.844.

Both reads are cheap. The mechanism (a UA margin or face) cannot plausibly reorder glyphs, so this ranks last among the
must-fixes. But the key accepts it, and R6 counts on it.

**M5 — L6 `[X]` grid ×4: one child moved 3 px inside the union bbox passes.**

Edit `android-paint-order.geometry.py` `x_rule` and replace the union-bbox reason with a subset read against the base:
- `lost = |fill(base) ∖ fill(a)| ≤ 4`.
- `extra = |fill(a) ∖ dilate₁(fill(base) ∪ dark(base))| ≤ 4`.

U2 only REVEALS fill where the band was. Measured (`fixcheck6.L5L6.out.txt`):

| picture | lost px | extra px |
|---|---|---|
| fixed (all six) | 0 | 0 |
| one-child-moved | 18–263 | — |
| round-1 child+3x | 359–1034 | — |
| round-1 scrambled | ≈ 900–11 400 | — |
| round-1 shrunk | 76–1369 | — |

Keep the crossings and band reasons.

**M6 — the standing set (PLAN §0, lines 220–223; §1 step 5) claims every gating key rejects its plausible wrong
pictures. 37 / 43 accept a round-2 fake. The set also covers gating keys only.**

The tally's non-gating keys have no fakes: `[S] dynamic-offset-vrl-rtl-001 android` (report), `[K]` ×2 (control) and the
two hyphenate-character controls. Two of these accept wrong pictures today: vrl-rtl-001 (`vrl.out.txt`) and `[K]` ios.

Fix:
- Port `r2fakes.py`'s builders (and `l4_011.py`, `vrl.py`) into `probes-teeth.py`, with every round-2 case pre-registered
  FAIL.
- Add a case set for every `pictureCorrectTally` key, not only the gating ones.
- Re-run `probes-selfcheck.py`. It must exit 1 until M1–M5 land, then 0.
- Re-fill the PLAN prose from its last lines (`plan-prose.py --fill`, `--check`).
- Keep the `line1-clipped-10px` case as the named in-tolerance exception until S1 decides it.

### should-fix

**S1 — L4: line 1's right edge at ±12 accepts a 10-px clip of the heading's last glyph on all 10 face-present keys.**
- The ±12 rests on web's face (Δ −8 / +2 / −8). The C arms are the ref's pixels (Δ 0), so no native face was ever
  measured.
- Fix: at stage 1, record Δx1 per platform and test from the device captures and set `X1_TOL` = max |Δ| + 2 (§10).
- Add the clip-10 fake as a standing case: expected FAIL once the tolerance allows, otherwise recorded in §9 as a known
  in-tolerance blind spot.

**S2 — `geometry-gate.py` accepts `--base` equal to the run (G15b exit 0 on a 3-px-shifted L1 picture; G14b 5 / 6 `[X]`
PASS).**
- Fix: exit 2 when `--base` == `<run>`.
- Exit 2 when the `--base` run has no `sections/` (today: exit 3 via UNMEASURED; refuse instead).

**S3 — `control-check.mjs`: a CARRIER capture that disappears is counted as "changed (carrier)" and the control HOLDS.**
- Fix: an `absent after` / `absent before` carrier is printed MISSING and the read is refused (exit 2), as for a missing
  section. A carrier may change, never vanish.

**S4 — `ab-arms55.sh --against`: it reads the run's LAST `== <run>` block, and it ignores MATCH / MISMATCH.**
- Fix: select the run's `corpus launch` block (`grep -E "^== $AGAINST corpus launch"`, the first one), and refuse when
  there is none.
- Refuse when that block has a `MISMATCH` or `digest=MISSING` line for a platform in the arm's `<platforms,>`.
- Add AB2 / AB3 to `ab-arms55.against-proof.sh` (a dry run in the throwaway, no git).

**S5 — Usage errors exit 1, and exit 1 reads as "a gating key FAILed" or "a rule fired".**
- The cases: `geometry-gate.py --sections` / `--lanes` with no value (IndexError); `probe-readout.mjs --lanes` / `--exp`
  with no value (TypeError); `adjudicate.mjs --exp` with no value.
- `geometry-gate.py --lanes L1,` silently widens to every lane.
- Fix: every `opt()` refuses a missing value with exit 2. `geometry-gate.py` refuses an empty `--lanes` / `--sections`
  entry, as `probe-readout.mjs` already does.

### nit

- **N1 (round-1 N1 / N2, still open).** `adjudicate.mjs` exits 0 on a `cur` = wave55-pre record, and
  `probe-readout.mjs --stage1` exits 0 on `cur` = wave55-final.
  - Fix: `adjudicate.mjs` takes `--run` (default wave55-final) and refuses another `cur`.
  - `probe-readout.mjs --stage1` requires `/^wave55-pre(-r\d+)?$/`; without `--stage1` it requires `wave55-probe`.
- **N2.** `control-check.mjs` `pngs()` drops `__\d+\.png` stems, so a new capture with a numeric last segment is
  invisible (0 such stems today). Fix: drop the filter, or name the duplicate pattern it was meant for.
- **N3.** `ab-arms55.sh` `write_records` never reads `ab-diff.mjs`'s exit code, and `verify_records` checks only that the
  read-out is non-empty, so an error message passes. Fix: keep the rc per arm and fail `verify_records` on a non-zero rc.
- **N4 (round-1 N3).** An unmeasured-now carrier with no prediction row is never read by `probe-readout.mjs` (0 live
  instances: 81 carrier cells, 5 without a row, 0 of them scored).

## 8. Reproduce

Copy `plan-skeptic-round-2.instrument-teeth.d/` to a scratch dir `<s>`, then:

```
/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/probes-teeth.py --scratch <s>/teeth-rerun   # the round-1 set (also feeds fixcheck*.py)
cd <s> && nice -n 19 /opt/homebrew/bin/python3.12 -B r2fakes.py L1|L2|L2U2|L3|L4|L5|L6
nice -n 19 /opt/homebrew/bin/python3.12 -B combo2.py; … vrl.py; … l4_011.py; … fixcheck.py … fixcheck9.py; … nonoop.py
(cd cc && python3.12 -B ccruns.py) and the control-check lines in cc/cc.out.txt
gg/mkexp.py <expectations.json> gg; the geometry-gate lines in gg/gg-refusals.out.txt
rec/: round 1's mk.mjs + mk2.mjs, then the reader lines in rec/readers.out.txt
lu-check2.sh <throwaway worktree with the untracked plan files copied in and the synthetic L6 delivery>
```
