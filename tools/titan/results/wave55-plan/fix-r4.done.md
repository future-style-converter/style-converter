# Wave 55 — plan-skeptic round 4: FIX pass (`fix-r4`)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `611c9f93` (the committed plan). HEAD was not changed:
no commit, checkout, stash or reset on the shared tree.

**Scope.**
- Edits only under `tools/titan/results/wave55-plan/` and `tools/titan/results/wave55-gate/`; no runtime, harness or
  converter byte touched. No gradle, xcodebuild, vite, emulator, simulator, adb or test-all.
- Every file a lane may read that this pass changed (the probes, `expectations.json`, `PLAN.md`; `geometry-gate.py`,
  `probe-readout.mjs` and `watchlist.txt` are byte-unchanged) was written ATOMICALLY:
  a temp file in the same directory, then `mv` / `os.replace` (`patchlib.py`, `install.sh` in the scratchpad). The work was
  developed and swept in a throwaway (`<scratchpad>/wave55-plan-r4-dev`) and installed in one batch at 23:33:57 local; every
  record below was then regenerated ON the shared tree.
- Throwaways (`git worktree add --detach … HEAD`, then `git worktree remove --force`): `wave55-plan-r4-dev`, `-selfcheck`,
  `-teeth` (inside `fix-r4.teeth-before-after.sh`), `-readers`, `-readers2`, `-toolkit`. `git worktree list` names none now.
- Pinned interpreter `/opt/homebrew/bin/python3.12 -B` everywhere, every long run at `nice -n 19`.

## The four must-fix findings (`plan-skeptic-round-4.verify.md` §3)

