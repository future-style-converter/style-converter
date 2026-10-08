# wave53 S1 — combined-tree skeptic (wave skill Phase 3)

Tree: the shared tree `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf`, branch
campaign/applier-campaign, HEAD `9516cf2d`, base `e330e255`. The 11 unit commits in landing order are 54c8460f (L5),
9df67ae3 (L1 U1), a8ffd1c6 (L1 U2), 205ab295 (L2 F1), 2c6ecea9 (L2 F2), de54f458 (L4-android), 6fcb3161 (L4-ios),
add9de84 (L3-A), adfb3fe8 (L3-B-web), 06c41979 (L3-B-ios) and 276757ea (L3-B-android). After them come the orchestrator's
plan and sweep commits 4adc107b, b70b092c, 4e9b69df, 073f67f5 and 9516cf2d.

Read: PLAN §0–§4, §6 and §10; the five lane notes and the five `skeptic.md`; `expectations.json`;
`wave53-gate/sweep-landed.txt` and its raw logs; `wave53-gate/control-check.mjs`.

Every check below was EXECUTED. My scripts and outputs are in this directory. Every mutation ran in an EXPORT of HEAD in
the session scratchpad, never in the shared tree. No device, Chromium, emulator or simulator was used. I ran no git
commit, checkout, stash or reset in the shared tree.

## Verdict: MIXED. The landed code is what the lanes said; two pre-registration records are falsified by execution

What holds, by execution:
- Every seam blob in its unit commit equals the lane's patch applied to that commit's parent. No seam hunk is unowned, none
  overlaps another, every one carries a comment, and each sits in the unit commit PLAN §4 assigns it to.
- Every suite in the sweep is green. Every doc table already carries the sweep's count.
- All five carrier sets re-derive from my own rule census exactly as `expectations.json` states them.
- I replayed 10 of 10 mutations, two per lane, with my own runner and mostly my own strings. Each went red, was restored
  byte-exact, and went green again.
- Every evidence pointer and every quoted cell resolves.
- The tree, stash and lock directory are clean.

What is wrong (two must-fix, both plan/expectations records, no runtime code):
- **M1.** L3 unit A cannot be reverted on its own. PLAN §6 rule 6 ("the same holds for every other pair") is false for
  L3: A comes out cleanly only after B-android, B-ios and B-web are all reverted.
- **M2.** L1's wire radius is under-reported. U1 adds one component to counter-reset-reversed-nested, which renumbers the 10
  css-lists documents after it. Nobody registered that shadow. The registered css-counter-styles shadow is attributed to
  "U1-B counter-bake", but it is U2 hunk M's 6 marker runs.

Seven open should-fix items follow, mostly skeptic findings that no fix pass took up, plus nits.

## 0. Incident in this session (mine, repaired; recorded so nobody wonders about the mtimes)

- **What happened.** My first seam-check command ran under zsh with a variable named `path`. In zsh, `path` is tied to
  `PATH`, so every later command in that shell failed with "command not found". The `cd` into the scratch directory failed
  too, so the redirect `git show … > $path` ran at the repo root.
- **Effect.** At 14:30:06 it truncated three seam files in the shared tree to 0 bytes: `tools/titan/extract-fixture.mjs`,
  `runtimes/swiftui/…/Renderer/ComponentRenderer.swift` and `runtimes/compose/…/core/renderer/ComponentRenderer.kt`.
- **Repair.** At about 14:33 I restored each file with `git show HEAD:<path> > <path>`. Each file's sha256 now equals its
  HEAD blob: `845a3d26…`, `905d1669…` and `da2df0b4…`. `git status --short` is empty again (apart from this new directory).
- **No reader in the window.** `ps` showed no gate, section-runner, test-all, Gradle build, xcodebuild or vitest process.
  Only the idle Gradle and Kotlin daemons from the 13:15 and 13:47 sweep were alive.
- **Side effect.** The three files' mtimes changed, though their bytes did not, so the next Gradle/Xcode build recompiles
  them.
- **Afterwards.** Every later script ran under `bash` from a file.

## 1. Seam-hunk audit

`git diff e330e255 HEAD` over the four seam files gives 13 hunks. `ComponentRenderer.tsx` has none, as registered.

| seam | hunk (HEAD lines) | unit commit | lane patch | §3 row | commented |
|---|---|---|---|---|---|
| extract-fixture.mjs | 1824 walkChildren site | 9df67ae3 U1 | lists-bakes/seam-1 | row 1 | yes (HTML §13.2.6.4.7) |
| | 1944–2000 IMPLIED_CLOSE_SCOPE + findImpliedClose | 9df67ae3 | same | row 1 | yes (banner, KNOWN GAP) |
| | 3617 scanOwnText site | 9df67ae3 | same | row 1 | yes |
| ComponentRenderer.swift | 1876 `else if` FloatAvoidLayout | 6fcb3161 L4-ios | float-avoid/seam-2 | row 3 | yes |
| | 3218, 3507, 3566, 4310 `horizontalWritingMode:` at the 4 PlaceholderLabel call sites | 205ab295 F1 | soft-hyphen/seam-1 | **none** (the row names only :4919-4921) | yes |
| | 4860 the `horizontalWritingMode` stored property | 205ab295 | same | **none** | yes |
| | 4944 the comment block for the moved precondition | 205ab295 | same | **none** | yes (comment-only) |
| | 4969 the guard → `SoftHyphenPolicy.admitsPreBreak` | 205ab295 | same | row 2 | covered by the 4944 block |
| ComponentRenderer.kt | 3663 FloatAvoidLayout branch before `val floatSegments =` | de54f458 L4-android | float-avoid/seam-1 | row 5 | yes |
| | 3865 `, N out-of-flow member(s) dropped` | 2c6ecea9 F2 | soft-hyphen/seam-2 | row 4 | yes |

