# wave 54 · L6 web-tail — skeptic report

Skeptic of builder lane L6 (units RS and W1). Shared tree `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf`,
branch `campaign/applier-campaign`, HEAD `31b76944` (= `db6e8aa0` + the plan's second fix pass; `git diff db6e8aa0 HEAD -- apps runtimes`
is empty). Run 2026-10-08 20:33–21:01. Nothing in the shared tree was edited: every mutation and every seam-applied run happened in
EXPORT trees (`git archive HEAD apps/web-harness runtimes/web` + the lane's files / `seam-1.patch`, node_modules symlinked, built by
`skeptic/sk6-census/mk.sh`). No device, Chromium, emulator, simulator, Gradle or full suite. The `skeptic/sk-*` files are an earlier
skeptic attempt that died at 20:02; everything below was re-executed (`skeptic/sk6-*`), and the two attempts agree.

## Verdict: MIXED — the lane's radius, pins and seam hold; two should-fix defects and a set of untested branches

The load-bearing claims reproduce exactly by my own scripts: RS 27 documents / 73 separators, W1 1 document / 4 joins, landed 28
= `expectations.json` web carriers, 0 native, 0 wire; the lift alone changes 0 / 1435; every one of the lane's 13 mutations is
RED → restored byte-exact → GREEN; `seam-1.patch` applies clean on HEAD and 7cce3b22; the tree compiles without the seam; the
geometry self-test holds; every quoted score matches `cells.mjs`. Nothing must be fixed before the gate. What is wrong: W1's
production gate (the `NodeRenderer` read of the host's `hyphens`) is not pinned, the "counted, never silent" W1 counter is read by
nothing, several W1 branches have no pin at all, and one predicted MED flip would be written without its picture residue.

## Executed repros (outputs under `skeptic/`)

| # | repro | result |
|---|---|---|
| 1 | Own DOM census `sk6-census/census.sk6.test.tsx` (real `ComposedCaptureGallery`, `?wpt=1&wptComposed=1`) over all 1435 per-test IR documents AND every section's combined `out/tmpOutput.json` split per canvas, in 5 export trees (base / lift-only / RS / W1 / landed), `diff-census.py` | `census-diff.wave53-final.out.txt`: lift 0/1435 (per-test and canvas); RS 27 (separators 2,6,2,1,1,8,6,2,19,5,5,1×14,1,1 = 73); W1 1 (-002, len −52: `</span><span>` ×4 removed); landed 28. Cross-check vs `expectations.json`: RS 27 = 27, W1 1 = 1, landed 28 = 28, **under-reported 0, expected-but-unchanged 0**; ios/android/wire [] |
| 2 | Same census on `wave54-open` (base, landed) | landed 28 changed; base and landed each identical between wave53-final and wave54-open (0 / 0) |
| 3 | Own static IR census `sk6-static-census.py` (inline tag set read from `ComponentRenderer.tsx`, no runtime code) on wave53-final and wave54-open | RS 27 docs / 73 separators; W1 4 joins in 1 doc; both runs identical. Headline counts re-derived: 264 runs hosts, 7 with own `hyphens: auto`, 21 abspos/fixed run members, 12 inert — all as the note says |
| 4 | Lane mutation replays, own runner `sk6-mutate.py` (export trees; RS with `seam-1.patch` applied) | `sk6-rs-mutations.out.txt`: RS-m1, m2, m3, m4, m4b all RED → GREEN, sha256 before = after (`c780ac1c…`, `1df076a6…`, `badb03dc…`). `sk6-w1-mutations.out.txt`: W1-m1…m7 and m5b all RED → GREEN (`eb7dcd38…`, `21389d5a…`, `69dd160f…`) |
| 5 | Own extra mutations | RS: marker read on NEXT, `{}` container in the child walk, `inline-table` dropped from the decline, (next, prev) order, prev-only inline gate → all RED. Survivor SK6-r5 (body-root found as `components[0]`) is an equivalent mutant on the corpus (the body-root is always first). W1: see defects D1 and D3 |
| 6 | `git apply --cached --check seam-1.patch` on temp indexes of HEAD and 7cce3b22; `git apply --check` on the working tree | rc 0, 0, 0 |
| 7 | Rule 2b: `tsc --noEmit` on the shared tree (harness unseamed, runtime with W1), on RS-alone and landed export trees, harness on W1-alone; `npm -w apps/web-harness run test -- tests/ui/ComposedRootSeparator.test.ts` on the shared tree | all rc 0; 7/7 green unseamed |
| 8 | Unit standalone (revert units): RS-alone tree 16 harness files / 112 tests; W1-alone tree runtime `tests/renderer` + conformance 13 files / 204 tests and 24 harness files / 198 tests; landed 16 / 112 and 13 / 204; base 14 / 98 and 12 / 194 | all green; each unit stands without the other |
| 9 | `geometry-gate.py wave53-final --base wave53-open --lanes L6 --self-test` | exit 0, HOLDS: gating 4 (all FAIL), control 44 (all PASS), report 15 (all FAIL), UNMEASURED 0 (`sk6-geometry-gate.L6.self-test.wave53-final.out.txt`) |
| 10 | `cells.mjs` on every L6 row (wave53-final; 007 also vs wave54-open) | all "from" values match: 007 f 0.9036, 008 f 0.8943, 022 f 0.9442, semi-other f 0.941, 010-019 P 0.9713, 020/021/024/025 P 0.9599, 013 P 0.9538, semi-input P 0.9596, scope f 0.9353, flow-root-list-item f 0.8003, static-inside-inline-block P 0.9803, block-in-inline P 1, appearance P 0.9873, attr P 0.9939, baseline-orth P 0.9854, 009 P 0.9811, -002 web f 0.9411, overlay-button-appearance unscored. "20 P (14 DEGENERATE) · 6 f · 1 unscored" recounted: true |
| 11 | Pins are verbatim: DOC002, DOC001 box 4, BS007 and the 24 census members compared to wave53-final per-test IR | all verbatim (DOC002 has **14** components, not 15 — nit N2) |
| 12 | PNGs looked at (ref │ web │ iOS): box-sizing-007 / -008 / -022, semi-replaced-stretch-other / -input, hyphens-out-of-flow-002, scope-pseudo-element, appearance-auto-input-non-widget-001, attr-style-sharing-1, baseline-with-orthogonal-flow-001, block-in-inline-015-print, display-flow-root-list-item-001, static-inside-inline-block, box-sizing-009 | geometry as the lane describes: second column x146 vs ref x151 (007 / 008), one merged 260-px run vs two 130-px rects (022), x187 vs x192 (semi), x116 vs x121 (static-inside-inline-block square), boxes 4/5 one-line `highway` vs `high‐/way` (-002). The -002 ref is the rel=match reference page, consistent with step 0's "host Chromium fails the test itself" |
| 13 | Own replay of RS on the four un-replayed at-risk passes (`sk6-atrisk.replay.py` + `-score.mjs`, the gate's `diffWebVsRef`; calibration rows reproduce cells.mjs) | static-inside-inline-block 0.9803 → 0.9955 / 0.999; appearance 0.9873 → 0.9979 / 0.9975; attr-style-sharing-1 0.9939 → 0.9991 / 0.9992; **baseline-with-orthogonal-flow-001 0.9854 → 0.9851 / 0.9850** (a fall, inside the −0.002 band — nit N1) |
| 14 | NodeRenderer-gate mutant SK6-w7 (`{ hyphensAuto: true }`) through the census | W1 runtime pins 46/46 GREEN, census changes **2** documents: -002 and **-001** (a P 1 must-not-move web cell) — `sk6-census/sk6-w7-nodeRenderer-gate-mutant.census.out.txt` |
| 15 | Hygiene: `git status` on web paths, seam sha256, lock dir, probe / scratchpad / "engine" greps, file sizes | only L6's 6 web paths; seam files `cc39ab6e…` / `87ac9ca4…` = HEAD; `wave54-lock/` holds no L6 lock; no probe leftovers, no scratchpad pointers; 109 / 187 / 171 lines; one "engine" (N3) |

## Defects, ranked

**D1 (should-fix) — W1's production gate is unpinned.** The only code that decides which hosts may join is
`NodeRenderer.ts:339` `{ hyphensAuto: styles.hyphens === 'auto' }`. Pin (d) never goes through it: the test's `plan()` helper
re-computes `buildStyles(...).hyphens === 'auto'` itself. Executed: SK6-w7 (`hyphensAuto: true`) and SK6-w8 (`!== 'manual'`) leave all
25 W1/InlineRuns tests and `NodeRenderer.test.tsx` green, while the census shows `hyphens-out-of-flow-001` (web P 1, must-not-move)
change. W1-m4 only proves the gate INSIDE `joinWordsAroundInertOutOfFlow`. Fix: an (f)-style pin that renders -001 box 4 (and a
`hyphens: none` copy of -002 box 4) through `NodeRenderer`, asserting `>high<` … `>­way<` stays split; re-run SK6-w7/w8 to RED.

**D2 (should-fix) — "counted, never silent" is not true on web.** `RunsPlacement.joinedOutOfFlowMembers` is written in
`InlineRuns.ts:170` and read nowhere (`grep -rn joinedOutOfFlowMembers runtimes/web/src apps/web-harness/src`: the definition and the
producer only). Compose's twin is printed in the fold breadcrumb (`ComponentRenderer.kt:3872`). The deviation from spec 03 §4.1
(rule 2: "paints the entries in order") is therefore silent at runtime. Fix: one line in `NodeRenderer` (a call-site condition) or
in `resolveRuns`, alongside its existing rule-5 `console.warn`: a breadcrumb when `joined > 0`; or reword the claim.

**D3 (should-fix, test gaps) — W1 branches with no pin at all** (each mutant GREEN under the full W1 file + InlineRuns):
- SK6-w1: the structural refusal (`children` / `runs` / `decorations`) deleted. This is the one that guards real paint: a member with
  painting children would move. (The earlier attempt's SK-x8 agrees.)
- SK6-w2: `FIXED` removed from the out-of-flow set.
- SK6-w5: the `pushText` continuation branch disabled (also SK-x3 / x4 / x5 of the earlier attempt).
- The alpha threshold loosened to `a < 0.5` (earlier attempt SK-x7); SK6-w9 / SK6-w10: the decimal-string alpha and `{keyword}` dialects.
Fix: add (e) variants (a member with a child, with `meta.runs`, with `meta.decorations`; alpha 0.3), a FIXED member that joins, and a
`[text, M, text, text]` continuation shape. None of these reach the corpus today (census), so nothing moves at the gate.

**D4 (should-fix, honesty label) — semi-replaced-stretch-other web.** The note's "Expected from L6" counts it as "+1 at MED" with no
caveat. Looked at: the label box paints **"abel"** (its "l" lies under the green border) where the ref paints "label", and that is
not RS's to fix. A P there is DEGENERATE in part unless the PR names the residue, as the plan does for the lost `<strong>`. Its
geometry key is report-only and checks only the x192 column edge, so a `GEOMETRY OK` will not certify the label.

**Nits.**
- N1. baseline-with-orthogonal-flow-001 web is an `up-or-stay` row ("stays or rises"), but the replay gives 0.9854 → 0.9850 / 0.9851.
  That is inside rule 2's −0.002 band, so it is not a revert risk. The picture stays DEGENERATE (`aaa` on its own line, `bbb ccc` on
  line 2). Record it, so that a −0.0004 is not read as a leak.
- N2. The test header and the note say DOC002 is "all 15 components"; it has 14 (verbatim nonetheless).
- N3. `ComposedRootSeparator.ts:25` says "The engine entry point" (vocabulary rule: "runtime").
- N4. The root walk has no `WPT_MODE` gate, where the child walk needs `WPT_MODE && WPT_COMPOSED_MODE`. `buildCaptureUrl` can emit
  `wptComposed` without `wpt`. No corpus effect: TITAN always sets both.
- N5. The docs hand-off cites spec 03 §4.1 "rule 1". The rule W1 departs from is rule 2.
- N6. W1-m5 is labelled "Color requirement dropped", but it is `|| true` (any member). The precise mutant (SK6-w3 `return outOfFlow`) is
  RED on (e) "no Color", so the claim holds in substance.
- N7. `step0-probe.out.txt` was produced by the 19:09 copy of the script. The file on disk was edited at 19:24 (disclosed), so the
  byte identity of the V0–V6 code that ran cannot be checked.
- N8. D7 (the lane's own flag) stands: `scope-pseudo-element` web is `up-or-stay` in `expectations.json` but "undirected" in §2's table.
  The PNG and the lane's replay both say up (boxes x118/220 → ref x123/229 with two separators).

## What the lane got right (checked, not assumed)

- The pure lift is corpus-wide byte-identical (0/1435 at canvas AND per-test level). The child-level suite `interInlineWs` is green
  and catches four of my child-walk mutants.
- Every RS separator lands where the ref has source white space. Looked at on 007 / 008 / 022 / semi ×2 / static / appearance /
  attr. The hidden-input separator in appearance-auto-input sits at line start and collapses. The 6 block-in-inline ones sit in
  zero-height anonymous lines.
- The W1 DOM change is exactly boxes 3-6 of -002 → `<span>highway</span><span member>` (V3's shape, 46 px at step 0).
- The revert units are complete and disjoint. RS = `ComposedRootSeparator.ts`, `tests/ui/ComposedRootSeparator.test.ts`, plus
  `seam-1.patch` (`ComponentRenderer.tsx`, `ComposedCaptureGallery.tsx`, `tests/ui/ComposedRootSeparatorWire.test.tsx`). W1 = the 4
  runtime paths. Each compiles and tests green alone. `integrate-seams.sh` reads `wave54-web-tail/seam-2.patch` by exact path, so the
  `branchH-not-landed/seam-2.patch` record cannot be picked up.
- The gate fixtures cannot reach W1: none of the 9 `gate-fixtures.txt` files mentions `hyphens`.

## What I could not check

- Pixels after the change: no Chromium. The predicted floors (007 ≥ 0.975, 008 ≥ 0.965, 022 ≥ 0.96, -002 ≥ 0.995) rest on the
  brief's replays plus my at-risk replays. Those are pixel shifts of 4/5 px, not a sub-pixel 4.4-px layout.
- That Chromium keeps `bbb ccc` on line 2 of baseline-with-orthogonal-flow-001, and that block-in-inline-015-print stays
  byte-identical.
- `integrate-seams.sh --dry-run` was not run: it creates a worktree, which this lane may not do. `git apply --check` on HEAD /
  7cce3b22 stands in for it.
- Full suites: by rule, only the focused files listed above.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (read-only for this skeptic); export trees
built by `skeptic/sk6-census/mk.sh` in the session scratchpad (base, lift, rs, w1, landed, w1u), no Gradle.
