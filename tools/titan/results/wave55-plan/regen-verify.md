# Wave 55 — the pre-landing regeneration: VERIFICATION record (`regen-verify.md`)

This file has two parts.
- **Part I** is the INDEPENDENT verifier's record (2026-10-12, after the regeneration installed its bytes). It re-executes the
  regeneration's claims from scratch and reads `regen.done.md` only for what it claims.
- **Part II** is the regeneration pass's own check record, kept VERBATIM (its bytes were sha256 `6797f1db121069fc…`) because
  `regen.done.md` cites its rows (V1–V13, R1–R3, R10).

# Part I — independent verification

**Tree.** `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `7566fb03` (plan `611c9f93` + checkpoints).
HEAD, index sha256 `90df4083…`, empty stash and `git status --porcelain=v2 --untracked-files=all` were identical at the
start and at the end of this pass. No commit, checkout, stash, reset or apply ran on the shared tree. This pass wrote one
file there: this one (temp beside it, then `mv`).

**Where things ran.**
- Throwaways (`git worktree add --detach … HEAD`, the seven `wave55-*` results dirs rsynced in at the shared tree's bytes,
  `tools/titan/runs`, `tools/wpt` and `node_modules` symlinked; removed at the end, symlinks first):
  - `<scratch>/wave55-regen-verify` — the generator runs, `--emit-manifest`, `regen.l5-proof.py`, the §RG reproduction;
  - `<scratch>/wave55-regen-verify-wt` — the `--wt` of `probes-selfcheck.py`;
  - `<scratch>/wave55-regen-lutest-verify/wt-D` — `land-units.selftest.sh` section D's own throwaway (Z2: removed by it).
- Read-only on the shared tree: `plan-prose --check`, the watchlist check, `geometry-gate --self-test`,
  `final55-analysis.gen.py --check`, `land-units.sh --check`, and the narrowed `probes-selfcheck.py` (its writes go to
  `--scratch` / `--out` / the `--wt` throwaway). After it, `find . -newer <marker>` on the shared tree lists 0 files,
  `tools/titan/runs` included.
- Pinned interpreter `/opt/homebrew/bin/python3.12 -B`; long runs at `nice -n 19`. No device, gradle, xcodebuild, vite,
  emulator, simulator or test-all.

## 0. The round-7 gate (before anything else)

`plan-skeptic-round-7.verify.md` and `plan-skeptic-round-7.verify.done.md` both existed at the first poll (written 10:51 local),
so no wait loop was needed. The done marker's last line is **`STATUS: PARTIAL`**:
- fix-r6's own claims re-execute, and the round-5 class is closed.
- **Open items:** the round-7 sweep still finds 1973 acceptances adding > 2 px, on 35 / 43 gating keys and 16 tally cells, in
  seven named classes MF1–MF7. MF1–MF6 carry a measured fix basis; MF7 has no tolerance-0 reference.

The regeneration records these as a BOUNDED claim and runs no round 8: PLAN §0 "Round 7", §10 item 9, and `ship-notes.md` §1
item 2. PLAN §0's per-class spans and key counts equal the round-7 table:

| class | px span | keys |
|---|---|---|
| MF1 | 4–2558 | 24 |
| MF2 | 4–144 | 1 |
| MF3 | 6–1008 | 4 |
| MF4 | 3350–4888 | 10 |
| MF5 | 4–138 | 5 |
| MF6 | 8–16 | 1 |
| MF7 | 200 | 1 |

They remain open (§7 below).

## 1. Results

| # | check | how (re-executed here) | result |
|---|---|---|---|
| I1 | the generator reproduces its outputs in a throwaway | `plan-build.py --out <scratch>` in `wave55-regen-verify` | exit 0, stderr empty. `expectations.json` **`b773904f`**, `watchlist.txt` **`4b1a9cd5`** and stdout = `plan-build.out.txt` **`67f5dbbc`**: all three BYTE-IDENTICAL to the shared tree |
| I2 | the two older states reproduce too (the base of I3) | HEAD's generator `67833a1d` and the fix-r6 generator `4e3aced5` (the regeneration's saved start copy; its sha256 equals `fix-r6.expectations-diff.txt`'s), each `--out` in the same throwaway | `980542d0` = HEAD's committed `expectations.json`, and `8fbfc83a` = fix-r6's installed one. Both watchlists are `4b1a9cd5`: the watchlist is unchanged since HEAD |
| I3 | the structural diff HEAD → regenerated is exactly the queued set | my own walker (`sdiff.py`: every leaf, `planDecisions` by id, scalar lists as set + order, dict key order) | **37 paths** = hop 1 (HEAD → fix-r6, **15**) ∪ hop 2 (fix-r6 → regen, **26**), 4 paths in both. Each hop's path set equals `fix-r6.expectations-diff.txt` / `regen.expectations-diff.txt` exactly. Hop 1: 15 / 15 are " Round 6 (D40) …" appends plus the D40 row. Hop 2: 26 / 26 content assertions hold. **0 paths outside the queued set** (§2) |
| I4 | nothing structural moved | a projection of every lane's captureCarriers, wireCarriers, mustNotMove, stage sections, revertUnits (revertOrder + captures), prediction (cell, floor, gating, direction, units) and geometry probes (cmd / expect / gating / report / keyCells), plus the complement, the union carriers and the ring-fence | HEAD vs regen: **identical**. fix-r6 vs regen, the gate's own inputs are also identical: every lane's `geometryProbes` minus `teeth`, `pictureCorrectTally`, `geometryGate`, `interpreter`, `stage1Probe`, `probeRun` |
| I5 | L6: the growth stem and the two wordings | read `lanes.L6.mayMove` / `mayMoveRule` / `stage1Decision` | `wpt__css-display__display-contents-dynamic-pseudo-insertion-001` is in `mayMove.android` (240 → 241) and in `unionMayMoveCaptures.android` (241); removed: none (the 19 withdrawn stems stay). `mayMoveRule` AND `stage1Decision` both carry "a band now under its child", "the band now inside the element's opacity group" and "a UA-widget replica now over the band" |
| I6 | L1: the R1 signature routes to the contingency | read `lanes.L1.stage1Decision` / `contingencyUnits.NB-ios-b.trigger` | `stage1Decision`: "The R1 SIGNATURE — the gating reason is `anchor: group 1 line 1 offset 1` only AND the iOS movers outside the carriers are only in-radius one-line SAME controls … each exactly one row low … routes to `land-units.sh --contingency L1:NB-ios-b` (never a revert of NB-ios), and the pre-registered wave55-pre-r1 re-probe must then print the 3 gating rows `→ LINEBOX OK` and those SAME controls byte-identical to wave55-open". `wave55-pre-r1` is a registered `stage1Probe.reprobes` id. The trigger names the R1 signature. `carrierRule` reads 58 / 18 / 4 SAME. C5 keeps 3 in-radius controls. `mustNotMove` is unchanged |
| I7 | L5: the `[T]` key and rows equal R1–R5 | §3 | each request is present, quoted in §3 |
| I8 | L5's probe is the lane's proposal | HEAD's probe `bd2f001a` + `patch` of the lane's `fix-r1.probe-proposal.diff.txt` (4 hunks, clean) | the result differs from the installed `f032061a` ONLY in comment lines: the R5 header correction, plus the "Pre-landing regeneration" paragraph. **Code identical** with comment lines stripped |
| I9 | L5's residual declaration is R3 | rebuild fix-r6's `residual-read.py` by removing the two rects and the two added comment lines | rebuilt sha256 = **`40211eb0`**, which is the hash reverify-r1 recorded. So the only change is `('rect', (130, 208, 136, 304)), ('rect', (136, 232, 151, 256))` in `SPECS['css-counter-styles/counter-suffix.html ios']`, R3 verbatim |
| I10 | L5's replays are R2 | every `counter-suffix-rows-replay/<tag>/counter-suffix.ios.png` vs its HEAD bytes, decoded | all 10 tags: **91 px changed, 0 outside the two R3 rects**, and the rects equal wave54-final Android's capture pixel for pixel |
| I11 | the probe prints OK on the fixed picture and WRONG on the standing fakes | `probes-selfcheck.py --teeth-lanes L5` from the SHARED tree (`--wt` = the throwaway) → `<scratch>` | exit 0. §0 self-test HOLDS (230 keys; residual 48 / 48 OK). §R 41 fixed pictures 0 / 0 / 0, windows in sync. §L5: `replay:ok-all` prints every gating key OK (`[T] counter-suffix ios … ink 0 … → GEOMETRY OK`, `[K] counter-suffix ios zones …,x0-129×y208-231,x151-end×y208-231,x0-129×y232-255,x151-end×y232-255,x0-129×y256-303,x136-end×y256-303,… → GEOMETRY OK`). Each fake tag turns its own key WRONG. §T: 34 cases, **0 violations**, "L5 fixed gate exit 0" and every fake exit 1 as pre-registered. §T3 HOLES 0. §T4 IN-ZONE 0. §T5 MUST-FIX 0, floor 10 |
| I12 | … and `probes-selfcheck.txt` re-runs byte-identical | (a) my I11 output vs the regeneration's own V11 output: compared byte for byte. (b) the fix-r6 record rebuilt in the throwaway (everything before `## §RG`; sha256 = **`808ceb16`**, the start hash `regen.done.md` names), then `regen.selfcheck-rg.py` run with MY narrowed output and MY `regen.l5-proof` output | (a) **identical** (`bdbfd2b5`). (b) the result is **BYTE-IDENTICAL** to the installed `probes-selfcheck.txt` **`e5de2fa7`**. The script's asserts held: §0 / §R / §L1–§L4 / §L6 / §L7 equal fix-r6's; §L5 differs only on the 10 `[K] … ios zones` lines, every verdict equal |
| I13 | the regeneration's L5 proof | `regen.l5-proof.py` in the throwaway | exit 0. Output **BYTE-IDENTICAL** to `regen.l5-proof.out.txt` (`2d0d60fb`), `violations: 0` |
| I14 | `plan-prose.py --check` | shared tree | **HOLDS**: 36 gen blocks · 43 decision ids · 46 wave54-final citations · 28 / 28 landing-order units. `PLAN.md` is `e3ffb639` before and after |
| I15 | watchlist | `RUN=wave54-final WATCH=…/watchlist.txt node …/wave52-plan/watchlist-check.mjs` | exit 0, **unmatched 0**. Output byte-identical to `watchlist-check.wave54-final.out.txt` |
| I16 | `geometry-gate --self-test` | `geometry-gate.py wave54-final --base {wave54-final, wave55-open} --self-test` | both exit 0, **HOLDS**: 0 violations, 0 self-check failures, residual 48 / 48 OK |
| I17 | `final55-analysis.sh` | `final55-analysis.gen.py --check` | exit 0: "**in sync** with expectations.json sha256 b773904f…". `bash -n` OK. Against HEAD the only diff is the two embedded sha256 lines (980542d0 → b773904f) |
| I18 | manifest ↔ tables | `land-units.sh --emit-manifest` in the throwaway (its `land-units.sh` = the shared `0ced008c`) | the emitted manifest is **byte-identical** to the shared `landing-manifest.json` `c933f68d`. vs HEAD: 1 path, `/deliveryContract/cutOn`, and `land-units.sh`'s diff vs HEAD is that one string |
| I19 | `land-units.sh --check` on the shared tree, all lanes | `bash …/land-units.sh --check`, with snapshots before and after: HEAD; index sha256; stash count; `status --porcelain=v2 --untracked-files=all`; `git diff HEAD`; the sha256 of every file under `tools/titan/results/wave55-*` | exit 0: **`SUMMARY  0 error(s), 0 warning(s) — landable (--check touched nothing: no checkout, no rm, no apply on the tree)`**. "manifest in sync"; "28 plan revert units (+ 2 contingency) agree with the table"; 27 revert dry-runs ok; both contingencies apply on top. **Snapshots identical before / after** |
| I20 | every L5 `patch:` sha256 equals its bytes | the 12 `*.patch` files in `wave55-counter-suffix-rows/` vs the note | 12 files, 12 `patch:` lines (lines 711–722, fix-r1's re-cut). **Each file's sha256 matches exactly one line.** The builder's section carries no `patch:` line; it points at the re-cut. The last `STATUS:` line is `COMPLETE` |
| I21 | the L5 unit sections name exactly the paths the patches touch | each "### <unit>" section of "Revert units — exactly the paths each unit commit holds", its `…/` tokens resolved by suffix against `diff --git` of the unit's patches (the mapping printed by `land-units.sh --check`) | 8 sections (7 units + U1-ios-LRM). Each names exactly its patches. **Every touched path is named, every name resolves to one touched path**, and no path is named that no patch touches. No `.patch` in the dir is left without a section |
| I22 | the L5 revert states cover every revertOrder chain | `fix-r1.revert-states.out.txt` `STATE … forward==reverse: yes` lines vs every prefix of every `revertOrder` in `expectations.json`, plus the contingency's landed chain `[U1-ios-LRM, U1-ios]` | **15 / 15 states present**, each forward == reverse and built. The note's `revertStatesCompiled:` lines (fix-r1, and reverify-r1 again) list the same states green |
| I23 | PLAN §10 | text read | item 2 is `scratchpad-exemptions.md`'s item, **pasted verbatim** (substring match, 2977 chars). Item 9 is the instrument outcome (rounds 3–7, round 7 PARTIAL, MF1–MF7, no round 8, the bounded claim). Item 6 is the L2 note (clip-3 android stays MED-HIGH: in the JSON it is still `MED-HIGH`). Item 7 is the L4 note (`headingStandsDown` KEPT). Items 3–5 and 8 cover L6 / L1 / L5 / `cutOn` |
| I24 | `land-units.selftest.sh` section D (not required: the regeneration's open finding) | `LU_SELFTEST_ONLY=D … land-units.selftest.sh <scratch>` from the shared tree | **6 passed, 7 failed**: D1, D2, D6, D7, D8, D9, D11. Z1: the shared tree is byte-identical; Z2: no throwaway left. The cause, from D6's `--check`: "ab-arms55.sh is tracked at HEAD, but its first patch CREATES it". The synthetic premise is stale; the real guard fires correctly. The regeneration's §6.1 is confirmed |

## 2. The structural diff, path by path (HEAD `980542d0` → `b773904f`)

**Hop 1, HEAD → fix-r6 (`8fbfc83a`).** This is the round-6 instrument fix, D40, which HEAD's commit message queues as "round 6
in progress"; round 7 re-executed it. All 15 paths are APPENDS of a " Round 6 (D40) …" sentence, or the new row:
- `/geometryGate/what`;
- `teeth` of L1 [0], L2 [1], L3 [0], L4 [0], L5 [0] and L6 [0];
- `stage1Decision` of L1, L2, L3, L4, L5 and L6;
- `/pictureCorrectTally/rule`;
- `+ /planDecisions[id=D40]`.

**Hop 2, fix-r6 → regen (`b773904f`).** 26 paths. Each maps to an item of `../wave55-gate/prelanding/censuses-and-deliveries.md`
§3, or to the lane's own request:

| path | queued item | asserted content |
|---|---|---|
| `+ generatedWith.inputs[…/wave55-android-paint-order/fix-r1/census.out.txt]`, `[…/reverify-r1/census.out.txt]` | §3.1 L6 (MUST): the stem is asserted from both censuses | the hashes are recorded; no other input hash changed |
| `+ generatedWith.inputs[…/wave55-hyphen-ios-linebox/census.out.txt]` | §3.2 L1 (SHOULD): 58 / 18 / 4 asserted from the census | — |
| `unionMayMoveCaptures.android`, `lanes.L6.mayMove.android` | §3.1 L6 | +1 stem, −0 |
| `lanes.L6.mayMoveRule`, `lanes.L6.stage1Decision` | §3.1 L6 | the two new pictures (I5) |
| `lanes.L6.carrierRule.U2`, `lanes.L6.revertUnits.U2.commit` | §3.1 L6 | `!config.overflow.shouldClip`, "29 clipping legacy bands in 26 documents", P3h / P3i |
| `lanes.L6.revertUnits.U1.commit` | §3.1 L6 | P5a-f |
| `lanes.L1.carrierRule.NB-ios`, `lanes.L1.revertUnits.NB-ios.commit` | §3.2 L1 | "58 text components", "18 documents", "4 SAME", all-prop-001 OUTSIDE; "5 SAME" gone |
| `lanes.L1.stage1Decision`, `lanes.L1.contingencyUnits.NB-ios-b.trigger` | §3.2 L1 (skeptic should-fix 1; HEAD's message: "L1's contingency routing") | I6 |
| `planDecisions[C5].resolution` | §3.2 L1 (PLAN §2 L1 R1 "four in-radius controls") | "3 in-radius controls remain" |
| `lanes.L4.ownership.owned[3]`, `lanes.L4.revertUnits.U2-android.commit` | §3.5 L4 (NIT) | "KEPT" |
| `lanes.L5.carrierRule.U3-ios` | §3.3 L5 (MUST), skeptic defect 1 | "6 glyphs in 4 rows" |
| `lanes.L5.predictions[0].{to, kind, confidence, geometry}` | §3.3 L5, R5 / fix-r1 (a) | MED; "rows 3-6 and 9-12"; "twin `ink:` read only" |
| `lanes.L5.geometryProbes[0].teeth` | §3.3 L5, R1–R3 | "Pre-landing regeneration (L5 fix-r1 R1-R3" |
| `lanes.L5.stage1Decision` | §3.3 L5, R4 / R5 | (i)–(iv) present; "all 6 gating keys OK" gone |
| `stayDegenerateEvenIfPass[6]` | §3.3 L5 ("the L5 counter-suffix ios rows"), U3 run-wide | "rows 3-6 and 9-12" |
| `+ planDecisions[id=D41]` | PLAN §10's rule (an appended decision row) | 42 → 43, last id D41 |

**Not in the JSON, as queued.**
- §3.4 L2: no L2 path changed; §10 item 6 records it.
- §4: the `cutOn` wording is a `land-units.sh` / manifest change only (I18).

## 3. L5: the lane's R1–R5 against the installed `[T]` key and rows (quotes from `wave55-counter-suffix-rows/_note.md`)

**R1 — the probe.**
- Lane: "Carve U3's six run boxes out of ios's base-identity zones and read them in `[T] ios`".
  - "`ROW9_OUT_IOS` x0-129 / x151-"
  - "`ZONES['ios']` skip rows 9–10 x130-150 and rows 11–12 x130-135"
  - "`u3_boxes_check`: each period box holds ONE dot at the ref's x ±1 / rows ±1; row 10's `2` box holds ONE ≥ 7-px-tall blob
    at the ref's left and right ±1 / rows ±1, filling ≤ 60 %"
  - "`t_ink` sums the five boxes with the digit window".
- Installed:
  - `ROW9_OUT_IOS = [(0, 208, 130, 232), (151, 208, None, 232)]`;
  - `ZONES['ios']` adds `(0, 232, 130, 256), (151, 232, None, 256), (0, 256, 130, 304), (136, 256, None, 304)`;
  - `u3_boxes_check` and `t_ink(…, U3_PERIODS + [U3_TWO])` are on the `[T] ios` path.
  - The gating map is unchanged: `"[T] counter-suffix ios": "U3-ios"`, expect `GEOMETRY OK`.
  - The code equals the lane's diff applied (I8).

**R2 — the standing replays.** Lane: "Re-grafting Android's five other boxes into each tag's ios picture makes the proposal
print exactly the installed probe's verdicts on the committed replays, for all 10 tags". Installed: I10, and
`regen.l5-proof` §2 shows "verdicts new-on-regrafted == old-on-committed: True" on all 10 tags (I13).

**R3 — the residual SPEC.** Lane: "gains `('rect', (130, 208, 136, 304))` and `('rect', (136, 232, 151, 256))`". Installed:
I9, residual 0 / 0 / 0 on the correct pictures (I13 §3), and §R in sync (I11).

**R4 — the precedence.** Lane:
- "(i) An `[H] ios` / `[T] ios` line whose only reason is `ink:` (twin) or `residual:` goes to the step-8 recorded look FIRST";
- "(ii) The LRM contingency (step 7) fires only on an `[H] ios` GEOMETRY reason … or on a look that shows the H-low shape";
- "(iii) A `[T] ios` geometry reason … reverts U3-ios";
- "(iv) A `[K] ios` FAIL whose differing pixels all lie in U3's boxes is U3's, never U1's".

Installed `stage1Decision`:
- "(i) an [H] ios / [T] ios line whose only reason is `ink:` (the twin) or `residual:` goes to the step-8 recorded look FIRST";
- "(ii) the LRM contingency fires only on an [H] ios GEOMETRY reason … or on a look that shows the H-low shape — never on a
  twin-only `ink:` or `residual:` line";
- "(iii) a [T] ios GEOMETRY reason (the digit's width / position / rows / fill, a U3 run-box glyph, a row-9 zone) reverts
  U3-ios";
- "(iv) a [K] ios FAIL whose differing pixels all lie in U3-ios's run boxes … is U3-ios's, never U1-ios's".

**R5 — generated text.** Lane: "'[H] / [T] ios twin-only WRONG at wave55-pre → step-8 look; OK from wave55-probe'", and the
header sentence corrected. Installed:
- `predictions[0].geometry`: "GEOMETRY OK from wave55-probe on; at wave55-pre [H] ios and [T] ios are predicted WRONG on the
  twin `ink:` read only … → the step-8 recorded look → TWIN_RUN_DEFAULT = 'wave55-pre' (L5 fix-r1 R5)";
- `confidence` "MED";
- `stage1Decision` no longer says "all 6 gating keys OK";
- the probe header says the round-2 sentence "was false".

PLAN §2 L5 renders the same row (I14 HOLDS).

## 4. What this pass re-derived, beyond re-running the regeneration's commands

- The two-hop generator chain I2, so the HEAD → regen diff is attributed hop by hop, not just compared to a record.
- The proposal-applied probe I8 and the rebuilt `40211eb0` I9, which tie the installed L5 bytes to the lane's own files.
- The decoded-pixel replay check I10.
- The unit-section ↔ patch-path resolution I21 and the revert-state coverage I22.
- The §RG reproduction I12(b), which proves the installed `probes-selfcheck.txt` is exactly fix-r6's record + a re-run anyone
  can repeat.
- The scratchpad grep. The session-scratchpad root appears in NONE of the files the regeneration wrote: the plan / gate
  outputs, `regen.*`, `ship-notes.md`, `regen.done.md`, Part II below.

## 5. Disclosures (none is a failure)

- **`probes-selfcheck.txt` is not a full re-run on `b773904f`.** Its header still cites `8fbfc83a` / HEAD `4e789976`, and §RG
  holds a NARROWED L5 re-run on `b773904f`. §8 step 2 reads "`probes-selfcheck.py` … in ONE throwaway". This is sound for
  three reasons:
  - every gate input of the other lanes is identical between fix-r6 and regen (I4);
  - their probe / teeth / gate files are sha-identical (§RG's list, re-checked by the script);
  - the narrowed run reproduces §0 / §R / §L1–§L7 line for line.
- **§C4 in a throwaway prints MISSING.** In a throwaway (symlinked `tools/titan/runs`), §C4 prints MISSING: this pass
  reproduced it, and so did the regeneration's dev run. The installed §RG was therefore run from the shared tree, and this
  pass did the same (I11). `ship-notes.md` §4 names the one-line `realpath` fix.
- **`toolkit-proofs.txt`** (modified since HEAD by fix-r6, not by the regeneration):
  - its header cites `8fbfc83a`;
  - it carries the session scratchpad path on 3 lines (lines 1, 140, 148; HEAD's version has 3 too).
  `scratchpad-exemptions.md` lists it as outside the scrub. §8 step 18's grep will list it.
- **D40's appends join without a sentence break.** In all six `stage1Decision` strings the text runs "… read at wave55-probe
  Round 6 (D40): …" (no period). This is cosmetic.

## 6. Commit coupling (for the orchestrator; not failures)

These files are untracked and must be committed with the regeneration:
- the generator reads three lane-dir census records (`generatedWith.inputs`): `wave55-hyphen-ios-linebox/census.out.txt` and
  `wave55-android-paint-order/{fix-r1,reverify-r1}/census.out.txt`;
- `residual-read.py` and fix-r6's files.

The 10 re-grafted replay PNGs pair with the probe and `residual-read.py`. A probe without its replays flips `[T]` ios on 7 of
10 tags (the lane's §R; `regen.l5-proof` §2).

## 7. Open (what still fails)

1. **Round 7's MF1–MF7.** 1973 acceptances > 2 px on 35 / 43 gating keys and 16 tally cells. These are open must-fixes under
   the bounded claim (PLAN §0, §10 item 9; `ship-notes.md`). No round 8 ran, by the orchestrator's rule.
2. **`land-units.selftest.sh` section D fails 7 / 133 at HEAD.** The cause is a stale premise: `ab-arms55.*` has been tracked
   since `611c9f93`. Reproduced here (I24). §8 step 6 needs a passing self-test on the current bytes, with its record citing
   the script's sha256, BEFORE the landing. So section D must be re-premised first. The landing guard itself is correct.
3. **The `U1-ios-LRM` trigger note is stale (should-fix).** `land-units.sh` ORDER row 30, and therefore `landing-manifest.json`'s
   `U1-ios-LRM` note, still says "after the stage-1 probe prints [H] counter-suffix ios WRONG (or H-low-shaped)". That
   contradicts `stage1Decision` precedence (ii): never on a twin-only `ink:` / `residual:` line. PLAN §10 item 5 says
   `stage1Decision` governs. Re-word it with the next table edit (`ship-notes.md` §3).

**Verdict:** the STATUS line that ends this file (after Part II) is this part's.

---

# Part II — the regeneration pass's own check record (verbatim)

# Wave 55 — the pre-landing regeneration: VERIFICATION record (`regen-verify.md`)

The checks the regeneration ran on the INSTALLED bytes. Companion to `regen.done.md`, which says what changed and why.

**Where these ran.**
- Every check below except R1, R2 and R10 ran on the shared tree `…/trusting-bohr-bd6fbf`, HEAD `7566fb03`, after the
  install. Each one was read-only there except the four atomic record writes it names.
- R1, R2 and R10 ran in throwaway worktrees named `<scratch>/wave55-regen-<role>`: `wave55-regen-dev` (the dev tree), `-selfcheck-wt`,
  `-readers-wt`, and `-lutest`, `-lutest-old`, `-lutest-new` for the land-units self-test. Each was created with
  `git worktree add --detach … HEAD`, with `tools/titan/runs` and `tools/wpt` symlinked to the shared tree. The
  `wave55-*` results dirs were rsynced in at the shared tree's bytes.
- Pinned interpreter `/opt/homebrew/bin/python3.12 -B`, every long run at `nice -n 19`.
- No device, gradle, xcodebuild, vite, emulator, simulator or test-all.

## The checks

| # | check | command (from the repo root) | result |
|---|---|---|---|
| V1 | the generator reproduces its outputs | `plan-build.py --out <scratch>` | exit 0; `expectations.json` (`b773904f`) and `watchlist.txt` (`4b1a9cd5`, unchanged) byte-identical; stdout = `plan-build.out.txt` |
| V2 | … in a throwaway worktree | the same, in `wave55-regen-selfcheck-wt`, with the shared plan / gate / lane dirs rsynced in | exit 0; both byte-identical |
| V3 | watchlist | `RUN=wave54-final WATCH=…/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs` | exit 0, **unmatched 0**; output byte-identical to `watchlist-check.wave54-final.out.txt` |
| V4 | the plan prose | `plan-prose.py --check` | **HOLDS**: 36 gen blocks · 43 decision ids · 46 wave54-final cell citations · 28 / 28 landing-order units |
| V4b | … can fail | `plan-prose.selfcheck.sh` (scratch copies) → `plan-prose.out.txt` | every mutation (18) turned the check red; G0 / G1 HOLD; the installed files are byte-identical before and after |
| V5 | the closing read-out | `../wave55-gate/final55-analysis.gen.py` then `--check` | regenerated, **in sync** with `b773904f`; `bash -n` parses. Its only diff is the embedded sha256 (lines 3 and 13) |
| V6 | the gate self-test, all lanes | `geometry-gate.py wave54-final --base <b> --self-test` for `wave54-final` and `wave55-open` | **HOLDS** on both bases: 0 violations, 0 self-check failures; residual 48 / 48 OK on 35 cells |
| V7 | the structural diff is exactly the intended set | `regen.expectations-diff.py <8fbfc83a> <installed> …` → `regen.expectations-diff.txt` | **YES**: 26 intended paths, 26 changed, 0 not intended, 0 intended-but-unchanged; planDecisions 42 → 43; top-level keys 37 → 37. "Before" is the start generator's dry run, byte-identical to the installed `8fbfc83a` (checked at the start) |
| V8 | the manifest changed only in `cutOn` | `land-units.sh --emit-manifest`; `regen.manifest-diff.py` → `regen.manifest-diff.txt` | **YES**, 1 difference: `/deliveryContract/cutOn` (`f6716e2f` → `c933f68d`) |
| V9 | the L5 probe / residual / replay change | `regen.l5-proof.py` → `regen.l5-proof.out.txt` | exit 0, **violations 0**. §1: base `[T]` ios WRONG on geometry, `[K]` OK. The correct-U3 picture: `[T]` ios `ink:` only (41 px) without a decided twin, OK with it; `[K]` OK. F1–F11 each rejected: F1–F7 and F9 by a `[T]` geometry reason, F8 / F11 by `[T]` `ink:`, F10 by `[K]`. F12 is the named floor. Every other key is identical old vs new. §2: all 10 re-grafted tags print the same 23 verdicts under the new probe as the committed tags under the old. §3: residual new 0 / 0 / 0 vs old 89 / 92 / 91 outside on the correct pictures; the U1-only pair is 0 under both |
| V10 | the re-graft is single-shot | `regen.replay-regraft.py --check` | exit 1, refused: every tag is already at its post bytes (the pre-sha guard) |
| V11 | the L5 standing set on the regenerated bytes | `probes-selfcheck.py --teeth-lanes L5 --out <scratch>` from the shared tree, appended as §RG by `regen.selfcheck-rg.py` | exit 0. §0, §R, §L1–§L4, §L6 and §L7 are line-for-line identical to the fix-r6 sections. §L5 differs only in 10 `[K]` ios zone-list lines, every verdict equal. §C4: 6 / 6 OK, identical. §T (L5): 34 cases, **0 violations**, 8 / 8 keys. §T3 HOLES 0. §T4 IN-ZONE 0, > 2-px holes 0. §T5 MUST-FIX 0, floor 10 |
| V12 | the landing check, all lanes | `bash tools/titan/results/wave55-plan/land-units.sh --check` | exit 0 — **`SUMMARY  0 error(s), 0 warning(s) — landable (--check touched nothing: no checkout, no rm, no apply on the tree)`** |
| V13 | git state | `git rev-parse`, `git status`, the index, `git stash list` | HEAD `7566fb03` (unchanged). Index sha256 `90df4083…`, as the round-7 verifier recorded it. 0 status lines outside `tools/titan/results/wave55-*`. Stash empty |
| R1 | readers-selfcheck | `readers-selfcheck.sh <wave55-regen-readers-wt> <scratch>`, run in the dev tree on the final bytes → `readers-selfcheck.txt` (installed) | "every check behaved as required". Against the fix-r6 record, two lines differ: E1 "withdrawn 240" → "241" (the may-move set), and E4's scratch-path tail |
| R2 | readers-calibration | `readers-calibration.sh <wave55-regen-readers-wt> <scratch>`, same tree → `../wave55-gate/readers-calibration.txt` (installed) | "every calibration behaved as required". Identical to the fix-r6 record except the header (HEAD, expectations sha) |
| R3 | toolkit-proofs (not required; recorded) | `toolkit-proofs.sh` in the dev tree | Sections A, B and D are line-for-line identical to the installed record. C2 / C3a went red only because the dev tree is a DETACHED worktree (`git symbolic-ref HEAD` fails, so the scratch clone is never made). C needs a branch checkout; its instruments' bytes are unchanged. **The installed `toolkit-proofs.txt` was left as is**: its header still cites `8fbfc83a` |
| R10 | land-units self-test (not required; a finding) | `land-units.selftest.sh <scratch>`, run three ways: from the shared tree (new script); from the dev tree with the OLD `land-units.sh` 817c9677 as a control; from the dev tree with the new script | **126 passed, 7 failed in all three runs.** The two dev-tree runs (old vs new script) print IDENTICAL PASS / FAIL lists, so the `cutOn` edit changes no behaviour. Every failure is in section D (L7's optional units and the records commit's force-adds). D's premise, that U3's planning files are UNTRACKED, no longer holds at HEAD: `ab-arms55.sh` is tracked since `611c9f93`. In the dev tree the six gitignored wave-54 `out/` files are also absent. The installed record (640276a0, 133 / 0) was left as is. **§8 step 6 needs section D re-premised before the landing** (`regen.done.md` "Open") |

## Records written to the shared tree (each atomically: a temp beside it, then `mv` / `os.replace`)

- The batch install (`<scratch>/regen/install.py`) wrote 22 changed and 8 new files. It refused to run unless every file it
  replaced still had the sha256 this pass started from, and every new file was absent; it found none changed.
- After the install, four more records were written:
  - `regen.l5-proof.out.txt` and `regen.expectations-diff.txt`, from V9 / V7;
  - `probes-selfcheck.txt` gained §RG by `regen.selfcheck-rg.py`, which refuses a second append and asserts the identity
    claims above;
  - then this file and `regen.done.md`.
- `grep` for a scratchpad path over every file this pass wrote returns nothing.

STATUS: COMPLETE — the regenerated plan verifies on the installed bytes. The structural diff is exactly the 26 intended paths.
The generator reproduces its outputs in place and in a throwaway. The watchlist is unmatched 0, `plan-prose --check` HOLDS, and
`final55-analysis.sh` is in sync. The self-test HOLDS on both bases, and the L5 standing set holds (0 violations, 0 must-fix).
The readers and calibrations behave as required. `land-units.sh --check` reads "SUMMARY 0 error(s), 0 warning(s) — landable".
One finding is outside the required set: `land-units.selftest.sh` section D fails 7 / 133 at HEAD with the old and the new
script alike.


---

# Verdict of Part I (the independent verification)

STATUS: COMPLETE — every requested check re-executes and holds on the installed bytes:
- the generator reproduces `expectations.json` / `watchlist.txt` / `plan-build.out.txt` byte for byte in a throwaway;
- HEAD → regen is exactly the 37 queued paths (15 D40 appends + 26 D41 items, 4 shared), 0 others, and nothing structural moved;
- L6 carries the stem and both wordings; L1 routes the R1 signature to `--contingency L1:NB-ios-b`;
- L5's probe / SPEC / replays / rows are R1–R5, and the probe is OK on the fixed picture and WRONG on every standing fake;
- `probes-selfcheck.txt` and `regen.l5-proof.out.txt` re-run byte-identical;
- `plan-prose --check` HOLDS, the watchlist is unmatched 0, `geometry-gate --self-test` HOLDS on both bases, and
  `final55-analysis.sh` is in sync;
- the manifest equals its tables, and `land-units.sh --check` reads 0 errors / 0 warnings with the shared tree byte-identical
  before and after;
- the L5 patch hashes, unit paths and revert states all check;
- PLAN §10 carries the exemption, the instrument outcome and the L2 / L4 notes.

Still failing (§7): round 7's MF1–MF7 (bounded, open), `land-units.selftest.sh` section D (7 / 133; blocks §8 step 6), and the
stale row-30 LRM trigger note.
