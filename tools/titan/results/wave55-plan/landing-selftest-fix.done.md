# Wave 55 — the landing-tool fix: `land-units.selftest.sh` section D re-premised, the row-30 LRM note, the toolkit-proofs header (`landing-selftest-fix.done.md`)

Closes `regen-verify.md` §7 items 2 and 3 and `regen.done.md` §6 item 1 (+ item 2), and re-runs the toolkit proofs on the
current bytes.

**Where.** Shared tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`. HEAD was `7566fb03` when the brief was read
and became **`55dd4cd9`** (the orchestrator's checkpoint, committed 2026-10-12 11:48 +0200) before the first edit; every run
below is on `55dd4cd9`, and at that HEAD the tracked tree was clean (only the lane dirs untracked). `<scratch>` = the session
scratchpad root. Throwaways: `<scratch>/wave55-lu-selftest-1` … `-4` (worktrees `git worktree add --detach … HEAD`) and the
toolkit's own `git clone --shared` (C3a, under `<scratch>/wave55-tk`); all removed at the end (worktree count 39 → 39).
No device, gradle, xcodebuild, vite. Every write to the shared tree: a temp beside the file, then `mv` (the manifest by
`--emit-manifest`'s own temp + mv; PLAN.md by `plan-prose.py --fill` on a temp copy beside it, then `mv`).

## 1. Section D of `land-units.selftest.sh`, re-premised on HEAD

**Cause (reproduced).** Section D assumed L7 U3's planning files (`wave55-gate/ab-arms55.{sh,selftest.sh,selftest.out.txt}`)
are UNTRACKED; they are tracked since `611c9f93` (U1's `flex-zero-gap-rules.*` since `a2a37a8e`), and the real lane delivers
`unit-U3.patch` as a patch that MODIFIES them. So D1/D2 (expected red), D6 (a creation patch over a tracked file → the
tool's "is tracked at HEAD, but its first patch CREATES it (cut on the wrong base)"), and D7–D9/D11 (the landing) failed.

**The new D** (same behaviours, current HEAD; every message asserted is the string `land-units.sh` prints — read from its
`err` / `warn` / `echo` lines 467–476 and 534–552):

| check | behaviour | asserted string / fact |
|---|---|---|
| D1–D3 | optional units with no patch whose files ARE tracked → clean | exit 0; `ok      L7:U3 (optional, no patch): <f> tracked at HEAD` ×3 (the manifest's `trackedAtHeadIfNoPatch`, 3 paths) and the U1 line |
| D4–D5 | the guard can still fail: a scratch copy of the script whose OPTIONAL-files row also names a loose UNTRACKED file (its own manifest emitted beside it, in scratch) | exit 1; `L7:U3 delivered no patch and …/ab-arms55.SYNTHETIC-untracked.txt is NOT tracked at HEAD`; the 3 tracked files still `ok` |
| D6–D7 | a tracked optional file that differs on disk | exit 0 with `WARN    L7:U3 delivered no patch but …/ab-arms55.sh differs from HEAD on disk` |
| D8–D9 | `unit-U3.patch` that CREATES the three tracked files (on disk) | exit 1; `<f> exists on disk, but its first patch CREATES it` ×3 |
| D10–D11 | … with `ab-arms55.sh` moved off disk | exit 1; `…/ab-arms55.sh is tracked at HEAD, but its first patch CREATES it (cut on the wrong base)` |
| D12–D13 | the real path on that | exit 1, `the preflight found … error(s)`; HEAD, index and status unchanged |
| D14–D15 | U3 as the real lane delivers it: a patch that MODIFIES its three tracked files, cut on HEAD | `--check` exit 0; the three listed `~`; six `ok      force-add`; `--check` touched nothing |
| D16–D17 | the real path: records + U2 + U3 | exit 0; the three subjects equal `landing-manifest.json`'s `records.subject` and the L7 U2 / U3 `subject`s in `seq` order (2, 3) |
| D18 | the records commit | exactly the lane dir + the manifest's six `records.forceAdd` files (gitignored wave-54 `out/`), now tracked |
| D19–D20 | the unit commits | U2 = its one created path; U3 = its three paths, committed and on-disk bytes = the model (HEAD + patch), modes kept (100644 100755 100755) |
| D21–D22 | bodies and tree | `(seq 02)` / `(seq 03)` + each patch's sha256, the manifest's U3 `note`, the trailer; throwaway clean |

Two supporting edits in the same script: `mkpatch` keeps the file mode of the file it cuts (`shutil.copymode`; D edits two
100755 runners — a no-op for the 100644 files sections A–C / F cut), and the header prints one more line, the FULL sha256 of
`land-units.sh`, `land-units.selftest.sh`, `landing-manifest.json` and HEAD (verify-r4 N1: the record cites its own script).
The header's D paragraph is rewritten to the new premise. `bash -n` OK.

**Runs.**
- `TMPDIR=<scratch>/lsf/tmp LU_SELFTEST_ONLY=D bash tools/titan/results/wave55-plan/land-units.selftest.sh <scratch>/wave55-lu-selftest-1` → exit 0, **24 passed, 0 failed** (D1–D22, Z1, Z2). Each D output file read: the refusals are the tool's real lines (e.g. D8 `SUMMARY  5 error(s)`).
- **The whole self-test**, on the final bytes: `TMPDIR=<scratch>/lsf/tmp bash tools/titan/results/wave55-plan/land-units.selftest.sh <scratch>/wave55-lu-selftest-2` → exit 0, **`SELFTEST  144 passed, 0 failed`** (A 20 · B 42 · C 20 · D 22 · E 18 · F 10 · G 10 · Z 2; was 133 = 122 + D 11). Every A / B / C / E / F / G / Z PASS line is identical to the `640276a0` record's. Z1: the shared tree's HEAD, index and status byte-identical before / after; Z2: no throwaway left.
- **The record** `land-units.selftest.out.txt` (sha256 `1fd6f22e…`) is that run's stdout with the 10 scratchpad roots (the ten `(throwaway … removed)` lines) written `<scratch>`. Its header cites HEAD `55dd4cd9…`, `land-units.sh` **`6a3e2b7e772bee298facbbc42df44b573ba917f77e66ff1109c1d918e8ee2634`**, `land-units.selftest.sh` `1391719c…` and `landing-manifest.json` `f7050c38…`.

## 2. ORDER row 30 (L5 `U1-ios-LRM`): the trigger re-worded to `stage1Decision` precedence (ii)

- Old note: "Lands only with --contingency L5:U1-ios-LRM, after the stage-1 probe prints [H] counter-suffix ios WRONG (or
  H-low-shaped) while [C] / [T] / [K] ios are OK; …".
- New note: "Lands only with --contingency L5:U1-ios-LRM, and only on precedence (ii) of expectations.json
  lanes.L5-counter-suffix-rows.stage1Decision: the stage-1 probe's [H] counter-suffix ios line carries a GEOMETRY reason (blob
  count, the period before the letter, the letter or the period off the ref's x ±2 / rows ±1, the letter fill), or the recorded
  look shows the H-low shape — never on a twin-only `ink:` or `residual:` line (such a line goes to the PLAN §7 stage-1 step-8
  recorded look first, precedence (i); only a wrong look there counts as the GEOMETRY reason). It edits the file U1-ios
  creates, so U1-ios's revertOrder then becomes [U1-ios-LRM, U1-ios]."
- `land-units.sh` `0ced008c…` → **`6a3e2b7e…`**: `git diff HEAD` is one line (135), and fields 1–11 of the row are asserted unchanged (only the note field).
- `bash tools/titan/results/wave55-plan/land-units.sh --emit-manifest` → exit 0. `landing-manifest.json` `c933f68d…` → **`f7050c387f06728c325be132a33d4cd74981393087a914361c6903dd939aacc9`**. `diff`: one line (782). A JSON walk of both: **1 path differs, `/units[29]/note`** (seq 30, L5 U1-ios-LRM, contingency).
- `plan-prose.py --check` before the fill: **FAILS — 1 violation** (gen:landing-order's sha line: the check sees the new manifest). `plan-prose.py --fill <temp copy of PLAN.md>`: 36 blocks rendered; the copy differs from PLAN.md in **one line, 1786**, under "## 4. Landing order and the unit commit list" (the manifest sha). `--check` on the copy HOLDS; the copy `mv`'d over PLAN.md (`e3ffb639…` → **`5ce96609…`**); `--check` on PLAN.md: **HOLDS** (36 gen blocks · 43 decision ids · 46 wave54-final citations · 28 landing-order units (want 28)).
- `TMPDIR=<scratch>/lsf/tmp bash tools/titan/results/wave55-plan/land-units.sh --check` on the shared tree, twice (after the fill, and again at the end): exit 0, **`SUMMARY  0 error(s), 0 warning(s) — landable`**, "manifest in sync … f7050c387f06…", "28 plan revert units (+ 2 contingency) agree", 27 revert dry-runs ok, both contingencies apply on top. Snapshots before / after (HEAD, index sha256, stash, `git diff HEAD` sha, `status --porcelain=v2 --untracked-files=all`, sha256 of every file under `tools/titan/results/wave55-*`): **identical** both times (2096 and 2099 lines).

## 3. `toolkit-proofs.txt` on the current bytes, scrubbed

- **Scratch copy** `<scratch>/lsf/tk/toolkit-proofs.scratch.sh` of `toolkit-proofs.sh` (`ed5d723d…`, unchanged), four edits: `SH` pinned to the shared tree; the `tee` goes to `<scratch>/wave55-tk/toolkit-proofs.raw.txt`; C1 runs `ab-arms55.selftest.sh` from a second throwaway at HEAD (`TK_C1WT`, its three runner files `cmp`-identical to the shared tree's) and reads that throwaway's record, so the tracked `ab-arms55.selftest.out.txt` beside the shared runner (the `-` context of `unit-U3.patch`) is never rewritten. Sections A, B, C2–C3b, D run exactly as written (C3a makes its own `git clone --shared` and removes it).
- `TK_C1WT=<scratch>/wave55-lu-selftest-4 bash <scratch>/lsf/tk/toolkit-proofs.scratch.sh <scratch>/wave55-lu-selftest-3 <scratch>/wave55-tk` (from the shared tree) → exit 0, **95 "as required", 0 "NOT as required"**: "every instrument behaved as required (each shown able to fail)". Header: shared tree HEAD 55dd4cd9; **expectations.json `b773904f788d023d…`**, adjudicate `262d22c3…`, control-check `cba40eb5…`, ab-arms55.sh `5cd6fccb…`, ab-arms55.selftest.sh `72ca276a…`, make-snapshot55 `df37730d…`. C3a: DRY RUN OK on the clone at 55dd4cd9; C3b: the shared tree refused for its 5 uncommitted tracked files (this pass's), refs / stash / HEAD / status unchanged. The shared-tree snapshot and refs + stash are identical before / after the run.
- vs the HEAD record (`a728b040…`): 12 lines differ, all expected — the header (date, HEAD, sha line), C2 / C3a / C3b (HEAD-derived strings), T3 / T5d / T5e / T5j / T5l (the self-test's fresh `git init` commit ids), D1 / D3d (the scrub). Sections A and B are line-identical.
- **Scrub** (`<scratch>/lsf/scrub.py`, root as an argument): 2 full roots + 2 `cut -c` remnants → `<scratch>` / `<scratch>…` on **lines 1, 102, 140, 148**; asserted no root, no session id left. Installed atomically: `toolkit-proofs.txt` **`b074a9d7…`**. Grep proof on the installed file: the root (with and without `/private`) 0 · the session id 0 · the per-user tmp dir name 0 · `/private/tmp` 0 · `/tmp/` 1 (line 126, `/tmp/titan-device-pool`, the device pool — not a scratchpad path).

## Files (sha256)

| file | before | after |
|---|---|---|
| `wave55-plan/land-units.selftest.sh` | `ad7ba2da` | `1391719c` |
| `wave55-plan/land-units.selftest.out.txt` | `85274cbd` (133 / 0, `640276a0`) | `1fd6f22e` (144 / 0, `55dd4cd9`) |
| `wave55-plan/land-units.sh` | `0ced008c` | `6a3e2b7e` |
| `wave55-plan/landing-manifest.json` | `c933f68d` | `f7050c38` |
| `wave55-plan/PLAN.md` | `e3ffb639` | `5ce96609` |
| `wave55-gate/toolkit-proofs.txt` | `a728b040` | `b074a9d7` |
| `wave55-gate/toolkit-proofs.sh` | `ed5d723d` | unchanged |

**Shared tree.** HEAD `55dd4cd9` before and after; stash empty; `git status` shows exactly the six files above modified plus this
file (new); no other byte under `tools/titan/results/wave55-*` changed (sha256 inventory); no `*.lsf` temp, no `__pycache__`.

## Open

1. `expectations.json` `lanes.L5-counter-suffix-rows.contingencyUnits.U1-ios-LRM.trigger` (generated by `plan-build.py:875`,
   rendered at PLAN.md:1369) still carries the old wording ("[H] … prints WRONG, or … H-low-shaped …, while [C] / [T] / [K] ios
   are OK"). Generator-owned, not this pass's; `stage1Decision` governs (PLAN §10 item 5). Align it at the next regeneration.
2. Hand prose that still calls the row-30 note stale: PLAN §10 item 5 (last sentence) and `ship-notes.md` §3 — now fixed in
   the table; owned elsewhere.
3. Records citing the previous bytes (history, not re-run here): `plan-prose.out.txt` ("installed before / after" PLAN
   `e3ffb639`, manifest `c933f68d`); `regen.manifest-diff.txt` / `regen-verify.md` I18 (`c933f68d`).
4. PLAN §8 step 6's OTHER self-test record, `wave55-gate/ab-arms55.selftest.out.txt`, is still the HEAD (`640276a0`-era) record;
   `unit-U3.patch` rewrites it at landing. Toolkit C1 re-ran that self-test on the current runner bytes in a throwaway: every
   check as required.
5. Round 7's MF1–MF7 remain open under the bounded claim (not this pass).
6. The orchestrator commits the six files and this note.

STATUS: COMPLETE — section D re-premised on HEAD 55dd4cd9 (22 checks, every asserted refusal is the tool's own line); the whole
self-test reads `SELFTEST 144 passed, 0 failed` and its record cites `land-units.sh` 6a3e2b7e… and HEAD; row 30's note follows
stage1Decision (ii), the manifest differs only in `/units[29]/note`, `plan-prose --check` HOLDS with §4 rendering f7050c38…,
and `land-units.sh --check` reads 0 errors / 0 warnings with the shared tree byte-identical before and after; toolkit-proofs
re-ran on the current bytes (95 as required, expectations b773904f…) and its record carries no scratchpad path.