**Per-unit replay** (`seamcheck.sh` → `seamcheck.out.txt`). Each lane patch was applied (`git apply --include=<seam>`) to
`git show <unit>^:<seam>` in a scratch repo, and the result was compared with `git show <unit>:<seam>`:
```
9df67ae3 extract-fixture.mjs         <- lists-bakes/seam-1   applied 845a3d263a14369b = commit  MATCH
205ab295 ComponentRenderer.swift     <- soft-hyphen/seam-1   applied 524e31581a58fd9d = commit  MATCH
2c6ecea9 ComponentRenderer.kt        <- soft-hyphen/seam-2   applied d251ace7442ef6e6 = commit  MATCH
de54f458 ComponentRenderer.kt        <- float-avoid/seam-1   applied da2df0b4cca689ad = commit  MATCH
6fcb3161 ComponentRenderer.swift     <- float-avoid/seam-2   applied 905d1669d3856364 = commit  MATCH
```
- `git log e330e255..HEAD -- <seam>` names only those commits: extract-fixture U1 only; CR.kt F2 + L4-android; CR.swift
  F1 + L4-ios; tsx none.
- The two L4 patch-borne test files (FloatAvoidSeamWiringTest.kt, FloatAvoidLayoutRasterTests.swift) landed in their
  units.
- No two hunks overlap. CR.swift's L4 hunk sits at 1873-1891 and L2's hunks start at 3215. CR.kt's L4 hunk is at 3660 and
  L2's at 3862.
- **Registry defect (should-fix S1).** L2's seam-1 is 7 hunks, and §3 registers one (":4919-4921 pre-break guard").
  - The lane disclosed this: note §1 "Where the tree disagreed" item 1, and the patch header.
  - But neither PLAN §3 nor §10 restates the registry.
  - §3's count, "Five seam patches … carry seven hunks", is stale. The true count is 13 seam-file hunks, plus 2 patch-borne
    new test files.

## 2. Sweep (`wave53-gate/sweep-landed.txt`; raw logs read in the session scratchpad `sweep-raw/`)

| suite | rc | count | note |
|---|---|---|---|
| tooling | 0 | `# tests 2300` · `# pass 2296` · fail 0 · **skipped 4** | the 4 are env-gated, pre-existing: post-load e2e, wave49-final full census (TITAN_CENSUS_FULL), composition-test fresh convert, converter round-trip (GEN_FIDELITY_CONVERT) |
| conformance | 0 | all documents valid (v1 + v2 goldens) | |
| web-runtime | 0 | 1369 passed | |
| web-harness | 0 | 330 passed | |
| converter | 0 | testcases=551 | `:converter:test` executed |
| compose | 0 | testcases=3432 | `compileDebugKotlin`, `compileDebugUnitTestKotlin` and `testDebugUnitTest` all EXECUTED (not UP-TO-DATE) |
| android-harness | 0 | 168, failures 0 | the first pass was UP-TO-DATE (987 ms); re-run with `--rerun-tasks`: executed, 168 |
| swiftui (Catalyst) | 0 | Executed 2190, 0 failures | |
| ios-harness (simulator) | 0 | Executed 30, 0 failures | |

- **Tree.** The sweep ran on 4adc107b. `git diff --name-only 4adc107b HEAD` touches only docs, `wave53-gate/sweep-landed.txt`
  and the plan files `expectations.json`, `plan-build.py`, `s1-workflow.js` and `soft-hyphen.geometry.py`. A grep of every
  suite's test files finds no suite that reads them, so the sweep stands for HEAD.
- **Doc tables.** README.md:210-217, CLAUDE.md:290-297, docs/STATUS.md:2638-2645 and the five tree READMEs all already
  carry 551 / 1369 / 3432 / 168 / 2190 / 330 / 2300 / 30. **No row has to be restamped.** The STATUS.md:2619-2621 line
  ("1361 · 321 · 3393 · 159 · 2147 · 2266") is the dated wave-52 ship record, and it is correct as history.
- **Nit N1.** `sweep-landed.txt` prints tooling as `# pass 2296` while the docs and the commit stamp 2300. 2300 is the
  `# tests` convention `doc-staleness-check.sh:85` uses. The committed record does not say "2300 tests, 4 skipped", and its
  raw logs point at a session scratchpad marked "not kept" (PLAN §0 "No scratchpad pointers").

## 3. Git hygiene

- `git status --short`: EMPTY. At session start it was empty; it was dirty 14:30-14:33 (§0), and it is empty after the
  restore. Only `tools/titan/results/wave53-S1/` is untracked now, and that is this report.
- `git stash list`: empty.
- `tools/titan/runs/wave53-lock/`: exists, with no entries.

