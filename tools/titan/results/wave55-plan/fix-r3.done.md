# Wave 55 — plan-skeptic round 3: FIX pass (`fix-r3`)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0`. HEAD was not changed: no commit,
checkout, stash or reset on the shared tree.

**Scope.**
- Edits only under `tools/titan/results/wave55-plan/` and `tools/titan/results/wave55-gate/`.
- No runtime, harness, converter, `tools/titan/*.{sh,mjs}` or `tools/visual/` byte touched.
- No gradle, xcodebuild, vite, puppeteer, emulator, simulator, adb, test-all or smoke.
- Every probe ran on the pinned `/opt/homebrew/bin/python3.12 -B`.
- `wave55-open` was never read. The probes read `wave54-final` / `-open` / `-probe`, the frozen refs and the briefs' replays.

**Git state commands.** All of these live under the scratchpad:
- `git worktree add --detach <scratchpad>/… HEAD` and `git worktree remove --force` of this pass's throwaways:
  `wave55-fix-r3-wt`, `-guard-wt`, `-tk-wt`, `-ab-wt`, `-teeth-wt`;
- `land-units.selftest.sh`'s own `wt-*`;
- the `git init` repositories of `ab-arms55.selftest.sh`;
- one `git clone --shared` (toolkit-proofs C3a, below).

`git worktree list` names none of them now.

## M1 — `beforeEveryDeviceRun` is an executable bash string

**Change (`plan-build.py`).**
- The prose moved to a new field `beforeEveryDeviceRunNote`.
- The string now starts `export PROJECT_ROOT=<the shared tree>;`. `SHARED_ROOT` is a constant, so a dry run in a throwaway
  renders the same bytes.
- Every path in the string is absolute (`git -C "$PROJECT_ROOT"`, `df -g "$PROJECT_ROOT"`,
  `source "$PROJECT_ROOT/tools/titan/own-processes.sh" && kill_own_gradle_daemons "$PROJECT_ROOT" <TREES: lines>
  <every registered /wave55- worktree>`), so it runs from any directory.
- The generator asserts `bash -n` on the string before writing. It also refuses prose in the string.

Interpretation, stated plainly: the task asked for the string to be the `source … && kill_own_gradle_daemons …` call. I kept
D35's two guard clauses in front of that call:
- the code-differs-from-HEAD refusal, untracked files included;
- the 20-GiB disk floor.

Dropping them would undo round-2 S1 / S5. The kill clause is the string's last command, as asked.

**Proof (`fix-r3.guard-proof.{sh,out.txt}`).**

| check | result |
|---|---|
| `bash -n <(printf '%s' "$STRING")` | round-2 string exit 2, round-3 string exit 0 |
| run verbatim with `bash -c`, from the repo root and from `/` | exit 0 both times; the `kill_own_gradle_daemons` summary is printed. No Gradle daemon ran before or after, so none was stopped. The roots include the registered `/wave55-` throwaways |
| red: `PROJECT_ROOT` re-pointed at a throwaway that holds one untracked runtime source | exit 2 (source removed: exit 0) |
| red: disk floor raised to 100000 GiB | exit 2 |
| red: `plan-build.py` with the round-2 note re-appended, run in the throwaway | `AssertionError … bash -n exit 2`, exit 1 |
| unmutated generator in the throwaway | byte-identical `expectations.json` |

**Regeneration.**
- `expectations.json` went from `56dbcfc0…` to `490dc5b9…`.
- `fix-r3.expectations-diff.txt` lists exactly 2 differences: `~ /beforeEveryDeviceRun` and `+ /beforeEveryDeviceRunNote`.
  No other string embedded the old text (1 occurrence: the field itself).
- `watchlist.txt` and `plan-build.out.txt` are byte-identical.

