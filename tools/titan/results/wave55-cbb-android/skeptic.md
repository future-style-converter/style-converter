TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-skeptic-L2

# wave55-cbb-android — lane L2 adversarial SKEPTIC

Scope: the builder note `_note.md` (STATUS: COMPLETE), its three patches, PLAN.md §0 / §2 "### L2 · cbb-android" / §3,
brief `wave55-plan/cbb-android.md`, `expectations.json` `lanes["L2-cbb-android"]`, `landing-manifest.json` seq 17–18.
Everything below was EXECUTED in the skeptic's own worktree (the `TREES:` line: `git worktree add --detach … 611c9f93`,
provisioned with both `local.properties` and the `node_modules` link, plus a `tools/titan/runs` link so the local-only
corpus pins execute instead of `assume`-skipping). The lane's patches and note were never edited. Nothing on the shared
tree was written outside this lane dir (the `skeptic*` files). No device, no Chromium, no emulator / simulator.

## Verdict: CLAIM-HOLDS — with one should-fix (the U2 modifier's measure body has no JVM pin)

The units do what the note says, the radius is what the plan says (independently re-derived, statically AND from the
device record), every pin replays red → restore → green, every revert state compiles, and the patches land cleanly
alone, in series and on top of L6's delivered patches. The one real gap: the device-facing half of U2
(`PercentFillPlusBandModifier.measure`) is never executed by a JVM test, so three executed mutations of it survive.
That gap is caught at stage 1 by the gating key `cbb-android.geometry.py` (device), so it does not block the gate.

## Repros (executed, with their outputs)

1. **Patches apply / sha256 / landing check.**
   - `shasum -a 256` of the three patches = the note's `patch:` lines (unit-U1 `addb9c05…`, seam-1 `21970595…`,
     unit-U2 `7773cef3…`).
   - `git apply --check` of each alone on 611c9f93: rc 0 ×3; then `git apply` in landing order unit-U1 → seam-1 →
     unit-U2: ok.
   - `land-units.sh --check --only L2` (read-only): `0 error(s), 0 warning(s) — landable`; 8 touched paths (4 created)
     clean at HEAD; cumulative series ok; revert dry-runs `L2:U1` / `L2:U2` `[ U2 U1 ]` reverse-apply ok →
     `skeptic-land-check-L2.txt`.
   - Re-landed bytes: `ContainingBlockBands.kt` sha256 `f4bb43ff…` and `DynamicValueResolver.kt` `19b15add…` equal
     `git show 5b333c8a:<path>`; the base `ComponentRenderer.kt` is `2445d9ea…` (the seam header's value); the two test
     files differ from 5b333c8a's only by the documented provenance lines, the `wave53-final → wave54-final` census path
     and the new C8 row (diffed).
2. **Full focused suite** (PLAN §2 L2 "May run", my worktree, full state): 12 classes, **111 tests, 0 failed, 0 skipped**
   (the two corpus pins EXECUTED: skipped=0) — DynamicValueResolverTest 33, PercentContentBoxBandTest 8,
   ChildContainingBlockSeamWiringTest 1, the rest unchanged. Neighbouring classes (sizing.*, core.variables.*,
   StyleApplier*, OutOfFlowContainingBlock*, PercentInset*, SeamReachability*, ElementContainingBlock*,
   AbsposInsetStretch*, AbsposAutoMargin*, VerticalTextFlow*, ListStyleUaRule*): 34 classes, 290 tests, 0 failed
   (`compileDebugKotlin` executed, not UP-TO-DATE) → `skeptic-revert-states.out.txt`.
3. **Every pin's mutation replayed** (`skeptic-mutations.txt`: `MUTATING … sha256 <pre>` written before each edit,
   `RESTORED … sha256 <post>` after the byte-exact restore, post == pre on all 18; then the per-run tallies). The lane's
   12 literals, applied by my own runner:

   | mutation | file | mutated run | restored run |
   |---|---|---|---|
   | M-U1a always subtract | ContainingBlockBands.kt | RED 4/33 (WPT container, nested-clip chain, C8, CBB census) | GREEN |
   | M-U1b never subtract | ContainingBlockBands.kt | RED 4/33 (300 → 276 row, dark-stage 70×80, C8, census) | GREEN |
   | M-U1c WPT ignores border-box | ContainingBlockBands.kt | RED 1/33 (explicit BORDER_BOX row) | GREEN |
   | M-U1-C8 breadcrumb deleted | ContainingBlockBands.kt | RED 1/33 (the C8 row) | GREEN |
   | M-U1d named argument dropped | ComponentRenderer.kt (seam applied) | RED 1/1 | GREEN |
   | M-U2a band dropped | PercentContentBoxBand.kt | RED P-U2-1 | GREEN |
   | M-U2b report the child size | PercentContentBoxBand.kt | RED P-U2-1 | GREEN |
   | M-U2c WPT clause dropped | PercentContentBoxBand.kt | RED P-U2-2 | GREEN |
   | M-U2d band test dropped | PercentContentBoxBand.kt | RED 2/8 (P-U2-2 + P-U2-6) | GREEN |
   | M-U2e width call site deleted | SizingApplier.kt | RED 2/8 (both P-U2-5 rows) | GREEN |
   | M-U2f centred placement | PercentContentBoxBand.kt | RED P-U2-4 | GREEN |
   | M-U2g unbounded not identity | PercentContentBoxBand.kt | RED P-U2-3 | GREEN |

   Six mutations of my own, aimed at what the lane's set does not reach:

   | mutation | what it breaks | result |
   |---|---|---|
   | SK3 the HEIGHT call site deleted (SizingApplier.kt) | the height axis | RED 2/8 (both P-U2-5 rows) — pinned |
   | SK5 the `clampOwnsAxis` clause dropped (owns) | D-a's clamp breadcrumb | RED P-U2-2 — pinned |
   | **SK1** measure body reports `childPx` instead of `reportedPx` | the parent would grow 20 px (the house split) | **GREEN 8/8 — survives** |
   | **SK2** measure body places at `(reported − child) / 2` | requiredWidth's centring (−10 px) | **GREEN 8/8 — survives** |
   | **SK7** measure body uses a zero band (`0f * bandCssPx.dp.toPx()`) | the whole fix (back to the 200×100 fill) | **GREEN 8/8 — survives** |
   | SK6 the `boxSizing != CONTENT_BOX` clause dropped (owns) | the border-box / dark-unset declines | GREEN 8/8 — survives (behaviourally redundant, see D2) |