**Path → unit.** `git show --stat` per commit; full list in my session log:
- L5 54c8460f has 16 source paths. All are in PLAN §2 L5 own:, plus three new files outside the list:
  `tools/visual/smoke-port.sh`, `tools/titan/own-gradle-daemons.test.mjs` and `tools/titan/own-adb-server.test.mjs`. The
  L5 note §1 "Where the tree overrode" item 2 discloses them, they landed in L5's own unit, and no other lane names them
  (nit N4).
- Every L1, L2, L3 and L4 path is on its lane's own: list or is its seam, in the unit §4 assigns:
  - U1 = seam-1 + counter-bake + both PseudoTextFold twins + pins;
  - U2 = bidi-marker-bake + bidi-bake + ListMarkerOutsideHang;
  - F1 = PreBreakPipeline + SoftHyphenPolicy + CR.swift + pins;
  - F2 = InertOutOfFlowMember + InlineRunFold + CR.kt + pins;
  - L4-android / L4-ios = the native twins + their seams;
  - L3-A has 10 paths, and B-web / B-ios / B-android have 3, 3 and 4.
- No path sits in another lane's commit.
- Each lane's results directory went in with its first unit.
- 4adc107b and later touch only `wave53-gate/`, `wave53-plan/` and the docs.

**Leftovers.**
- No `*.orig`, `*.rej`, `*Probe*`, census or class files in any source tree.
- The ignored-but-present files in source trees are only `tools/titan/wpt-buckets.json` (expected) and the smoke outputs
  `tools/visual/{a11y-report.json, interaction-report.json, interaction-snapshots/, report/}`. No probe files.

**Revert units** (beyond the brief; PLAN §6 rule 6 rests on this):
- `git diff <c>^ <c> | git apply -R --check` on HEAD, read-only: 10 of 11 reverse-apply alone. **add9de84 (L3-A) does
  not.**
- Then a real 3-way `git revert` in a `--shared` scratch clone (`revert-orders.sh` → `revert-orders.out.txt`):
  ```
  revert [add9de84]                                -> CONFLICT in ScreenshotCaptureScreen.kt CaptureCanvas.swift ComposedCaptureGallery.tsx
  revert [276757ea add9de84]                       -> CONFLICT in CaptureCanvas.swift ComposedCaptureGallery.tsx
  revert [276757ea 06c41979 add9de84]              -> CONFLICT in ComposedCaptureGallery.tsx
  revert [276757ea 06c41979 adfb3fe8 add9de84]     -> clean
  B-web, B-ios, B-android alone; L5, U1, U2, F1, F2, L4-android, L4-ios alone; F2→F1; U2→U1  -> clean
  ```
  → **must-fix M1** (§Defects).

## 4. Pointer audit

`pointers.py` → `pointers.out.txt`: 444 path tokens across the five notes and five skeptic reports. Every one resolves to a
tracked file, a lane-directory file, a WPT source under `tools/wpt/`, or a gitignored run artefact that exists. Examples
checked by hand:
- `tools/titan/runs/wave53-hh-probe/gate-driver/driver.log`: `gradlew --stop` 0, `kill_own_gradle_daemons roots=` 1,
  `provision rc=0`. These are exactly the L5 note's window-5 claims.
- `wave53-open/…/per-test-ir/wpt__css-text__hyphens__hyphens-span-001.json`;
- `ssim.js` is the npm package.

The tokens that matched no file are command placeholders (`<out.json>`, `__ref.json`) or not paths at all (`System.out`).

**Cells.** `node tools/titan/results/wave52-gate/cells.mjs '<every target, mover and L5 must-not-move term>' wave52-ship
wave53-open` (session log). Every value quoted in the five notes matches. For example:
- nested: web P 0.9506, ios P 0.9508, android P 0.9509;
- counter-suffix: web P 0.9818, ios P 0.9802, android P 0.9547;
- span-001: android P 0.9532, ios f 0.8652;
- out-of-flow-001: android P 0.9685, ios f 0.8935; out-of-flow-002 android P 0.982;
- the contents-root target: f 0.534/0.5341, 0.5346, 0.5355;
- margin-root-001: f 0.4511 / 0.4509 / 0.451; margin-root-002: f 0.339 / 0.3391 / 0.3389;
- 006: P 0.9941 / 0.9953 / 0.9944;
- contain-bfc-floats: 001 P 0.9531 / 0.9519, 002 f 0.9322 / 0.9311; flow-root-002 P 0.9734 ×2;
- multicol-007 android P 0.9519;
- the five L5 iOS cells: 0.9974 / f 0.9668 / 0.9974 / 0.9997 / 0.9993;
- `anchor-center-safe-rtl`: unscored.

## 5. Census replay (my own scripts; `s1-census.py` → `s1-census.out.txt`, plus the U1 differential)

Rules are re-implemented from PLAN §2, over the 1435 `wave53-open/sections/*/per-test-ir` documents and the frozen
`bidi-baked-fixtures/`:

| lane | my rule | my set | `expectations.json` | match |
|---|---|---|---|---|
| L1 U1 | static extraction, pre (`git archive e330e255`) vs HEAD, sha1 of `[fixture, refFixture]` over all 1435 tests (`hash-extract.mjs` → `u1-differential.out.txt`) | changed 1: counter-reset-reversed-nested; 0 errors on either side | U1.wire = {nested} | yes |
| L1 U2-M | absolutely positioned component carrying `meta.markerText` | {counter-suffix} | ⊂ wire | yes |
| L1 U2-P | bidi-bake root (`baked-bidi-visual-order`, 79 roots in 17 fixtures) with a non-zero `padding*`, no content-box clip/origin, overflow visible | {counter-suffix, anchor-center-safe-rtl, bidi-lines-001, bidi-lines-002}; zero-padding only: {dir-style-02a, dir-selector-change-003, -004} | wire 5 = nested + these 4; android captures = all 5, web/ios = {nested, counter-suffix} | yes |
| L2 F1 | a space-less string with U+00AD in `text`/`meta.runs[].text`, inherited horizontal-tb, not pre/nowrap, Hyphens ≠ AUTO | {span-001, out-of-flow-001, hyphenate-character-001, -003, -004} | ios 5 | yes |
| L2 F2 | a runs host whose child member is abspos/fixed, alpha-0 Color, childless, props ⊆ {Position, Color, Hyphens} | {out-of-flow-001, -002} | android 6 = F1 ∪ F2 | yes |
| L3 | body-root (284) with BackgroundImage / with last Display TABLE / INLINE_TABLE | A {margin-root-001, -002, contents-root-background}; B {s-11-1-1b-006} | 4 × 3 | yes |
| L4 | T0 (k ≥ 1 leading floats, then exactly one last non-float sibling); strict (+ BFC root, alternating sides, px float boxes with no box wire, px or contained intrinsic width, no Clear) | T0 20 documents; strict 3 {bfc-floats-001, -002, flow-root-002} | 3 × 2 natives | yes |
| L5 | no capture-path source file changed | the commit touches only shell scripts, tests and the test target in `project.yml` (diff read) | [] | yes |

**The renumbering shadow (must-fix M2).** The per-section id counter is contiguous in `tests.list` order on wave53-open:
0 non-contiguous steps in css-lists, css-counter-styles, css-text and css-anchor-position.
- `count-comps.mjs` → `component-counts.out.txt`: the real extractor gives counter-reset-reversed-nested **7 → 8**
  components ("1. One" restored), and counter-suffix **19 → 19** statically.
- css-lists `tests.list` has counter-reset-reversed-nested at index 37 of 48, so **10 css-lists documents shift +1 under
  U1**. They are content-identical, so they are ids-only.
- The css-counter-styles +6 is hunk M. The hh-probe extract log reads `[bidi-bake: baked — 2 roots, 10 runs]`, where
  wave53-open read `… 4 runs`: 6 new run components. At hh-probe time U1's seam-1 was not applied, and counter-bake (U1-B)
  touches only reversed counters.
- So `expectations.json` `wireRenumbering.addedAfter` ("L1 U1-B counter-bake"), PLAN §10 item 6 ("(L1 U1-B)") and L1 note
  §5 item 5 ("U1-B's wire radius") all misattribute it. It is U2.
- The gate will not leak on it. `control-check.mjs`'s classifier is generic (shift = running component-count delta), and
  none of the 25 shadow documents references an id outside `id`/`slot.parent`: `meta.runs` children are referenced by name
  (checked).
- But U1's own pre-registered wire set ("Unit 1 against cdb8a845: {counter-reset-reversed-nested}", §4 step 3) is false by
  bytes: it is 1 content-changed + 10 renumbered.

## 6. Mutations (my runner `smut.py`; results `mutations/*.json`; full sha256 before / mutated / restored in each)

Every mutation ran in an EXPORT of HEAD in the scratchpad:
- node: `git archive` of `tools/titan`, `tools/visual`, `test-all.sh`;
- web: `mk-web-export.sh`, with real `node_modules` directories of per-entry links, so caches land in the export;
- Gradle: `git archive` of `apps/android-harness`, `runtimes/compose`, `schema`, `fixtures`, run with `--no-daemon`.

The baseline in that export: PreBreakPipeline 20, InlineRunFold 45, FloatAvoidPlan 15 (1 skipped: the corpus census, since
`tools/titan/runs` is absent there) and FloatAvoidSeamWiring 2, with 0 failures.

