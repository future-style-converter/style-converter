# Wave 55 — plan-skeptic round 7: VERIFY (independent of fix-r6)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`. HEAD was `4e789976` when this pass started and is `7566fb03`
now: the orchestrator committed the pre-landing audit at 09:28 local (`wave55-gate/prelanding/` + two `wl7a/` records). That
commit touches no file this pass read. `fix-r6.done.md` was read only for what it claims. The L5 fix lane and the pre-landing
auditors were running throughout; this pass edited no file they read.

**Inputs (sha256, first 16).** Every one matches `fix-r6.done.md`:

| file | sha256 |
|---|---|
| `expectations.json` / `watchlist.txt` / `PLAN.md` | `8fbfc83a9ba3fdaa` / `4b1a9cd5bb421e3b` / `9eea4b896a42f865` |
| `geometry-gate.py` / `residual-read.py` / `probe-readout.mjs` / `plan-build.py` / `plan-prose.py` | `09e35e92a66af703` / `40211eb03f6835cc` / `d627607ddcbb4d8c` / `4e3aced59c910860` / `2d4d802275cbbeed` |
| probes: hyphen-ios-linebox / compose-wpt-content-box-cb / cbb-android / positioned-parent-slot | `0dc54c73` / `c52fe610` / `76abeb24` / `e01e82af` |
| probes: ua-heading-margin / counter-suffix-rows / android-paint-order | `50344dc6` / `bd2f001a` / `360e6e28` |
| `probes-teeth.py` / `-r2` / `-r3` / `-r4` / `-r5` / `probes-selfcheck.py` / `.txt` | `90c8cb64` / `590a1858` / `86a3fa11` / `686e21fe` / `897be4ed` / `9c9c483c` / `808ceb16` |
| `fix-r6.zones.py` / `.out.txt` / `readers-selfcheck.sh` / `.txt` | `b40f0062` / `d82f92fa` / `aaccdda4` / `8d86acb1` |

**How it ran.**
- Read-only on the shared tree. Every record a script would write beside itself went to the scratchpad.
- One throwaway, `<scratchpad>/wave55-plan-r6-verify7`: `git worktree add --detach … HEAD`, the current `wave55-plan/` and
  `wave55-gate/` bytes rsynced in (`diff -rq` against the shared dirs empty), `tools/titan/runs` and `tools/wpt` symlinked.
  The verdict scripts ran there, and it is the selfcheck's `--wt`. It was removed at the end.
- Pinned interpreter `/opt/homebrew/bin/python3.12 -B`, `nice -n 19`, at most three of this pass's processes at a time. No gradle,
  xcodebuild, vite, emulator, simulator, adb or test-all.
- Every gate read below is the REAL `geometry-gate.py` on the generated `expectations.json`, on a symlink-tree fake run, with
  `--base wave55-open` (the base `geometryGate.cmd` registers; its captures equal wave54-final's on all 35 key cells, pixel for
  pixel — checked).

## 1. Claims re-executed

