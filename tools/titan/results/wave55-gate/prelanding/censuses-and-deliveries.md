# wave 55 · pre-landing audit — CENSUSES AND DELIVERIES

Role: PLAN §8 step 4 ¶2. A lane hand-off that changes a carrier, a prediction or a unit regenerates `expectations.json`
before the landing. Auditor date: 2026-10-12.

## Where this ran

- **Read-only on the shared tree.** The only path written is this file. No git state was changed, and no gradle,
  xcodebuild, vite, device or test-all step ran.
- **Throwaway worktree.** `<scratchpad>/wave55-audit-censuses-deliveries`, created with `git worktree add --detach …
  611c9f93`, reset between runs, and removed at the end.
- **Patch snapshot.** The lane patches were applied from a byte copy of the lane dirs. Its sha256 values equal every
  lane's `patch:` lines (7 / 7 lanes, 41 patches).
- **HEAD moved during the audit.** The task named 611c9f93. The shared tree is now at **4e789976**, the orchestrator's
  "wave55-plan/gate checkpoint: instrument rounds 3–5" commit. That commit changes 136 files, every one under
  `tools/titan/results/`, and **0 paths outside it**. As a result:
  - every seam file is byte-identical at 611c9f93 and HEAD, so the seam headers' base is still right;
  - `landing-manifest.json` is unchanged (sha256 `f6716e2f…`);
  - `expectations.json` 611c9f93 → HEAD (`490dc5b9…` → `980542d0…`) differs per lane only in `geometryProbes[].teeth`
    and in the L4 / L5 `stage1Decision` text (round-4 D39). `captureCarriers`, `wireCarriers`, `revertUnits`,
    `predictions`, `mustNotMove` and `mayMove` are identical, so each lane's census comparison holds against either
    file. The comparisons below use HEAD's `expectations.json`.

## Lane status

| lane | last `STATUS:` | audited |
|---|---|---|
| L1 hyphen-ios-linebox | COMPLETE | yes |
| L2 cbb-android | COMPLETE | yes |
| L3 positioned-parent-slot | COMPLETE | yes |
| L4 ua-heading-margin | COMPLETE | yes |
| L5 counter-suffix-rows | **PARTIAL — fix-r1 in progress** | **PENDING** (informational checks only, on the builder's patch bytes) |
| L6 android-paint-order | COMPLETE (after fix-r1; reverify-r1 records present) | yes |
| L7 instruments | COMPLETE | yes |

## 1. Census and prediction deltas against `expectations.json` lanes[…]

In this table, "plan" means `expectations.json` at HEAD. The skeptic's census is in each lane's `skeptic.md`, plus
`reverify-r1/` for L6.

| lane | lane census vs plan | skeptic census vs plan | units | predictions | regeneration? |
|---|---|---|---|---|---|
| L1 | carriers iOS 14 / web 0 / Android 0 / wire 0: IDENTICAL. Radius SHRANK: all-prop-001 is outside it (`All: INITIAL` drops the LineHeight), 58 components in 18 documents, not the plan's "60 in 19, 5 SAME" | superset census: 14 IDENTICAL; confirms the shrink | NB-ios (+ NB-ios-b contingency) = plan | 14 rows, unchanged (note: "the plan's, unchanged") | **YES (should)**: carrierRule shrink text, and the skeptic's stage1Decision should-fix 1 |
| L2 | Android 14 / U1 14 / U2 1 / wire 0: IDENTICAL; census A 14 documents, B 122 / 2 | static + device diff of wave54-probe: 13 + 006 moved, inside the 14: IDENTICAL | U1, U2 (group cbb) = plan | 14 rows; the clip-3 confidence is restated MED, against the plan's MED-HIGH | nit: the confidence field only |
| L3 | U1 3 / U2 7 / union 9 / wire 0: IDENTICAL | JVM + Python, with the harness root rewrite: IDENTICAL | U1, U2 = plan | 9 rows identical (values and confidences) | no |
| L4 | iOS 4 / Android 4, U1-<p> 4, U2-<p> 3, wire 0: IDENTICAL on both natives | static replay: IDENTICAL | 4 units = plan. **Content deviates:** `headingStandsDown` is KEPT; the plan text says "removed" | 8 rows identical | no (text drift only: nit) |
| L6 | **GROWTH 1**: `wpt__css-display__display-contents-dynamic-pseudo-insertion-001` android (unscored), outside carriers ∪ mayMove ∪ sharedStems ∪ ring | skeptic: GROWTH 4 on the builder's patches. fix-r1 withdrew 3 in code (a clipping box keeps the base chain). reverify-r1 (independent JVM census): **GROWTH 1, the same stem**. 0 occurrences of the stem in `expectations.json` | U1, U2, U3 = plan; U2's content changed (the clip condition) | carrier rows unchanged; fix-r1 adds "identical" rows that fall under complement / mayMove semantics | **YES (must)** |
| L7 | 0 / 0 / 0, wire 0: IDENTICAL; U1 optional with no patch | IDENTICAL | 8 patches + U1 (no patch) = manifest | 42 must-not-move cells = `mustNotMove`, all resolve | no |
| L5 | (builder) grew 0 / shrank 0 | skeptic verdict **MIXED, must-fix 1**: U3-ios's `tnum` substitutes `period.tf` / `two.tf`, so `[T]` / `[K]` counter-suffix ios FAIL on a correct picture. Needs a probe and plan regeneration (fix-r1 `fix-r1.probe-proposal.diff.txt`, base = HEAD's probe `bd2f001a…`) | — | — | **YES (once the lane is COMPLETE)** |