| lane | mutation (mine unless noted) | red | restore | green |
|---|---|---|---|---|
| L1 | findImpliedClose `depth++` → `depth += 0` | helper-2, N2, N1 (3/4) | 845a3d26… byte-exact | 4/4 |
| L1 | `paddingIsSpent`: drop the NON-zero guard | V3b (1/56) | 5bce8710… byte-exact | 56/56 |
| L2 | PreBreakPipeline guard → space-only (the lane's M-a) | (a) `aSpacelessSoftHyphenRunFiresWithTheTakenHyphen`, (d) `…FiveLines` | byte-exact | 20/20 |
| L2 | `OUT_OF_FLOW_POSITIONS = setOf("FIXED")` | out-of-flow-001, -002 box 4, both-facts, transparent-ink (4/45) | byte-exact | 45/45 |
| L3 | RootBackgroundPropagation.ts `if (contained && false)` | a4_contained_noPropagation (1/8) | 59ec2cba… byte-exact | 8/8 |
| L3 | CanvasTableBody.ts always `return null` | both s006_* (2/3) | 56df07d2… byte-exact | 3/3 |
| L4 | right float `x = l` (hug the left edge) | P1, P3, P7 | fb440ee4… byte-exact | 15 run, 1 skipped |
| L4 | §10.6.7 for every box (the lane's M7) | P6 | fb440ee4… byte-exact | 15 run, 1 skipped |
| L5 | `_op_dir_in_root` without the `/` boundary | mixed/sibling/nested/no-log | c4bbf32c… byte-exact | 7/7 (DISCOVERY skipped) |
| L5 | smoke fallback `new="$default"` | "default port held → 3400-3499" (+ range pin) | dbfcd94e… byte-exact | 6/6 |

- Host Gradle daemons 7686 and 23423 were alive before and after the L5 runs.
- The first smoke-port baseline failed pin 6 only because the export lacked `test-all.sh`, which the pin reads. I added
  `test-all.sh` to the export and re-ran; that run is the one recorded.
- No Swift (Catalyst) mutation was replayed; see "Could not check".

## 7. Honesty

- **Vocabulary.** Added lines carry "the engine's own" ×4: `RootBackgroundPropagation.ts:32`, `.kt:186`, `.swift:200`, and
  the web test `:45`. Each means our runtime (nit N2). "Chrome IS the reference engine" (CanvasTableBody / the gallery)
  names a browser engine, which is fine.
- **Size: new logic over 200 lines** (nit N3):
  - RootBackgroundPropagation.kt 223, .swift 218, .ts 211;
  - tests ComposedCanvasRootBackgroundTest.kt 214, FloatAvoidPlanTests.swift 210, own-gradle-daemons.test.mjs 208.
- **Size: oversized files that gained more than a call site** (PLAN §0):
  - smoke.sh 312 → 386 (+87, including the new `build_fixtures` from the orchestrator window);
  - ComposedCaptureGallery.tsx +95; ScreenshotCaptureScreen.kt +72; CaptureCanvas.swift +42;
  - bidi-bake.mjs +91 (disclosed); PseudoTextFold.swift 228 → 291; PseudoTextFold.kt 201 → 269.
- **Comment density.** My scan of added non-test source finds the longest uncommented code runs in bidi-marker-bake.mjs
  (17 lines) and smoke.sh (17). Everything else is ≤ 10, in block-comment style (nit N5).
- **Comments vs code:**
  - `bidi-marker-bake.mjs:98` `if (!el) continue;` contradicts its banner "never dropped silently" (S2).
  - `ComposedCanvasIcbClipTests.swift:23-25, :63` say `buildPhase: resources`; project.yml uses `copyFiles: destination:
    resources` (S6).
  - The L4 note §2 "runtime admits a subset of the census set" (l.81-83) is still the false sentence the L4 skeptic named
    (S5).
  - The L5 header's "is recorded in …/_note.md" is now TRUE: the window-1 results are appended.
- **Labels:**
  - The `hyphenate-character-00x` flips are still "DEGENERATE by construction".
  - B-android is predicted to FAIL and expected to be reverted.
  - The 22 wrong-picture s-11-1-1b passes are booked as DEGENERATE.
  - multicol-007 is a recorded wall.
  - **But** counter-suffix ios/android stay `kind: degenerate->faithful`, though rows 3-6 (iOS) and 5-6 (Android) keep wrong
    ink after U2. That is L1 skeptic D6, unaddressed (S3).

## Defects, ranked

**MUST-FIX (before `wave53-probe` is read; both are pre-registration records, no runtime code)**

- **M1. L3: unit A is not independently revertible.** PLAN §6 rule 6 says "the same holds for every other pair", and
  execution falsifies it for L3. Evidence: `revert-orders.out.txt`.
  - add9de84 conflicts in all three canvas files while any B unit stays. It reverts cleanly only after B-android, B-ios AND
    B-web are reverted.
  - The cause is structural. B-android's `TableBodyForest.rewrite(owned)` lives inside A's new `composedCanvasRoots`,
    B-ios's rewrite wraps A's strip in `splitRoots`, and B-web's wrapper branch abuts A's gallery hunk.
  - Why it matters: unit A carries six gating rows (the contents-root target ×3, floor 0.99, plus geometry). The Android one
    rests on a url-layer path the JVM never executed (L3 skeptic D3). A single-platform miss there therefore reverts A on all
    three platforms, AND takes B-web's HIGH 006 gain with it. The alternative is a hand-resolved conflict in the three
    hottest canvas files under a running probe.
  - **Fix:** a §10 addendum plus `expectations.json` `lanes.L3-canvas-root.revertUnits`. State the order "A's revert =
    B-android → B-ios → B-web → A (A takes every B with it: s-11-1-1b-006 ×3 return to wave53-open)". Optionally prepare an
    A-only revert patch now (the u2-narrow precedent) that keeps the B call sites on the unstripped forest.
- **M2. L1: the wire radius is under-reported, and the registered renumbering shadow is misattributed.** Evidence:
  `component-counts.out.txt`, `u1-differential.out.txt`, the hh-probe vs wave53-open extract-log lines, and §5.
  - U1 adds one component to counter-reset-reversed-nested (7 → 8), so the 10 later css-lists documents are renumbered by
    +1. Nothing pre-registers this: not §4 step 3, not `wireRenumbering.measuredShadow`, not the L1 note.
  - The registered css-counter-styles shadow (+6 × 15) is attributed to "U1-B counter-bake". It is U2 hunk M: the bidi-bake
    went from 4 to 10 runs, and the static counter-suffix count is 19 → 19.
  - The R4b classifier is generic, so neither shadow leaks. But a revert of U1 or U2 changes which shadow exists, and the
    record names the wrong unit.
  - **Fix** (orchestrator plan files plus one L1 note line):
    - `plan-build.py` `wireRenumbering.measuredShadow`: add
      `css-lists: {after: wpt__css-lists__counter-reset-reversed-nested, shift: 1, documents: 10, unit: U1 (A)}`;
    - restate the css-counter-styles entry and `addedAfter` as `unit: U2 (hunk M, bidi-marker-bake marker runs)`;
    - regenerate `expectations.json`;
    - correct PLAN §10 item 6 and L1 note §5 item 5;
    - restate §4 step 3's U1 set as "1 content-changed + 10 renumbered".

**SHOULD-FIX**

- **S1. L2 / plan: the §3 registry is stale.**
  - L2 seam-1 has 7 hunks. Six of them (the `horizontalWritingMode` property, its 4 call sites and the moved-precondition
    comment block) map to no §3 row.
  - "Five seam patches … carry seven hunks" should read 13 seam-file hunks + 2 patch-borne test files.
  - Disclosed by the lane, but never restated in §3 or §10.
- **S2. L1: a silent fallthrough landed.**
  - `tools/titan/bidi-marker-bake.mjs:98` `if (!el) continue;`: an item whose rect is not re-found keeps its marker, with no
    `marker-not-baked` stamp. This is the hard rule "No silent fallthroughs".
  - L1 skeptic D1 found it. There was no L1 fix pass, and its repro is `wave53-lists-bakes/skeptic/silent-skip-repro.mjs`.
  - Fix: emit `{ error: 'list item not re-found by rect' }` for that key, so `planMarker` declines and stamps it.
  - Also open from that report: the marker stamps are unpinned fixture-side (D3), the pseudo `who` key is unpinned (D4),
    and the half-leading in the glyph tops (D5).
- **S3. L1 honesty: the counter-suffix native labels.**
  - `expectations.json` labels the ios and android rows `degenerate->faithful`, though rows 3-6 (iOS) and 5-6 (Android) stay
    wrong after U2 (L1 skeptic D6).
  - Restate them as "RTL rows picture-correct; cell stays DEGENERATE on rows 3-6 / 5-6".
  - Related (L1 skeptic nit 11): `predictions[counter-suffix ios].gating: true`, but `geometryProbe.geometryGating` omits
    it.
- **S4. L3: open skeptic should-fixes (no L3 fix pass); M1 amplifies them.**
  - XI1: the iOS call sites have no pin. No test references `rootImageBackground`, the strip or the
    `TableBodyForest.rewrite(` call in CaptureCanvas.swift.
  - XC1-3: the Android `canvasModifier` paint composition is unpinned.
  - The Android target's HIGH / gating evidence should cite `css-break/background-image-00x`, not
    `css-image-fallbacks…002`.
- **S5. L4: open skeptic should-fixes (no L4 fix pass).**
  - The density `* scale` is unpinned (S1/S4 survive).
  - The gate admits `position: absolute|fixed` floats (CSS 2.1 §9.7; 0 corpus carriers).
  - The note §2 "subset" sentence is false.
  - The census tests run on the raw tree, not on `ContentsUnboxing.resolve`.
- **S6. L5: `ComposedCanvasIcbClipTests.swift:23-25, :63` still name `buildPhase: resources`.** That is the phase that
  silently drops a `.swift`; project.yml uses `copyFiles: destination: resources`. A maintainer who "aligns" project.yml with
  the comment breaks all 7 pins (L5 skeptic D1).
- **S7. The size rule (PLAN §0) is exceeded with new logic** (§7):
  - three new logic files of 211-223 lines;
  - `smoke.sh` (already > 300) gained the `build_fixtures` function;
  - the gallery, ScreenshotCaptureScreen and CaptureCanvas gained helpers, not just call sites.

**NITS**
- **N1.** `sweep-landed.txt`:
  - record "tooling 2300 tests (2296 pass, 4 skipped)" and name the four skips;
  - the raw logs live only in a session scratchpad.
- **N2.** Change "the engine's own" ×4 to "the runtime's own".
- **N3.** Three test files run 208-214 lines.
- **N4.** `expectations.json`:
  - `revertUnits.L3 A.commit` still names "ColorApplier / BackgroundImageApplier one-liners", which were not edited;
  - `revertUnits.L5` does not name `smoke-port.sh` / `own-gradle-daemons.test.mjs` / `own-adb-server.test.mjs`.
- **N5.** 17-line uncommented code stretches remain in bidi-marker-bake.mjs (`inPageMarkerProbe`) and smoke.sh.
- **N6.** The nits in the five `skeptic.md` files that no fix pass took up stay as recorded there. Examples: L5 nits 4-7,
  L4 nits 5-8, L3 nits 4-6, L2 D5/D6 (queued).

## What I could NOT check

- Anything on a device, Chromium, emulator or simulator, by rule. That covers:
  - the gate-flag (`--post-load --bidi-bake --vt-bake`) wire differential;
  - U2's marker bake itself (my U2 census is by rule over the pre-fix wire, plus the hh-probe extract log);
  - every capture-level prediction and geometry line;
  - the R4/R4b controls on a post-landing run.
- That the gate's converter numbering produces exactly +1 × 10 in css-lists. I did not run `:converter:run` on a
  re-extracted section. The conclusion rests on the real extractor's 7 → 8, the measured contiguous per-section counter,
  and the hh-probe's +6 × 15 precedent.
- Swift (Catalyst) mutation replays and the full suites. Focused runs only, in exports; the sweep's record was read, not
  re-run. `doc-staleness-check.sh` was not re-run, because it runs the whole tooling suite on the shared tree. Its inputs
  (the doc rows) were compared by hand instead.
- Whether L3-A's revert conflict could be auto-resolved by a different merge strategy. I tested only the default 3-way
  `git revert`.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (read-only except this
directory and the §0 restore of three seam files to their HEAD bytes; no Gradle here). Gradle ran ONLY in the export
`/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/s1/exp-kt/apps/android-harness`
(`--no-daemon`; no daemon left: the live 9.6.1 daemons were 7686 and 23423 before and after).

STATUS: COMPLETE

## Fix pass (fix lane, 2026-10-07, on the shared tree — no commit, checkout, stash or reset; no device, Chromium, emulator or simulator)

Every defect's check was re-run first and reproduced. Runners, mutation records and the revert re-run are in `fixpass/`
(`mutations.out.txt`: sha256[:16] before / mutated / restored for every mutation; `revert-orders.rerun.out.txt`).
Gradle, Catalyst and vitest ran ONLY in exports of the fixed working tree (or of the A-only tree) in the session
scratchpad; the shared tree saw focused node runs only. Seam files untouched (`git diff --quiet` on all four).

### Defect → action

| # | re-check (before acting) | action | post-fix evidence |
|---|---|---|---|
| **M1** L3 A not independently revertible | `revert-orders.sh` in a fresh `--shared` clone: the same 13 lines (A alone conflicts in the three canvases; clean only after B-android, B-ios, B-web) | Both forms of the fix. (a) Pre-registered: PLAN §10 item 8 + inline note at §6 rule 6; `expectations.json` `revertUnits.A.revertOrder` = [B-android, B-ios, B-web, A], `revertTakes` = 006 ×3 back to wave53-open; revertRule[5] names the exception; A's `commit` string now names its 10 real paths (nit N4). (b) Prepared `wave53-canvas-root/a-only-revert.patch` (u2-narrow precedent): revert B×3 + A, re-pick B×3 with the three conflicts resolved onto the unstripped forest, then the fix pass's non-A edits; header = base sha256 per path | `git apply --check` clean on the fixed tree; patch-on-tree = the verified tree for every tracked file. A-only tree: web-harness 324/324, web runtime 1361/1361 (A's 6 + 8 pins gone), `tsc` 0 ×2, compose 3434/3434, android-harness 160/160 (A's 9 gone), Catalyst 2184/2184 (A's 8 gone); B-android's stack pin red under BK (composedCanvasRoots skips the forest) → restored → green |
| **M2** L1 wire radius / shadow attribution | `count-comps.mjs` re-run: nested 7 → 8, counter-suffix 19 → 19; css-lists `tests.list` line 38 of 48; per-test-ir ids contiguous (nested 381-387, next 388); hh-probe vs open extract.log 10 vs 4 runs; `counter-suffix.html` holds 0 `reversed` | `plan-build.py`: `wireRenumbering.addedAfter` and `measuredShadow.css-counter-styles` now say U2 (hunk M) with basis; new `measuredShadow.css-lists` {after nested, shift 1, documents 10, unit U1, basis "derived, not yet gate-measured"}; `revertUnits.U1/U2.wireShadow`. PLAN §10 item 9 + inline notes at §4 step 3 and §10 item 6; L1 note window result 5 corrected | `expectations.json` regenerated; the fields `adjudicate.mjs` / `control-check.mjs` read are identical (projection compare: True); `watchlist.txt` byte-identical (405) |
| **S1** §3 registry stale | `git diff -U0 e330e255 HEAD` on CR.swift: 8 hunks (L2 7, L4 1); seamcheck replays MATCH | PLAN §10 item 10 restates the registry (13 seam-file hunks: extract-fixture 3, CR.swift 8, CR.kt 2, tsx 0; + 2 patch-borne tests) + inline note at §3 | plan text only |
| **S2** L1 silent fallthrough (+ D3/D4/D5) | `bidi-marker-bake.mjs:98` `if (!el) continue;`; `silent-skip-repro.mjs` → `facts {}`, stamp null | `if (!el)` → error fact; error facts skip the CDP/model merge (declined + `marker-not-baked`). D3 pinned (VF2). D4: P1 pin in counter-bake.test.mjs + the P2 corner named in `setCounter`'s comment (order unchanged). D5: glyph tops `− q0`. Corpus radius 0: the only list items inside any bake root are counter-suffix's 4 (census of the 17 frozen fixtures); the CDP probe re-found all 4 with q.y 0 | New pins VF1 (the REAL in-page function on a fake `document`), VF2, VF3, P1; mutations FX1-FX5 red → restored → green. Focused node on the shared tree 678/678. File stays 200 lines |
| **S3** counter-suffix native labels | dump: both `degenerate->faithful`; ios `gating: true`, absent from geometryGating | `kind` = "RTL rows picture-correct; cell stays DEGENERATE on rows 3-6 (iOS)" / "… rows 5-6 (Android)"; ios row `gatingMeans` = floor 0.95 only, geometry report-only (= PLAN "0.95 · report"; no flag changed). L1 note §4 rows relabelled; PLAN §10 item 11 (incl. §1's "L1 6" = 4 + 2 partial). PR wording handed to the orchestrator | gate-read fields unchanged |
| **S4** L3 should-fixes 1-3 | grep: no test references `rootImageBackground` / the strip / the rewrite; `canvasModifier` composition unpinned | XI1: `wave53-canvas-root/ios-callsite-pins.mjs` (5 pins, 6 in-memory mutations). XC1-3: pure `canvasPaintPlan` → `CanvasPaint(bottomUp, overpaintFrame)`, consumed by `canvasModifier`; pin a7 on verbatim 001 + the target + a source pin of the consumer. Citation → `css-break/background-image-000/-001/-002` android P 1 (cells.mjs-verified; plain `{url: data:image/png…}` layers, checked in the IR) | iOS pins 5/5 GREEN, XI1-XI6 all RED (`ios-callsite-pins.out.txt`); ios-source-pins 7/7. XC1, XC2, XC3, XC9 red → restored → green |
| **S5** L4 should-fixes 1-4 | FloatAvoidPlan.kt checks Position only on container/BFC; no scale ≠ 1 pin; note l.81-83 unchanged; census on raw tree | P9 (both twins, via the .tmpl + `gen-pins.py`); §9.7 Position gate {absent, STATIC, RELATIVE} on both twins + P5d G6 (ABSOLUTE/FIXED refused, RELATIVE admitted); note sentence restated; both census pins walk `ContentsUnboxing.resolve` | Census both twins: 1435 docs, 10668 resolved components, the same 3 carriers. Mutations: Kotlin S1, S4, G6 red; Swift S1, S4, G6b red (SW-G6 was a broken mutation — BUILD FAILED, recorded, replaced) → restored → green |
| **S6** L5 IcbClip comments | grep l.24 / l.63 `buildPhase: resources` | Both comments name the Copy Files phase (`copyFiles: destination: resources`, dstSubfolderSpec 7) and why `buildPhase: resources` drops a `.swift` | comment-only (`//`/`///` lines only, checked); the ios-harness XCTest needs a simulator — not run |
| **S7** size rule | wc / numstat as reported | Split: `RootBackgroundUniformity.{kt,swift,ts}` (predicates moved verbatim): .kt 223 → 188, .swift 218 → 161, .ts 211 → 138. `build_fixtures` → sourced `tools/visual/smoke-fixtures.sh` (smoke.sh 386 → 354) + a wiring pin (SF1 red). **Not split, recorded as exceptions for the PR:** gallery +95, ScreenshotCaptureScreen +72, CaptureCanvas +42 (canvas-local A/B helpers in the hottest files — moving them now would re-touch the probe-gated call sites), bidi-bake.mjs +91, PseudoTextFold.swift 291 / .kt 269 (L1 U1 logic), test files over 200 (ComposedCanvasRootBackgroundTest.kt 250, the generated FloatAvoid pin files) | web RW1, RW3 and Swift SA5 + a stack-uniformity mutation red on the split modules; suites below |

Nits taken in passing: N2 ("the engine's own" ×4 → "the runtime's own"), N4 (both `revertUnits` strings). Not taken: N1
(the sweep record is the orchestrator's file), N3/N5/N6 (recorded where they are).

### Suites (exports of the FIXED working tree unless noted)

- compose `:runtime:testDebugUnitTest` 3434/3434 (+2: P9, P5d G6); android-harness `:app:testDebugUnitTest` 169/169 (+1: a7).
- SwiftUI Catalyst full: 2192/2192 (+2). The first export run showed 7 failing cases (ChUnitInlineAxis ×3, TableColumnWidths
  ×2, two Upright raster tests) — the export lacked the harness's Inter fonts; with `apps/ios-harness/StyleConverterTest/
  Resources` added: those 4 classes 8/8, then the full suite 2192, 0 failures.
- web runtime 1369/1369, web-harness 330/330, `tsc --noEmit` 0 on both.
- tooling: focused on the shared tree (extract-fixture-implied-close, extract-fixture, counter-bake, generated-content-bake,
  bidi-bake, counter-style-bake, smoke-port, interaction-states, a11y-audit, web-port-guard) 678/678.
- Expected full-sweep deltas: tooling 2300 → 2305 (+5), compose 3432 → 3434, android-harness 168 → 169, swiftui 2190 →
  2192; web 1369, web-harness 330, converter 551, ios-harness 30 unchanged.

### Hand-offs (orchestrator)

1. Commit the fix pass (it is not a revert unit of any lane; the L3-A `a-only-revert.patch` base is this fix-pass tree).
2. Re-run the single-writer sweep and restamp the doc tables before `wave53-probe`: `doc-staleness-check.sh` counts
   `@Test` / `func test` and will fail on the new pins until then.
3. PR text: the counter-suffix native labels (§10 item 11) and the S7 size exceptions above.
4. At `wave53-probe`: the css-lists +1 × 10 shadow is derived, not measured — read it in the wire control's "renumbered"
   rows (explaining carrier: counter-reset-reversed-nested).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (node only; no Gradle, no
xcodebuild). Gradle (`--no-daemon`) and xcodebuild (private `-derivedDataPath`) ran only in session-scratchpad exports:
`…/scratchpad/fix/{ktA,ktF,ktAF}/apps/android-harness` and `…/scratchpad/fix/{swF,swAF}`; no Gradle daemon left (the
live 9.6.1 daemons are still 7686 and 23423, the sweep's).

STATUS: COMPLETE (fix pass)
