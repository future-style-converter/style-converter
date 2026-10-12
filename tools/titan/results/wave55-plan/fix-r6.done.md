# Wave 55 — plan-skeptic round 6: instrument FIX pass (`fix-r6`)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `4e789976` (the plan commit `611c9f93` plus the
orchestrator's checkpoint of the plan dirs). HEAD was not changed: no commit, checkout, stash or reset on the shared tree.
Inputs read: `plan-skeptic-round-5.verify.md` (+ `.done.md`, `plan-skeptic-round-5.verify.d/`: `fakes5.py`, `adjudicate5.py`,
`residual.py`, the six `fakes5.L*.json`) and `fix-r4.done.md`.

**Scope.**
- Edits only under `tools/titan/results/wave55-plan/` and `tools/titan/results/wave55-gate/`. No runtime, harness or converter
  byte touched. No gradle, xcodebuild, vite, emulator, simulator, adb or test-all.
- Every file a lane or auditor may read was written ATOMICALLY (a temp file in the same directory, then `mv` / `os.replace`).
  The work was developed and swept in a throwaway (`<scratchpad>/wave55-plan-r6-dev`, standing set there: 611 cases,
  0 violations), then installed in one ordered batch by a scratchpad script that refused to run unless every file it replaces
  still had the bytes this pass started from, new modules before the files that import them. Every record below was then
  regenerated against the INSTALLED bytes.
- Throwaways (`git worktree add --detach … HEAD`, symlinks removed first, then `git worktree remove --force`):
  `wave55-plan-r6-dev`, `-selfcheck`, `-readers`, `-readers-wt`, `-calib-wt`, `-toolkit`. `git worktree list` names none now.
- Pinned interpreter `/opt/homebrew/bin/python3.12 -B` everywhere; every long run at `nice -n 19`.

## The class, closed generically: the RESIDUAL read

Round 5's 704 acceptances (M1–M6) all stood on paint where no read of a key looks. Round 6 adds no read per key per colour.
`geometry-gate.py` applies ONE read, `residual-read.py`, to every gating and every `pictureCorrectTally` key: 48 keys on 35
cells. A key cell with no declaration is a refusal (exit 2). The read only ever turns a probe's PASS into FAIL: `residual: …`
on the row, and UNMEASURED when the base capture is missing.

| read (tolerance 0) | rule | what it catches |
|---|---|---|
| **outside** | outside the key cell's declared change zone, the capture equals the `--base` capture pixel for pixel | a faint copy, an off-colour speck, a line in a row no probe reads; every key of the cell fails at once |
| **vacated** | where the base's class ink LEFT (Z_B \ Z_C), only background or a declared parent fill shows, never beside class ink present at that pixel on both pictures | faint ink in the rows a heading left; a hole eroded into a box that did not move |
| **non-class** | inside the zone, a changed pixel is background, a declared ink class or its declared anti-aliasing; a recorded window holds ONE connected change | off-colour paint inside the zone; a speck in a corner window that does not touch the arc |

**The zones are declarations, not tolerances** (`residual-read.py` `SPECS`, one per cell).
- **Class zones, ungrown, on the capture AND the base.** Each uses a colour its probe reads pixel for pixel:
  - L2 abspos: strict green ∪ red. L2 clips: strict green ∪ backdrop pink.
  - L3 stack: the green squares. L3 dyn: the magenta child. L3 bidi: the blue runs.
  - L6 `[X]`: the child fill. L6 `[PO]`: the menus' red.
- **Band and rect zones.** L1 uses the probe's own core line bands ±3 (x < 140). L4 uses its dark bands ±2 (y96, or y112 for 011,
  to y399): the rows the two-level XORs read. L5 uses its `[H]` / `[C]` / `[T]` / `[I]` zones. The L3 dir rows use the three
  row bands.
- **Windows.** Five cells change pixels in no class: clip ×4 and bidi. `fix-r6.zones.py` derives their windows
  (`fix-r6.zones.out.txt` §2):
  - Every pre-registered PASS picture is read WITHOUT windows, against wave54-final.
  - The leftover pixels are clustered, and each cluster's bbox + 1 px becomes a window: 20 windows.
  - So the zone grows by exactly "the pixels the unit legitimately changes", taken from the fixed replay's or wave54-probe
    capture's own diff against the base. Clip-3 U2's 573 px and bidi's 93 px are covered with no tolerance.
  - The literal is checked against the derivation (`--check`).