**Predicted flips against the plan's prediction rows.**
- L1 predicts hyphenate-character-004 ios.
- L2 predicts clip-3 android.
- L3 predicts dir-style-02a android.
- L4 predicts inset-005 / 006 / 014 on ios and android.
- L6 and L7 predict 0 flips.

Every one is a plan row, so **no lane predicts a flip the plan lacks.** Every cited "from" resolves:
- the regex re-resolution of `wave54-final <sec>/<test> <plat> <P|f> <ssim>` over the notes and skeptics finds 61
  citations and 0 mismatches;
- `cells.mjs '<the lanes' target terms>' wave54-final wave55-open` shows every target cell unchanged between the two
  runs.

## 2. Deliveries

### Patch series in `landing-manifest.json` order (throwaway at 611c9f93, `git apply --check` then `git apply`, per unit)
- **28 revert units: 0 failures.** L7:U1 has no patch (optional) and its three files are tracked at HEAD. Every
  revertOrder chain reverse-applies on the fully landed series (`git apply -R --cached` on a copy of the index): 28 / 28.
- **With both contingencies on top (30 units): 0 apply failures.** On that tree, NB-ios and U1-ios reverse only after
  their contingency: [NB-ios-b, NB-ios] and [U1-ios-LRM, U1-ios] both reverse-apply, as the manifest notes say
  ("revertOrder then becomes …").
- **The series without L5** (L5 is pending): 22 units, 0 failures. It applies independently of L5's patches.

### `land-units.sh --check --only L1,L2,L3,L4,L6,L7` (read-only)

| where | result |
|---|---|
| throwaway, with the six lane dirs and the six force-add files copied in | `SUMMARY 0 error(s), 0 warning(s) — landable`; 27 patches cumulative; 20 revert dry-runs ok; NB-ios-b applies on top; "28 plan revert units (+ 2 contingency) agree with the table … (expectations.json)" |
| the shared tree at HEAD 4e789976 | `0 error(s), 0 warning(s)`; 63 touched paths clean (36 created) |

### Seam headers (each recomputed with `git show 611c9f93:<file> | shasum -a 256`)
- **.kt** (`2445d9ea…`, 8488 lines): L2 seam-1, L3 seam-1, L4 seam-1 / seam-3, L5 seam-4 / seam-5. Each names base
  611c9f93 and `2445d9ea…`. L3 seam-1 and L5 seam-4 also name SeamReachabilityTest.kt `7a1b061f…`, which is correct.
- **.swift** (`417853ce…`, 6291 lines): L1 seam-1, L4 seam-2 / seam-4, L5 seam-1 … seam-3 and seam-6. Each names
  611c9f93 and `417853ce…`.
- **The contingency seam-1b** names its base as the NB-ios state, `208e41ba…`. Recomputed after unit-NB-ios + seam-1
  on 611c9f93, it is `208e41bab6a0…c5f5`: equal.

### Ownership and the deliveryContract (every `diff --git` path of every patch)
- **Ownership.** Every unit patch touches only paths in its lane's PLAN §2 `Owned:` list, or in `ownership.owned`.
  L7 U3 touches `wave55-gate/ab-arms55.*`, its owned U3 files and not a lane records dir. L7 U4 touches
  `ComposedRootSeparator.ts`, its optional U4c.
- **Forbidden rules: 0 violations.**
  - No unit patch touches a seam file.
  - Each seam patch touches exactly its registered seam file, plus patch-borne tests.
  - No patch touches a lane records dir.
  - No unit shares a path with its own seam patch.
  - No rename or copy.
