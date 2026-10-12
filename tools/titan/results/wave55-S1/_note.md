TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-S1-skeptic

# wave55 S1 — the combined-tree skeptic (wave skill Phase 3; PLAN §8 step 8)

Tree of record: `campaign/applier-campaign`, landed range `b4f64963..5bbc2a41` (records commit `fb94a502` + 27 unit commits;
the shared tree's HEAD is `1b53f3a3`, which adds only `wave55-gate/_note.md` and `sweep-landed.txt`). Every mutation, revert
and build ran in ONE throwaway worktree, `git worktree add --detach <scratchpad>/wave55-S1-skeptic 5bbc2a41` (TREES: line
above), provisioned per PLAN §0 (both `local.properties` copied; `node_modules` and the two workspace `node_modules`
symlinked; the gitignored `tools/titan/runs` and `tools/wpt` symlinked read-only so the corpus pins execute; Swift with
`-derivedDataPath <wt>/.dd`). On the shared tree this pass ran read-only commands only (`git log/show/diff/grep/ls-tree`,
`plan-prose.py --check`, `final55-analysis.gen.py --check`, `cells.mjs`) and wrote only this directory. No device, Chromium,
emulator or simulator. Records in this directory are copies of the scratch originals with the session scratchpad root
replaced by `<scratchpad>` (so §8 step 18's grep stays clean); the scripts therefore need that root put back to re-run.

## Verdict: CLAIM-HOLDS — no must-fix; three should-fix items (docs restamp, a landing record, ship-time scratchpad scrub), nine nits

The landed tree is the plan's tree: every one of the 27 unit commits equals its delivered patches line for line, every seam
hunk sits in the commit and patch the manifest names, the sweep of record is green on all nine suites, every lane's carrier
set re-derives from my own census without an under-reported document, two (or more) pins per lane go red under my own
mutations and green after a byte-exact restore, and every revertOrder chain of `expectations.json` reverts cleanly on the
landed tree, compiles and passes its suite. Nothing found blocks stage 1.

Checks executed, in the task's order: seam-hunk audit (§1) · sweep read + doc rows (§2) · git hygiene (§3) · pointer audit (§4)
· census replay ×7 (§5) · 18 mutation brackets, ≥ 2 per lane (§6) · 26 revert states (§7) · honesty (§8, with
`plan-prose.py --check` and `final55-analysis.gen.py --check`). The Swift suite also ran twice more on the landed bytes
(2273 / 1 skip / 0 failures both times) after one flaky red in a comment-only revert state (§7).

## 1. Seam-hunk audit (executed)

Command: `git diff -U0 b4f64963 5bbc2a41 -- <the four seam files> SeamReachabilityTest.kt`, then `git show -U0 <sha> -- <seam>`
per unit commit (`seam-hunk-map.txt`), and `seamaudit.py` (`seamaudit.out.txt`): for every manifest unit, the multiset of
`+`/`-` lines per path in its commit vs in its `applyOrder` patches.

- `ComponentRenderer.tsx` and `extract-fixture.mjs`: **no hunk** in the range (registry: "no hunk") — HOLDS.
- `ComponentRenderer.kt`: 10 hunks → 6 commits, each in its registered seam patch:
  - :1152 / :1161 → `69f95267` L4 U2-android `seam-3.patch` (row 2); :1162 / :1165 → `85afc360` L4 U1-android `seam-1.patch`
    (row 1); :1968 / :1970 → `5ade247e` L2 U1 `seam-1.patch` (row 3); :3667 / :3674 → `56c69e24` L3 U1 `seam-1.patch` (row 4);
    :4507 → `3a8249d2` L5 U2o-android `seam-4.patch` (row 5); :4230 + :4697 → `c5699c09` L5 U2i-android `seam-5.patch` (row 6).
- `ComponentRenderer.swift`: 15 hunks → 7 commits: `cac1d6a3` L4 U1-ios seam-2 (row 1, :343-361), `1dc521c1` L4 U2-ios seam-4
  (row 2, :356), `2ca54bc8` L5 U1-ios seam-1 (row 3: :3998, :4594, :4602, :4641, :4665, :4689 in its own coordinates),
  `3179cf60` U2o-ios seam-2 (row 4), `5faec331` U2i-ios seam-3 (row 5: :3851 + :4688), `b7108a42` L1 NB-ios seam-1 (row 6:
  cut :5100-5114, insert :5183, :5217), `fc436181` L5 U3-ios seam-6 (row 7).
- `SeamReachabilityTest.kt`: 5 one-hunk commits at the pinned anchors — L4 ×2 after `"content/PseudoGeneratedBox.kt",`
  (`85afc360`, `69f95267`), L5 ×1 after `"images/ImageCandidateChain.kt",` (`3a8249d2`), L3 ×2 after
  `"transforms/TransformInheritance.kt",` (`56c69e24` patch-borne in seam 1, `0c6b071c` in unit-U2) — the C10 shape.
- Each seam patch touches exactly ONE seam file, the registered one; no unit patch touches a seam file (`git-hygiene.txt`).
- No two hunks overlap (the series landed by `git apply`, and per-commit line multisets equal the patches'); every hunk
  carries a comment (the diff above, read in full).
- **Nit N1:** two comment-only hunks are not on §3's anchor list: seam-5's RenderListItemMarker KDoc gap sentence
  (`.kt` base :4201-4204, landed :4228-4232) and seam-3's marker-row banner gap sentence (`.swift` base :3826-3829, landed
  :3850-3854). Both are inside the registered patches and disclosed (`wave55-counter-suffix-rows/_note.md` "Deviations from
  the plan's registry" item 2); §3 rows `.kt` 6 / `.swift` 5 and §10 item 5 do not name them.
- **Nit N2:** the L2 seam hunk's comment opens "Wave 54 (lane L4, CBB-android)" — true provenance (5b333c8a), but the code
  ships in wave 55 (L2 U1, the re-land of a wave-54 revert); a reader of `ComponentRenderer.kt:1969` cannot tell it was
  reverted and re-landed.

## 2. Sweep (read, then re-derived from the raw logs)

`tools/titan/results/wave55-gate/sweep-landed.txt` (tree 5bbc2a41) and its raw logs (`<scratchpad>/sweep-raw-w55/*.log`):

| suite | rc | count | raw-log proof |
|---|---|---|---|
| converter `--rerun-tasks` | 0 | 562 | `4 actionable tasks: 4 executed`, BUILD SUCCESSFUL |
| web runtime | 0 | 1390 | `Test Files 96 passed`, `Tests 1390 passed` |
| web-harness | 0 | 344 | `Tests 344 passed` |
| compose `:runtime:testDebugUnitTest --rerun-tasks` | 0 | 3574 | `15 actionable tasks: 15 executed` (compileDebugKotlin, compileDebugUnitTestKotlin, testDebugUnitTest all ran) |
| android-harness `:app:testDebugUnitTest --rerun-tasks` | 0 | 169 | `39 actionable tasks: 39 executed` |
| swiftui (Catalyst) | 0 | 2273 (1 skipped) | `** TEST SUCCEEDED **`; the skip is NormalLineBoxTests' env-gated SAME-controls log |
| tooling `node --test` | 0 | 2351 (2347 pass, 4 skipped) | `# fail 0` |
| IR conformance `--emit` | 0 | all valid | `IR conformance: all documents valid` |
| ios-harness | 0 | 30 | `** TEST SUCCEEDED **` |

All green. The counts are independently confirmed by `tools/visual/doc-staleness-check.sh` run on 5bbc2a41 in the throwaway
(`doc-staleness.on-5bbc2a41.txt`): it derives converter 562 · web 1390 · compose 3574 · swiftui 2273 · tooling 2351 ·
android-harness 169 · web-harness 344 from source — identical to the sweep. It exits **1**: the restamp has not happened.

**Doc rows to restamp (16; should-fix S1 below):**
- `README.md`:211 web 1381→1390, :212 compose 3486→3574, :214 swiftui 2212→2273, :216 tooling 2340→2351;
- `CLAUDE.md`:291 / :292 / :294 / :296 — the same four;
- `docs/STATUS.md`:2879 / :2880 / :2882 / :2884 — the same four (the :2857-2859 prose is the dated wave-54 gate-tree record);
- `runtimes/web/README.md`:71 (1381→1390); `runtimes/compose/README.md`:15 and `apps/android-harness/README.md`:29
  (3486→3574); `runtimes/swiftui/README.md`:16 (2212→2273).
Unchanged rows: converter 562, web-harness 344, android-harness 169, ios-harness 30, conformance.

Count attribution, re-derived from my revert states (§7): tooling +11 = U2's 9 wire-differential pins + U6-tooling's 2 (U5
adds 0 tests — `sweep-landed.txt`'s "U5 bake entries" is a table edit inside S6-2, nit N8); web +9 = U6-web 7 + U4 2.

## 3. Git hygiene (executed; `git-hygiene.txt`)

- `git status --short` on the shared tree: **empty** at S1 start (this directory is the only write since).
- `git stash list`: **0** entries. `tools/titan/runs/wave55-lock/`: **absent**.
- `git diff --stat b4f64963 5bbc2a41`: 668 files, 84 outside `tools/titan/results/`. Records commit `fb94a502` holds only
  results dirs: the seven lane dirs (581 − 6 files) + the six force-added `wave54-oof-layout/**/out` files (obligation 0(o)).
- Every one of the 84 code paths maps to its lane's §2 `Owned:` list, a seam patch, a §0 shared path, or a §10-item-5
  patch-borne seam pin, and sits in that lane's unit commit (path list per commit read in full). The six L5 seam pins
  (`ListMarkerBidiSeamRasterTests.swift`, `ListMarkerSuffixGap{Hang,Inside}RasterTests.swift`,
  `ListMarkerSuffixGap{HangSeam,RowSeam}Test.kt`, `FontVariantNumericTabularRasterTests.swift`) are the §10 item 5 records.
- Each unit commit's staged set equals the paths its patches name, and its `+`/`-` lines equal the patches' (`seamaudit.py`:
  27 / 27 `equalPaths=True lineMismatch=[]`); each commit body cites its patches' sha256 prefixes — 38 / 38 match the files;
  every lane note's `patch: … sha256` line matches its file.
- L7 U1 (optional, no patch): its three `trackedAtHeadIfNoPatch` files are tracked at 5bbc2a41.
- Zero probe / log / png / build files among the 84 code paths. Ignored-only build products (`.kotlin/`, harness assets).
- Scratchpad paths: inside the landed range, only `TREES:` lines (19, all lanes) and `unit-U3.patch:6` (§10 item 2) — HOLDS.
  **But** (should-fix S3) 11 wave-55 plan records committed at 611c9f93 carry the session scratchpad root outside both
  exemptions: `wave55-plan/fix-r3.guard-proof.out.txt` (:17, :19) and ten files under
  `wave55-plan/plan-skeptic-round-1.instrument-teeth.d/` (`ab-dry.out.txt`, `gate-out/combo-L1…L6.txt`,
  `gg_refusals.out.txt`, `lu-check.run1.land-units.out.txt`, `rec/run-adj.out.txt`). §8 step 18's grep will list them.
- **Should-fix S2:** the landing's own stdout (the throwaway `--check`, the shared-tree `--check` and the `land-units.sh`
  run of 2026-10-12) is not in `wave55-gate/` — §8 step 6: "Its stdout goes to `$TMPDIR` and is copied into `wave55-gate/`
  AFTER the run". `wave55-gate/_note.md` "Landing" summarises it ("0 errors, 0 warnings — landable"); this pass re-verified
  the landing from git itself (above), so nothing is lost but the record.

## 4. Pointer audit (executed; `pointer_audit.py` → `pointer_audit.out.txt`, `line_audit.py` → `line_audit.out.txt`)

Over the seven `_note.md` + `skeptic.md` (TREES: lines skipped):
- **Paths:** 1098 resolve as tracked files / dirs of 5bbc2a41 directly, 61 more by basename inside the wave-55 dirs, 5 are
  patterns (`{…}` / `*`). The 29 unresolved are all non-evidence: gitignored by design (`tools/titan/runs/…`, `tools/wpt/`,
  `fixtures/wpt/`, `local.properties`, `tools/titan/wpt-buckets.json` — `.gitignore:79`, the two embedded `tmpOutput.json`),
  a future record (`wave55-gate/stage1/wave55-pre/geometry.json`), an ellipsis path (`runtimes/swiftui/…/ListMarkerBidi.swift`),
  or tokenizer fragments of prose (`DocumentFontRegistry.shared`, `BorderKt.shrink`, `{swift.txt,out.txt}`,
  `revert-check-proof.sh` = `wave55-gate/ab-arms55.revert-check-proof.sh`, tracked). **No cited evidence file is missing.**
- **Cells:** 61 `wave54-final <sec>/<test> <platform> <P|f> <ssim>` citations; re-derived with
  `node tools/titan/results/wave52-gate/cells.mjs '<the 59 terms>' wave54-final wave55-open` (`cells-rederived.txt`):
  **61 / 61 equal the scorer**, and all 59 rows are identical on wave55-open.
- **:LINE pointers:** the lane notes are cut on base 611c9f93 by design; read by hand where the landing moved a seam file:
  - Nit N3: `wave55-instruments/_note.md`:124 cites the Compose breadcrumb at `ComponentRenderer.kt:3905-3906`; on the landed
    tree it is :3932-3933 (+27: L4 / L2 / L3 seams above it). Base-anchored, correct at 611c9f93.
  - The other source pointers checked hold on 5bbc2a41: `InertOutOfFlowWordJoin.ts:98-120`, `InlineRunFold.kt:177` and
    `:460-467`, `InlineRuns.ts:58`, `OverflowExtractor.kt:30-37`.
- **Line citations ADDED in landed source** (all checked on 5bbc2a41): `UAPositionedHeadingMargin.swift:17` → MarginCollapsePlanner
  `:82–89` (the out-of-flow bail) HOLDS; `UAHeadingFoldGate.swift:26-27` → InlineRunFlow `:298 / :305 / :312 / :468`,
  InlineSpanRing `:251` HOLD; `NormalLineBox.swift:115` → `LineBoxMetrics:138` HOLDS. **Nit N4:** `NormalLineBoxTests.swift:133`
  "(the :3220 call site)" — the `horizontalWritingMode:` argument it means is `ComponentRenderer.swift:3244` on the landed tree
  (+24: L4 seam 2 and L5 seam 1 land above it); cite it by symbol.

## 5. Census replay (my own scripts over `tools/titan/runs/wave54-final/sections/*/per-test-ir` + the landed sources)

`irlib.py` loads the 1435 per-test IR documents (last declaration wins, as the runtimes' `lastOrNull`); each `census_L<n>.py`
models its unit's predicate from the LANDED source, independently of the lane's script. Output files beside them.

| lane | my predicate (from the landed code) | result vs `expectations.json` | verdict |
|---|---|---|---|
| L1 | text runs whose effective `LineHeight` is the declared keyword `normal` (own, else inherited; an own `All` drops both) — `NormalLineBox.usedLineHeight`'s declaredNormal guard | **58 components / 18 documents** = the lane's 58 / 18; the 14 iOS carriers ⊂ radius; the other 4 are the documented SAME / DECLINED (disclosure-styles 6 H + 12 V, anonymous-table-ws-001, boundary-shaping-010, hyphens-vertical-001 2 V); all-prop-001 outside | HOLDS |
| L2 | U1: a non-border-box box with a declared size and a non-zero band on that axis (first-declared px, as `pxOf`) and ANY descendant that reads a block (oof / % / calc / vertical) — a deliberate SUPERSET | 92 documents: 13 carriers, 48 L6 mayMove, 29 other lanes' carriers, 2 unlisted (cross-fade-target-alpha, abspos-container-change-dynamic-001). **Empirical check** (`probe_diff_L2.py`): wave54-probe ran HEAD + 5b333c8a (= U1's code) on Android; of my radius documents in probed sections, exactly the 13 carriers moved vs both wave54-final and wave55-open — the 2 unlisted and every L6 flex carrier stayed byte-identical. flex-gap-decorations-006 (a carrier) is outside my superset and did not move at the probe (over-coverage). css-grid (48 radius docs, L6 carriers / mayMove) was not probed: rests on the lane's JVM census | HOLDS (css-grid unchecked) |
| L3 | Column-loop pairs (ABSOLUTE/FIXED child of an ABSOLUTE/FIXED/STICKY parent that is not a transform-CB host — `isPositionedContainer`), U1 = host-inactive (coarse hoist model) LTR parent, U2 = RTL parent, px Width, exactly one physical inline inset | U1 15 docs, U2 **7 docs / 13 children**; union ⊆ 9 carriers ∪ the lane census's 13 identity / CLAMP / AUTO controls, plus backface-visibility-hidden-004 (my coarse host model says inactive; the L3 skeptic's JVM host dump says ACTIVE → mounted today, identity; named must-not-move) | HOLDS |
| L4 | U1: own Position ABSOLUTE/FIXED h1-h6 with ≥1 undeclared block-margin edge, horizontal; U2: h1-h6 with element children | U1 heading scope **4 docs / 4 components** (inset-005 / -006 / -011 / -014); 12 abspos headings declare both edges (identity); non-heading `<p>` skips in s-11-1-1b-005 / -006 and anchor-center-safe-rtl (logged, no margin); U2 hosts = the same 4 docs. = carriers ios 4 / android 4 exactly | HOLDS |
| L5 | U1-ios: marker (baked, else the list-item table) whose UAX #9 natural direction (`naturalIsRTL`, re-implemented) ≠ the item's merged Direction; U2: baked marker ending U+3001 (the local tables never emit U+3001 — grep); U3: own FontVariantNumeric | U1 2 docs (counter-suffix א. / ב.; disclosure-styles 6 RTL items); U2 **36 markers in 4 docs** (9 / 23 / 2 / 2); U3 6 components in counter-suffix — ios 5 / android 4 = carriers exactly | HOLDS |
| L6 | every component with a visible band (explicit px or style-only 3-dp medium; physical, logical, shorthand) that does NOT clip (fix-r1's `!shouldClip`) and has a non-`<br>` child, own text / runs or a pseudo; plus any bordered component under Opacity ≠ 1 | **261 documents** (the lane's "261 exposed"); ⊆ carriers 27 ∪ mayMove 241 ∪ the 12 shared stems ∪ ring-fence, except ONE: `css-contain/contain-animation-001` (complement; css-contain is a stage-2 section) — its only content is the text `" "` at 100 px under a 50-px green band with `contain: strict`. The lane's JVM census reads it `text=false` (Kotlin `isBlank`), so it is a content-less band; an NBSP has no ink, so the paint-order flip moves no pixel. It moves only if the behind-content painter's raster differs from androidx's — the corpus-wide R1 risk the `[ID]` keys gate (nit N9). 20 mayMove stems fall outside my superset (over-coverage, harmless). L6 reaches exactly the 12 other-lane carriers of the C2 list | HOLDS |
| L7 | U5 can decline a marker only where a list item and `mask*` / `mix-blend-mode` co-occur in the WPT source (+ linked .css) | over all 1435 sources: 89 with a list item, 51 with mask / blend, **0 with both** → wire-identical, as [W-L7a] measured (288 identical). U4 changes one console string (no render path); U2 / U3 / U6 are tests / tooling | HOLDS |

No under-reported radius.

## 6. Mutations (my own, in the throwaway; brackets in `mutation-log.txt`: MUTATING before, RESTORED after, sha256 equal)

| id | lane · unit | mutation | focused suite | red → restore → green |
|---|---|---|---|---|
| S1-L1a | L1 NB-ios | `NormalLineBox.blinkLineSpacing`: ascender `.toNearestOrEven` | NormalLineBoxTests | 1 failure → byte-exact → 9 / 9 (1 env-skip) OK |
| S1-L1b | L1 NB-ios | drop the `horizontalWritingMode` guard (D22) | NormalLineBoxTests | 2 failures → byte-exact → OK |
| S1-L2a | L2 U1 | `ContainingBlockBands.subtracts` always true under WPT | DynamicValueResolverTest | 4 CBB rows + census red → OK |
| S1-L2b | L2 U2 | `PercentContentBoxBand.owns` drops `bandPx == 0f` | PercentContentBoxBandTest | P-U2-2 + P-U2-6 red → OK |
| S1-L3a | L3 U1 | `engages` = `hostActive \|\| composedCapture` (no RTL guard) | PositionedParentSlotGateTest | U1a + U1c red → OK |
| S1-L3b | L3 U2 | `originPx` = +max | PositionedParentRtlOriginTest | U2a + U2c red → OK |
| S1-L4a | L4 U1-android | `UaPositionedHeadingMargin.apply` ignores `enabled` | UaPositionedHeadingMarginTest | "capture off - the SAME list" red → OK |
| S1-L4b | L4 U2-android | `UAHeadingFoldGate.standsDown`: a leaf heading stands down | UAHeadingFoldGateTest | "a leaf heading applies" red → OK |
| S1-L4c | L4 U2-ios | `UAHeadingFoldGate.standsDown` (Swift): non-bold tag → false | UAHeadingFoldGateTests | red → OK |
| S1-L5a | L5 U2o-android | `ListMarkerSuffixGap.gapDp` never 0 | ListMarkerSuffixGapTest | "an ideographic comma suffix takes no gap" red → OK |
| S1-L5b | L5 U1-ios | `ListMarkerBidi.painted` isolates even when directions agree | ListMarkerBidiTests | red → OK (10 / 10) |
| S1-L6a | L6 U2 (fix-r1) | StyleApplier: `sidesBehindContent = true` for a clipping box | BorderSidesBehindContentTest | P3h + P3i red → OK |
| S1-L6b | L6 U2 | the `applySidesBehindContent` line dropped | BorderSidesBehindContentTest | P3a, P3b, P3d… red → OK |
| S1-L7a | L7 U2 | wire-differential: any uniform shift is a renumbering (running-delta check dropped) | wire-differential.test.mjs | 1 / 9 red → OK |
| S1-L7b | L7 U5 | `MARKER_PAINT_INITIAL` loses `mixBlendMode` | bidi-bake.test.mjs | S6-2 red (1 / 68) → OK |
| S1-L7c | L7 U6-web | `isInertOutOfFlowMember` no longer requires transparent ink | the U6-web file ALONE | **stays green** (7 / 7) |
| S1-L7c2 | (the same) | (the same) | + the wave-54 `InertOutOfFlowWordJoin.test.tsx` | 1 / 19 red → OK — the branch is pinned, by the wave-54 file (U6-web pins only the branches no wave-54 test reached, as its header says) |
| S1-L7d | L7 U6-web | FIXED is no longer out of flow (W2's branch) | the U6-web file | 1 / 7 red → OK |

18 brackets, 18 byte-exact restores; every lane has ≥ 2 pins proven able to fail on the landed tree.

## 7. Revert states (executed; `revert_states.sh` → `revert-states.txt`)

Every distinct `revertOrder` chain of `expectations.json` `lanes.*.revertUnits` (contingencies are not landed, so their chains
are not states of this tree), applied to the LANDED tree 5bbc2a41 with `git revert --no-commit <commits, revertOrder>` in the
throwaway, then the whole suite of the platform(s) the chain touches (Compose: the full `:runtime:testDebugUnitTest`; Swift:
the full Catalyst `xcodebuild test`; L5 D touches all three, so Compose + Swift + `node --test tools/titan/*.test.mjs`;
tooling: the full `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs`; web: both vitest workspaces), then
`git reset --hard 5bbc2a41` and a tracked-clean check. Every revert applied without a conflict.

| lane | chain (revertOrder) | paths | suite | result |
|---|---|---|---|---|
| L1 | NB-ios | 3 | swift | 2264 / 0 failures |
| L2 | U2, U1 (both units' chain) | 8 | compose | 3559 green |
| L3 | U2, U1 | 8 | compose | 3564 green |
| L3 | U2 | 5 | compose | 3569 green |
| L4 | U2-android, U1-android | 10 | compose | 3550 green |
| L4 | U2-android | 7 | compose | 3565 green |
| L4 | U2-ios, U1-ios | 8 | swift | 2250 (1 skip) / 0 failures |
| L4 | U2-ios | 5 | swift | 2264 (1 skip) / 0 |
| L5 | U1-ios — on the full tree, i.e. WITH U2i-ios / U2o-ios / U3-ios / D applied (C12's dry-run, on the landed bytes) | 4 | swift | 2262 (1 skip) / 0 |
| L5 | U2i-ios | 2 | swift | 2272 (1 skip) / 0 |
| L5 | D, U2i-ios, U2o-ios | 12 | compose + swift + titan tooling | 3574 · 2266 (1 skip) / 0 · 1762 / 0 fail |
| L5 | U2i-android | 2 | compose | 3573 green |
| L5 | D, U2i-android, U2o-android | 13 | compose + swift + titan tooling | 3568 · 2273 (1 skip) / 0 · 1762 / 0 fail |
| L5 | U3-ios | 6 | swift | 2268 (1 skip) / 0 |
| L5 | D | 6 | compose + swift + titan tooling | 3574 · **2273 with 1 failure** on the first run · 1762 / 0 fail; **re-run on the same bytes: 2273 / 0 failures** (`rerun-L5D.swift.log`) |
| L6 | U2, U1 | 8 | compose | 3551 green |
| L6 | U2 | 4 | compose | 3565 green |
| L6 | U3 | 1 | compose | 3572 green |
| L7 | U2 | 3 | tooling | 2342 tests / 0 fail (−9: the wire-differential pins) |
| L7 | U3 | 3 | tooling | 2351 / 0 fail |
| L7 | U4 | 5 | web | 1388 + 344 green (−2: InlineRunsLatitudeCitation) |
| L7 | U5 | 2 | tooling | 2351 / 0 fail |
| L7 | U6-compose | 4 | compose | 3566 green |
| L7 | U6-swift | 2 | swift | 2267 (1 skip) / 0 |
| L7 | U6-web | 1 | web | 1383 + 344 green (−7) |
| L7 | U6-tooling | 1 | tooling | 2349 / 0 fail (−2) |

**Every revert state builds and passes** (PLAN §0 "Revert states compile" HOLDS on the landed tree). The single red run is a
Swift flake, not a revert defect: unit D is comment-only (§8), the identical Swift bytes passed in the two other D-bearing
states and in the re-run, and the failing test's name was lost because the driver reused its temp log (the xcresult was
pruned; DerivedData keeps the last three). Recorded under "What I could not check"; the landed bytes then passed two more full runs (`swift-reruns.txt`).

## 8. Honesty (executed reads)

- `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/plan-prose.py --check` (shared tree, read-only mode) →
  "36 gen blocks · 43 decision ids · 46 wave54-final cell citations · 28 landing-order units (want 28) … **HOLDS**", rc 0;
  `final55-analysis.gen.py --check` → "in sync with expectations.json sha256 b773904f…", rc 0. `git status` unchanged after both.
- **DEGENERATE labels kept:** `expectations.json` still carries the 11 `stayDegenerateEvenIfPass` cells and the 2
  `degenerateByConstruction` documents of PLAN §1; no unit commit body claims a fix (`git log --format=%b b4f64963..5bbc2a41`);
  the L4 U2-android subject itself says "005 / 006 android stayDegenerateEvenIfPass".
- **"runtime", never "engine":** 0 hits for `engine` in the added lines of the range outside the `StyleEngine/` path; the lane
  notes use it only to say they do not.
- **Comments vs measurements** (re-derived, not trusted):
  - `PositionedParentRtlOrigin.kt:47-48` "11 RTL Column-loop parents / 13 children (all declare a px Width and only `left`)" —
    my census: 11 parents, 13 children, 7 documents, every child `pxW` + `left` only. HOLDS.
  - `FontVariantNumericApplier.swift:19` "6 components DECLARE it, all in counter-suffix" — HOLDS (census_L5).
  - `bidi-marker-paint.mjs` "Corpus reach: 0 of the 17 bidi-baked sources" — HOLDS, and 0 of all 1435 (census_L7).
  - `UAPositionedHeadingMargin.swift` "21.44px, or 17.42px for 014's 26px monospace face" — .67 × 32 / .67 × 26. HOLDS.
  - `NormalLineBox` DejaVu Sans Mono 16 → 19 (14.85 → 15, 3.77 → 4) and raw 18.625 — HOLDS by arithmetic on 1901 / −483 / 2048.
  - The wave-40 numbers both UAHeadingFoldGate files quote ("inset-011 0.6999 → 0.6290, inset-014 0.9317 → 0.9150") are labelled
    as iOS wave-40 history; inset-011 ios is still 0.6999 at wave54-final, inset-014 ios is 0.9238 today — history, not a claim.
  - **Nit N7:** `UAElementFontRule.kt:120` kdoc "its removal is queued (BACKLOG)" — no BACKLOG item exists yet; it becomes true
    when `wave55-plan/ship-notes.md` item 1 is copied into BACKLOG at ship (PLAN §10 item 7).
- **Unit D is comment-only:** stripping comments from the six files before / after `5bbc2a41` leaves byte-identical code.
  **U4** changes one console string (`InlineRuns.ts`) and comments; no render path.
- **Sizes:** every NEW main-source file ≤ 200 lines (largest `BehindContentBorder.kt` 200, `PositionedParentSlotGate.kt` 198).
  Oversize files received only §0-listed edits: `ComponentRenderer.kt` 8488 → 8523 and `.swift` 6291 → 6359 (seam hunks),
  `StyleApplier.kt` +6, `BorderSideApplier.kt` +11, `DynamicValueResolver.kt` +8, `SizingApplier.kt` +3, `StyleBuilder.swift` +2,
  `TypographyAggregate.swift` +3, `UAElementFontRule.swift` +6, `ListMarkerRow.{kt,swift}` +6 doc. **Nit N5:** four NEW test files
  pass the 200-line target and are not in §0's list: `UAPositionedHeadingMarginTests.swift` 271, `BehindContentBorderTest.kt`
  266, `UaPositionedHeadingMarginTest.kt` 225, `UAHeadingFoldGateTests.swift` 214 (all < 300, so no split is mandated).
- **Every new line commented:** every seam hunk carries its comment; the new runtime files read well-commented (heuristic: ≤ 18
  code lines per file without an own or adjacent comment, mostly multi-line signatures). **Nit N6:** `tools/titan/wire-
  differential.mjs` (60 of 116 code lines) and `.sh` (45 of 92) have long stretches of dense uncommented logic (e.g. mjs
  :139-155, the classify tail).

## Defects, ranked

**Must-fix: none.**

**S1 (should-fix, before ship; the orchestrator's restamp) — the docs restamp is pending.** `doc-staleness-check.sh` on 5bbc2a41
exits 1; the 16 rows of §2 still carry the wave-54 counts. PLAN §8 step 7 put the restamp before this pass. It does not touch
anything a device run builds from.

**S2 (should-fix, records) — the landing's stdout is not in `wave55-gate/`.** PLAN §8 step 6 copies it there after the run; only
`_note.md`'s summary exists. Copy the three outputs (throwaway `--check`, shared `--check`, the landing) if they survive, or
state in `_note.md` that the records are the commits themselves (this pass verified them, §1 / §3).

**S3 (should-fix, ship-time §8 step 18) — 11 committed wave-55 plan records carry the session scratchpad root**, outside the TREES:
and §10-item-2 exemptions (§3 list; committed at 611c9f93 and kept through the 7566fb03 scrub, which covered the lane dirs).
Scrub them to `<scratchpad>` (they are `.txt` outputs, not patches) or add a §10 exemption; otherwise the step-18 grep is red.

**Nits:** N1 the two seam comment hunks off §3's anchor list; N2 the L2 seam comment's "Wave 54 (lane L4…)" provenance with no
"re-landed in wave 55"; N3 the L7 note's `ComponentRenderer.kt:3905-3906` (now :3932-3933); N4 `NormalLineBoxTests.swift:133`
"(the :3220 call site)" (now :3244 — cite by symbol); N5 four test files over 200 lines; N6 wire-differential comment density;
N7 `UAElementFontRule.kt:120` "removal is queued (BACKLOG)" ahead of the BACKLOG item; N8 `sweep-landed.txt` credits "U5 bake
entries" with part of the tooling +11 (U5 adds no test: U2 9 + U6-tooling 2); N9 L6's census treats an NBSP-only text as no
content — `contain-animation-001` android (complement, stage-2 css-contain) is exposed to U2's painter swap with an inkless
glyph only: identity unless the R1 raster-parity risk fires, which the `[ID]` keys gate.

## What I could not check

- Anything on a device: NB-ios snapping (R1), the L2 U2 oversized placeable under `#outer`'s clip, L3's composed mount and RTL
  origin, L4's margins and restored face, L5's isolates in CoreText and `monospacedDigit` on the bundled Inter, L6's behind-content
  raster parity corpus-wide (including every content-less band in the complement). Stage 1 / stage 2 decide those.
- L2's radius in css-grid: wave54-probe did not run css-grid, so the 48 grid documents of my superset rest on the lane's JVM census
  (they are all L6 carriers / mayMove, so a move there is inside a declared band — but an L2-caused move on an L6 `[X]` gating
  key would be mis-attributed; the drop-L6-U2 arm reads css-grid).
- L3's exact host predicate: my hoist model is coarse; one disagreement (backface-visibility-hidden-004) was settled by the L3
  skeptic's JVM host dump, not by my own execution.
- L7 U5's wire effect beyond a static source scan: the [W-L7a] record is the execution evidence.
- The ios-harness XCTest on revert states (simulator). The tooling suite in my worktree skips 25 tests (the Chromium /
  gitignored-corpus `proving:` and `post-load` rows, the `GEN_FIDEL` / `TITAN_CENSUS_FULL` gates; the sweep on the shared tree
  skipped 4), so the tooling revert states cover 2326 of 2351.
- The name of the one flaky Catalyst test (§7): it failed once in 15 full Swift runs this wave (the sweep, 12 revert-state runs,
  2 landed re-runs), on a state whose Swift bytes passed three other times. A flake in the Catalyst suite can redden the
  single-writer sweep of §8 step 11½; if it recurs, capture the `.xcresult` before re-running.

## Cleanup and records

- The throwaway worktree is removed (`git worktree remove --force`, its `.dd` DerivedData deleted first); `git worktree list`
  no longer shows it; the shared tree's `git status --short` lists only this directory (untracked).
- Disclosure: before the removal I ran `./gradlew --stop` from the throwaway to release its Gradle daemon. That command stops
  every Gradle 9.6.1 daemon of this user, not only the throwaway's. Any other session's daemon that was idle at the time was
  stopped as well. Daemons restart on the next build, but a build running elsewhere at that moment would have failed. I did not
  check for one first. Next time, stop only this worktree's own daemon (its pid from `gradle --status` under that root).
- Records in this directory (scratchpad root scrubbed to `<scratchpad>` / `<S1-worktree>`): `seam-hunk-map.txt`,
  `seamaudit.{py,out.txt}`, `git-hygiene.txt`, `doc-staleness.on-5bbc2a41.txt`, `pointer_audit.{py,out.txt}`,
  `line_audit.{py,out.txt}`, `cells-rederived.txt`, `irlib.py`, `census_L1…L7.{py,out.txt}`, `probe_diff_L2.{py,out.txt}`,
  `mutate.py` + `mut-*.json` + `mutation-log.txt`, `revert_states.sh` + `states-compose-tooling.txt` + `revert-states.txt`,
  `swift-reruns.txt`.

STATUS: COMPLETE