- **Anti-aliasing** (`fix-r6.zones.out.txt` §4):

  | rule | used for | what it allows |
  |---|---|---|
  | `neutral` | black text | a grey |
  | `ramp` | coloured text | white → lime / blue |
  | `box` | inside windows only | an arc pixel bordered by ≥ 2 palette classes, inside the palette present within 2 px on either picture |
  | `segment` | inside windows only | a two-colour blend |

  - Measured minima: 0 / 0 / 0 / 14. Installed: 2 / 3 / 3 / 14.
  - The three blends get one rounding step. `box` needs 14 because Android's clip AA mixes in the hidden pink layer.
- **The fixed pictures read 0 / 0 / 0.** `fix-r6.zones.out.txt` §3 covers all 41 pictures on the 35 cells (every
  PASS-registered picture of the standing set).
  - One named exception: `[X] …safe-002`, 42 px outside. It has no passing fixed picture (round-3 N5: its replay keeps
    50 crossing px), so it is not a window source.
- **Every legitimate arm also reads 0 / 0 / 0:**
  - L1 Rm / Rm1 / Rw / R0 / R1 / Rd / X;
  - L4 every arm folder of 005 / 006 / 014 / 011 on both natives (C, D, U1, B, M*, R*, T, X2, C4);
  - the L3 U1 / U1U2 / U2 replays, including dir row 7 under U1 alone;
  - L5 ok-all, L6 U1 / U2, and the clip-3 replay.
- **Non-zero only on wrong pictures:** bidi PLAIN (506); clip-3 `fake-centered` / `fake-unclipped`; safe-002 (above); the L5
  C-space-only / H-broken / K-leak fakes.

