# Wave 55 — plan-skeptic round 1: FIX pass (`fix-r1`)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0` (unchanged; no git state change: no commit,
checkout, stash or reset). Edits only under `tools/titan/results/wave55-plan/` and `tools/titan/results/wave55-gate/`. No runtime,
harness, converter, `tools/titan/*.{sh,mjs}` or `tools/visual/` file touched. No gradle / xcodebuild / vite / puppeteer / emulator /
simulator / adb / test-all / smoke. Every state-changing git command was `git worktree add --detach <scratch>/… HEAD` or its
`git worktree remove` (the throwaways `wt-fix`, `wt-fix2`, the land-units self-test's `wt-E` / `wt-F` / `wt-F2`, the
process-holes repro's `wt-status`); `git worktree list` holds no scratch tree at the end. `wave55-open` was never read (every proof
reads `wave54-final`, which it reproduced byte-for-byte).

**Result.** `expectations.json` regenerated (sha256 `cd3936d0…` → `a1fdca71…`; the structural diff is `fix-r1.expectations-diff.txt`,
81 differences, every one a fix below; `watchlist.txt` byte-identical, unmatched 0). `plan-build.py --out` reproduces
`expectations.json`, `watchlist.txt` and `plan-build.out.txt` byte-for-byte. `plan-prose.py --check` HOLDS (36 gen blocks, 30
decision ids). Re-run on the final sha256, each in a throwaway worktree where needed: `probes-selfcheck.txt` (self-test HOLDS; §T
84 cases, 0 violations, 43 / 43 gating keys reject a plausible fake), `readers-selfcheck.txt` (every check as required, A–E),
`../wave55-gate/readers-calibration.txt` (K1–K12, J1–J5, F0–F3 as required), `plan-prose.out.txt` (every mutation red, M16 / M17
new), `land-units.selftest.r1-EF.out.txt` (E0–E12, F1–F6: 25 / 25), `../wave55-gate/ab-arms55.against-proof.out.txt` (P1–P5).

## generator-truth

| id | action | re-check |
|---|---|---|
| **M1** hyphens-vertical-001 ios moved by NB-ios | Option (a), D22. Brief §4 amendment (`hyphen-ios-linebox.md`): `usedLineHeight` gains `horizontalWritingMode` (seam passes PlaceholderLabel's stored flag, `:4868` / `:3220`); pin **P8** on `__2-320` verbatim → nil, mutation **M8** drop the guard; the Catalyst pin logs `lineCountIsExact` / `renderedLineCount` for the 5 SAME controls. Census: `hyphen-ios-linebox.census.mjs` derives a vertical run as declined (re-run: `.census.out.txt` line 58 corrected, `.census.json` byte-identical: still a SAME control and a stage-1 byte-identity row). `plan-build.py`: carrierRule, NB-ios commit text; PLAN §2 L1 (bullet + pin table) | census diff: one line; JSON `cmp` identical; `expectations.json` NB-ios carrierRule / commit restated |
| **M2** L6 may-move misses the 3-dp medium default | D21. New `android-paint-order.maymove-medium.py` mirrors `BorderSideExtractor` (logical sides, `BorderWidth` / `BorderStyle` shorthands, width keywords, `applyMediumDefault`): it reproduces the scan's **252** under the explicit rule (asserted by `plan-build.py`) and adds **26** documents — the skeptic's 24 plus `box-decoration-break-clone-001 / -002` (logical `BorderBlock*` sides, which neither the brief nor the skeptic modelled). Crossing excess of all 26: no carrier. `plan-build.py` adds them by rule; brief amendment (1438 explicit → 1973 Compose-banded; legacy 1963); D21 in §9 | `android-paint-order.maymove-medium.out.txt`; may-move android 214 → **240**; complement and R5 band unchanged (3352); `control-check` on the skeptic's "leak" fixture (bidi-lines-002 android) now reads may-move (`fix-r1.control-check-cases.out.txt`); K2 79 leaks (was 81: two wave-54 movers are now may-move) |
| **S1** drop-NB-ios "4 cells" | expect = exactly 6 cells (hyphenate-character ×4 +0.017 / +0.016 / +0.019 / +0.027, dotted ×2 +0.004 / +0.005; boundary-shaping ≤ 0.0002 unlisted; hyphens-vertical-001 identical by D22); NB-ios-b case named | `abArms[0].expect` |
| **S2** drop-L6-U2 cannot see clip-rect | `filter-effects` added to the arm's sections (clip-rect stays; basic-blur's D2 move reported); may-move outside the sections attributed by `mayMoveRule` | `abArms[2].sections` 6 → 7 |
| **S3** two `drop-U2` arms, no per-arm against | arms `drop-NB-ios` / `drop-L4-U2` / `drop-L6-U2` with `units`, `drops`, `against` (wave55-pre for L4); `abRead.cmd` on `<arm.against>`; `plan-build.py` asserts unique names | `plan-build.out.txt` "A/B arms" line |
| **S4** stale snapshot reader | `corpusSnapshotSource` / `closingGateFiles` name `wave55-gate/make-snapshot55.mjs` | expectations diff |
| N1 | `mayMoveRule` sentence: cells are must-not-move cells of the complement OR a named list | done |
| N2 | `drops()` puts a landed contingency (and its `-S1` commits) before the unit | done (`--contingency` dry run: NB-ios revertOrder [NB-ios-b, NB-ios]) |
| N3 | L4 carrierRule names s-11-1-1b-005, **-006** and anchor-center-safe-rtl | done |
| N4 | **kept**: counter-suffix stays in both `pictureCorrectTally.full` and `stayDegenerateEvenIfPass`; the retirement condition already lives in `lanes.L5-counter-suffix-rows.degenerateRetirement` (the honesty list entry points at it). Queued as a naming nit | — |