| # | keys | new read (probe) | measured on the passing pictures (`fix-r4.measure.out.txt`) |
|---|---|---|---|
| M1 | L4 face-present: `[M]` / `[F]` 005 / 006 / 014 ios, 014 android, `[M]` 005 / 006 android (10) | `face xor:` (`ua-heading-margin.geometry.py`) — every face-present heading band = the FACE REFERENCE's band at the best whole-px shift (±`X0_TOL` x, ±`TOP_TOL` y), over the band's rows ±2 and the WHOLE width, two nested ink masks (min channel < 200, RGB sum < 300), XOR ≤ `FACE_XOR_TOL` 2. Compose no-wrap rows: every band when the picture wraps like the ref (C arm: the verifier's fix (a)); when it does not (D arm) band 1 left of the ref's line-1 right edge + "fox" against the ref's line 2 moved there, clipped at x390. Natives only (the web rows are controls) | C / D arms: 0 on every band of every key; web (not judged): 1 … 101 per band (Chromium vs the harness's own underline) |
| M2 | L6 `[X]` safe-001, grid align-self-001, justify-self-001, align-self-vertWM-001, align-self-rtl-001 (5) | `extra` (`android-paint-order.geometry.py`) = fill outside base fill ∪ base band ∪ (the band's 1-px ring where the BASE is not white canvas), ≤ 1 — no ring around the base FILL | six U2 replays 0 under all four candidate regions; over 288 strictly-additive fakes: round-3 dilation passes 52, undilated 5, band-ring alone 11 (the child copied 1 px left: 104 px), chosen region 5 (all `st1-far4`, a 1-px dot: the floor) |
| M3 | L5 `[H] counter-suffix ios` | `ink:` (`counter-suffix-rows.geometry.py`) — rows 3-4's whole marker zone x0-63 = a native's OWN two glyphs (letter, period) as `--base` paints them, each moved by its own whole-px offset (blob-aligned ±2 x, ±1 y), XOR ≤ 2 summed over both rows. Twins: iOS's own (U1-ios reorders the glyphs, never redraws them) and Android's (the ok-all model grafts Android's rows) | ok-all ios `ink 0 vs wave54-final android`, android `0` |
| M4 | L5 `[T] counter-suffix ios` | `ink:` — the digit window x136-150 of row 9 = Android's tabular '1' in `--base` (the ok-all graft source) at the best offset (±2, ±1), XOR ≤ 2: the window holds that glyph and nothing else | ok-all 0; Android's '1' vs the ref's / web's 22, vs iOS's proportional base '1' 35 (why the read is not ref-relative, and why stage 1 re-pins it) |

**Beyond the 17 keys (so the verifier's own generator reads 0 holes, not only 0 IN-ZONE).** Its summary counts HOLES by
an ANY mask (min < 200 OR sum < 300); a 1-px smear that only darkens a glyph's antialiased edge added 3-8 core px while the
min-channel XOR read 0-2. Every round-3/4 `ink:` read now compares the ink LEVEL (both nested masks) and sums its zones:
- L1 `ink:` (`hyphen-ios-linebox.geometry.py`): both masks, summed over the lines (was max per line);
- L4 today's-face / 011 `ink:` (crop read): both masks, summed over the bands;
- L5 `[C]` (zone widened to x0-63) and `[I]` (round 3's per-zone ±2 let 4 px through as 1-2 per zone): both masks, summed
  over the key's zones; `[I]` natives also read 005 outside their line windows (prose, x0-199, below the list) = the base.

**Stage-1 mechanism (the device face does not exist yet).** The L4 face reads and L5 `[T]` compare a native line with
ANOTHER rasterizer's pixels (Chromium's ref, Android's '1'). The device distance is measured: the inset-001…004 anchors —
the 2em bold face the natives already paint — sit 1768-2510 px level-XOR / −13.9 … +9.4 % dark px from the ref
(`fix-r4.measure.out.txt` §2). Re-setting a ref tolerance to that would re-open the class, so instead:
- `FACE_RUN_DEFAULT` (L4) and `TWIN_RUN_DEFAULT` (L5), both `None`, plus `--face-run` / `--twin-run` for dry runs;
- PLAN §7 stage-1 step 8 (round-4 paragraph) and both lanes' `stage1Decision`:
  - a device line whose ONLY reason is `face xor:` / a twin `ink:` is decided by a recorded look;
  - the same probe edit then sets the default to `'wave55-pre'`;
  - from `wave55-probe` on, those keys read the decided device pixels, XOR ≤ 2;
  - the tolerances are never widened.
- `fix-r4.stage1-pin-proof.{py,out.txt}` proves it on stand-in device runs:
  - L4: another rasterizer's AA on the C arm reads `face xor:` 277-314 → WRONG vs the ref, OK (0) with `--face-run`, and a
    2×2 blob → WRONG (xor 8) with it;
  - L5: the ref's '1' in iOS's window → WRONG (22) without, OK (0) with `--twin-run`, and a blob → WRONG (8).
  - Every case as required.

## The sweeps (bar: 0 acceptances)

**The verifier's own generator, unchanged** (`plan-skeptic-round-4.verify.d/fakes4.py` 379b3a1e… + `zones4.py`, plan dir
hard-coded = this tree), every lane, on the installed bytes → `fix-r4.verifier-rerun.out.txt`:

| lane | before (verifier's record, round-3 bytes) | after (round-4 bytes) |
|---|---|---|
| L1 | IN-ZONE 0 · HOLES 19 (9 > 2 px) | IN-ZONE **0** · HOLES 10 (**0** > 2 px) |
| L2 | 0 · 0 (0) | **0** · 0 (**0**) |
| L3 | 0 · 18 (0) | **0** · 18 (**0**) |
| L4 | **98** · 177 (79) | **0** · 81 (**0**) |
| L5 | **10** · 15 (8) | **0** · 6 (**0**) |
| L6 | **28** · 33 (28) | **0** · 5 (**0**) |

Same fake runs and reads per lane before and after (1160 fake runs, 4045 reads). **IN-ZONE 0, HOLES > 2 px 0** on every lane.
`fakes4.py` exits 0 on every lane; it exited 1 on L1 / L4 / L5 / L6 before.

**The round-3 sweep** (`plan-skeptic-round-3.fakes/sweep.py`, every lane, gating + control + tally rows), as
`probes-selfcheck.txt` §T3: 987 key reads, **HOLES 0, named residuals 0**. Round 3's two residuals (the 2×2 blob touching a
face-present 014 glyph, ios / android) are now caught by `[M]` / `[F]` 014; `sweep.py RESIDUALS` is emptied with a comment
saying so.

**The named residual that stays — the 2-px floor (S2):**
- A fake adding ≤ 2 px under the probe's own mask passes every `±2` / `XOR ≤ 2` read by construction. On the installed bytes
  that is 120 faked captures: L1 10, L3 18, L4 81, L5 6, L6 5. They are 1-px dots, 1-2-px smears, and sub-2-px copies of a
  rare colour class (`fix-r4.verifier-rerun.out.txt` lists each).
- §T4 also counts zones4's benign rows (≤ 2 px in the key's own zone, or the ink lies in a sibling key's zone that rejects
  it): 493.
- The passing pictures read 0, so closing the floor means a 0-px tolerance. That is a stage-1 device question, named in
  PLAN §0 and D39 as the verifier recommended.
- It is the only residual. The fix-r3 named residual is CLOSED without a device face.

## Standing set, verdicts, equivalence

- **`probes-teeth-r4.py`** pins 50 cases, loaded by `probes-teeth.py run_all()` after r3:
  - every variant the verifier recorded IN-ZONE (42 variants, 136 key acceptances), each pre-registered FAIL on exactly the
    keys that accepted it;
  - the verifier's zone-restricted L5 fakes (`l5-zone-fakes.py`, 7);
  - fix-r3's 014 residual.
  - Builders: `plan-skeptic-round-4.fakes/added_ink4.py`, copied verbatim from the verifier's `fakes4.py`.
  - `fix-r4.teeth-r4-equiv.out.txt`: all 42 IN-ZONE cases (286 native captures) are pixel-identical to the fake runs the
    verifier's original `fakes4.py` wrote on this tree.
- **`probes-teeth-r2.py`:** D33's clip-10 case is pre-registered FAIL while `FACE_XOR_TOL < 10` (`face_xor_tol()` reads it):
  `face xor:` closes that blind spot.
- **`fix-r4.teeth-before-after.out.txt`** (throwaway, 265 cases):
  - on the round-3 bytes (`plan-skeptic-round-4.fakes/round3-probes/` = HEAD's): exit 1, **145 violations, all round-4
    cases**; the clip-10 case is as pre-registered (PASS);
  - on the current bytes: exit 0, **0 violations**, 48 / 48 keys reject a fake.
- **`probes-selfcheck.txt`** (regenerated by `probes-selfcheck.py`, which gains §T4 and now writes atomically):
  - §0 self-test HOLDS;
  - §T 265 cases / 0 violations / 48 of 48;
  - §T3 987 reads / HOLES 0 / residuals 0;
  - §T4 the verifier's generator (relative-path copy, `diff` = the two path lines) over every lane: IN-ZONE 0, > 2-px
    holes 0;
  - 43 / 43 gating keys able to fail, 42 with a fixed picture accepted (safe-002: round-3 N5, unchanged).
- **Verdicts preserved:**
  - `fix-r4.verdicts.sh` → `fix-r4.verdicts-preserved.out.txt`: the 45 argument sets of fix-r3's proof, round-3 bytes vs
    round-4 bytes — **every verdict preserved**;
  - `fix-r3.verdicts.sh` re-run as a scratch copy → `fix-r4.verdicts-vs-round2.out.txt`: round-2 vs round-4, every verdict
    preserved, line-identical to the committed `fix-r3.verdicts-preserved.out.txt` but for its header.

## Regeneration (the generator needed round-3 / round-4 rows)

- `plan-build.py` → `expectations.json` `490dc5b9…` → `980542d0…`. `fix-r4.expectations-diff.{py,txt}` shows **10
  structural differences**:
  - six `teeth` strings (L1, L2 cbb, L3 — fix-r3's queued round-3 text — and L4, L5, L6) and two `stage1Decision` strings
    (L4, L5), each a pure APPEND (the old string is a prefix of the new);
  - `planDecisions` + D38 (round 3, owed by fix-r3) and + D39 (round 4).
- `plan-build.py --out <scratch>` on the shared tree reproduces `expectations.json` and `watchlist.txt` byte-for-byte, and its
  stdout is byte-identical to `plan-build.out.txt`.
- `PLAN.md` `80a4f4f2…` → `35639d61…`: `plan-prose.py --fill` (header sha, L1-L6 geometry, L4 / L5 facts, stage1-required,
  plan-decisions) plus hand prose:
  - §0 item 5 and the "Pins can fail" bullet: S1 restated; the 2-px floor named; round-3 residuals and D33's exception
    closed;
  - §7 stage-1 step 8: the round-4 paragraph;
  - §9 D38 / D39 rows and their intro bullet.
- Checks: `plan-prose.py --check` HOLDS (36 blocks, 41 decision ids); `plan-prose.out.txt` re-run with every mutation RED
  (18). No regenerable count is typed by hand.
- `../wave55-gate/final55-analysis.sh` regenerated (`9efba967…`, diff = the embedded sha); `--check` in sync.
- `../wave55-gate/readers-calibration.txt` re-run as a scratch copy in a throwaway: every calibration as required, 31 checks,
  normalised diff 0.
- `readers-selfcheck.sh` copies the round-4 files into its throwaway; `readers-selfcheck.txt` re-run: every check as
  required. The only differing text is a scratch directory name.
- `../wave55-gate/toolkit-proofs.sh`: its C2 / C3a fixture hard-coded the chain `640276a0 a2a37a8e`.
  - That chain stopped reverse-applying once the plan commit `611c9f93` edited those commits' files (re-run: 2 checks NOT
    as required, on HEAD, not on any instrument).
  - The chain is now the branch's two newest commits, read at run time (`ed5d723d…`).
  - Re-run: every instrument as required. The record cites expectations `980542d0…`.
- Watchlist on wave54-final: `unmatched 0`, output byte-identical to `watchlist-check.wave54-final.out.txt`.
- `geometry-gate.py wave54-final --base wave54-final --self-test`: **HOLDS** (gating 43 FAIL, control 160 PASS, report 27 FAIL).
- `beforeEveryDeviceRun` parses (`bash -n`).

## Files (sha256, first 8)

**Changed `wave55-plan/`:**
- probes: `ua-heading-margin.geometry.py` 50344dc6, `counter-suffix-rows.geometry.py` bd2f001a,
  `android-paint-order.geometry.py` 360e6e28, `hyphen-ios-linebox.geometry.py` 0dc54c73;
- the set: `probes-teeth.py` 79af7c0e, `probes-teeth-r2.py` 590a1858, `plan-skeptic-round-3.fakes/sweep.py` aa5bba36,
  `probes-selfcheck.py` 7aeb3f1b (+ `.txt` 682496d3);
- the plan: `plan-build.py` 67833a1d, `expectations.json` 980542d0, `PLAN.md` 35639d61, `plan-prose.out.txt` c935904d;
- `readers-selfcheck.sh` 56e5bc8c (+ `.txt` 8bc29a99).

**Changed `wave55-gate/`:**
- `final55-analysis.sh` 9efba967;
- `readers-calibration.txt` e48ad46d;
- `toolkit-proofs.sh` ed5d723d (+ `.txt` 44127f37).

**New `wave55-plan/`:**
- `probes-teeth-r4.py` 686e21fe;
- `plan-skeptic-round-4.fakes/`: `added_ink4.py` 6608eff7, `fakes4.py` 0fe94ba7, `zones4.py` eecd13f2, `round3-probes/` (the
  seven probes at HEAD);
- `fix-r4.{measure,stage1-pin-proof,teeth-r4-equiv,expectations-diff}.py` with their outputs;
- `fix-r4.{verdicts,teeth-before-after}.sh` with their outputs;
- `fix-r4.verdicts-vs-round2.out.txt`, `fix-r4.verifier-rerun.out.txt`, and this file.

## Not done / queued

- **Stage 1 (by design):** decide the face / '1' / marker keys on the device lines by the recorded look, then set
  `FACE_RUN_DEFAULT` / `TWIN_RUN_DEFAULT = 'wave55-pre'`, with the step-6 regeneration, before `wave55-probe`.
  - Keep `X1_TOL` / `FACE_INK_TOL` as already planned.
  - The 2-px floor: a 0-px tolerance only if the device pictures permit it.
- The lanes' own `geometry-gate.py --self-test` reads the new probes from now on. Their gating keys still FAIL on today's
  pictures and PASS the briefs' fixed pictures (verdicts preserved).
- Verify-r4 N1 (self-test records cite the script under test, not their own sha256) and fix-r3's queued nits are unchanged.
  The committed `ab-arms55.selftest.out.txt` was not rewritten: toolkit C1 ran a scratch copy whose record stayed in the
  scratchpad.
- The orchestrator commits the two plan dirs. This pass made no git state change on the shared tree.

**Shared tree, before → after:**
- HEAD `611c9f93` → `611c9f93`;
- index sha256 `fba384c3…` → `fba384c3…`;
- `git status --porcelain --untracked-files=all` outside `tools/titan/results/wave55-*`: 0 → 0 lines;
- `git stash list` empty;
- no `__pycache__`, temp or `.r4old` file left in the plan dirs;
- no `wave55-plan-r4-*` worktree registered.

STATUS: COMPLETE — the verifier's 17 gating keys (M1-M4) each gained a read the strictly-additive fakes cannot pass while
their fixed pictures still print OK, proven on both sweeps over every lane: the verifier's own generator gives IN-ZONE 0 and
0 holes > 2 px (was 136 / 124), and the round-3 sweep gives HOLES 0 and residuals 0. The standing set holds: 265 cases, 0
violations, 145 new violations on the round-3 bytes. Every verdict on the 45 argument sets is preserved, the self-test and
the prose check HOLD, and the watchlist is unmatched 0. The one remaining residual is the named 2-px floor (S2), a
0-px-tolerance device question for stage 1. fix-r3's named 014 residual and D33's clip-10 are closed without a device face.
The device-face caveat is handled by the FACE_RUN / TWIN_RUN stage-1 re-pin, which is proven on stand-in runs.
