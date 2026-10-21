# Wave 55 — the pre-landing REGENERATION (`regen`; PLAN §8 step 4 ¶2)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `7566fb03`, the plan `611c9f93` plus its checkpoints.

**Git state.** HEAD, the index (`90df4083…`), the stash and every path outside `tools/titan/results/wave55-*` are unchanged.
There was no commit, checkout, stash or reset. The orchestrator commits.

**Order of work.**
- This pass waited for the round-7 verifier before any edit. It started at 10:33 and found
  `plan-skeptic-round-7.verify.done.md` with STATUS at 10:51.
- Every edit was built in the throwaway `<scratch>/wave55-regen-dev` from the shared tree's START bytes plus scripted, asserted
  replacements (`<scratch>/regen/edit_*.py`). The whole chain was re-run there, and the result was installed in one guarded
  batch: a temp file beside each target, then `os.replace`. The batch refused to run unless every replaced file still had its
  start sha256.
- Checks are in `regen-verify.md`.

**Inputs read.**
- `../wave55-gate/prelanding/{citations,censuses-and-deliveries,scratchpad-exemptions}.md`.
- The seven lane notes, all with last `STATUS: COMPLETE`. L6 was read through its builder, fix-r1 and fix-r2 sections, its
  `fix-r1/` and `reverify-r1/` censuses and `skeptic.md`. L5 was read through its fix-r1 requests R1–R5 with every evidence
  file they cite, and its reverify-r1. Also the L1 census and skeptic, the L2 / L3 / L4 notes, and the L3 skeptic.
- `fix-r6.done.md`, `plan-skeptic-round-7.verify.md`, `.done.md` and `.verify.d/`.

## 1. What the regeneration changed, item by item

| item | prio | what | where |
|---|---|---|---|
| L6 census growth | MUST | `display-contents-dynamic-pseudo-insertion-001` android joins `mayMove` by an ASSERTED rule. Both lane censuses name exactly this growth stem on a pseudo exposure (`fix-r1/census.out.txt`, `reverify-r1/census.out.txt`), and its run-of-record IR is a kept `display: contents` box with pseudos and a 4-side band. 240 → 241, `unionMayMoveCaptures` likewise. The cell is unscored, so the complement and the watchlist are unchanged | `plan-build.py` L6 (`l6_contents_pseudo`) |
| L6 rule wordings | MUST | `mayMoveRule` and `stage1Decision` accept three pictures: a band now under its child; "the band now inside the element's opacity group" (anchor-center-overflow-003; basic-blur, ring-fenced); "a UA-widget replica now over the band" (flex-gap-decorations-023, Δ +0.0007, DEGENERATE) | `plan-build.py` L6 |
| L6 U2 / U1 text | MUST | `carrierRule.U2` and `revertUnits.U2.commit` carry U2's clip condition: `!config.overflow.shouldClip`; 29 clipping bands in 26 documents keep the base chain; P3h / P3i. `revertUnits.U1.commit` adds P5a–f | `plan-build.py` L6 |
| L5 U3-ios run-wide `tnum` | MUST | R1: the probe takes the lane's executed proposal verbatim — 6 anchored edits parsed from `fix-r1.probe-proposal.py` with `ast`, the result equal to its `.diff.txt` — plus the R5 header correction. R2: the 10 standing replays are re-grafted from Android's base, 91 px each, 0 outside the boxes (`regen.replay-regraft.py`, pre-sha guarded). R3: `residual-read.py` declares the two U3 rects. R4 / R5: the counter-suffix ios row goes MED-HIGH → MED, with its `to` / `kind` / `geometry` texts; `carrierRule.U3-ios`; the honesty-list note "rows 3-6 and 9-12"; and `stage1Decision`'s precedence (i)–(iv) with the wave55-pre key predictions | `counter-suffix-rows.geometry.py`, `residual-read.py`, `counter-suffix-rows-replay/*/counter-suffix.ios.png`, `plan-build.py` L5 |
| L1 radius and R1 routing | SHOULD | `carrierRule.NB-ios` reads 58 / 18 / 4 SAME, asserted from the lane census (71 runs, 58 components, 18 documents; all-prop-001 outside the radius). The NB-ios commit text is updated. The NB-ios-b `trigger` and `stage1Decision` now say: the R1 signature (gating reason `anchor: group 1 line 1 offset 1` only + in-radius one-line SAME controls each one row low) → `--contingency L1:NB-ios-b`, never an NB-ios revert, and the `wave55-pre-r1` re-probe must show those controls byte-identical. C5's tripwire 4 → 3. `mustNotMove` is unchanged | `plan-build.py` L1, C5 |
| L2 clip-3 confidence | NIT | the plan's MED-HIGH stands (PLAN §10 item 6) | prose only |
| L3 `cutOn` | NIT | re-worded: no writer APPENDS at the end as it is at landing time; L3 cuts AT its pinned anchor, which is the list's last entry at HEAD. `landing-manifest.json` re-emitted; its only difference is that string; PLAN §4 re-filled | `land-units.sh`, `landing-manifest.json` |
| L4 `headingStandsDown` | NIT | the two generator strings say KEPT (rule 2b); PLAN §10 item 7; the BACKLOG deletion item is in `ship-notes.md` | `plan-build.py` L4 |
| D41 | — | the regeneration's own `planDecisions` row and its §9 hand row and intro bullet | `plan-build.py`, PLAN §9 |