## instrument-teeth

| id | action | re-check |
|---|---|---|
| **M1** L2 bbox-only rule | WAVE-55 COPY `compose-wpt-content-box-cb.geometry.py` (+ green-mass reason after the bbox reason: \|green − ref\| ≤ max(40, 0.5 %)); `plan-build.py` points `lanes.L2…geometryProbes[0].cmd` at it; contains / report strings unchanged (bbox reason first) | hollow 396 / corners2 2 / stripe30 7000 / gut-cross 957 / square-3-corners 19846 → WRONG; wave54-probe U1 picture OK; web / iOS controls OK |
| **M2** cbb clip-3 one corner | four 20×20 corners ±8 (ref [251, 250, 252, 252]) + the mass | square-3-corners [400, 400, 400, 254], gut-cross [0, 0, 0, 254] → WRONG; replay [254 ×4] OK; F3 / F4 / F6 still WRONG |
| **M3** L3 keys | dir-style rows: ink-column profile xcorr (peak ≥ 0.75 at 0 ±1); bidi-tab: band mass ±20 % + left edge ±1; stack: 1-px ring vs web ±4 (ref row reads cells only); `[S]` dyn: magenta count ±5; `[W]` dyn: full bbox vs web ±1 + 100 ±5; vrl-rtl twins included | every skeptic fake WRONG (gut 0.19, mirror 0.77@+5 / 0.68@−2, ±2/±3x at shift ±2/±3; bidi gut / rtl±; grown / slab / dup rings 21 / 21 / 10; hollow 36; ±3y, wider+5); U1U2 replay OK on all 9 android rows; U1 / U2 / PLAIN arms as before |
| **M4** L4 `[M]` blind | `[M]` face-present: band LIST vs ref (tops / bottoms ±1, left ±3, line-1 right ±12, same count); today's face: every band = wave54-final + margin ±1, x ±1 (`BASE_BANDS` from wave54-final). **Deviations, with reason:** line-1 right ±12 (not ±3): web's own face measures Δ8 on 005 / 014 line 1, so ±3 would fail the web controls and any device font advance; 005 / 006 **android** `[M]` read band 1 only (`NO_WRAP`): Compose does not wrap the unbounded abspos heading (arm D, R2) — the full list would revert U1-android for another mechanism's wrap, which the `[F]` report rows read | arms: T / R / X2 / B / C4 WRONG on every native `[M]`; U1 OK on `[M]`; C OK on all; D OK except `[F]` 005 / 006 android (report). Skeptic fakes: +3y, −2y, +12x, line1-truncated → WRONG everywhere; line2-removed → WRONG except `[M]` 005 / 006 android (by design) and the no-ops |
| **M5** L5 blob-only | `[I]` ≥ 2 marker blobs ending at ref ±2 **and** (beyond the skeptic's fix, which left I-marker-block accepted) the ideograph's fill ≤ ref + 0.25 (二: ref 0.19, Android 0.11, block 1.0); `[C]` ideograph width ±2; `[T]` digit rows ±1 + fill ≤ 0.6; `[H]` letter fill ≤ 0.6; `[K]` natives + box x64-389 × y64-159 | I-comma-removed, I-marker-block, C-ideograph-gutted, T-1-bar7, T-1-down3, H-letter-block → their key WRONG; H-item-text-gone → `[K]` WRONG; ok-all OK; wave54-final controls OK |
| **M6** L6 `[X]` / `[PO]` | `[X]` vs the BASE capture: fill bbox ±1, fill ≥ 0.98 × base, band ≥ 0.9 × min(base, ref); `[PO]` navbar whole blue (+ overlap red − 416) ≥ 0.95 × 866 | border-erased / child±3 / shrunk / scrambled WRONG on all six; navbar-sides-erased (320) WRONG; replays OK except safe-002 (its known 50 crossings) |
| **M7** L1 centroid-only | `glyphs:` base-line identity (each line's crop = the base capture's same line translated by whole rows, same count, correlation ≥ 0.97); cmd gains `--base <base>`; `reasonToUnit` names it | text+3x / −2x, g2l1+3x, hyph-removed (003 / 004), lines swapped, right-half-cut → WRONG; Rm / Rw / R0 OK (1.000); R1 still `anchor:` (+ drift), Rd drift |
| **M8** tally over-claims | grid `[X]` ×4 → `pictureCorrectTally.paintOrderOnly` (never counted; rule text); entries 1–2 + `hyphenate-character.geometry.py hyphenate-character-00{1,3} ios`; `[K]` zone (M5). D23 | tally 22 → **18 in full + 4 paint order only**; PLAN §1 rendered (`gen:tally`, `gen:tally-by-lane`) |
| **M9** no plausible-wrong fakes | `probes-teeth.py` (the fam builders ported: target ink removed, ±2/±3 px, a segment shortened, a count-preserving rearrangement; fake runs under `--scratch`; the REAL gate on the generated JSON) wired into `probes-selfcheck.py` §T; exit 1 on any accepted fake / rejected fixed picture / uncovered gating key. Its ability to fail: `readers-selfcheck.txt` E5 (the pre-round-1 L2 rule → 21 violations, exit 1) | `probes-selfcheck.txt` §T: 84 cases, 0 violations, 43 / 43; the skeptic's all-wrong combo now exits **1 in all six lanes** (`fix-r1.combo.out.txt`; L4's 3 PASS = 011 ×2 fixed pictures + `[M]` 005 android line2-removed, by design) |
| **S1** control-check holds on missing data | iterates PRE and POST files: a capture absent after / before → counted LEAK; a pre per-test IR absent after → WIRE LEAK; a section present in one run only / a listed section absent / a listed section comparing 0 captures → exit 2 (refused) | K7–K12 (typo, absent section, vanished capture, vanished document, no `--sections` on a partial run, the mutation without the pre side HOLDS again); the skeptic's cases: `fix-r1.control-check-cases.out.txt` (css-txt / no-css-text → 2; no-ios-dir / capture-gone / wire-doc-gone → 1) |
| **S2** geometry-gate vacuous scopes | exit 2 on an unknown `--sections` / `--lanes` entry and on 0 keys in scope; usage exit 2 | `fix-r1.reader-refusals.out.txt`; B1–B6 unchanged |
| **S3** probe-readout `--lanes L9` | exit 2 on a `--lanes` prefix of no lane | `fix-r1.reader-refusals.out.txt` |
| **S4** `[F]` tops ±3 | part of M4: `[F]` gains the band rule, tops / bottoms ±1; PLAN §2 L4 says a device need for ±2 is recorded and decided in §10 | +3y / −2y WRONG on `[F]` 005 / 006 ios |
| N1, N2 | **queued**: `adjudicate.mjs` / `probe-readout.mjs` `score.cur` checks (no live instance; would perturb the synthetic calibration records) | — |
| N3 | **partly**: `abRead.armsFile` says one arms file per `against`; the runner's own refusal of a mixed file is queued (S5's tree check already refuses an against-run of another tree) | — |