| # | claim | executed | result |
|---|---|---|---|
| 1 | `geometry-gate --self-test` HOLDS | `geometry-gate.py wave54-final --base wave54-final --self-test` | **HOLDS**, exit 0: 230 keys, gating 43 FAIL, control 160 PASS, report 27 FAIL, residual 48 / 48 OK, self-check failures 0 |
| 2 | `plan-prose --check` HOLDS | on a scratch copy of PLAN.md, then on the installed one | **HOLDS**, exit 0 both: 36 gen blocks · 42 decision ids · 46 citations · 28 / 28 units. PLAN.md `9eea4b89` unchanged |
| 3 | watchlist unmatched 0 | `RUN=wave54-final WATCH=…/watchlist.txt node wave52-plan/watchlist-check.mjs` | **unmatched 0**, exit 0; output byte-identical to `watchlist-check.wave54-final.out.txt` |
| 4 | `expectations.json` is generated | `plan-build.py --out <scratch>` | `expectations.json` and `watchlist.txt` **byte-identical**; stdout = `plan-build.out.txt` |
| 5 | `fix-r6.verdicts-preserved` | `fix-r6.verdicts.sh` in the throwaway (45 argument sets, round-4 bytes vs current) | exit 0, every verdict preserved. Body **identical** to the installed record once the throwaway path is written as its `<throwaway>` placeholder; only the timestamp / HEAD header differs |
| 6 | `fix-r6.verdicts-vs-round2` | `fix-r3.verdicts.sh` as a scratch copy pinned to the throwaway | exit 0, every verdict preserved; body **identical** (paths normalised) |
| 7 | `fix-r6.teeth-r5-equiv` | `fix-r6.teeth-r5-equiv.py` in the throwaway | **byte-identical** to the installed record: 346 pinned cases, 788 faked captures, 0 differ |
| 8 | `probes-selfcheck.txt` reproduces | the shared `probes-selfcheck.py --scratch <scratchpad> --wt <throwaway> --out <scratchpad>/…` (§0, §R, §L1-§L7, §C4, §T, §T3, §T4, §T5; 65 min at nice 19) | exit 0. **Identical** to the installed `probes-selfcheck.txt` (1612 lines) except line 3, the HEAD the orchestrator's commit moved (`4e789976` → `7566fb03`); with that line restored the sha256 is `808ceb164ea36a33`, the installed one. That covers: §0 HOLDS; §R every fixed picture 0 / 0 / 0 and the windows literal in sync; §T 611 cases / **0 violations** / 48 of 48 — every standing fake, the round-5 pins included, prints WRONG and every fixed case PASS; §T3 HOLES 0; §T4 IN-ZONE 0; §T5 MUST-FIX 0, floor 60 |
| 9 | every fixed picture prints OK | the 41 fixed pictures of the standing set (35 cells; the L4 cells under both the C and the U1 arm) in two fake runs, real gate, `--base wave55-open` (`fixed7.out.txt`) | **PASS with residual 0 / 0 / 0 on every gating / tally key** except the by-design cases: `[X] …safe-002` (FAIL; residual 42 px outside — round-3 N5, no passing fixed picture) and the four `[F]` keys under U1 alone (FAIL by design; residual 0 / 0 / 0) |
| 10 | the residual reads 0 on every fixed picture and legitimate arm | the installed read over every replay / arm picture of the key cells (`arms7.out.txt`, 181 pictures) | **174 read 0 / 0 / 0.** The 7 non-zero are exactly the named wrong pictures: bidi PLAIN 506; clip-3 `fake-centered` 4904 / 0 / 119 and `fake-unclipped` 3179 / 0 / 4; safe-002 42; L5 `C-space-only` 476, `H-broken` 183, `K-leak` 68. No widened zone is needed |
| 11 | nothing outside the two plan dirs changed | git state before / after (§5) | **holds** |

## 2. The round-7 sweep

### 2.1 Instrument

`plan-skeptic-round-7.verify.d/fakes7.py` was written for this round. It reuses no builder of `fakes5.py`, `added_ink*.py` or
`fakes4.py`. It reuses only:
- `probes-teeth.py`'s plumbing (`make_run`, `cap`, `shift`) and its choice of fixed pictures;
- `residual-read.py`'s declarations, to PLACE paint inside or outside each declared zone. They never judge a fake; the gate does.

**Fixed pictures.** 9 fixed sets, 41 pictures on the 35 key cells: L1 Rm; L2 the wave54-probe U1 captures and the clip-3 U2
replay; L3 the U1U2 / U1 / U2 replays; L4 the C and U1 arms; L5 ok-all; L6 the six U2 replays and the `[PO]` U1 replay.

**471 families in seven classes**, each applied to every cell it fits:

| class | families | what |
|---|---:|---|
| S | 11 | the standing classes, generic: class ink removed (all, right half, a 3×3 hole), moved +2 / −3 px in x and y, cut by 3 px at the right / bottom end, mirrored, top / bottom halves swapped |
| A | 138 | strictly additive in-zone: 2×2 / 3×3 specks (touching the ink, or ≥ 3 px from it) and 10- / 30-px 1-px lines on the zone's own canvas, in 23 colours — the cell's class colour (mask zones); black, greys 100 … 245, red and a blue tint (neutral zones); lime / blue ramps at 100 … 8 % (the dir rows) |
| F | 32 | faint copies of the class ink at α .5 / .25 / .15 / .08, moved (+1, 0), (+2, +1), (0, +5), (−3, +3), painted only on canvas: restricted to the declared zone, and free |
| G | 6 | a duplicated glyph / class component at full ink, 25 % and 15 %, in-zone and out of zone |
| W | 12 | the whole picture blended with itself moved (+3, +2), (0, +5), (+1, 0) at α .5 / .25 / .12 / .06 |
| O | 176 | background-only paint OUTSIDE every declared zone: 22 colours (black; greys 128, 204, 230, 245, 252; near-white 254; red, lime, blue, yellow, magenta, cyan, CSS green, teal, #66ccff, pink, two off-white tints; the cell's class colour at 100 / 50 / 15 %) × a 3×3 speck, a 30-px line and a 12×12 block × far from the zone, 1-3 px outside it, and on a non-white flat outside it (a parent box, the clip's blue field) |
| T | 96 | targeted at the read's own allowances: palette flats and blends grown 1 / 2 / 4 px from a recorded window's change; CSS green on the box's 1-px AA ring; an "other" stack square moved; child fill over the parent band; `[PO]` overlap px reverted plus red elsewhere; vacate-class yellow; faint (10-20 %) copies over glyph edges |

**Runs.** Every family makes at most two fake runs (the L4 U1 arm runs apart from the rest). Each run is a symlink tree of
wave54-final with every fixed set in place and every applicable cell faked at once. Every gating / tally key reads only its own
cell (plus the ref, web or the base): this was checked probe by probe. The real gate reads the run with `--base wave55-open` and
the faked cells' lanes and sections. In all: **710 fake runs, 9933 cell fakes, 12769 owning gating-key reads**, plus the tally
entries. No key printed UNMEASURED or lacked a row.

**Acceptance** (`adj7.py`):
- A fake is ACCEPTED when an OWNING gating key that PASSed its fixed picture still PASSes, or when every key of the cell's
  `pictureCorrectTally` entry still PASSes.
- The owners of a fake are the keys whose zone its changed px touch. This matters on two cells: the dir-style rows 6 / 7 / 9 and
  counter-suffix ios `[H]` / `[C]` / `[T]`. On every other cell, every gating key is an owner.
- A PASS whose same-unit sibling key FAILs the fake is not counted, because the unit reverts anyway (round 3's sibling rule). This
  applies to 2 clip-3 reads.
- More than 2 changed px is a MUST-FIX acceptance. 2 px or fewer is the named 2-px floor (D39).

### 2.2 Result per lane

| lane | cell fakes | MUST-FIX acceptances (gating reads + tally entries) | floor (≤ 2 px) | gating keys accepting a > 2-px fake | tally cells |
|---|---:|---:|---:|---|---:|
| L1 hyphen-ios-linebox | 720 | 210 | 0 | **all 3** | 2 / 2 |
| L2 cbb-android | 1602 | 120 | 32 | clip, clip-2, clip-4 (**3 of 8**) | 3 / 6 |
| L3 positioned-parent-slot | 1392 | 231 | 0 | dir rows 6 / 7 / 9, bidi, stack (**5 of 7**) | 1 / 3 |
| L4 ua-heading-margin | 3840 | 924 | 0 | **all 12** | — |
| L5 counter-suffix-rows | 960 | 462 | 0 | **all 6** | 4 / 4 |
| L6 android-paint-order | 1419 | 26 | 0 | `[PO]` and all 5 testable `[X]` (**6 of 7**) | 6 / 6 |
| **all** | **9933** | **1973** (1552 + 421) | **32** | **35 of 43** | **16 of 21** |

**The 8 gating keys that hold:**
- L2 `abspos-autopos-htb / vlr / vrl-ltr android`;
- clip-3: `compose-wpt-content-box-cb` holds, and the cbb key's two acceptances are sibling-rejected;
- L3 `[S] dynamic-offset-rtl-001` and `[W] dynamic-offset-rtl-002`;
- `[X] …safe-002`, which cannot be tested (no passing fixed picture: round-3 N5).

**The residual read itself works as declared.** Of the 5465 O-family fakes (paint outside every declared zone):
- **5140** carry a colour that is not the cell's own declared class. The gate's residual read FAILs every one, on every key of
  its cell.
- **325** carry the cell's own class colour (black / dark on the band zones, the mask class on the mask zones). That paint is
  zone by construction, so the probes judge it. They reject all of it except on bidi (MF2).

## 3. Defects

All 1973 acceptances fall into seven mechanisms (`classify7.out.txt`). In each one, paint lands where the residual read ALLOWS
it, and no probe read sees it. None of them is the round-5 class (paint outside the zones), which fix-r6 closed.

| # | mechanism | gating reads + tally | distinct fakes | gating keys | changed px |
|---|---|---:|---:|---:|---|
| MF1 | faint in-zone ink on a neutral / ramp zone | 1398 + 290 | 1122 | 24 | 4 … 2558 |
| MF2 | bidi: the class colour outside the RTL bands | 22 + 22 | 22 | 1 | 4 … 144 |
| MF3 | palette paint grown from a recorded window | 96 + 96 | 96 | 4 | 6 … 1008 |
| MF4 | L4 face-present heading moved ±2-3 px in x | 20 + 0 | 12 | 10 | 3350 … 4888 |
| MF5 | L6 `[X]`: child fill painted over the parent band | 10 + 10 | 10 | 5 | 4 … 138 |
| MF6 | L6 `[PO]`: overlap px reverted, red added elsewhere | 3 + 3 | 3 | 1 | 8 … 16 |
| MF7 | L3 stack: an "other" square relocated | 3 + 0 | 3 | 1 | 200 |

### Must-fix

**MF1 · L1 ×3, L4 ×12, L5 ×6, L3 dir rows ×3 (24 gating keys; tally cells 001 / 003 ios, counter-suffix ios / android, 005 ios / android) — faint in-zone ink.**
- The band / rect zones declare `aa: 'neutral'` (any grey) or `'ramp'` (white → lime / blue, and lime ↔ blue).
- So inside the zone, the residual allows ANY grey or ramp colour.
- Every probe's ink levels start at min channel < 200. L1 / L4 / L5 use min < 200 plus core / dark; the dir rows' `mass:` uses
  `ink`.
- Paint with min ≥ 200 inside the zone is therefore invisible to both. 1112 of the 1122 accepted MF1 fakes add NO px at
  probe level (`level7.out.txt`).
- Accepted examples:
  - a 15 % ghost of every hyphenate line 5 px below it (1555 px, all 3 L1 keys and both tally entries);
  - a (215,215,215) 30-px line in the 005 heading's rows ([M] / [F] ×2 platforms);
  - a 15 % duplicated glyph in 011's band ([M] 011 ×2);
  - a (230,230,230) line in 005's [I] item window;
  - a 15 % lime line in dir row 6;
  - every A-family speck / line in greys 200-245.
- **Fix basis** (`basis7.out.txt`): on 71 legitimate pictures (every fixed picture of these cells, the L1 Rm / Rm1 / Rw / R0 / R1
  / Rd / X arms, the L4 C / D / U1 arms, the L3 U1 / U1U2 replays, L5 ok-all), all **172 598** changed non-white in-zone px lie
  within **1 px** of probe-level ink (min < 200) of the capture or the base. **0** lie farther.
- So "a changed, non-white in-zone px > 1 px from probe-level ink is non-class" is tolerance-0-safe on every legitimate
  picture. In-process it closes 1063 of the 1134 region-cell fakes (`propose7.out.txt`).
- What it leaves:
  - the 12 MF4 shifts;
  - 59 faint 2×2 specks and overpaints that sit wholly inside the 1-px AA ring. These are floor-like, 4 px, and must be named or
    closed by the fix pass.

**MF2 · L3 `[S] bidi-tab-001 android rtl-rows` (gating + tally) — blue ink anywhere outside the four RTL bands.**
- The cell's zone is the capture's `blue3` mask, wherever it lies, so new blue paint makes its own zone.
- The probe reads blue only inside the RTL bands y86 / 106 / 186 / 226 (+20).
- Accepted: a 12×12 blue block at the canvas corner (144 px), 30-px blue lines, and 3×3 specks far away, next to the zone or on a
  yellow box. Strict #0000ff and the measured class colour both pass.
- **Fix basis** (`basis7b.out.txt`): the fixed U1U2 replay changes **0** blue px outside the RTL bands. A zone restricted to
  `blue3` ∩ the four bands (∪ the windows) reads 0 / 0 / 0 on it, and 506 on PLAIN (unchanged). It closes all 22 MF2 fakes.
- Every other mask cell's probe reads its class colour over the whole capture, so the same own-zone construction is safe there
  — except where MF5-MF7 show a count-preserving gap.

**MF3 · clip, clip-2, clip-4 (gating + tally), bidi (gating + tally) — palette paint inside a recorded window.**
- Inside a window, `non-class` accepts any FLAT palette colour and any box / segment blend, provided the window's change stays ONE
  connected component.
- Paint grown 1-4 px from the recorded change joins that component.
- Accepted on the clips: a #ffff00, #cccccc, #66ccff or white crescent on the corner arc (37-396 px). The compose probe reads
  only green / red / pink.
- Accepted on bidi: a black frame, white, grey, a 30 % blue blend or a 50 % yellow blend around the '0' glyph (89-1008 px). The
  probe reads blue components and the yellow boxes' bbox.
- Pink (fail ink) is rejected by the probe. Black and other off-palette colours on the clips are rejected by the residual.
- **Fix basis** (`basis7c.out.txt`): the fixed pictures' window changes take few capture classes:
  - clip: green 1146 / AA 97 / #66ccff 6;
  - clip-2: green 576 / AA 57 / grey 6;
  - clip-4: green 398 / AA 53;
  - clip-3: green / grey / AA plus white 3 and #66ccff 4;
  - bidi: yellow 451 / AA 312 / white 173 / blue 112.
- Declaring each window's capture classes would close:
  - every clip-4 crescent;
  - clip's white / yellow / grey and clip-2's white / yellow / #66ccff crescents;
  - bidi's black and flat grey.
- The rest uses classes the fixed pictures do use: clip #66ccff, clip-2 grey, bidi white / yellow and the blends. Closing those
  needs the window recorded as the fixed change's own pixel MASK, not its bbox + 1. That reads 0 by construction on the fixed
  pictures; stage-1 device pictures then re-record through the existing D40 path.

**MF4 · L4 face-present `[M]` 005 / 006 / 014 × ios / android and `[F]` 005 / 006 / 014 ios, 014 android (10 gating keys) — the
heading moved 2-3 px sideways.**
- On the C arm, the whole heading moved +2 or −3 px in x passes: the band rule's left edges allow ±3 (`X0_TOL`), and `face xor:`
  searches dx ±3.
- The residual cannot see it, because the bands span the full width.
- Today's-face rows (U1 arm) and 011 hold (x ±1).
- The plan names `X1_TOL`'s stage-1 re-set (D33), not `X0_TOL`.
- **Fix basis:** web's left edges equal the ref's on 005 / 006 / 014 (Δx0 0, the probe's own web rows); the C arm reads 0. Set
  `X0_TOL` (and the `face xor:` dx span) from the measured Δx0 at stage 1, as `X1_TOL` is, or name the ±3 as a D33 residual with
  its reason.

**MF5 · L6 `[X]` safe-001 and grid align-self-001 / -rtl-001 / -vertWM-001 / justify-self-001 (gating + tally) — child fill over
the parent band.**
- `extra` deliberately excludes the base band, because U2 reveals fill where the band was. `band` only needs ≥ 0.9 × its floor.
  The residual sees child-fill paint as zone ink.
- So 3-9 % of a parent's border repainted in the child's teal / yellow, where no child is, passes (46-138 px).
- A 2×2 yellow over safe-001's border beside a child passes too (4 px).
- **Fix basis** (`basis7d.out.txt`): on all six U2 replays, every revealed fill px (fill on the base band) belongs to a fill
  component that holds base fill. That is 0 orphan reveals; the band fakes have 40-162. Counting orphan reveals closes the
  band fakes. The 4-px speck joined to a child stays: name it with the floor, or bound the reveal to the child's own box.

**MF6 · L6 `[PO] backdrop-filter-clip-rect android` (gating + tally) — count-preserving paint-order regression.**
- 4 / 6 / 8 overlap px are reverted to the base's blue (the parent border over the menu), and as many red px are added elsewhere
  inside the menus' bbox.
- This passes, because the overlap rows allow blue ≤ 8 / red ≥ 400 and the red count is ±2.
- **Fix basis:** the fixed U1 replay reads overlap red 416 / blue **0** exactly; the base reads 0 / 416. Blue ≤ 0 (or the 2-px
  floor) with red = 416 closes it.

**MF7 · L3 `[W] anchor-position-multicol-nested-001 android stack` — an "other" square relocated (200 px).**
- `others` counts green outside the four cells (±2 of web's 300) and never asks where it is.
- The residual sees green3 paint as zone ink. So moving one of the three other squares 24-40 px passes.
- No tolerance-0 identity exists to borrow:
  - the fixed U1 replay's other squares differ from the base (200 px green outside the cells the base lacks);
  - they differ from web too (mask XOR 276; iOS 180).
- The fix must decide where the other squares belong (for example the replay's own squares, as a positional read ±1). Until
  then this is an open count-preserving class on the gating key.

### Should-fix / notes

- **S1 — restate the claims.** `fix-r6.done.md` and PLAN §0 / D40 say every gating and tally key rejects every fake class AND
  any paint outside its declared zone, with the 2-px floor as the only residual.
  - The second half holds: every O-family fake in a non-class colour is FAILed by the residual on every key.
  - The first half does not. 35 of 43 gating keys and 16 tally cells accept a > 2-px fake INSIDE their declared zone (MF1, MF3)
    or in the class colour (MF2, MF5-MF7), and 10 face-present keys accept a ±2-3 px move (MF4).
- **N1 — the floor reproduces as named.** 32 acceptances add ≤ 2 px:
  - clip-3's faint +1 x copies of the box (2 px);
  - #66ccff / white 1-2 px at the clip arcs.
- **N2 — the by-design exceptions:**
  - `[X] …safe-002`: no passing fixed picture, 42 px outside;
  - `[F]` under U1 alone: WRONG by design, tested on the C arm.
  - No widened zone is declared beyond the 20 recorded windows.
- **N3 — not re-run here:** `readers-selfcheck.sh`, `readers-calibration.txt` and `toolkit-proofs.txt` (not in this round's
  brief); verify-r4 N1 and fix-r3's queued nits.

## 4. Evidence (`plan-skeptic-round-7.verify.d/`)

**Scripts** (run from that directory with the pinned interpreter; `common7.py` holds the plumbing):

| script | what it does |
|---|---|
| `fakes7.py --scratch <dir> --out <jsonl> [--shard i/n] [--only prefix]` | the generator + real-gate sweep |
| `fixed7.py <scratch>` | the fixed pictures through the gate |
| `adj7.py [sweep7.records.tsv]` | the per-key adjudication; re-derives `adj7.out.txt` byte-for-byte from the compact records |
| `classify7.py adj7.out.txt` | groups the acceptances by mechanism |
| `level7.py adj7.out.txt` | MF1's probe-level count |
| `arms7.py` | the residual over every replay / arm |
| `basis7.py` / `basis7b.py` / `basis7c.py` / `basis7d.py` | the fix bases of MF1 / MF2 / MF3 / MF5-MF7 |
| `propose7.py acc7.txt` | how far the MF1 / MF2 bases close the acceptances |
| `show7.py` / `look7.py` | the looks |

**Records:**
- `sweep7.records.tsv` (9933 fakes: verdict + residual state per key);
- `adj7.out.txt`, `acc7.txt`, `classify7.out.txt`, `level7.out.txt`;
- `fixed7.out.txt` / `.json` (+ the two gate outputs);
- `arms7.out.txt`, `basis7*.out.txt`, `propose7.out.txt`, `ofam7.out.txt`;
- `checks7.out.txt` (§1 rows 1-8 with their diffs);
- `look7-acceptances.png`: 12 accepted fakes, fixed | fake | changed px.

The fake runs lived only in the scratchpad.

## 5. Tree

- **Before → after:**
  - HEAD `4e789976` → `7566fb03` (the orchestrator's commit, above);
  - index sha256 `f6065c58…` → `90df4083…` (the same commit);
  - `git status --porcelain --untracked-files=all` outside `tools/titan/results/wave55-*`: 0 → 0 lines;
  - `git stash list` empty;
  - no `wave55-plan-r6-verify7` worktree left registered.
- **This pass's writes to the shared tree:** only `plan-skeptic-round-7.verify.md`, `plan-skeptic-round-7.verify.done.md` and
  `plan-skeptic-round-7.verify.d/`.
  - Each was installed atomically: a temp beside it, then `mv`.
  - No existing file was edited. No probe, `geometry-gate.py`, `residual-read.py`, `probe-readout.mjs`, `expectations.json`,
    `watchlist.txt`, `PLAN.md` or `probes-selfcheck.txt` byte was written.
  - No `__pycache__` or temp was left behind.
- **Written by another session during the pass:** `../wave55-gate/prelanding/` (3 new files) and `../wave55-gate/wl7a/step4-…`,
  `step5-…` (the 09:28 commit). Every other file of the two plan dirs is byte-identical to this pass's start.

STATUS: PARTIAL — fix-r6's own claims all re-execute. The self-test HOLDS; `plan-prose --check` HOLDS; the watchlist is unmatched 0; plan-build regenerates byte-identically. probes-selfcheck.txt re-runs identical but for its HEAD line (611 standing cases, 0 violations; §T5 0 must-fix). teeth-r5-equiv is byte-identical and both verdicts-preserved records reproduce. Every fixed picture prints OK with residual 0 / 0 / 0, except the by-design safe-002 and [F] under U1 alone. The residual FAILs all 5140 out-of-zone fakes in a non-class colour: the round-5 class is closed. But this round's independent sweep (471 families of every round-1-5 class, 710 real-gate fake runs, 9933 cell fakes) finds 1973 acceptances adding > 2 px, on 35 of 43 gating keys and 16 tally cells, by seven mechanisms: MF1 faint (min ≥ 200) in-zone ink, which the neutral / ramp allowance passes and no probe level sees — L1 ×3, L4 ×12, L5 ×6, dir rows ×3; MF2 bidi blue outside the RTL bands; MF3 palette paint grown from a recorded window — clip / -2 / -4, bidi; MF4 L4 face-present heading ±2-3 px in x (X0_TOL ±3) — 10 keys; MF5 [X] child fill over the band; MF6 [PO] overlap revert plus red elsewhere; MF7 stack other square relocated. Each is must-fix; all but MF7 carry a measured tolerance-0 fix basis. The 2-px floor (32) reproduces as named.
