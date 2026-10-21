# Wave 55 — plan-skeptic round 4: VERIFY (independent of fix-r3)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0` (unchanged). `fix-r3.done.md` was read
only for its claims. Inputs at the bytes fix-r3 names:

| file | sha256 |
|---|---|
| `expectations.json` / `watchlist.txt` / `PLAN.md` | `490dc5b9…` / `4b1a9cd5…` / `80a4f4f2…` |
| `plan-build.py` / `plan-prose.py` / `geometry-gate.py` | `22a178e5…` / `2d4d8022…` / `39b9a0d7…` |
| probes: positioned-parent-slot / compose-wpt-content-box-cb / cbb-android / hyphen-ios-linebox / ua-heading-margin / counter-suffix-rows / android-paint-order | `134d16f2` / `c52fe610` / `76abeb24` / `191cd7bb` / `13fc864b` / `75822450` / `57cb351c` |
| `probes-teeth.py` / `-r2` / `-r3` | `cc706a5b…` / `b7f302a9…` / `668d0c54…` |
| `ab-arms55.sh` / `land-units.sh` / `toolkit-proofs.sh` | `5cd6fccb…` / `817c9677…` / `1f13b423…` |

**How it ran.**
- Everything was read-only on the shared tree, or ran in a throwaway, or wrote only under the scratchpad.
- The throwaways were `git worktree add --detach <scratch>/… HEAD`, each removed with `git worktree remove --force`:
  - `wave55-r4v-tk-wt` (toolkit-proofs);
  - `wave55-r4v-guard-wt` (the guard's red proofs);
  - `land-units.selftest.sh`'s own `wt-*`.
- The git-init repos (`ab-arms55.selftest.sh`) and the `git clone --shared` (toolkit C3a) live in the scratchpad.
- The two self-tests that `tee` their record into the tree ran as SCRATCH COPIES, so no committed record was rewritten.
  Each copy changed only `HERE` / `SH` and the tee target; for toolkit-proofs, the C1 line also calls the scratch copy of
  the ab-arms self-test.
- No gradle, xcodebuild, vite, puppeteer, emulator, simulator, adb, test-all or smoke. `wave55-open` was never read.
- Every probe ran on the pinned `/opt/homebrew/bin/python3.12 -B`.

## 1. Claims re-executed

| # | claim | executed | result |
|---|---|---|---|
| 1a | `beforeEveryDeviceRun` is an executable bash string | `bash -n <(jq -r .beforeEveryDeviceRun expectations.json)` | **exit 0** |
| 1b | it runs verbatim from the repo root (and from anywhere) | `bash -c "$S"` from the repo root, then the note's `bash -c "$(jq -r …)"` form from `/` | **exit 0** both times. It printed `[own-processes] kill_own_gradle_daemons roots=[<shared tree>]: stopped [] left []`: no Gradle daemon was running, and no TREES: line or `/wave55-` worktree was registered |
| 1c | its two guard clauses can fail | the string with `PROJECT_ROOT` re-pointed at a throwaway: clean / + untracked `runtimes/r4-probe.kt` / + a tracked `converter/build.gradle.kts` edit / + a results-only file | exit **0 / 2 / 2 / 0**. The refusal names "the tree differs from HEAD in code". The kill clause runs only after both checks pass. The throwaway (named `/wave55-…`) was itself picked up as a root, as the filter intends |
| 2 | the added-ink class is closed (fix-r3 M2: "§T3 HOLES 0, 2 named residuals") | my own fakes (§2), on every gating key's fixed picture, through the real gate | **does NOT hold: 4 must-fix findings across L4, L5 and L6** (§3). L1, L2 and L3 hold. Every fixed picture still prints OK (§2.4) |
| 2b | the standing set holds on the current bytes | `probes-teeth.py --scratch <s>` (run_all: r1 + r2 + r3 cases) | **215 cases, 0 violations, 48 / 48** gating + tally keys reject ≥ 1 fake; exit 0 |
| 3a | `ab-arms55.selftest.out.txt` cites the current runner and reproduces | cites runner `5cd6fccb…` = current. Re-run as a scratch copy (git-init repos in the scratchpad) | "every check as required (mutants M-L18, M-per-arm, M-dry RED)", 43 lines as required, exit 0. Diff against the committed record, once timestamp and scratch path are normalised: only the commit shas of the fresh git-init repos (lines 25, 38, 39, 44, 46) |
| 3b | `land-units.selftest.out.txt` cites `land-units.sh` and reproduces | cites `817c9677…` = current. Re-run A–G (throwaway worktrees, its own `wt-*`) | **133 passed, 0 failed**; Z1 shared tree identical, Z2 no throwaway left. Normalised diff (shas, scratch paths, timestamp): **0 lines** |
| 3c | `toolkit-proofs.txt` cites the current bytes and reproduces | header: expectations `490dc5b9`, adjudicate `262d22c3`, control-check `cba40eb5`, runner `5cd6fccb`, ab self-test `72ca276a`, make-snapshot55 `df37730d` — all current. Re-run A–D as a scratch copy (throwaway worktree; C3a scratch clone) | "every instrument behaved as required", 95 lines as required, exit 0. Normalised diff: **0 lines** |
| 4a | `plan-build.py --out` regenerates byte-identically | `plan-build.py --out <scratch>` | `expectations.json` **cmp identical** (`490dc5b9…`); `watchlist.txt` identical (`4b1a9cd5…`); stdout identical to `plan-build.out.txt` |
| 4b | watchlist unmatched 0 | `RUN=wave54-final WATCH=…/watchlist.txt node wave52-plan/watchlist-check.mjs` | **unmatched 0**; output byte-identical to `watchlist-check.wave54-final.out.txt` |
| 4c | geometry-gate self-test HOLDS | `geometry-gate.py wave54-final --base wave54-final --self-test` | **HOLDS**, 230 keys: gating 43 FAIL, control 160 PASS, report 27 FAIL, UNMEASURED 0, self-check failures 0 |
| 4d | `plan-prose.py --check` HOLDS | on a scratch copy of PLAN.md, then on the installed one (PLAN.md cmp-unchanged afterwards) | **HOLDS** (36 gen blocks · 39 decision ids · 46 citations · 28 / 28 units) |
| 4e | the two arms once both named `drop-U2` have distinct run ids, each with its own `--against` | `abArms` × `abRead` rendered, plus a PLAN.md scan | `drop-L4-U2` → `wave55-ab-drop-L4-U2`, `--against wave55-pre`. `drop-L6-U2` → `wave55-ab-drop-L6-U2`, `--against wave55-final`. Distinct ids and distinct against runs. `drop-NB-ios` (`wave55-final`) is also unique, and no arm is named `drop-U2`. PLAN §8 carries the L4 read-out `ab-diff.mjs wave55-ab-drop-L4-U2 wave55-pre …`, the `--dry-run --against wave55-pre` and "`wave55-final` for both closing arms" |
| 5 | nothing outside the two plan dirs changed | `git status --porcelain --untracked-files=all`, index sha256, HEAD, stash, worktree list — before and after | see §5 |

## 2. The added-ink sweep: my own variants

### 2.1 Instrument

`plan-skeptic-round-4.verify.d/fakes4.py` was written without reusing `added_ink.py`.

**Fixed pictures.** It reads every `*fixed` case of the standing set. These are the briefs' replays under `*-replay/`,
and for L2 U1 the wave54-probe device captures. Cases that share pictures are merged into one group.

**Per native capture:**
- The target is the pixels where the fixed picture differs from the wave54-final capture.
- Colour classes are the top-2 SOLID exact colours of those pixels. A class must be at least 0.6 × the farthest changed
  colour's distance from the canvas, and near-duplicates are merged. An anti-aliasing grey is never a class. When a diff
  is AA only, the darkest colour of the diff's bbox is used.
- Every fake is **strictly additive**: class ink is written only where the destination is not already class ink. Nothing
  is moved or erased.

**Variants** (offsets chosen apart from the fixer's):

| class | variants |
|---|---|
| vertical duplicate | whole-picture `min()` ghosts +1 / −1 / +4 / −5 / +9 y; the target copied ±1 / ±2 / +4 / +9 y; each band ±3 y |
| horizontal duplicate | whole-picture ghosts +3 / −1 / −4 / +7 x; the target copied ±1 / +2 / +3 / −4 / +10 x; each band +2 / +5 x |
| doubled glyph | the first / last / largest glyph abutting itself (+w+1); the middle / first glyph smeared +1 x; the last glyph below itself; every band's last glyph abutting |
| stray ink in the band | 1-px dot 4 px past the end; a 4×1 dash; a 1×4 bar in the widest gap; 2×2 left of the band; 3×3 above; 2×2 below; a 1-px underline; a 2×2 after every band's end and in every band's widest gap |

Strays are placed only on free background, so each adds exactly its own pixels.

**Gate.** Each variant is written into a symlink-tree fake run of wave54-final (`probes-teeth.make_run`, under the
scratchpad) and read by the REAL `geometry-gate.py` on the generated `expectations.json` (`probes-teeth.gate`), with
`--base wave54-final` as rounds 1–3 did.

**Classification**, per faked capture:
- **HOLE**: no gating or control row reading the capture moved.
- **CTRL-ONLY**: only a control moved.
- **GATE-PASS**: a gating key under test passed while a sibling caught the fake.

**Zones** (`zones4.py`). Each acceptance is re-read for NEW INK inside the passing key's OWN zone, under that probe's own
ink mask:
- L1, L3 dir and magenta, L5 and L4 U1 / 011: min channel < 200;
- L4 face-present: RGB sum < 300 (`dark`);
- L2: the green mask; L3 bidi: blue; L6: child fill / red.

The zones are:
- the whole capture where one key reads it;
- dir-style rows 6 / 7 / 9: their 20-px rows;
- bidi `RTL_BANDS`;
- counter-suffix `[H]` x0-63 × y64-111, `[C]` x0-63 × y112-159, `[T]` row 9 (y208-231).

An acceptance with **> 2** new-ink px in the key's own zone is IN-ZONE, a must-fix. One with ≤ 2 sits on the 2-px floor
that every XOR / mass tolerance has (§2.3).

**Records.** `fakes4.L{1..6}.out.txt` and `zones4.L{1..6}.out.txt`, all made with the final `fakes4.py` `379b3a1e…`
(`zones4.py` `3c3cc0b5…`).

Every lane was run twice with the same fakes, a draft run and the recorded one. Every verdict, count and classification is
identical. The only differing text is which reader's probe line a HOLE row quotes, since that comes from set order.

Earlier drafts of the generator were discarded, not counted:
- one carried the canvas-coloured dilation ring along, so its "+1 x copy" was really a 1-px MOVE;
- one used anti-aliasing greys as ink classes.

### 2.2 Result per lane

| lane | fake runs | gating-key reads of a faked capture | IN-ZONE acceptances (must-fix) | verdict |
|---|---:|---:|---|---|
| L1 hyphen-ios-linebox | 213 | 625 (3 keys) | 0. All 19 accepted fakes add ≤ 2 px under the probe's mask (INK_TOL 2) | **holds** |
| L2 cbb-android | 78 | 311 (8 keys) | 0. No fake accepted | **holds** |
| L3 positioned-parent-slot | 377 | 540 (7 keys) | 0. 18 accepted fakes add ≤ 2 px. All 122 GATE-PASS and 33 CTRL-ONLY carry their ink in a sibling row's zone (another dir row / `[C] unaffected-rows` / `[C] ltr-rows`) | **holds** |
| L4 ua-heading-margin | 254 | 1819 (12 keys) | **98, on 10 of 12 gating keys** (the face-present branch). The U1 (today's face) branch and `[M] 011` ×2 hold | **FAILS** — M1 |
| L5 counter-suffix-rows | 83 | 417 (6 keys) | **[H] ios** 7 (up to 33 px) and **[T] ios** 1. `[I]` 005 ios / android: 4 px, but 1–2 per probe sub-zone (§2.3). `[C]` ios / android hold | **FAILS** — M3, M4 |
| L6 android-paint-order | 155 | 333 (6 keys) | **28, on all 5 testable `[X]` keys** (up to 500 px). `[PO]` holds | **FAILS** — M2 |

### 2.3 Accepted on the 2-px floor (listed apart; per this round's rule they still need fixing or naming)

These fakes add ≤ 2 new-ink px in the key's own zone, or ≤ 2 in each of the probe's own sub-zones. Every `INK_TOL = 2` /
`others ±2` / `mass ±2` / `XOR ≤ 2` rule accepts them by construction:
- L1: 19 (smears, 1-px dots);
- L3: 18 (dir-row last-glyph slivers, 1-px dots, the stack's 1-px dot);
- L5 `[I]` 005 ios / android: the whole target copied ±1 x, or band 0 / band 2 copied +2 x. That is 4 new px, but 1–2
  in each of `ink_check`'s four zones (marker / item × two lines), measured: XOR 1 per zone.

The passing pictures read 0 on these masks. They close only with a 0-px tolerance, which is a stage-1 device question.
Recommendation: name the 2-px floor as a residual class in PLAN §0 / D33 instead of tightening blind.

### 2.4 Fixed pictures

Each group's fixed run prints every gating key under test OK. The exceptions are known and by design:
- `[X] flex-…-safe-002 android` has no passing fixed picture. Its replay keeps 50 crossing px: round-3 N5. It is the one
  gating key this sweep cannot test.
- Under the U1-alone arm, the four `[F]` keys stay WRONG by design. They are tested on the C arm.

The standing set (§1 row 2b) holds: 215 / 0 / 48 of 48.

## 3. Defects

### Must-fix

**M1 · L4 `ua-heading-margin`: the face-present keys accept added ink far beyond the named residual.**

The keys: `[M]` and `[F]` 005 ios, 006 ios, 014 ios, 014 android, and `[M]` 005 android, 006 android. That is 10 gating
keys, 98 IN-ZONE acceptances, all HOLEs (no row of the capture moves). Examples (`l4-look.png`: fake left, fixed C arm
right):

| key | fake | added dark px |
|---|---|---:|
| `[M]` / `[F]` 005 ios | band 1's last glyph doubled ("brownn") | +158 |
| `[M]` / `[F]` 005 ios | first glyph doubled | +26 |
| `[M]` / `[F]` 014 ios / android | the whole heading copied 1 px down / right | +75 / +126 |
| `[M]` 005 / 006 android | band 2 ("fox") copied 5 px right | +335 |
| `[M]` 005 / 006 android | the heading copied 1 px left | +185 / +189 |

The two mechanisms:
- **`face ink`.** It allows ±FACE_INK_TOL = 6 % of the ref's dark px per band (about ±100 px) and counts components. An
  extra copy that touches the glyphs adds no component.
- **The Compose no-wrap rows (005 / 006 android).** They read band 1 only over the ref's line-1 columns. They check the
  bands below band 1 by top / bottom ±1 (`verdict_margin`), never by ink, so a doubled second line passes whole.

fix-r3 names only "2 faked captures (a 2×2 blob on 014)" as a residual. The real extent is 10 keys and up to +335 px. PLAN
§0's "Two faked captures are NAMED residuals" and fix-r3's "HOLES 0" therefore do not describe the instrument.

Fix (the orchestrator chooses):
- (a) read every band of the no-wrap rows with `face_ink` when the picture wraps (the C arm does);
- (b) tighten FACE_INK_TOL to what the device-free pictures measure — the C arms 0 %, web ≤ 1.8 % — until stage 1 re-sets
  it;
- (c) at stage 1, read the face-present bands against `wave55-pre`'s own device face, XOR ≤ 2, as fix-r3 itself
  suggests;
- or re-scope the named residual in PLAN §0 / `sweep.py RESIDUALS` to its measured extent.

Under D31 the new fakes join the standing set.

**M2 · L6 `android-paint-order`: every testable `[X]` key accepts a child fill drawn a second time 1 px off.**

The keys: flex `safe-001` and grid `align-self-001`, `justify-self-001`, `align-self-vertWM-001`, `align-self-rtl-001`
(android), with 28 acceptances, all HOLEs:
- the child fill copied ±1 px vertically or horizontally: +24 … +500 px (safe-001 +500, grid +29 … +104);
- its first or middle component smeared +1 x: +6 … +125;
- a 1×4 fill-coloured bar in the gap: +4, align-self-001 and rtl.

Cause: `extra` counts fill outside `dilate1(base fill ∪ base band)`, so any growth of at most 1 px around the child is
invisible. `bbox ±1` and `lost` (a subset read) cannot see added fill either.

Measured (`l6-extra-undilated.out.txt`):
- without the dilation, the five fixed replays read `extra` **0**, and the fakes 4 … 500;
- with it, both read 0 (the bar: 0 on align-self-001 / rtl, 4 on justify / vertWM, which do fail).

Fix: count `extra` against the base fill ∪ band undilated, or dilate only into the base band's pixels, at ≤ 1. Device AA is
to be read at stage 1 as D33.

**M3 · L5 `[H] counter-suffix ios` has no ink read: added ink in its own zone (rows 3-4, x0-63) passes.**

Fakes restricted to that zone (`l5-zone-fakes.out.txt`):
- the zone's glyph ink copied 1 px down: +11 px, **every row of the capture PASS** (HOLE);
- copied 1 px right: +8 px, HOLE.

In the sweep:
- a doubled first-glyph sliver: +3 px, HOLE;
- the whole-picture / target 1-px copies: +7 … +33 px in `[H]`'s zone, with `[H]` PASS. These were caught only because
  `[C]`, `[T]` and `[K]` read their own zones of the same fake.

`[H]` does reject a separate 2×2 / 3×3 blob: it reads as a new period. Round 3 added `ink:` to `[C]` and `[I]`, not `[H]`.

Fix: an `ink:` read on rows 3-4's marker zone. Against the base translated, if U1-ios only moves the marker; against the
ref / web twin if it changes the glyph.

**M4 · L5 `[T] counter-suffix ios` accepts added ink inside its own digit window (x136-150, row 9).**

- The window's ink copied 1 px down adds +7 px, and **every row PASSes** (HOLE).
- A 2×2 / 3×3 blob beside the '1' adds +4 / +9 px. `[T]` passes; only the controls `[M] counter-suffix ios`
  (rtl-marker-bake) and `counter-suffix ios` (lists-bakes) fail. A control FAIL is loud but reverts nothing.

Cause: `t_check` reads the digit blob's rows and fill. `ROW9_OUT` reads only OUTSIDE the window.

Fix: the window's total ink (min < 200) against the ref ±2 / base XOR, or "no ink in the window but the digit's".

### Should-fix / notes

- **S1.** PLAN §0 / D31 and fix-r3 state the added-ink class is closed ("HOLES 0", "the probes now read how MUCH ink").
  That holds for fix-r3's own variant set and for L1 / L2 / L3. It does not hold for L4 face-present, L5 `[H]` / `[T]` and
  L6 `[X]`. The statements need restating after M1–M4.
- **S2.** The 2-px floor (§2.3) is not named anywhere as a residual class. Name it, or show the device pictures permit 0.
- **N1.** The self-test records cite the script under test, not the self-test script's own sha256:
  - `land-units.selftest.out.txt` lacks `land-units.selftest.sh` `ad7ba2da…`;
  - `toolkit-proofs.txt` lacks `toolkit-proofs.sh` `1f13b423…`;
  - `ab-arms55.selftest.out.txt` lacks its own `72ca276a…`, though toolkit-proofs' header carries it.

  They reproduce today (§1 row 3), so this is traceability only.
- **N2.** The `beforeEveryDeviceRun` worktree filter matches ANY registered worktree whose path contains `/wave55-`, this
  verifier's scratch throwaway included (§1 row 1c). That is as documented, and the kill only touches daemons whose logs
  name those roots. Keep scratch throwaways un-named `wave55-…` during a device window, or the gate stops their daemons
  too.
- Nits N1–N6 of round 3 and fix-r2's queued nits are outside this pass and were not re-checked. N3 is fixed: the per-key
  table now reads "with a fixed picture accepted 42".

## 4. Not executed here, and why

- **fix-r3's own sweep (`sweep.py`, ~25 min) was not re-run.** Its record is `probes-selfcheck.txt` §T3. This pass ran an
  independent variant set instead (1160 fake runs) plus the standing set (215 cases).
- **The ab-arms against-proof and revert-check-proof records were not re-run.** They are not among the three self-test
  records the task named.
- **No instrument was changed by this pass**, so nothing new is owed to `probes-selfcheck.txt`. `fakes4.py` /
  `zones4.py` / `l5-zone-fakes.py` are verifier instruments. Each is shown both ways in its own records:
  - `fakes4.py` reports accepted AND caught fakes for every key;
  - `zones4.py` classifies both IN-ZONE and benign;
  - `l5-zone-fakes.py` shows `[H]` FAIL on its blobs and PASS on its copies.

## 5. Tree

- **Before → after:**
  - HEAD `640276a0` → `640276a0`;
  - index sha256 `db6b5da3…` → `db6b5da3…`;
  - `git status --porcelain --untracked-files=all` outside `tools/titan/results/wave55-{plan,gate}/`: 0 → 0 lines;
  - `git stash list` empty;
  - `git worktree list` names no scratch tree.
- **Files under the two plan dirs:** the 771 files hashed before the first run are byte-identical after.
- **New files (this pass):**
  - this report and `plan-skeptic-round-4.verify.done.md`;
  - `plan-skeptic-round-4.verify.d/`:
    - `fakes4.py`, `zones4.py`, `l5-zone-fakes.py`, `l6-extra-undilated.py`, `l4-look.py`;
    - their outputs `fakes4.L*.out.txt`, `zones4.L*.out.txt`, `l5-zone-fakes.out.txt`, `l6-extra-undilated.out.txt`,
      `l4-look.png`;
    - `checks.out.txt` (§1's executed commands and outputs; scratch paths written `<scratch>`).
- Fake runs live only in the scratchpad.

STATUS: PARTIAL — every check the task listed was re-executed: (1) the guard string, (3) the three self-test records,
(4) generator, watchlist, self-test, prose and arms. All hold. (2) the independent added-ink sweep found 4 must-fix
findings: L4 face-present ×10 keys, L6 `[X]` ×5, L5 `[H]` ios, L5 `[T]` ios. The plan's "added-ink class closed / 2 named
residuals" claim does not hold until they are fixed or named.