## process-holes

| id | action | re-check |
|---|---|---|
| **M1** SeamReachabilityTest one insertion point | pinned anchors (L4 :159, L5 :167, L3 :182), each cut on HEAD — in `plan-build.py` commit / ownership strings, C10's resolution, PLAN §2 L3 / L4, §3 table, §4, §9 C10, `land-units.sh` header + `deliveryContract.cutOn` + C10 constraint text; new self-test section **F** | F1–F6: PINNED → `--check` clean, five revert dry-runs OK; END → "the series STOPS" (rc 1); repro E5(c) unchanged-green |
| **M2** probes read no hash | `stage1Probe.hashRead`, `probeRun.hashRead`, pre-registered `stage1Probe.reprobes` (`wave55-pre-r1` / `-r2`) each with `hashRead`; PLAN §0 hash rule, §7 steps 1 / re-probes / stage 2, §8 12.2 (+ N7 Android witness) | repro E11 lists the four hash reads in PLAN |
| **M3** a killed lane lands | `land-units.sh`: non-COMPLETE / missing STATUS → error; no `TREES:` → error; every lane-dir patch needs `patch: <name> sha256 <64 hex>` matching; a note line naming no file → error; a `*.patch` the table does not know → error. Resume rule in §8 step 3; `beforeEveryDeviceRun` adds every registered `wave55-` worktree; TREES-first in §0. Self-test `note()` now writes TREES / patch lines (A18 / D6 re-note added); new section **E** | E0–E12 (no STATUS / PARTIAL / no TREES / re-cut patch / stale patch → rc 1; restored → 0; read-only); repro E6: 3 / 3 cases now exit 1 (were "landable") |
| **M4** may-move not withdrawn | `probe_decisions()` empties `lanes.L6.mayMove` on an L6 U2 / U1 revert (`mayMoveWithdrawn`); `unionMayMoveCaptures` recomputed after it | repro E4: 240 → **0**; readers-selfcheck E1 (withdrawn 240, arm withdrawn), E2 (nudge → LEAK), E3 (generator mutation without the withdrawal → may-move, HOLDS) |
| **S1** D vs U2o | U2o-ios `[D, U2i-ios, U2o-ios]`, U2o-android `[D, U2i-android, U2o-android]` (+ `revertOrderWhy`); land-units rows 22 / 24; `--emit-manifest`; rule 6 text; D27 | plan-prose check 6 (manifest == JSON revert orders) HOLDS |
| **S2** re-probe records overwrite | `PROBE_READOUT` writes every read under `wave55-gate/<stage>/<full run id>/` (`records` key) | repro E7: every read of `wave55-pre-r1` writes under `stage1/wave55-pre-r1/` |
| **S3** unnamed regeneration inputs | committed `probe-decisions/{reverted,restate,contingency,holds}.json`, read by default; append-only assertion against the installed JSON; `generatedWith {script, argv (minus --out), inputs sha256}` + report line 1; D25 | readers-selfcheck E4 (a newer-only decision file → "decision row dropped", exit 1). The repro's E8 still prints its own two lines because it installs nothing and greps for a key named `arg` / `input` (`generatedWith` holds them) — the installed-record case is E4 |
| **S4** one sweep | §8 8½ (post-S1 sweep → `sweep-landed.post-s1.txt`) and 11½ (gate-tree sweep + restamp + doc-staleness → `sweep-landed.txt`) | PLAN §8 |
| **S5** L4 arm on another tree | §7 step 5: the L4 arm runs BEFORE any stage-1 revert / swap commit; `ab-arms55.sh` refuses an `--against` run whose hash block names a tree that differs from HEAD in code (`runtimes apps converter tools/titan`, results / *.md excluded), a "+ uncommitted" block, or a run dir with no block | `ab-arms55.against-proof.out.txt` P1–P5 (dry-run only, in the throwaway); the toolkit self-test's cases are untouched by design (no run dir, no block → note) but it was NOT re-run here (it `git init`s) |
| **S6** dirty tracked records before arms | §7 step 5 and §8 12.5: commit the gate records with named paths before each arm window; `baseline-stats.json` diff recorded, restored only when timestamp-only | PLAN |
| **S7** prose numbers drift | gen blocks `tally-by-lane` and `stage1-required` (§1, §7) replace the hand lists; every other count cited; calibration count computed AFTER the probe decisions; plan-prose `--check` rule **7** (a STRAY list of regenerable phrases) + self-check M16 / M17 | repro E9: 8 rows ↔ "the 8 gating flip rows"; E10: the regenerated plan HOLDS with no stale hand count; M16 / M17 red |
| **S8** arms do not say what they drop | `drops` generated (contingency, `-S1*`, unit; child first); reverted units narrow an arm or move it to `abArmsWithdrawn`; §8 step 15 builds the arms file from `drops` | repro E4: drop-L6-U2 withdrawn; readers-selfcheck E1 |
| **S9** S1 / fix model, dirty device runs | `beforeEveryDeviceRun` first clause refuses a tree that differs from HEAD in code; §8 step 8: S1 mutates only in a throwaway; fix lanes deliver `fix-S1-<U>.patch`, landed with `git apply --index --check` / `--index` + a named-path commit | PLAN §0 / §8 |
| **S10** lane worktrees unprovisioned | §0 D18 bullet: `local.properties` copy, `node_modules` link, TREES first | PLAN |
| **S11** final54 copied at gate time | `wave55-gate/final55-analysis.gen.py` → `final55-analysis.sh` (every command an expectations string through a round-tripping substitution, terms from the rows, last line = verdict, `--check` on drift) | calibration F0 (in sync), F1 identity → FAILS (R6 1, adjudicate 1), F2 all-met → HOLDS, F3 drifted copy → DRIFT |
| **S12** revert states not compiled | §0 bullet + `deliveryContract.note` (`revertStatesCompiled:`), §8 step 8 S1 re-run, rule 6 text | PLAN / manifest |
| **S13** [W-L7a] tool copy | §8 step 5: run from a third throwaway at HEAD + `unit-U2.patch`; record both patches' sha256; re-run on any change | PLAN |
| **S14** owed regeneration | §8 step 2's first sub-step; executed in this pass (regenerate → fill / check → final55 gen / check → probes-selfcheck, readers-selfcheck, readers-calibration on the new sha256) | headers of the three records cite `a1fdca71…` |
| N1 / N2 / N3 / N4 / N6 / N7 | step 6 copies the lane dirs into the throwaway; step 18 carries the scripted-edit lesson; §7 step 0 `ANDROID_SERIAL`; risk 8 (the net first meets L6 U2 at the gate); TREES lines exempt from the step-18 grep; the Android witness in 12.2 | PLAN |
| N5 | **queued**: `ab-arms55.sh` writes every read-out under `final/`; the L4 arm's is a stage-1 record by content (PLAN §7 step 5 says so) | — |