- **The two sharedPaths rules hold.**
  1. Only the three `sharedPaths` are touched by more than one lane:
     - `ComponentRenderer.kt`: {L2, L3, L4, L5};
     - `ComponentRenderer.swift`: {L1, L4, L5};
     - `SeamReachabilityTest.kt`: {L3, L4, L5}.
  2. The two seam files are reached only through seam patches. The SeamReachabilityTest writers sit at their pinned
     anchors:
     - L4 seam-1 → seam-3 after `"content/PseudoGeneratedBox.kt",`;
     - L5 seam-4 after `"images/ImageCandidateChain.kt",`;
     - L3 seam-1 → unit-U2 after `"transforms/TransformInheritance.kt",`.
- **L5 (informational).** Its seam patches add six NEW patch-borne raster or seam tests. Examples:
  `ListMarkerBidiSeamRasterTests.swift` and `ListMarkerSuffixGapRowSeamTest.kt`. They are allowed by the
  deliveryContract, but PLAN §3 lists "—" as the patch-borne file for those rows and they are not in PLAN §2 L5
  `Owned:`. They need a §10 line when L5 completes.

### `revertStatesCompiled:` against every revertOrder chain
- L1: NB-ios; base; rule 2b; the contingency chain [NB-ios-b, NB-ios] and its rule 2b; NB-ios-b reverted. Complete.
- L2: A full, B (U2 reverted), C base, D / E rule 2b, F U2 alone. Complete for [U2, U1] ×2.
- L3: full; U1 (U2 reverted); base; rule 2b. Complete for [U2, U1] and [U2].
- L4: per platform, full, U2-<p> reverted, base. Complete for all four chains.
- L6 (fix-r1, re-run on the re-cut patches, and again by reverify-r1): U3 reverted, U2 reverted, U2 then U1, U2+U3,
  base, full. Complete.
- L7: FULL and FULL−U for each of the nine units. Complete.

### [W-L7a] anchor (closes the L7 skeptic's D1 for the record that exists)
The window ran on unit-U2 `6a9d7edf…` and unit-U5 `0ff38b3b…`. These still equal the lane's `patch:` lines and the
current file bytes.

Anchoring both of its sides to the run of record was done with the classifier from unit-U2, applied in the throwaway:
`wire-differential.mjs classify --pre runs/wave55-open/sections --post runs/wave55-wl7a/{pre,post} --no-status
--sections <the six>`.
- Both sides read `identical 288 · content-changed 0 · renumbered 0 · one-sided 0 · renumbered-anomalous 0`, exit 0.
- Each side carries 17 `[bidi-bake: baked` stamps.
- counter-suffix bakes `2 roots, 10 runs` on both sides.

So the throwaway pipeline was the gate's pipeline, and the bake engaged. Step 4's "identical" is not vacuous.

## 3. Regeneration needs (exact generator tables)

All of these go through `plan-build.py` and `probe-decisions/*`, then `plan-prose.py --fill` / `--check`, and then the
§8 step 2 re-run set, all in one commit.

1. **L6-android-paint-order (MUST, census growth).** These are edits to plan-build.py's `L6 = {…}` table.
   - **`'mayMove'`.** Add `wpt__css-display__display-contents-dynamic-pseudo-insertion-001` (android) as a new
     asserted list beside `l6_may_raw | l6_may_medium | l6_opacity`, by rule: "a kept `display: contents` box with
     pseudos (`ContentsUnboxing.isUnboxable` false) whose legacy band now paints behind its `::after`".
     - Sources: `wave55-android-paint-order/fix-r1/census.out.txt` and `reverify-r1/census.out.txt`.
     - The union may-move count becomes 241. The cell is unscored, so the complement is unchanged.
   - **`'mayMoveRule'` and `'stage1Decision'`.** Besides "a band now under its child", accept two more pictures:
     - "the band now inside the element's opacity group" (anchor-center-overflow-003, and basic-blur, which is
       ring-fenced);
     - "a UA-widget replica now over the band" (flex-gap-decorations-023, predicted +0.0007, DEGENERATE).
   - **`'carrierRule'['U2']` and `'revertUnits'['U2']['commit']`.** U2 now applies only where
     `!config.overflow.shouldClip`: 29 clipping bands in 26 documents keep the base chain. Add pins P3h / P3i.
   - **`'revertUnits'['U1']['commit']`.** Add P5a–f.
   - The 19 withdrawn mayMove stems may stay, as harmless over-coverage.
