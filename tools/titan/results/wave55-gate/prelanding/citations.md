# wave 55 · pre-landing auditor CITATIONS (PLAN §8 step 4 ¶1)

Read-only audit of the lane notes `tools/titan/results/wave55-<lane>/_note.md`. Nothing on the shared tree was edited
except this report; patches were applied only in a throwaway worktree (`<scratchpad>/wave55-audit-citations`,
`git worktree add --detach … HEAD`, removed at the end). No gradle / xcodebuild / vite / device / test-all.

**Tree.** The task named HEAD 611c9f93; the shared tree is now at **4e789976** ("wave55-plan/gate checkpoint: instrument
rounds 3–5 …"). `git diff --name-only 611c9f93 HEAD` touches only `tools/titan/results/` (no `runtimes/`, `schema/`,
`apps/`, `tools/titan/*.mjs|sh`, `tools/visual/` file), so every base-relative pointer in the notes still reads the
same bytes, and every lane patch applies on HEAD (the five that do not apply alone are the stacked ones, by design:
L1 `unit-NB-ios-b`, L3 `unit-U2`, L4 `unit-U2-android` / `seam-3` / `seam-4`).

**Lanes audited:** hyphen-ios-linebox (L1), cbb-android (L2), positioned-parent-slot (L3), ua-heading-margin (L4),
android-paint-order (L6), instruments (L7). **Pending:** counter-suffix-rows (L5): its last `STATUS:` line is
`STATUS: PARTIAL — fix-r1 in progress` (`_note.md:374`; a fix lane is still writing), so it was skipped.

## Verdict per check

| check | L1 | L2 | L3 | L4 | L6 | L7 |
|---|---|---|---|---|---|---|
| gate-cell citations re-resolved with `cells.mjs` (wave54-final, wave55-open) | 15/15 | 14/14 | 14/14 | 8/8 | 15/15 | 42/42 |
| tracked paths resolve (git ls-files / lane dir / own patch) | OK | OK | OK | OK | OK | OK |
| `:LINE` pointers read with `sed -n` | 5/5 | OK (1 nit) | 10/10 | 12/12 | OK (1 nit) | OK (1 nit) |
| no scratchpad pointer outside `TREES:` | OK | OK | OK | OK | OK | OK |
| MUTATING / RESTORED pairs, post == pre | 17/17 | 26/26 | 20/20 | 29/29 | 28/28 | 46/46 (interleaved, nit) |
| unit sections = paths of the unit's patches (`diff --git` headers) | EXACT ×2 | EXACT ×2 | EXACT ×2 | EXACT ×4 | EXACT ×3 (+ fix-r1 restatement ×3) | EXACT ×9 (U1: trackedAtHead) |
| every `*.patch` has `patch: <name> sha256` = file bytes; no `patch:` line names a missing file | 4/4 | 3/3 | 3/3 | 8/8 | 3/3 | 8/8 |
| last `STATUS:` line is COMPLETE | yes | yes | yes | yes | yes | yes |

**108 cell citations** (61 strict `<run> <sec>/<test> <platform> <P|f> <ssim>`, 47 loose ones whose run is named by
their table / section header — every one of them `wave54-final`): **108 resolve exactly**, and every cited cell is
byte-equal between wave54-final and wave55-open. The P/f-only rows (gradient-powerless-hue ×3) resolve f. Also
checked: every other `P|f <number>` in the six notes is one of these 108 (scan: none uncovered); the three cells L6
calls unscored (backdrop-filter-clip-rect-zoom, anchor-center-overflow-003, display-contents-dynamic-pseudo-insertion-001)
are unscored in both runs; L2's "android 1128 → 1129 / 1362" matches wave54-final's 1128 P / 1362 scored; L6's "the other
15 grid / flex U2 carriers … stay P" holds (all 15 P).