## Files

- **Changed (tracked, wave55-plan):** the six lane probes (`android-paint-order`, `cbb-android`, `counter-suffix-rows`,
  `hyphen-ios-linebox`, `positioned-parent-slot`, `ua-heading-margin` `.geometry.py`), `hyphen-ios-linebox.census.{mjs,out.txt}`,
  the briefs `hyphen-ios-linebox.md` (§4 amendment) and `android-paint-order.md` (§5 amendment).
- **Changed (untracked):** `PLAN.md`, `plan-build.py` (+ regenerated `expectations.json`, `plan-build.out.txt`; `watchlist.txt`
  identical), `plan-prose.py`, `plan-prose.selfcheck.sh` (+ `.out.txt`), `probes-selfcheck.py` (+ `.txt`), `readers-selfcheck.sh`
  (+ `.txt`), `geometry-gate.py`, `probe-readout.mjs`, `land-units.sh` (+ regenerated `landing-manifest.json`),
  `land-units.selftest.sh`; `../wave55-gate/{control-check.mjs, ab-arms55.sh, readers-calibration.sh (+ .txt), final55-analysis.sh}`.
- **New:** `compose-wpt-content-box-cb.geometry.py` (wave-55 copy), `probes-teeth.py`, `android-paint-order.maymove-medium.{py,out.txt}`,
  `probe-decisions/{reverted,restate,contingency,holds}.json`, `land-units.selftest.r1-EF.out.txt`, `fix-r1.{combo,control-check-cases,
  reader-refusals,process-holes.rerun,expectations-diff}.out.txt|.txt`, this file; `../wave55-gate/{final55-analysis.gen.py,
  ab-arms55.against-proof.{sh,out.txt}}`.