4. **My own blast-radius census** (`skeptic-census.py` → `skeptic-census.out.txt`, wire-level, written from the renderer
   code, not from the lane's scripts):
   - **B (U2):** 122 percentage axes in the 1435 documents; runtime-band carriers **2** — `clip-3 0-172 W 100% pad 20` and
     `… H 100% pad 20`; 0 axes that only a style-less border width would add. Includes logical sizes folded per writing
     mode, logical paddings, font-relative paddings, and the runtime's UA list padding (ListStyleUaRule). = the lane's
     P-U2-6 and brief census B. The lane's M-U2d correction (2 → **118**, not 122) is consistent: 122 − the 4 css-tables
     clamp axes (band 0, PercentSizeClamp), and 0 border-box % axes.
   - **A (U1), static:** the block threaded twice (frozen / WPT) through every slot tree, with the UA list padding folded
     in BEFORE the block (the renderer computes it from the post-`ListStyleUaRule` list; the lane's JUnit census does not)
     and vertical text checked against BOTH the incoming and the box's OWN published block. Superset of readers: 646
     boxes / 90 documents. Restricted to the renderer's actual readers (the six readers of `LocalContainingBlock` /
     `LocalElementContainingBlock` in `runtimes/compose/src/main`: the OOF branch's `resolveOutOfFlowPercentSizes` /
     `AbsposInsetStretch` / `AbsposAutoMargin`, `MarginApplier` / `PaddingApplier` / `PercentInsetPositioned`, the
     resolver's %-expressions, `VerticalTextFlowLayout`): **72 boxes in 22 documents** — the lane's 14 plus 8 that my
     predicate over-approximates: `flex-abspos-staticpos-margin-001/-002/-003` (auto margins, no opposing insets),
     `css-writing-modes/available-size-003/-013`, `inline-box-border-vlr-001`, `css-counter-styles/…/disclosure-styles`
     (vertical text whose own block is unknown on both readings), `css-text/hyphens/hyphens-vertical-001` (own block
     differs, but its height is definite).
   - **A (U1), device:** `skeptic-probe-diff.py` → `skeptic-probe-diff.out.txt`. `wave54-probe` (206d71d9) differs from
     0d0d3a3d on every Android path ONLY by 5b333c8a (re-checked: `git diff --stat 206d71d9 0d0d3a3d -- runtimes/compose
     apps/android-harness tools/titan/extract-fixture.mjs converter` = the five 5b333c8a files + two READMEs; tools/titan
     outside results: no diff). Decoded-pixel comparison of all **1243** android captures of its 26 sections against
     BOTH wave54-final and wave55-open (base-to-base noise: **0**): **exactly 13 captures moved**, all inside the lane's
     14 (autopos ×6, semi-replaced ×3, nested-clip ×4); `flex-gap-decorations-006` Δ 0. **None of my 8 static extras
     moved** — they are false positives of my superset, all in probed sections. The 4 unprobed sections (css-color,
     css-grid, css-sizing, css-view-transitions) carry 426 superset boxes, every one `oof`-only with no reader (no %, no
     opposing insets, no auto margin) — 0 refined readers.
   - **Result:** captureCarriers android 14 / iOS 0 / web 0, wireCarriers 0, U1 14 / U2 1 — IDENTICAL to
     `expectations.json`. **The census did not grow.** The fixture net: one non-WPT fixture declares content-box
     (`fixtures/properties/sizing/box-sizing.json`), with no % size — census C = 0 holds.
5. **Verbatim payloads.** clip-3 `965c7730…` and autopos-htb-ltr `421c514a…` are byte-identical at wave53-final,
   wave54-final, wave55-open and wave54-probe; every per-test-ir file of wave54-final equals wave55-open's (`diff -rq`,
   all 30 sections). P-U2's `middle` / `inner` lists equal 0-172 / 0-173 property for property, in wire order; the
   DynamicValueResolverTest rows are the documented sizing / band subsets with verbatim values.
6. **Scores** (`cells.mjs … wave54-final`, and `wave54-open wave54-probe wave54-final`): every from-value in the note is
   exact — clip-3 android f 0.9574 (probe P 0.9668), clip P 0.9649 → probe 1, -2 0.9689 → 1, -4 0.977 → 0.9999,
   autopos ltr ×3 P 0.9966 → 0.9967, htb-rtl 0.9869 → 0.9966, vlr/vrl-rtl 0.9966 → 0.9966, semi-replaced 0.9739 / 0.4117 /
   0.4396 → 0.9843 / 0.4555 / 0.4679, 006 f 0.8223 → 0.8223; controls cross-fade-target-alpha android P 0.9957,
   contained-by-viewport-000/-001 P 0.9966 / 0.9967; ring-fenced basic-blur android f 0.904 (report only).
7. **Geometry.** `geometry-gate.py wave54-final --base wave54-open --lanes L2 --self-test` → rc 0, `self-test: HOLDS —
   0 violation(s)`; gating 8 FAIL, control 22 PASS, report 3 FAIL — byte-identical to the lane's record
   (`skeptic-geometry-gate.wave54-final.selftest.txt`). On `wave54-probe` (U1 only, device): gating PASS 6 / FAIL 2 —
   exactly the two clip-3 keys (`green (36, 36, 215, 115) vs ref (36, 36, 235, 135)`, corners, XOR 5591), revert rule 4
   names U2 only (`skeptic-geometry-gate.wave54-probe.txt`).
8. **Pictures** (opened, my own crops: `skeptic-clip3-look.png` ref | final android | probe android | final iOS, and
   `skeptic-carriers-look.png` for autopos-htb-ltr / -htb-rtl / nested-clip-4). Colour runs along row 80 / column 120 of
   clip-3: ref `blue[16..25] grey[26..35] green[36..235]` / `…green[36..135]`; final `…green[36..195] pink[196..215]
   grey[216..225] blue[226..235]`; probe `…green[36..215] grey[216..225] blue[226..235]` — #middle's grey band and
   #outer's blue band visible INSIDE #outer, exactly the brief's discriminator. autopos-htb-ltr: final red L, probe = ref;
   htb-rtl: final displaced green + stray strip, probe = the iOS picture (10-px red strip at the right); clip-4: final pink
   L, probe = ref. The note's descriptions hold.
9. **Revert states** (my worktree, clean 611c9f93 + the kept patches, compiled + the focused suite;
   `skeptic-revert-states.out.txt`) — identical counts to the lane's `revertStatesCompiled:` lines:
   A-full [U1 seam U2] 111/0 · B-revert-U2 [U1 seam] 103/0 · C-revert-U1 [] 96/0 · D-U1-no-seam [U1] 102/0 (rule 2b) ·
   E-U1-U2-no-seam [U1 U2] 110/0 (rule 2b) · F-U2-alone [U2] 104/0 · A restored 111/0. All gradle rc 0, 0 skipped.
10. **Cross-lane (beyond the note, which had no other lane delivered):** L6 android-paint-order's delivered unit-U1/U2/U3
    (sha256 = its note's `patch:` lines; §4 lands them before L2, C13) applied on 611c9f93, then L2's three patches: all
    apply; L2's suite + L6's new classes + SeamReachabilityTest → 17 classes, 0 failed, **including `P-U2-5 live chain`**
    (the band nodes still sit outside the BackgroundElement and the last PaddingElement with L6's StyleApplier /
    BorderRadiusApplier changes) → `skeptic-revert-states.out.txt`.

## Defects, ranked

**D1 (should-fix) — the device-facing half of U2 is never executed by a JVM pin.**
`PercentContentBoxBand.kt` `PercentFillPlusBandModifier.measure` is where the fix lives (convert the band to px, measure
the child tight at the target, report the incoming max, place start-anchored). The pins exercise only the pure helpers
(`measureSpec` with an explicit band, the constant `placementOffset`) and the data-class INPUTS of the node (P-U2-5 live
chain); nothing calls `measure`. Executed: SK1 (reports `childPx` → the parent grows), SK2 (centres in the body,
ignoring `placementOffset`), SK7 (zero band in the body → back to the 200×100 fill, i.e. no fix at all) all leave
PercentContentBoxBandTest **8/8 GREEN**. So M-U2b / M-U2f "report the child size" / "centred placement" are pinned only
at the helper level the body is free to bypass. Fix: one JVM row that runs `measure` on a fake `MeasureScope` (density 1)
with a recording `Measurable` / `Placeable` under `Constraints(maxWidth = 200, maxHeight = 100)` and asserts the child was
measured tight at 220 (and 120 for the height node), the result is 200 (100), and the placement x / y is 0 (also under
RTL); then SK1 / SK2 / SK7 must turn it red. Not gate-blocking: the stage-1 gating key `cbb-android.geometry.py`
(bands / corners / mask / fail ink, with its F3 centred / F4 unclipped / F6 double fakes) reads exactly these failure
shapes on device — but today a broken body would cost a device window and, by the pair rule, both units.

**D2 (nit) — the `owns` box-sizing clause is unpinned (SK6 survives).** `SizingExtractor` fills
`contentBoxInflateX/Y` only for an effective CONTENT_BOX, so via `applySizing` a border-box or dark-unset axis always
arrives with band 0 and declines at the band clause; P-U2-2's "declared border-box keeps the fill" and "dark stage, unset"
rows pass for the band reason, not the clause they name. Behaviourally redundant on the call path; a direct row
`owns(percent, borderBoxCfg, 20f, false) == false` would pin it, or the clause could be documented as defensive.

**D3 (nit, report-only) — the lane's U1 census (5b333c8a's `cbbCensus`) has two modelling gaps the radius happens not to
expose:** it computes each box's published block from the raw `n.properties`, without the runtime's
`ListStyleUaRule` 40-px list padding that the renderer folds in before `childContainingBlock`, and it tests `vtext`
against the INCOMING block while `VerticalTextFlowLayout` reads `LocalContainingBlock` inside the box's own provider (the
box's own published block). My census covers both and finds no carrier either adds; the device diff confirms 13 + 006.
The pin would not catch a future list-container or own-level carrier.

**D4 (nit) — style.** The two SizingApplier call sites are 172 / 173 characters, and `PercentContentBoxBandTest.kt` meets
the 200-line ceiling (199) by compacting lines to 161–164 characters. The height call site has no comment of its own (the
one allowed comment line above the width site says "both axes"). No uncommented logic elsewhere; no scratchpad pointer
in any delivered file (only the note's `TREES:` line); no probe leftovers; every path is in L2's `own:` list; the seam
patch is one seam file + its patch-borne test, header names 611c9f93, `2445d9ea…` and the content anchor.

**Not defects (checked):** D-a (band lane asked before the clamp lane, the two clamp lines → `else if`) is
routing-identical whenever `owns` is true (it requires `pctW == null`) and makes the clamp breadcrumb live (SK5 red);
the `-rtl` autopos rows rise in score with a red strip still showing, are not claimed, and are already in
`expectations.json` `stayDegenerateEvenIfPass` (the note could say DEGENERATE in so many words); clip-3's MED confidence
(below the plan's MED-HIGH) is the honest reading of R1.

## What I could not check
- The device draw of U2 (R1: the 220×120 placeable under #outer's rounded clip with BackdropModifier's two-pass on
  #inner's abspos child). Only stage 1 decides; JVM evidence cannot, and D1 means the JVM does not even run the measure.
- The union with lanes not yet delivered (L4's seams 1 / 3 land on `ComponentRenderer.kt` before L2's seam 1; L3, L5,
  L7): checked only with L6. The S1 skeptic's landed-tree sweep owns that.
- Android captures of the 4 sections `wave54-probe` did not run (css-color, css-grid, css-sizing, css-view-transitions):
  covered only by the static census (0 readers there), not by a device diff.
- Compose intrinsic queries through `PercentFillPlusBandModifier` (the LayoutModifier default intrinsics, R2): not
  executed anywhere; 0 corpus carriers ask #middle for intrinsics, as far as the code shows.

## Skeptic records in this dir
`skeptic.md` (this file) · `skeptic-census.py` + `.out.txt` · `skeptic-probe-diff.py` + `.out.txt` ·
`skeptic-mutations.txt` · `skeptic-revert-states.out.txt` · `skeptic-land-check-L2.txt` ·
`skeptic-geometry-gate.wave54-final.selftest.txt` · `skeptic-geometry-gate.wave54-probe.txt` · `skeptic-clip3-look.png` ·
`skeptic-carriers-look.png`.