**The structural diff of `expectations.json`** (`8fbfc83a` → `b773904f`; `regen.expectations-diff.txt`) is EXACTLY the 26
intended paths. Not one other value changed. The 26 are:
- 3 new `generatedWith.inputs` (the L1 and the two L6 census records);
- L1: carrierRule, NB-ios commit, NB-ios-b trigger, stage1Decision;
- L4: owned[3], U2-android commit;
- L5: carrierRule.U3-ios, prediction[0]'s to / kind / confidence / geometry, stage1Decision, the probe's teeth;
- L6: carrierRule.U2, mayMove, mayMoveRule, stage1Decision, the U1 / U2 commits;
- `unionMayMoveCaptures`, `stayDegenerateEvenIfPass[6]`, C5, + D41.

No carrier, unit, floor, gating flag, must-not-move cell or watch line changed.

**Inputs outside the plan dir.** The generator now READS three lane-dir records (`generatedWith.inputs`):
`wave55-hyphen-ios-linebox/census.out.txt`, `wave55-android-paint-order/fix-r1/census.out.txt` and
`wave55-android-paint-order/reverify-r1/census.out.txt`. The orchestrator commits them with this regeneration, or before it.
`land-units.sh`'s records commit still carries the rest of the lane dirs.

## 2. PLAN.md