Then the chain:
- `plan-prose.py` renders the note; `--fill`, then `--check`: HOLDS. PLAN §7 step 0 is reworded ("executable verbatim from
  any directory").
- `plan-prose.selfcheck.sh` → `plan-prose.out.txt`: every mutation red.
- `final55-analysis.gen.py` regenerated; `--check` in sync. The diff is the embedded sha only.
- `../wave55-gate/readers-calibration.sh` (throwaway) → `readers-calibration.txt`: K1–K15, J1–J7 and F0–F4 all as required,
  header sha `490dc5b9…`.
- `readers-selfcheck.sh` (throwaway) → `readers-selfcheck.txt`: every check as required. A: the dry run reproduces
  `expectations.json` / `watchlist.txt` byte-for-byte.
- Watchlist check on wave54-final: `unmatched 0`, identical to `watchlist-check.wave54-final.out.txt`.

## M2 — ADDED ink

### L3 `[S] dir-style-02a android row6 / row7 / row9` (`positioned-parent-slot.geometry.py`)

Four reads were added. The thresholds come from measurements (`plan-skeptic-round-3.fakes/measure/l3-dir-*.out.txt`).

| read | rule | passing pictures | added-ink fakes |
|---|---|---|---|
| `rows:` | the peak of the ink-row profile ≥ **0.97** (was: position only) | ref / web 1.00; android U1U2 / U1 / today 0.994–1.000 | row-band ghosts +3 / +5 / +7 / −3 y: 0.905–0.939; whole ghosts 0.69–0.76 |
| `mass:` | text ink (x ≥ 21) = the reference ±**2 px**. The reference is the BASE capture's row 3 for rows 6 / 9 (same string; the replay builds rows 6 / 9 from it; ref / web / iOS render rows 3 / 6 / 9 pixel-identical) and the base's row 7 text for row 7 | 0 px apart (12 reads; ref / web / iOS twin XOR 0) | 3×3 over glyphs +3; 2×2 in a gap +4; doubled glyph ≥ +9; horizontal copy ±1 / ±2 px: +37 % … +77 % |
| `lead:` | ink left of x21 = the base row-3 dot ±1 px, same bbox ±1 | equal | doubled dot: 10 vs 5 |
| `yext:` | text ink rows = the reference's ±1 | equal | ghost bottom +2; a blob above the x-height: top 1 vs 4 |

The reference is read from the BASE capture, so a fake that also paints row 3 cannot move it.

**Results.**
- The fixed replay (U1U2) prints GEOMETRY OK on all three rows. U1 / PLAIN / U2 / today keep their verdicts.
- Every added-ink fake prints WRONG:
  - the verifier's all-rows band ghost +3 / +5 / +7 (`r3-L3-dir-rows679-*`);
  - the per-row band ghosts ±;
  - horizontal copies ±1 / ±2;
  - doubled dots (left, end);
  - 2×2 / 3×3 strays (gap, above, inside);
  - a doubled last glyph;
  - whole-picture ghosts +7 / −12 y and copies ±1 / ±2 x.
- The probe header names the stage-1 rule. A device picture that differs only by sub-pixel anti-aliasing is a measured
  tolerance read (as D33), never widened to pass a pinned fake.

### The sweep over every lane

**Tool: `plan-skeptic-round-3.fakes/`.**
- `added_ink.py` builds the fakes:
  - ghosts ±2 / ±3 y;
  - copies ±1 / ±2 x;
  - target-region ghost and copy;
  - a doubled glyph on clean background;
  - 2×2 / 3×3 strays in the target's own ink colour;
  - plus the L3 row / RTL-band / cell variants.
- `sweep.py` runs every variant on every fixed picture of the standing set through the real gate. A faked capture must be
  rejected by a gating / control / tally row that reads it; otherwise it is a HOLE.

**History.**

| run | record | holes |
|---|---|---|
| L3 on round-2 bytes | `sweep.L3-before.out.txt` | 83 / 167 key reads accepted |
| all lanes after the L3 fix | `sweep.mid.out.txt` | **112 holes**, all on L1 / L4 / L5 / L6 |
| after the first lane fixes | `sweep.post-fix1.out.txt` | 14 |
| final | `probes-selfcheck.txt` §T3 | **0 holes, 2 named residuals**, 987 key reads |

**Fixes.**

| lane / key | new read | basis (measured) |
|---|---|---|
| L1 `hyphen-ios-linebox 00x ios` | `ink:` each line's ink mask = the base line's ±2 px (XOR, same crop) | replays 0; iOS / web / ref same-string lines at different y: XOR 0 (`measure/l1-twins`) |
| L2 `compose-wpt-content-box-cb` + `cbb-android` (clip-3) | `core:` green outside the ref mask's 1-px AA band ≤ 1, ref-green missing inside it ≤ 1 | web / iOS / U1 device picture / clip-3 replay: 0 / 0 on all 10 tests (`measure/l2-core`). The bbox and `contains` strings are unchanged |
| L3 `[S] bidi-tab-001 rtl-rows` | `ink:` per RTL band against the base band's blue components: count equal; the box inside its base bbox ≤ +2 px; the '0' ring's size ±1 and ≤ +2 px; the small parts ±1 | android today / U1U2 replay / ref / web (`measure/l3-bidi-components`) |
| L3 `[W] … stack` | `others:` every green px outside the four cells = web's ±2 (was ±10 % over a ±2 ring) | web / iOS / U1 replay: 300 / 300 / 300 (`measure/l3-stack-others`) |
| L4 `[M]` today's face and 011 | `ink:` band ink mask = the base band + margin, XOR ≤ 2 | U1 arm: 0 |
| L4 `[M]` / `[F]` with the face | `face ink:` per band, the ref's dark-component count and dark px ±6 % (FACE_INK_TOL, re-set at stage 1 like X1_TOL: PLAN §7 stage-1 step 8). For the 005 / 006 Compose no-wrap rows, the bands below band 1 must be none or the ref's | web −1.8 … +1.3 %, components equal; C arm 0 % |
| L5 `[C]` (natives), `[I]` (natives) | `ink:` against the base. Rows 5-6 marker zone translated by one whole-px dx; 005 marker zone unmoved; 005 item zone translated; XOR ≤ 2 | ok-all replay: 0 on every zone |
| L6 `[X]` | `extra` ≤ 1 (was 4) | six U2 replays: 0 |
| L6 `[PO]` | red n ±2 (was ±16), pink ±2 (was ±100) | ref / web / iOS 1552 / 10000 on every run; U1 replay 1552 / 10000; android today 1136 = 1552 − 416 |

**Verdicts preserved** (`fix-r3.verdicts.sh` → `fix-r3.verdicts-preserved.out.txt`): 45 argument sets, the round-2 bytes
against the current bytes, line by line — every verdict is preserved. They cover the replays, arms, tags, `--fakes`,
today and the controls-can-fail pairs.

**Standing set.**
- `probes-teeth-r3.py` adds 85 pinned cases (L1 4 · L2 8 · L3 63 · L4 4 · L5 2 · L6 4), loaded by `probes-teeth.py run_all()`:
  - L3: the verifier's all-rows ghost, 14 variants × 3 rows, 5 whole-picture variants, 4 bidi, 3 stack;
  - L2: the `core:` cases (a corner-cut-out blob and a 3×3 hole, under the 40-px XOR);
  - every other lane's fixed pictures painted again +2 x / +3 y.
- `fix-r3.teeth-before-after.{sh,out.txt}` runs the set:
  - on the round-2 probe bytes (`plan-skeptic-round-3.fakes/round2-probes/`, the sha256s fix-r2 recorded): 215 cases,
    **77 violations, all round-3 cases**;
  - on the current bytes: **0**.
- The L1 tally controls are the wave-54 probe, outside this plan's edits. They accept a horizontal copy, which their
  sibling linebox keys reject. That is pinned as a named case.

**`probes-selfcheck.py`** (throwaway `--wt`) → `probes-selfcheck.txt`:
- §0 self-test HOLDS: 230 keys; gating 43 FAIL, control 160 PASS, report 27 FAIL.
- §T: 215 cases, 0 violations, 48 / 48.
- **§T3** (new, round-3 section): 987 key reads, every faked capture listed; HOLES 0, named residuals 2.
- Per-key table: "with a fixed picture accepted 42". This is **N3** fixed: §T's fixed pictures now count. Only
  flex-safe-002 lacks a passing picture.
- The L6 `--fakes` left this tree's fakes untouched.

**Named residuals (open).** They are listed in `sweep.py RESIDUALS` and PLAN §0. Two faked captures:
- `L4 C-fixed stray2-mid` on `text-decoration-inset-014` ios / android: a 2×2 blob touching a glyph inside a face-present
  band. It adds no component, +4 of ~1500 dark px, and moves the band bottom +1, inside ±1.
- No device 2em face exists to compare with pixel for pixel. The same blob on the today's-face branch is rejected.
- Suggested follow-up: after stage 1, read the face-present bands against `wave55-pre`'s own device face.

## S1 — self-tests re-run on the current bytes (records refreshed; each cites its script's sha256)

| self-test | script sha256 | result |
|---|---|---|
| `../wave55-gate/ab-arms55.selftest.sh` (run as toolkit C1, git-init repos under the scratchpad) → `ab-arms55.selftest.out.txt` | runner `5cd6fccb…` | every check as required: T1–T7, M-L18 / M-per-arm / M-dry RED, T5b2, T5l, T5m, M-onebyone RED |
| `land-units.selftest.sh`, all of A–G (throwaways) → `land-units.selftest.out.txt` | `817c9677…` | **133 passed, 0 failed**; Z1 shared tree identical, Z2 no throwaway left |
| `../wave55-gate/toolkit-proofs.sh` A–D → `toolkit-proofs.txt` | expectations `490dc5b9…`, adjudicate `262d22c3…`, control-check `cba40eb5…`, runner `5cd6fccb…` | every instrument as required |
| `ab-arms55.against-proof` (P1–P5, AB2–AB4) | runner `5cd6fccb…` | as required |
| `ab-arms55.revert-check-proof` (R1–R6) | runner `5cd6fccb…` | as required |

Notes on the toolkit run:
- **C3 was reworked.** The shared tree now carries uncommitted tracked plan records, which the runner refuses by design.
  C3a shows DRY RUN OK on a `git clone --shared` of the branch at HEAD, under the scratchpad. C3b shows the shared tree's
  dry run refused for exactly that reason. It flips to DRY RUN OK once the records are committed.

Notes on the runner (`ab-arms55.sh`):
- The header's stale "names TWO arms drop-U2" is rewritten.
- `--help` prints the whole header.
- **N6** is fixed in `revert-check-proof.sh`: a missing scratch dir is created, an unusable one is refused (was SIGPIPE 141).

## A/B arms

Confirmed by execution (`fix-r3.abarms-confirm.out.txt`):
- The arms are `drop-NB-ios`, `drop-L4-U2` and `drop-L6-U2`. Names and run ids (`wave55-ab-drop-*`) are unique; none is named
  `drop-U2`.
- Each rendered runner line carries `--against <its own against>`, and its ab-diff read-out names its run id against it:
  - `drop-L4-U2` → `wave55-pre`;
  - `drop-NB-ios` and `drop-L6-U2` → `wave55-final`.
- The runner takes one `--against` per invocation, so there is one arms file per against.

No regeneration was needed.

## Files

**Changed (`wave55-plan`):**
- `plan-build.py` `22a178e5…`, `expectations.json` `490dc5b9…`, `PLAN.md` `80a4f4f2…`, `plan-prose.py` `2d4d8022…`
  (+ `plan-prose.out.txt`);
- probes:
  - `positioned-parent-slot` `134d16f2…`
  - `compose-wpt-content-box-cb` `c52fe610…`
  - `cbb-android` `76abeb24…`
  - `hyphen-ios-linebox` `191cd7bb…`
  - `ua-heading-margin` `13fc864b…`
  - `counter-suffix-rows` `75822450…`
  - `android-paint-order` `57cb351c…`
- `probes-teeth.py` `cc706a5b…`, `probes-selfcheck.py` `926b873d…` (+ `.txt`), `readers-selfcheck.sh` `88d31046…`
  (+ `.txt`), `land-units.selftest.out.txt`.

**Changed (`wave55-gate`):**
- `ab-arms55.sh` `5cd6fccb…` (comments and `--help` only), `ab-arms55.revert-check-proof.sh` `67f794d3…`,
  `toolkit-proofs.sh` `1f13b423…`, `final55-analysis.sh` `be593259…`;
- records: `ab-arms55.selftest.out.txt`, `ab-arms55.against-proof.out.txt`, `ab-arms55.revert-check-proof.out.txt`,
  `toolkit-proofs.txt`, `readers-calibration.txt`.

**New:**
- `probes-teeth-r3.py` `668d0c54…`;
- `plan-skeptic-round-3.fakes/` (`added_ink.py` `5c3fecdb…`, `sweep.py` `55a13301…`, `round2-probes/`, `measure/`, three
  sweep records);
- `fix-r3.{guard-proof,teeth-before-after,verdicts}.{sh,out.txt}`, `fix-r3.expectations-diff.txt`,
  `fix-r3.abarms-confirm.out.txt`.

**Re-run:** `../plan-skeptic-round-3.fakes/sweep.py --scratch <dir> [--lanes …]` (from the repo root, pinned
interpreter). `probes-selfcheck.py` runs it as §T3.

## Not done / queued

- The generated L3 `teeth` description in `expectations.json` (plan-build.py:534-536) still names round 1-2 reads only.
  Changing it would widen M1's two-field diff. It is queued for the next regeneration, with a D-row for round 3, which
  `planDecisions` has not got yet.
- `readers-selfcheck.sh` D1 now passes `--teeth-lanes L7`. Its CANNOT FAIL read is per key; a full §T3 there costs
  about 25 min.
- Nits N1 (control-check extra positional), N2 (exit 1 on a missing input file), N4 (uncited hand counts) and N5
  (safe-002: no passing picture before stage 1) are queued unchanged. So are fix-r2's queued nits.
- Device-side tolerances are left to stage 1, by design: X1_TOL and FACE_INK_TOL; any `ink:` / `mass:` / `core:` reason on
  an otherwise-correct device picture.

**Shared tree, before → after:**
- HEAD `640276a0` → `640276a0`;
- index sha256 `db6b5da3…` → `db6b5da3…`;
- `git status --porcelain --untracked-files=all` outside the two plan dirs: 0 → 0 lines;
- no `__pycache__`, nothing new under `tools/titan/runs` (fake runs live in the scratchpad);
- `git stash list` empty;
- `git worktree list` names no scratch tree.

STATUS: COMPLETE — M1, M2 (L3 plus the every-lane sweep and its fixes), S1 and the A/B-arm confirmation were each done
with executed proof, and the records were regenerated and refreshed on the final bytes. One deliberate, named residual
stays open: 2 faked captures of the L4 014 face-present keys, which cannot be closed before a device face exists (stage 1).