2. **L1-hyphen-ios-linebox (SHOULD, before the stage-1 read).** These are edits to the `L1 = {…}` table.
   - **`'stage1Decision'`.** Route the R1 signature to `--contingency L1:NB-ios-b`, with a re-probe that requires those
     controls byte-identical, instead of "an iOS clause → revert NB-ios". The signature is:
     - the gating reason is `anchor: group 1 line 1 offset 1` only;
     - the iOS movers are only the in-radius one-line SAME controls, each one row off.

     This is the L1 skeptic's should-fix 1. No fix pass ran: the note has one `STATUS:` line.
   - **`'carrierRule'['NB-ios']`.** "60 text components in 19 documents … 5 SAME" becomes 58 / 18 / 4 SAME, with
     all-prop-001 outside the radius. Also change the NB-ios `'commit'` text "the 5 SAME controls", and PLAN §2 L1 R1
     "four in-radius controls".
   - `mustNotMove` is unchanged: all-prop-001 ios stays named, with the same 0.002 band.
3. **L5-counter-suffix-rows (MUST once COMPLETE; pending now).** The skeptic's must-fix 1 needs the
   `counter-suffix-rows.geometry.py` `[T]` / `[K]` ios zones and the L5 counter-suffix ios rows changed. The fix
   lane's proposal is a plan-probe diff for the orchestrator to apply and regenerate. Re-audit L5 after its last
   `STATUS: COMPLETE`.
4. **L2-cbb-android (nit; changed prediction field only).** In the `L2` predictions, the
   `backdrop-filter-nested-border-radius-clip-3 android` row's confidence would change 'MED-HIGH' → 'MED' if the
   lane's restatement is adopted. That moves one android f→P flip from HIGH/MED-HIGH to MED/MED-LOW in the
   generator's tier tally. Otherwise, record in §10 that the plan's MED-HIGH stands.
5. **Fold in at the same regeneration (nit, no carrier effect).** L4 `'revertUnits'['U2-android']['commit']` and
   `'ownership'['owned']` say "headingStandsDown removed", but the patch keeps it for rule 2b (the note's decision 1).
   Also, the kept kdoc says "removal is queued (BACKLOG)", and there is no such BACKLOG item yet.

## 4. Other delivery findings

- **Records hygiene (should-fix, before the records commit).** Several records carry scratchpad paths:
  - 40 L4 `mutations/*.{red,green}.txt` files hold 1469 `/tmp/claude-501/…` lines (xcodebuild logs);
  - `wave55-instruments/u3/selftest.base-bytes.out.txt`;
  - `wave55-gate/wl7a/step4-wire-differential.txt` and `step5-classify-only.txt`.

  The records commit would carry them, and they would trip PLAN §8 step 18 ("`grep` for a scratchpad path returns
  nothing"; only lane `TREES:` lines are excepted). Rewrite them to `<wt>` / `<S>` placeholders first, as L7 did for
  its Swift log, or record an explicit exemption. `unit-U3.patch`'s two pointer lines are REMOVED lines, so the
  landed tree loses the pointer, but the patch file itself is a record.
- **Contract wording (nit).** `deliveryContract.cutOn` says "never at the list end". L3's pinned anchor,
  `"transforms/TransformInheritance.kt",`, is the list end at HEAD; the L3 skeptic noted this too.
  `land-units.sh --check` passes, and the self-test proves any order. Re-word the contract.
- **Open skeptic should-fixes with no fix pass yet.** These are pin gaps, not census items. If adopted, the patch
  bytes change, and `land-units.sh --check`, the revert states and the [W-L7a] patch-sha rule must be re-run:
  - L1: the document-face pin (needs a new test file outside `own:`);
  - L2: the `PercentFillPlusBandModifier.measure` pin;
  - L3: the `originModifier` mechanism pin;
  - L4: the seam-4 patch-borne pin (that changes PLAN §3 .swift row 2);
  - L7: D2's five guards.

## 5. Not checked here

- No compile and no suites: the lanes and skeptics did that, and the S1 sweep owns it.
- Nothing on a device.
- I did not re-run the lanes' JVM / Swift censuses. The census conclusions rest on the lane + skeptic (+ L6
  reverify) records, cross-checked against `expectations.json`. I confirmed L6's growth stem statically: its IR has
  `Display: CONTENTS`, a 10-px solid band and pseudos, and it has 0 occurrences in `expectations.json`.
- L5 is not audited beyond the builder-bytes apply and the census statement, because its fix-r1 is in progress.