- **Hand prose, scripted (`<scratch>/regen/edit_plan_md.py`, 13 asserted replacements):**
  - header item 5;
  - §0, the round-7 bounded claim (below);
  - §2 L1 (the logged SAME controls; R1's tripwire is three, and the R1 signature routes to NB-ios-b);
  - §2 L5 (U3's run-wide `tnum`; the LRM fallback only on a geometry reason; R3);
  - §7 stage 1 steps 3 and 7;
  - §9 (the D41 intro bullet, C5's "3 controls", the D41 row);
  - §10 items 2–9.
- Then `plan-prose.py --fill` and `--check` (HOLDS) and `plan-prose.selfcheck.sh` (18 / 18 RED).
- **§10 items.**
  - 2: the scratchpad-path exemption, pasted verbatim from `scratchpad-exemptions.md`;
  - 3: L6;
  - 4: L1;
  - 5: L5;
  - 6: L2;
  - 7: L4;
  - 8: L3 `cutOn`;
  - 9: the instrument rounds 3–7 and the bounded claim.

## 3. Round 7 and the bounded claim (no round 8)

`plan-skeptic-round-7.verify.md` ends **STATUS: PARTIAL**.
- fix-r6's own claims all re-execute, and the round-5 class is closed: the residual read FAILs all 5140 out-of-zone fakes of a
  non-class colour.
- The verifier's independent sweep still finds 1973 acceptances > 2 px, on 35 / 43 gating keys and 16 tally cells (MF1–MF7).

As instructed, **no further round was started.** The claim is BOUNDED per D31 in PLAN §0 and §10 item 9:
- every gating and tally key rejects every fake class in the standing set, and any paint outside its declared zone in a colour
  that is not the cell's declared class;
- it does NOT reject the seven NAMED classes, each recorded with its accepted-fake pixel span and its keys:

  | class | what passes | px span | keys |
  |---|---|---|---|
  | MF1 | faint in-zone ink, min ≥ 200 | 4–2558 | 24 |
  | MF2 | bidi blue outside the RTL bands | 4–144 | 1 |
  | MF3 | window-grown palette paint | 6–1008 | 4 |
  | MF4 | L4 face heading ±2–3 px in x | 3350–4888 | 10 |
  | MF5 | `[X]` child fill over the band | 4–138 | 5 |
  | MF6 | `[PO]` overlap revert + red elsewhere | 8–16 | 1 |
  | MF7 | stack square relocated | 200 | 1 |

- the 2-px floor is named as before (32 in round 7).

`ship-notes.md` lists the seven classes with the verifier's measured fix bases for the next wave, and states the claim the
closing PR may cite.

## 4. Regeneration chain (`regen-verify.md` has every command and number)

| check | result |
|---|---|
| `plan-build.py` | regenerated; `--out` byte-identical in place AND in a throwaway worktree |
| watchlist | unmatched 0; `watchlist.txt` byte-unchanged |
| `plan-prose` | `--fill` / `--check` HOLDS; selfcheck 18 / 18 red |
| `final55-analysis.sh` | regenerated, `--check` in sync |
| `geometry-gate --self-test` | HOLDS on wave54-final and wave55-open (residual 48 / 48) |
| `readers-selfcheck.sh` | as required (re-run on the final bytes) |
| `readers-calibration.sh` | as required (re-run on the final bytes) |
| L5 standing set (`probes-selfcheck.txt` §RG) | §T 34 / 0 violations, §T3 0, §T4 0, §T5 0 must-fix; every other lane's section line-identical |
| `regen.l5-proof` | violations 0 |
| `land-units.sh --emit-manifest` | manifest differs only in `cutOn` |
| `land-units.sh --check` (shared tree, all lanes) | **`SUMMARY  0 error(s), 0 warning(s) — landable (--check touched nothing: no checkout, no rm, no apply on the tree)`** |

## 5. Files (sha256, first 8)

**Changed.**

| file | before | after |
|---|---|---|
| `plan-build.py` | 4e3aced5 | ef4df5f8 |
| `expectations.json` | 8fbfc83a | b773904f |
| `plan-build.out.txt` | 687a9306 | 67f5dbbc |
| `PLAN.md` | 9eea4b89 | e3ffb639 |
| `plan-prose.out.txt` | c94104f1 | 1481af07 |
| `counter-suffix-rows.geometry.py` | bd2f001a | f032061a |
| `residual-read.py` | 40211eb0 | 1d500f2b |
| `land-units.sh` | 817c9677 | 0ced008c |
| `landing-manifest.json` | f6716e2f | c933f68d |
| `probes-selfcheck.txt` (+ §RG) | 808ceb16 | e5de2fa7 |
| `readers-selfcheck.txt` | 8d86acb1 | c4314825 |
| `../wave55-gate/final55-analysis.sh` | 3f66d5e6 | e194e28e |
| `../wave55-gate/readers-calibration.txt` | 10b13a97 | ba805256 |

The 10 replay pictures `counter-suffix-rows-replay/<tag>/counter-suffix.ios.png`:

| tag | before | after |
|---|---|---|
| ok-all | 3ff66c08 | 13acc9b9 |
| C-nocomma | 3ff66c08 | 13acc9b9 |
| I-wholeline | 3ff66c08 | 13acc9b9 |
| H-broken | 9f81eeef | 7301fd35 |
| C-shift8 | 9f81eeef | 7301fd35 |
| C-space-only | 9f81eeef | 7301fd35 |
| H-nodot | 7b2eb665 | 001a7081 |
| H-flush | 7e5e0c0f | 79478b79 |
| H-low | ff15bf19 | f7bfd2ca |
| K-leak | 8f7379ac | 6ffdbc85 |

**New.**

| file | sha256 |
|---|---|
| `regen.replay-regraft.py` | 2ddf5bf0 |
| `regen.replay-regraft.out.txt` | 6f400b9b |
| `regen.l5-proof.py` | defe072f |
| `regen.l5-proof.out.txt` | 2d0d60fb |
| `regen.expectations-diff.py` | af6b101c |
| `regen.expectations-diff.txt` | 1431d920 |
| `regen.manifest-diff.py` | 9818f07b |
| `regen.manifest-diff.txt` | 0247a646 |
| `regen.selfcheck-rg.py` | fc002dec |
| `ship-notes.md` | 73d3510e |
| `regen-verify.md` | — |
| `regen.done.md` | — |

**Unchanged on purpose.**
- `watchlist.txt` `4b1a9cd5`, and every other probe.
- `geometry-gate.py`, the `probes-teeth*.py` set, `fix-r6.zones.py`, `../wave55-gate/toolkit-proofs.txt`
  (`regen-verify.md` R3) and `land-units.selftest.out.txt` (R10).
- `plan-skeptic-round-5.fakes/round4-probes/counter-suffix-rows.geometry.py`: the round-4 bytes, history.

## 6. Open (for the orchestrator)

1. **`land-units.selftest.sh` section D fails 7 / 133 at HEAD `7566fb03`, with the old and the new `land-units.sh` alike.**
   The like-for-like runs give identical PASS / FAIL lists (`regen-verify.md` R10). The cause is section D's premise: L7 U3's
   planning files are UNTRACKED. That is false since `611c9f93` tracked `ab-arms55.sh`. §8 step 6 requires a passing
   self-test on the current bytes before the landing, so section D must be re-premised first; for example, simulate the
   untracked case in its throwaway. This pass did not edit the self-test: it was outside the request.
2. **Pre-registered texts the manifest still carries.** `land-units.sh` row 30's note ("[H] counter-suffix ios WRONG (or
   H-low-shaped)") predates L5's precedence (ii). `stage1Decision` governs. The note was left unchanged so that the manifest's
   only diff stays `cutOn` (`ship-notes.md` §3).
3. **Commit coupling.** Commit the three lane census records the generator now reads with this regeneration, or before it
   (§1). Commit the 10 re-grafted replay PNGs with the probe and `residual-read.py`: a probe without its replays flips
   `[T]` ios to `ink:` WRONG on 7 of the 10 committed tags, ok-all included (the lane's `fix-r1.probe-proposal.out.txt` §R;
   `regen.l5-proof.out.txt` §2 proves the pairing).
4. **The round-7 classes MF1–MF7 are open** under the bounded claim (§3, `ship-notes.md`). A picture-correct count is read
   under that bound.

STATUS: COMPLETE — every queued item was applied: L6 MUST, L5 MUST (R1–R5), L1 SHOULD, and the L2 / L3 / L4 NITs. The §10
items (2–9) are written, and round 7's PARTIAL is recorded as a bounded claim with MF1–MF7 named (no round 8). The structural
diff of `expectations.json` is exactly the 26 intended paths. The generator reproduces its outputs in place and in a throwaway;
the watchlist is unmatched 0; `plan-prose --check` HOLDS; `final55-analysis.sh` is in sync; the self-test HOLDS; the L5
standing set and `regen.l5-proof` hold with 0 violations; the readers and calibrations are as required. `land-units.sh --check`
over all lanes reads "SUMMARY  0 error(s), 0 warning(s) — landable". One finding is outside the request: the land-units
self-test's section D is stale at HEAD (§6.1).