**One probe change.** In `positioned-parent-slot.geometry.py`, `MAG_TOL` = 2 (was ±5) on the dyn child's magenta count.
- The 2×2 yellow the verifier put over the magenta child equals the BASE there (the parent's own colour), so no residual read can
  see it.
- Every passing picture reads exactly 100: ref, web, iOS, Android and the U2 replays.

**Attribution of the 704 round-5 acceptances** (`fix-r6.residual-on-r5.out.txt`: every accepted fake rebuilt with the
verifier's own generator and read):

| rejected by | fakes |
|---|---:|
| outside | 687 |
| vacated | 11 |
| non-class | 3 |
| the L3 dyn `MAG_TOL` (no residual can see them) | 3 |

Of the 85 floor fakes, 25 are now rejected too.

## Proofs (each record regenerated by its script against the installed bytes)

| check | record | result |
|---|---|---|
| **the verifier's own sweep, every lane, through the real gate** | `probes-selfcheck.txt` §T5 (`plan-skeptic-round-5.fakes/fakes5.py`: the verifier's file with its plan path made relative; `diff` against `verify.d/fakes5.py` shows the path line and the scratch guard only; `adjudicate5.py` verbatim) | 901 fake runs, 2388 key reads. **MUST-FIX acceptances 0** (were 704). Floor (≤ 2 px) 60 (were 85): 1-px specks on every lane, and on L4 / L5 ≤ 2-px faint slivers and a 1-px strike remnant |
| the standing set (`probes-teeth.py` + r2 / r3 / r4 / **r5**) | `probes-selfcheck.txt` §T | **611 cases, 0 violations**, 48 / 48 gating + tally keys reject a fake (was 265 cases) |
| the round-3 sweep | §T3 | 987 reads, HOLES 0, named residuals 0 |
| the round-4 verifier's sweep | §T4 | IN-ZONE 0, > 2-px holes 0; benign / floor 298 (was 493) |
| `probes-teeth-r5.py` pins the verifier's fakes | `fix-r6.teeth-r5-equiv.out.txt` | 346 pinned fake runs (every run with a > 2-px acceptance), 788 faked captures: all byte-identical to the verifier's ORIGINAL generator, and each one's changed px and bbox equal its record. Records are loaded sha256-pinned (`fakes5.L*.json`) |
| the declarations | `fix-r6.zones.out.txt`, also §R | every cell declared; 41 fixed pictures 0 / 0 / 0; the windows literal in sync; the tolerance minima |
| the gate self-test | `probes-selfcheck.txt` §0 | **HOLDS**: gating 43 FAIL, control 160 PASS, report 27 FAIL; residual 48 / 48 OK on the identity pair |
| the brief pictures | §L1–§L7 and the per-key table | 43 / 43 gating keys able to fail; 42 with a fixed picture accepted (safe-002: round-3 N5) |
| the round-6 verdicts are preserved | `fix-r6.verdicts-preserved.out.txt` (`fix-r6.verdicts.sh`: round-4 bytes = `plan-skeptic-round-5.fakes/round4-probes/` vs current) | the 45 argument sets: **every verdict preserved** |
| the round-2 verdicts are preserved | `fix-r6.verdicts-vs-round2.out.txt` (`fix-r3.verdicts.sh` as a scratch copy) | every verdict preserved; line-identical to `fix-r4.verdicts-vs-round2.out.txt` once the tree path is normalised |
| the generator | `fix-r6.expectations-diff.txt` (`fix-r6.expectations-diff.py`) | `expectations.json` `980542d0` → `8fbfc83a`: **15 differences, appends only** (six `teeth`, six `stage1Decision`, `geometryGate.what`, `pictureCorrectTally.rule`, + D40). `plan-build.py --out` on the shared tree reproduces it; `watchlist.txt` and `plan-build.out.txt` are byte-unchanged |
| the plan prose | `PLAN.md` `10eeae07` → `9eea4b89`; `plan-prose.out.txt` | `plan-prose.py --fill`, plus hand prose (below). `--check` HOLDS: 36 blocks, 42 decision ids. The selfcheck re-run turns all 18 mutations RED |
| the closing read-out | `../wave55-gate/final55-analysis.sh` | regenerated; the only difference is the embedded sha; `--check` in sync; parses |
| the watchlist | — | unmatched 0 on wave54-final, byte-identical to `watchlist-check.wave54-final.out.txt` |
| the readers | `readers-selfcheck.txt` | every check as required, plus the new B12 / B13: B12 a 3×3 black speck far from every `[PO]` read is FAILed on `residual:`, and a copy with the read disabled PASSes it — the read is load-bearing; B13 an undeclared gating cell is refused (exit 2) |
| | `../wave55-gate/readers-calibration.txt` | every calibration (31) as required; the diff against the installed record is empty after normalising the header and shas |
| | `../wave55-gate/toolkit-proofs.txt` | every instrument as required. The gate dir is byte-identical before and after the run |

**Hand prose in `PLAN.md`:**
- the header item 5;
- §0 "Pins and instruments can fail": the round 5 / 6 paragraph. It restates §0 exactly: every gating and tally key rejects every
  fake class in the standing set AND any paint outside its declared zone; the 2-px floor stays the NAMED residual;
- §7 stage-1 step 8: the round-6 paragraph;
- §9: the D40 intro bullet and its table row.

**Standing-set and reader edits forced by the new read** (each makes a check stricter, never looser):
- `probes-teeth-r3.py`: round 3's "named blind spot" (`hdup+2x` on the wave-54 hyphenate-character tally controls) is closed.
  The copy changes the preamble outside the cell's zone (3114 / 2329 px). Its pre-registration is now FAIL on every key.
- `readers-selfcheck.sh`:
  - copies the round-6 files into its throwaway;
  - D1 now promotes a control on a DECLARED cell (`hyphenate-character-004 ios`). B13 refuses the old flex-gap one before any
    probe runs;
  - E5 keeps showing the standing set turns red on the wave-54 L2 rule. That rule now fails only on the clip-3 corner squares /
    corner blob, because the residual read closes hollow / corners2 / stripe30 even under it.

## Not done / named

- **The 2-px floor (D39) stays the one NAMED residual class.** §T5 lists 60 such fakes and §T4 lists 298: a class-consistent
  addition of ≤ 2 px inside a zone, under a probe's ±2. Closing it is a 0-px probe tolerance, a stage-1 device question.
- **Inside a zone, class-consistent paint is the probes' job.**
  - Example: green painted into a clip-3 corner cut-out. The installed probes reject it; the wave-54 rule did not (readers E5).
  - Inside a recorded window, the only paint left open is a palette blend that joins the window's one recorded change. That is
    an arc-boundary pixel; no sweep (§T5, §T4, §T3, §T) found an acceptance of that kind.
- **Stage-1 device question (by design, PLAN §7 stage-1 step 8, D40).** The windows are replay and wave54-probe footprints, and
  the residual tolerance is 0.
  - A device picture that legitimately changes a pixel outside its declared zone reads `residual:`.
  - Such a line is decided by a recorded look. If it is right, `fix-r6.zones.py --also wave55-pre:<cell> --write` re-records that
    cell's windows from the device capture, as a probe edit plus the step-6 regeneration.
  - It is never a tolerance. Each of the 35 cells is carried by exactly one lane, and no may-move stem overlaps any of them, so a
    residual FAIL can only name the owning lane's unit.
- **safe-002** keeps round 3's N5: it has no passing fixed picture, and stage 1 decides on the device picture.
- **Earlier nits are unchanged:**
  - Verify-r4 N1: self-test records cite the script under test.
  - fix-r3's queued nits.
  - `ab-arms55.selftest.out.txt` was not rewritten: toolkit C1 ran a copy in a throwaway.
- **`plan-skeptic-round-7.verify.md` is not written here.** It is the independent verifier's.
- **The orchestrator commits the two plan dirs.** This pass made no git state change.

## Files (sha256, first 8)

**New in `wave55-plan/`:**

| file | sha256 |
|---|---|
| `residual-read.py` | 40211eb0 |
| `probes-teeth-r5.py` | 897be4ed |
| `fix-r6.zones.py` | b40f0062 (+ `.out.txt` d82f92fa) |
| `fix-r6.teeth-r5-equiv.py` | d7d42e06 (+ `.out.txt` 182d8438) |
| `fix-r6.residual-on-r5.py` | a70dbeea (+ `.out.txt` 766f83aa) |
| `fix-r6.expectations-diff.py` | d5de683a (+ `.txt` b89ece91) |
| `fix-r6.verdicts.sh` | aed6d661 (+ `fix-r6.verdicts-preserved.out.txt` bf4edfac) |
| `fix-r6.verdicts-vs-round2.out.txt` | 749d5cda |
| `plan-skeptic-round-5.fakes/added_ink5.py` | 5bf23bfe |
| `plan-skeptic-round-5.fakes/fakes5.py` | e0a6a183 |
| `plan-skeptic-round-5.fakes/adjudicate5.py` | 856162e1 |

`plan-skeptic-round-5.fakes/round4-probes/` holds the seven probes at HEAD. This file is new too.

**Changed in `wave55-plan/`:**

| file | sha256 |
|---|---|
| `geometry-gate.py` | 09e35e92 |
| `positioned-parent-slot.geometry.py` | e01e82af |
| `probes-teeth.py` | 90c8cb64 |
| `probes-teeth-r3.py` | 86a3fa11 |
| `probes-selfcheck.py` | 9c9c483c (+ `.txt` 808ceb16, was 682496d3) |
| `readers-selfcheck.sh` | aaccdda4 (+ `.txt` 8d86acb1) |
| `plan-build.py` | 4e3aced5 |
| `expectations.json` | 8fbfc83a |
| `PLAN.md` | 9eea4b89 |
| `plan-prose.out.txt` | c94104f1 |

**Changed in `wave55-gate/`:**

| file | sha256 |
|---|---|
| `final55-analysis.sh` | 3f66d5e6 |
| `readers-calibration.txt` | 10b13a97 |
| `toolkit-proofs.txt` | a728b040 |

**Unchanged:** `watchlist.txt`, `probe-readout.mjs`, every other probe.

**Shared tree, before → after:**
- HEAD `4e789976` → `4e789976`;
- index sha256 `f6065c58…` → `f6065c58…`;
- `git status --porcelain --untracked-files=all` outside `tools/titan/results/wave55-*`: 0 → 0 lines;
- `git stash list` empty;
- no `__pycache__`, `*.tmp-r6`, `.r6old*` or `*.mut.py` left in the plan dirs;
- no `wave55-plan-r6-*` worktree registered.
- **Written by another session during the pass:** `../wave55-gate/prelanding/` (two files, 01:59 local). This pass did not write
  them. Every other file that differs from the pass's start is this pass's.

STATUS: COMPLETE — The round-5 class is closed generically. `geometry-gate.py` applies the residual read to all 43 gating and 5
tally-only keys, and every fixed picture reads 0 / 0 / 0. The verifier's own sweep, re-run over every lane through the real gate,
now accepts **0** fakes adding > 2 px (it accepted 704). The standing set holds: 611 cases, 0 violations, 48 / 48 keys. §T3 has 0
holes, and §T4 has 0 IN-ZONE acceptances and 0 holes > 2 px. Verdicts are preserved on all 45 argument sets, against both round-4
and round-2 bytes. `expectations.json` has appends only (D40); `plan-prose --check` HOLDS; `final55-analysis.sh` is in sync; the
self-test HOLDS; the watchlist is unmatched 0. The readers, the calibrations and the toolkit behave as required. The one named
residual is still the 2-px floor (§T5: 60).