- Records are redacted of scratch paths (`readers-*` now redact in-stream); `../wave55-gate/toolkit-proofs.txt` and
  `ab-arms55.selftest.out.txt` (the toolkit stage's) still carry scratch paths — queued for §8 step 18.

## Not run here (and why), queued

- `land-units.selftest.sh` sections A–D and `ab-arms55.selftest.sh`: they COMMIT in throwaways / `git init` — outside this pass's one
  permitted git command. The selftest's `note()` now writes TREES / patch lines and re-notes before A18 / D6, so A–D should hold
  unchanged; the orchestrator re-runs both before the landing (§8 step 6).
- The briefs' own recorded probe outputs (`*.geometry.*.out.txt`, `*.replays.txt`) predate the re-ruling; `probes-selfcheck.txt`
  is the record of the round-1 rules.
- `probes-teeth.py` is 434 lines (the per-lane builders): split per lane when next touched (nit).
- Nits queued: GT N4 naming; IT N1 / N2 (`score.cur` guards), N3 (mixed-against arms file refusal); PH N5 (arm read-out path).

STATUS: COMPLETE — every must-fix and should-fix of the three round-1 reports is fixed (two instrument-teeth fixes deliberately
deviate, with the measured reason: L4 `[M]` reads band 1 only on the Compose no-wrap rows and line 1's right edge at ±12; L5 `[I]`
adds a glyph-fill read the proposed fix lacked; GT-M2 finds 26 documents, not 24); every regeneration is generated, filled, checked
and re-calibrated on the final sha256 `a1fdca71…`; land-units sections A–D and the ab-arms `git init` self-test are left to the
orchestrator for the reason above.