**Pointers and sha256 claims re-derived in the throwaway** (each lane's patches applied in landing order on HEAD):
- L1: ComponentRenderer.swift 6291 lines, sha256 417853ce…26f3 at base; :5055-5069 = the `leading` statement + banner,
  :5151-5152 = `lineCountIsExact`, :5154 = `lineHeightPx: effectiveLineHeight`, :5335 = `.padding(.vertical, leading.halfLeading)`;
  after NB-ios 6309 lines, sha256 208e41ba…c5f5, :5353 = the padding line; NormalLineBox.swift 929108b6… (NB-ios) and
  a5f073ee… (+NB-ios-b) = the bracket pre-shas; 160 / 191 / 178 / 199 lines; seam-1 +34 / −16, seam-1b +7 / −1.
- L2: ContainingBlockBands.kt f4bb43ff… = `git show 5b333c8a:…`; DynamicValueResolver.kt 19b15add… (= 5b333c8a's; HEAD =
  5b333c8a^), 663 → 671; test 447 → 626; ComponentRenderer.kt 8488 → 8492, seam-only sha 7ddeb9b0… = M-U1d's pre;
  PercentContentBoxBand.kt ad266adf… (191), SizingApplier.kt e8f62e49… (561 → 564), test 199; payload documents
  clip-3 965c7730… / autopos-htb-ltr 421c514a… identical in wave53-final, wave54-final, wave55-open and wave54-probe;
  wave54-probe geometry record: gating 6 PASS / 2 FAIL (both clip-3), xor 5591, corners [251, 250, 252, 252].
- L3: base ComponentRenderer.kt 2445d9ea…, :3644-3661 the host-gated mount, :3650 `val outOfFlowSlotMount: Modifier =`;
  SeamReachabilityTest.kt 7a1b061f…, :182 `"transforms/TransformInheritance.kt",`; PositionedParentFlowSlot.kt :87-89
  "Host-gated like RC1"; ScreenshotCaptureScreen.kt :587 `LocalWptComposedMode provides true`; U1 state: gate 3f9584c4…
  (140 lines), CR bd391001… (8492), GateTest :38 / :40 / :42 = the (F,T,F) / RTL / dark-stage rows; full state: gate
  754085db… (198), RtlOrigin dc7a408e… (178), FlowSlot be07853b… (179), SeamReachabilityTest 241. The cross-lane dry run
  (L2 seam-1 → U1 → seam-1 → U2, then −U2 −seam-1 −U1) re-executed on the CURRENT L2 bytes: applies and reverses.
- L4: HEAD CR.kt :1161 `standsDown = …headingStandsDown(component)`, :1162 `)`, :1165 `val unresolvedProperties`; seam-1
  hunk @1160 +17/−1, seam-3 hunk @1149 +8/−5; SeamReachabilityTest :159 `"content/PseudoGeneratedBox.kt",`; CR.swift
  :340-356 `mergedProperties(now:)` (seam-2 hunk @340); DynamicValueResolver.kt :191 `fontSizePxOf`, L2's hunks at :206 /
  :232; UAElementFontRule.kt :26-27 (U1 state) names UaPositionedHeadingMargin.kt; full state: 8507 / 6315 / 197 / 292 /
  245 / 252 / 260 / 184 / 179 lines; every bracket pre-sha equals the landed bytes; the four restored files equal
  `wave54-ua-heading-face/held/SHA256SUMS.txt`; after seam 3 `headingStandsDown(` has no caller in `runtimes/`.
- L6: HEAD EffectsFacade.kt c2d5f2c9… / FilterExtractor.kt 21af6aa5…; full (fix-r1) state BehindContentBorder.kt
  c67accab… (200), StyleApplier.kt e0b379be… (1194), BorderRadiusApplier.kt 3ad5a63a… (284), BorderSideApplier.kt
  cda285c3… (647) — so M2 / M9 (not re-run in fix-r1) did run on the delivered bytes of those files; BordersFacade 152,
  BorderRadiusSelfPaintTest 257, BorderSidesBehindContentTest 175, EffectsClipFilterOrderTest 79, BehindContentBorderTest 266.
- L7: section-runner.sh :375 / :416 / :435 / :620 = extract / combine / convert / split; InertOutOfFlowMember.kt :89-114
  `admits`; InlineRunFold.kt :165 / :177 / :460-467; ComponentRenderer.kt :3905-3906 the fold breadcrumb; U1's three files
  tracked, sha256 bd8d5c7f… / d92563fa… / 999f22a7…; landed wire-differential.mjs 97fc097b… / .sh f8a73de0… (100755) =
  the last U2 bracket pre-shas; ab-arms55.sh 5cd6fccb… → 021c7845… (319), its self-test record cites 021c7845, the three
  proof records cite 5cd6fccb; `--help` prints 2-66; every stated line count (144 / 180 / 173 / 248 / 263 / 191 / 185 / 80 /
  110 / 169 / 1403 / 110 / 55 / 34 / 67 / 36 / 68 / 85 / 200 / 51) matches; [W-L7a]'s `window-run.txt` cites the current
  U2 / U5 sha256 (6a9d7edf… / 0ff38b3b…). The U4 spec paragraph carries no line numbers.
- Geometry self-tests re-run on the CURRENT probe bytes (4e789976 changed hyphen-ios-linebox / ua-heading-margin /
  android-paint-order `.geometry.py`): `geometry-gate.py wave54-final --base <base> --lanes Ln --self-test` HOLDS for L1
  (3 gating FAIL / 15 control PASS / 11 report FAIL, both bases), L2 (8 / 22 / 3), L3 (7 / 32 / 2, both bases), L4 (12 / 34 /
  8), L6 (7 / 29 / 1), L7 (0 / 12 / 0) — the notes' counts still describe the current probes.

## Mismatches (each with its line)

1. **must-fix · L6 · plan regeneration owed.** `wave55-android-paint-order/_note.md:138-144` (builder census GROWTH 1)
   and `:556-571` (fix-r1 "Plan regeneration need" items 1 and 4) ask for
   `wpt__css-display__display-contents-dynamic-pseudo-insertion-001` in L6 `mayMove` BY RULE and for two `mayMoveRule`
   wordings ("the band now inside the element's opacity group" — anchor-center-overflow-003; "a UA-widget replica now
   over the band" — flex-gap-decorations-023). At HEAD 4e789976 `expectations.json` L6 `mayMove.android` has 240 stems,
   none of them that document, and `mayMoveRule` has neither wording. PLAN §0 "A census that grows regenerates the plan
   before landing" / §8 step 4 ¶2: regenerate via `plan-build.py` → `plan-prose.py --fill` / `--check` (+ the
   readers' recalibration) in one commit before the landing; otherwise a stage-2 css-display move reads as an R4 leak.
2. **should-fix · L6 · stale TREES pointer.** `_note.md:334` "Fix worktree: the FIRST `TREES:` line" — the first line is
   now `TREES: …/wave55-reverify-L6` (`:1`), prepended by the re-verifier; the fix worktree is `:2` (`wave55-fix-L6`).
   A resumed fix lane following the sentence re-opens the re-verifier's tree (PLAN §8 step 3 resume rule). Fix: name
   `wave55-fix-L6` instead of "FIRST".
3. **should-fix · L6 · the re-verify pass is not in the note.** `reverify-r1/` (census, revert states, and 9 bracketed
   mutations in `reverify-r1/mutations.log.txt`, all post == pre) is referenced nowhere in `_note.md` except the `TREES:`
   line; its `mut.sh` writes brackets outside the note on purpose (to keep `STATUS: COMPLETE` last), whereas PLAN §0 puts
   them in the note. One of its mutations SURVIVED: `reverify XR3` (`mutations/XR3.edit`: `config.overflow.shouldClip` →
   `config.overflow.clipsX` on the two StyleApplier legacy-branch lines) → `gradle rc=0 … tests=36 failed=0`; no verdict
   (equivalent mutant vs an unpinned clip-on-one-axis case, e.g. `overflow-y: clip` with `overflow-x: visible`) is recorded
   anywhere on disk. Fix: a pointer section above the final `STATUS:` line naming `reverify-r1/` and XR3's adjudication
   (or a pin that kills it).
4. **should-fix · L1 L2 L3 L4 L6 L7 · scratchpad paths in lane-dir records** (not the notes). PLAN §8 step 18: "`grep` for
   a scratchpad path returns nothing (lane notes' `TREES:` lines excepted)". Files carrying the session scratchpad path
   today: L1 4 (`land-units.check.L1.txt`, `skeptic.md`, `skeptic-land-check.out.txt`, `skeptic-land-check.L1L4.out.txt`),
   L2 2 (`skeptic.md`, `skeptic-land-check-L2.txt`), L3 3 (`skeptic.md`, `skeptic/land-units-check-L3.txt`,
   `skeptic/mutation-log.txt`), L4 41 (`skeptic.md` + 40 `mutations/*.{red,green}.txt` gradle / xcodebuild logs), L6 1
   (`skeptic.md`), L7 2 (`skeptic.md`; `unit-U3.patch`, whose two REMOVED lines carry the base record's scratch path —
   disclosed at `instruments/_note.md:393`). Fix before the records commit: rewrite to `<wt>` / `<scratch>` (as L7 did
   for its Swift logs) or record an explicit exemption for the patch's `-` lines.
5. **nit · L7 · pointer baselines mixed.** `instruments/_note.md:123` `InertOutOfFlowWordJoin.ts:98-120` is BASE numbering
   (U4's comment re-wrap moves `isInertOutOfFlowMember` to :99-121), while `:126` `InlineRuns.ts:58`
   (`joinedOutOfFlowMembers`) is POST-U4 numbering (base :57; base :58 is `}`). Label each, or cite symbols (§8 step 18).
6. **nit · L2 · base-only pointer handed to the docs pass.** `cbb-android/_note.md:240` ":254-256" — at HEAD the phrase
   "a px band cannot be added to a fraction" is :254-255 (:256 is the next bullet, `Calc`); after U2 lands it is :257-258.
   The item joins C9 for the closing docs pass, which reads the landed tree: re-point or cite by phrase.
7. **nit · L6 · mutation pointer on the wrong bytes.** `android-paint-order/_note.md:180` "M2 restore `result.border(` at
   :207" — :207 is HEAD's line; M2 mutated the U1-state file (pre 3ad5a63a…), where the call site is :210.
8. **nit · L7 · interleaved brackets.** `instruments/_note.md:481-506`: `MUTATING …HyphenateCharacterExtractor.swift`
   (U6-S1, :481) stays open across the five U2 pairs (:483-:492) until :493, and U6-S2 (:494) across the next five
   (:496-:505) until :506 — two mutation drivers in flight in one worktree. Every pair is post == pre and the suites are
   disjoint (node vs Catalyst), but the concurrency is not disclosed; add one line.
9. **nit · L4 · window request paraphrases a superseded stage1Decision.** `ua-heading-margin/_note.md:262-265` ("quoted,
   not re-invented", :253) predates the round-4 D39 clause committed in 4e789976 (`expectations.json`
   `lanes["L4-ua-heading-margin"].stage1Decision` now adds: a face-present native line whose only reason is `face xor:`
   is decided by the recorded look, never by widening FACE_XOR_TOL; the probe edit sets `FACE_RUN_DEFAULT = 'wave55-pre'`).
   The orchestrator reads expectations.json, so this is prose only.
10. **nit · L1 → PLAN prose.** `hyphen-ios-linebox/_note.md:154-158`: the L1 census puts `all-prop-001` OUTSIDE the
    NB-ios radius (`all: initial` resets its line-height), but PLAN.md:681-682 (§2 L1 R1) still counts "four" in-radius
    tripwire controls; `expectations.json` is unaffected (all-prop-001 ios is a must-not-move row either way), so no
    regeneration — a PLAN prose fix for the next `plan-prose.py --fill`.

No other mismatch: no unresolved path, no scratchpad pointer in a note outside `TREES:`, no unpaired or sha-changed
bracket, no unit-section / patch-header difference, no `patch:` line off its file's bytes, no non-COMPLETE last STATUS
among the six. Records that exist but are named by no note (informational): `android-paint-order/fix-r1/builder-patches/`
(the superseded builder cut as `*.patch.txt`, sha256 27dd5bc2… / b10eae97… / 7bf15926… = the note's "builder cut" lines,
not `*.patch`, so `land-units.sh --check` does not see them) and `android-paint-order/reverify-r1/` (item 3).

## Regeneration needed before landing
- **L6-android-paint-order** — `mayMove` + `mayMoveRule` (item 1). L1, L2, L3, L4, L7: censuses, carriers, units and
  predictions IDENTICAL to `expectations.json` at 4e789976 (carriers L1 ios 14; L2 android 14 / U2 1; L3 android 9 / U1 3 /
  U2 7; L4 4 + 4 / U2 3 + 3; L7 0). L5: pending.

## How it was checked (reproducible; no device)
- Citations: a regex over each note for `<run> <sec>/<test> <platform> <P|f> <ssim>` (backticks stripped) plus a
  hand-curated list of the loose table rows (run = the section / table header named per row below), resolved in ONE
  call `node tools/titan/results/wave52-gate/cells.mjs '<all 103 terms>' wave54-final wave55-open` and matched on the
  exact key `<sec>/<test>.<ext>|<platform>`.
- Paths: every path-like token outside `TREES:` resolved against `git ls-files` ∪ untracked ∪ ignored ∪ the lanes'
  own patch-created files; `…/` and bare names by suffix / basename; WPT test names, future outputs
  (`wave55-gate/stage1/wave55-pre/geometry.json`), gitignored harness files and `<placeholder>` paths set aside by hand.
- Brackets: a sequential parser (open per file, RESTORED must match file and sha).
- Units: `landing-manifest.json` `units[].applyOrder` → `diff --git` headers, compared with the section's path list.
- `patch:` lines: the same `sed` expression `land-units.sh --check` uses, against `shasum -a 256`.

## Appendix — every citation and its gate value
| lane | line | form | cell | cited | wave54-final | wave55-open | status |
|---|---|---|---|---|---|---|---|
| hyphen-ios-linebox | :58 | strict | `wave54-final css-text/hyphens/hyphenate-character-004 ios` | f 0.9477 | f 0.9477 | f 0.9477 | OK |
| hyphen-ios-linebox | :185 | strict | `wave54-final css-text/hyphens/hyphenate-character-001 ios` | P 0.9611 | P 0.9611 | P 0.9611 | OK |
| hyphen-ios-linebox | :186 | strict | `wave54-final css-text/hyphens/hyphenate-character-003 ios` | P 0.957 | P 0.957 | P 0.957 | OK |
| hyphen-ios-linebox | :187 | strict | `wave54-final css-text/hyphens/hyphenate-character-004 ios` | f 0.9477 | f 0.9477 | f 0.9477 | OK |
| hyphen-ios-linebox | :188 | strict | `wave54-final css-text/hyphens/hyphenate-character-002 ios` | P 0.9534 | P 0.9534 | P 0.9534 | OK |
| hyphen-ios-linebox | :190 | strict | `wave54-final css-text-decor/text-decoration-dotted-001 ios` | f 0.5318 | f 0.5318 | f 0.5318 | OK |
| positioned-parent-slot | :12 | strict | `wave54-final selectors/dir-style-02a android` | f 0.9425 | f 0.9425 | f 0.9425 | OK |
| positioned-parent-slot | :14 | strict | `wave54-final css-writing-modes/dynamic-offset-rtl-001 android` | P 0.9952 | P 0.9952 | P 0.9952 | OK |
| positioned-parent-slot | :15 | strict | `wave54-final css-text/bidi/bidi-tab-001 android` | P 0.9805 | P 0.9805 | P 0.9805 | OK |
| positioned-parent-slot | :253 | strict | `wave54-final selectors/dir-style-02a ios` | f 0.9455 | f 0.9455 | f 0.9455 | OK |
| positioned-parent-slot | :253 | strict | `wave54-final css-writing-modes/dynamic-offset-rtl-001 ios` | P 0.9955 | P 0.9955 | P 0.9955 | OK |
| ua-heading-margin | :234 | strict | `wave54-final css-text-decor/text-decoration-inset-005 ios` | f 0.8995 | f 0.8995 | f 0.8995 | OK |
| ua-heading-margin | :235 | strict | `wave54-final css-text-decor/text-decoration-inset-006 ios` | f 0.8987 | f 0.8987 | f 0.8987 | OK |
| ua-heading-margin | :236 | strict | `wave54-final css-text-decor/text-decoration-inset-014 ios` | f 0.9238 | f 0.9238 | f 0.9238 | OK |
| ua-heading-margin | :237 | strict | `wave54-final css-text-decor/text-decoration-inset-014 android` | f 0.923 | f 0.923 | f 0.923 | OK |
| ua-heading-margin | :238 | strict | `wave54-final css-text-decor/text-decoration-inset-005 android` | f 0.9 | f 0.9 | f 0.9 | OK |
| ua-heading-margin | :239 | strict | `wave54-final css-text-decor/text-decoration-inset-006 android` | f 0.8993 | f 0.8993 | f 0.8993 | OK |
| ua-heading-margin | :240 | strict | `wave54-final css-text-decor/text-decoration-inset-011 ios` | f 0.6999 | f 0.6999 | f 0.6999 | OK |
| ua-heading-margin | :241 | strict | `wave54-final css-text-decor/text-decoration-inset-011 android` | f 0.6852 | f 0.6852 | f 0.6852 | OK |
| instruments | :238 | strict | `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-101 android` | P 0.9736 | P 0.9736 | P 0.9736 | OK |
| instruments | :239 | strict | `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-101 ios` | P 0.9943 | P 0.9943 | P 0.9943 | OK |
| instruments | :240 | strict | `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-101 web` | P 0.9932 | P 0.9932 | P 0.9932 | OK |
| instruments | :241 | strict | `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-102 web` | P 0.9791 | P 0.9791 | P 0.9791 | OK |
| instruments | :242 | strict | `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-103 android` | P 0.9913 | P 0.9913 | P 0.9913 | OK |
| instruments | :243 | strict | `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-103 ios` | P 0.9978 | P 0.9978 | P 0.9978 | OK |
| instruments | :244 | strict | `wave54-final css-counter-styles/arabic-indic/css3-counter-styles-103 web` | P 0.9986 | P 0.9986 | P 0.9986 | OK |
| instruments | :245 | strict | `wave54-final css-counter-styles/counter-suffix web` | P 1 | P 1 | P 1 | OK |
| instruments | :246 | strict | `wave54-final css-gaps/flex/flex-gap-decorations-033 android` | P 1 | P 1 | P 1 | OK |
| instruments | :247 | strict | `wave54-final css-gaps/flex/flex-gap-decorations-033 ios` | P 0.9993 | P 0.9993 | P 0.9993 | OK |
| instruments | :248 | strict | `wave54-final css-gaps/flex/flex-gap-decorations-033 web` | P 1 | P 1 | P 1 | OK |
| instruments | :249 | strict | `wave54-final css-text/bidi/bidi-lines-001 android` | P 0.9629 | P 0.9629 | P 0.9629 | OK |
| instruments | :250 | strict | `wave54-final css-text/bidi/bidi-lines-001 ios` | P 0.9926 | P 0.9926 | P 0.9926 | OK |
| instruments | :251 | strict | `wave54-final css-text/bidi/bidi-lines-001 web` | P 0.9998 | P 0.9998 | P 0.9998 | OK |
| instruments | :252 | strict | `wave54-final css-text/bidi/bidi-lines-002 android` | P 0.9818 | P 0.9818 | P 0.9818 | OK |
| instruments | :253 | strict | `wave54-final css-text/bidi/bidi-lines-002 ios` | P 0.989 | P 0.989 | P 0.989 | OK |
| instruments | :254 | strict | `wave54-final css-text/bidi/bidi-lines-002 web` | P 0.993 | P 0.993 | P 0.993 | OK |
| instruments | :255 | strict | `wave54-final css-text/bidi/bidi-tab-001 ios` | P 0.9829 | P 0.9829 | P 0.9829 | OK |
| instruments | :256 | strict | `wave54-final css-text/bidi/bidi-tab-001 web` | P 0.9978 | P 0.9978 | P 0.9978 | OK |
| instruments | :257 | strict | `wave54-final css-text/boundary-shaping/boundary-shaping-009 android` | f 0.7531 | f 0.7531 | f 0.7531 | OK |
| instruments | :258 | strict | `wave54-final css-text/boundary-shaping/boundary-shaping-009 ios` | f 0.8919 | f 0.8919 | f 0.8919 | OK |
| instruments | :259 | strict | `wave54-final css-text/boundary-shaping/boundary-shaping-009 web` | f 0.9482 | f 0.9482 | f 0.9482 | OK |
| instruments | :260 | strict | `wave54-final css-text/hyphens/hyphenate-character-005 ios` | f 0.9428 | f 0.9428 | f 0.9428 | OK |
| instruments | :261 | strict | `wave54-final css-text/hyphens/hyphenate-character-005 web` | P 0.974 | P 0.974 | P 0.974 | OK |
| instruments | :262 | strict | `wave54-final css-text/hyphens/hyphens-out-of-flow-001 web` | P 1 | P 1 | P 1 | OK |
| instruments | :263 | strict | `wave54-final css-text/hyphens/hyphens-out-of-flow-002 android` | P 0.9943 | P 0.9943 | P 0.9943 | OK |
| instruments | :264 | strict | `wave54-final css-text/hyphens/hyphens-out-of-flow-002 ios` | P 0.9971 | P 0.9971 | P 0.9971 | OK |
| instruments | :265 | strict | `wave54-final css-text/hyphens/hyphens-out-of-flow-002 web` | P 1 | P 1 | P 1 | OK |
| instruments | :266 | strict | `wave54-final css-writing-modes/bidi-plaintext-br-001 android` | f 0.9482 | f 0.9482 | f 0.9482 | OK |
| instruments | :267 | strict | `wave54-final css-writing-modes/bidi-plaintext-br-001 ios` | P 0.9572 | P 0.9572 | P 0.9572 | OK |
| instruments | :268 | strict | `wave54-final css-writing-modes/bidi-plaintext-br-001 web` | P 1 | P 1 | P 1 | OK |
| instruments | :269 | strict | `wave54-final selectors/dir-selector-change-003 android` | P 0.9994 | P 0.9994 | P 0.9994 | OK |
| instruments | :270 | strict | `wave54-final selectors/dir-selector-change-003 ios` | P 0.9996 | P 0.9996 | P 0.9996 | OK |
| instruments | :271 | strict | `wave54-final selectors/dir-selector-change-003 web` | P 1 | P 1 | P 1 | OK |
| instruments | :272 | strict | `wave54-final selectors/dir-selector-change-004 android` | P 0.9944 | P 0.9944 | P 0.9944 | OK |
| instruments | :273 | strict | `wave54-final selectors/dir-selector-change-004 ios` | P 0.9958 | P 0.9958 | P 0.9958 | OK |
| instruments | :274 | strict | `wave54-final selectors/dir-selector-change-004 web` | P 1 | P 1 | P 1 | OK |
| instruments | :275 | strict | `wave54-final selectors/dir-style-02a ios` | f 0.9455 | f 0.9455 | f 0.9455 | OK |
| instruments | :276 | strict | `wave54-final selectors/dir-style-02a web` | P 1 | P 1 | P 1 | OK |
| instruments | :277 | strict | `wave54-final selectors/dir-style-03a android` | P 0.9974 | P 0.9974 | P 0.9974 | OK |
| instruments | :278 | strict | `wave54-final selectors/dir-style-03a ios` | P 0.9986 | P 0.9986 | P 0.9986 | OK |
| instruments | :279 | strict | `wave54-final selectors/dir-style-03a web` | P 1 | P 1 | P 1 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-001 ios` | P 0.9792 | P 0.9792 | P 0.9792 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-002 ios` | P 0.9695 | P 0.9695 | P 0.9695 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-003 ios` | P 0.9781 | P 0.9781 | P 0.9781 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-004 ios` | P 0.9774 | P 0.9774 | P 0.9774 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-005 ios` | P 0.9774 | P 0.9774 | P 0.9774 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-006 ios` | P 0.9714 | P 0.9714 | P 0.9714 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-007 ios` | P 0.9764 | P 0.9764 | P 0.9764 | OK |
| hyphen-ios-linebox | :189 | loose | `wave54-final css-text/boundary-shaping/boundary-shaping-008 ios` | P 0.9789 | P 0.9789 | P 0.9789 | OK |
| hyphen-ios-linebox | :190 | loose | `wave54-final css-text-decor/text-decoration-dotted-002 ios` | f 0.5426 | f 0.5426 | f 0.5426 | OK |
| cbb-android | :215 | loose | `wave54-final filter-effects/backdrop-filter-nested-border-radius-clip-3 android` | f 0.9574 | f 0.9574 | f 0.9574 | OK |
| cbb-android | :216 | loose | `wave54-final filter-effects/backdrop-filter-nested-border-radius-clip android` | P 0.9649 | P 0.9649 | P 0.9649 | OK |
| cbb-android | :217 | loose | `wave54-final filter-effects/backdrop-filter-nested-border-radius-clip-2 android` | P 0.9689 | P 0.9689 | P 0.9689 | OK |
| cbb-android | :218 | loose | `wave54-final filter-effects/backdrop-filter-nested-border-radius-clip-4 android` | P 0.977 | P 0.977 | P 0.977 | OK |
| cbb-android | :219 | loose | `wave54-final css-flexbox/abspos/abspos-autopos-htb-ltr android` | P 0.9966 | P 0.9966 | P 0.9966 | OK |
| cbb-android | :219 | loose | `wave54-final css-flexbox/abspos/abspos-autopos-vlr-ltr android` | P 0.9966 | P 0.9966 | P 0.9966 | OK |
| cbb-android | :219 | loose | `wave54-final css-flexbox/abspos/abspos-autopos-vrl-ltr android` | P 0.9966 | P 0.9966 | P 0.9966 | OK |
| cbb-android | :220 | loose | `wave54-final css-flexbox/abspos/abspos-autopos-htb-rtl android` | P 0.9869 | P 0.9869 | P 0.9869 | OK |
| cbb-android | :221 | loose | `wave54-final css-flexbox/abspos/abspos-autopos-vlr-rtl android` | P 0.9966 | P 0.9966 | P 0.9966 | OK |
| cbb-android | :221 | loose | `wave54-final css-flexbox/abspos/abspos-autopos-vrl-rtl android` | P 0.9966 | P 0.9966 | P 0.9966 | OK |
| cbb-android | :222 | loose | `wave54-final css-position/position-absolute-semi-replaced-stretch-button android` | f 0.9739 | f 0.9739 | f 0.9739 | OK |
| cbb-android | :223 | loose | `wave54-final css-position/position-absolute-semi-replaced-stretch-input android` | f 0.4117 | f 0.4117 | f 0.4117 | OK |
| cbb-android | :224 | loose | `wave54-final css-position/position-absolute-semi-replaced-stretch-other android` | f 0.4396 | f 0.4396 | f 0.4396 | OK |
| cbb-android | :225 | loose | `wave54-final css-gaps/flex/flex-gap-decorations-006 android` | f 0.8223 | f 0.8223 | f 0.8223 | OK |
| positioned-parent-slot | :219 | loose | `wave54-final selectors/dir-style-02a android` | f 0.9425 | f 0.9425 | f 0.9425 | OK |
| positioned-parent-slot | :220 | loose | `wave54-final css-text/bidi/bidi-tab-001 android` | P 0.9805 | P 0.9805 | P 0.9805 | OK |
| positioned-parent-slot | :221 | loose | `wave54-final css-writing-modes/dynamic-offset-rtl-001 android` | P 0.9952 | P 0.9952 | P 0.9952 | OK |
| positioned-parent-slot | :222 | loose | `wave54-final css-writing-modes/dynamic-offset-rtl-002 android` | P 0.9953 | P 0.9953 | P 0.9953 | OK |
| positioned-parent-slot | :223 | loose | `wave54-final css-writing-modes/dynamic-offset-vrl-rtl-001 android` | P 0.9952 | P 0.9952 | P 0.9952 | OK |
| positioned-parent-slot | :224 | loose | `wave54-final css-writing-modes/dynamic-offset-vrl-rtl-002 android` | P 0.9952 | P 0.9952 | P 0.9952 | OK |
| positioned-parent-slot | :225 | loose | `wave54-final css-anchor-position/anchor-position-multicol-nested-001 android` | f 0.9573 | f 0.9573 | f 0.9573 | OK |
| positioned-parent-slot | :226 | loose | `wave54-final css-anchor-position/anchor-position-multicol-004 android` | P 0.9686 | P 0.9686 | P 0.9686 | OK |
| positioned-parent-slot | :227 | loose | `wave54-final css-text/hyphens/hyphenate-character-005 android` | f 0.9474 | f 0.9474 | f 0.9474 | OK |
| android-paint-order | :259 | loose | `wave54-final filter-effects/backdrop-filter-clip-rect android` | P 0.9559 | P 0.9559 | P 0.9559 | OK |
| android-paint-order | :261 | loose | `wave54-final css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-001 android` | P 0.9619 | P 0.9619 | P 0.9619 | OK |
| android-paint-order | :262 | loose | `wave54-final css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-002 android` | P 0.9635 | P 0.9635 | P 0.9635 | OK |
| android-paint-order | :263 | loose | `wave54-final css-grid/abspos/grid-abspos-staticpos-align-self-001 android` | P 0.9673 | P 0.9673 | P 0.9673 | OK |
| android-paint-order | :263 | loose | `wave54-final css-grid/abspos/grid-abspos-staticpos-justify-self-001 android` | P 0.9667 | P 0.9667 | P 0.9667 | OK |
| android-paint-order | :263 | loose | `wave54-final css-grid/abspos/grid-abspos-staticpos-align-self-vertWM-001 android` | P 0.9612 | P 0.9612 | P 0.9612 | OK |
| android-paint-order | :263 | loose | `wave54-final css-grid/abspos/grid-abspos-staticpos-align-self-rtl-001 android` | P 0.9646 | P 0.9646 | P 0.9646 | OK |
| android-paint-order | :265 | loose | `wave54-final css-grid/abspos/grid-abspos-staticpos-align-self-safe-001 android` | f 0.9441 | f 0.9441 | f 0.9441 | OK |
| android-paint-order | :266 | loose | `wave54-final css-images/gradient/gradient-powerless-hue-hsl android` | f (no value) | f 0.7542 | f 0.7542 | OK |
| android-paint-order | :266 | loose | `wave54-final css-images/gradient/gradient-powerless-hue-hwb android` | f (no value) | f 0.755 | f 0.755 | OK |
| android-paint-order | :266 | loose | `wave54-final css-images/gradient/gradient-powerless-hue-lch android` | f (no value) | f 0.7013 | f 0.7013 | OK |
| android-paint-order | :547 | loose | `wave54-final css-cascade/revert-layer-015 android` | f 0.9663 | f 0.9663 | f 0.9663 | OK |
| android-paint-order | :548 | loose | `wave54-final css-pseudo/first-line-and-placeholder android` | P 0.9743 | P 0.9743 | P 0.9743 | OK |
| android-paint-order | :551 | loose | `wave54-final css-gaps/flex/flex-gap-decorations-023 android` | P 0.9541 | P 0.9541 | P 0.9541 | OK |
| android-paint-order | :465 | loose | `wave54-final css-gaps/flex/flex-gap-decorations-023 android` | ? 0.9541 | P 0.9541 | P 0.9541 | OK |
