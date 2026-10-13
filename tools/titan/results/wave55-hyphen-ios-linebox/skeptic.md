TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-skeptic-L1

# wave55 L1 · hyphen-ios-linebox — SKEPTIC report (unit NB-ios + contingency NB-ios-b)

Skeptic worktree on the TREES: line, `git worktree add --detach … 611c9f93`, provisioned per PLAN §0 (both
`local.properties`, the `node_modules` link) before any build; every Swift build used `-derivedDataPath <wt>/.dd`. Nothing was
committed, no device / Chromium / emulator / simulator / gate / full suite ran, and the lane's patches and note were not
edited. The skeptic wrote only this file and the `skeptic-*` files in this lane directory. The worktree is left at the
NB-ios state (unit-NB-ios + seam-1), sha256-checked.

## Verdict: CLAIM-HOLDS (no must-fix; two should-fix, four nits)

Every claim the note makes that can be executed off-device reproduced, using the skeptic's own scripts.
- **Patches.** They apply on 611c9f93 in landing order. Each `patch:` sha256 matches the file bytes. Each patch's paths
  equal the note's unit section and `landing-manifest.json`. The seam headers name the base and the seam file's sha256 at
  base: seam-1 `417853ce…26f3`, recomputed equal; seam-1b names the NB-ios state `208e41ba…c5f5`, recomputed equal.
  `land-units.sh --check` passes for L1 alone (0 errors, 0 warnings) and together with L4 (both Swift lanes that have
  delivered so far).
- **Pins.** All eight lane mutations were replayed red with re-written variants, plus a K0 the lane did not run. Every
  restore was byte-exact, followed by a green run.
- **Census.** An independent superset census found the same 14 iOS carriers, 0 web, 0 Android and 0 wire. The census
  did not grow.
- **Revert states.** All six states the note claims compile were rebuilt, and the focused suites pass in each.
- **Cells and pictures.** Every claimed "from" cell matches `cells.mjs`. The PNG geometry matches the description.

The should-fix items are coverage and decision-rule gaps. None contradicts a delivered claim.

## Repros (executed; records beside this file)

1. **Patch integrity.**
   - `shasum -a 256 *.patch` equals the four `patch:` lines:
     - unit-NB-ios `5658918a…3728`;
     - seam-1 `5c9f15bd…1721`;
     - unit-NB-ios-b `8f99c2a4…5aed`;
     - seam-1b `8d83b716…1c50`.
   - `git apply --check` succeeds for unit-NB-ios alone, for seam-1 alone and for both in applyOrder. unit-NB-ios-b plus
     seam-1b apply on top of them.
   - The blob chain is consistent:
     - ComponentRenderer.swift `1b734784` (= `HEAD:` blob) → `06cc4e93` → `03125a90`;
     - NormalLineBox.swift `c0e43154` → `2a02969c`;
     - NormalLineBoxTests.swift `42c4550a` → `6aaac741`.
   - Applied state: ComponentRenderer.swift sha256 `208e41ba…c5f5` (6309 lines), NormalLineBox.swift 160 lines, tests 191
     lines. With NB-ios-b: 178 / 199 lines and ComponentRenderer.swift 6315 lines. Seam diffstat +34 / −16 (seam-1) and
     +7 / −1 (seam-1b), as the note says.
   - Paths: the unit patches touch only the two owned NEW files. The seam patches touch only `ComponentRenderer.swift`.
     Nothing in the patches lies outside the lane's ownership.
   - `land-units.sh --check --only L1` → "0 error(s), 0 warning(s) — landable" (`skeptic-land-check.out.txt`).
   - `--only L1,L4` → 10 patches apply cumulatively, every revert dry-run reverse-applies, and NB-ios-b applies on top
     (`skeptic-land-check.L1L4.out.txt`). L5, whose seam 6 lands after L1's seam 1, had not delivered patches at review
     time.
2. **Pin mutations** (`skeptic-mutations.py`; records `skeptic-mutations.K1.out.txt`, `skeptic-mutations.out.txt` and
   `skeptic-mutations.K9.out.txt`; bracket lines below).
   - Red as required, each then restored byte-exact (post sha256 = pre `929108b6…c637`) and green 9/9:
     - K1 round the sum, written as terms unrounded with one rounding at the return → P2 (+P3) red;
     - K2 ceil the sum → P2 (+P3) red;
     - K3 banker's rounding on the descender term only → P3 red;
     - K4 drop exactness → P4 red;
     - K5 drop WPT → P4 red;
     - K6 ignore `effective` → P5 red;
     - K7 every no-face design → Inter → P6 red;
     - K8 drop the horizontal guard → P4 + P8 red;
     - K0 drop the declared-`normal` guard (no lane mutation names it) → P4 red.
   - K9 on the contingency state (verticalPads never splits) → P9 red; restore `a5f073ee…cf` byte-exact, green 10/10.
   - **Survivors. Each SX probe stayed GREEN on all 9 NormalLineBoxTests, so the branch it touches is untested:**
     - SX1: `faceMetrics` ignores the document face;
     - SX2: the below-raw log-once never fires;
     - SX3: banker's rounding on the line-gap term;
     - SX4: the size-0 guard removed.

     These are defects 2–4 below.
   - SX1b (`skeptic-face-probe.out.txt`): a THROWAWAY probe test, written in the skeptic worktree only and deleted
     afterwards, registers the committed `tools/titan/fonts/mono-pin/DejaVuSansMono.ttf` as the document face
     `monospace`, the way `DocumentFaceMetricsTests` does.
     - Green on NB-ios: Catalyst reports DejaVu 92 as asc 85.396 / desc −21.697 / lineHeight 107.0, and the Blink box with
       the face is 107 against 108 for the design alone.
     - RED under SX1, while all 9 NormalLineBoxTests stay green: (88.95, −19.41) ≠ (85.40, −21.70), and 108 == 108.
     - Green again after a byte-exact restore.
3. **Census** (`skeptic-census.py` → `skeptic-census.out.txt`). This script is independent of the lane's `census.py`
   and of the brief's `.census.mjs`. It is a deliberately SUPERSET model: every text component is assumed to reach
   PlaceholderLabel, and the wrap width is assumed known, so every run with a space is pre-broken. The all-reset and
   merge follow the renderer's order, the inherited set is parsed from `InheritedText.swift`, and face metrics come from
   the face files through fontTools.
   - Radius: 58 declared-`normal` runs in **18** documents. The lane counted 71 runs in 18, because it splits each
     hyphenate host per `<br>` group; the documents are identical.
   - **iOS carriers 14, identical to `expectations.json` `captureCarriers.ios` and to `revertUnits.NB-ios.captures.ios`**
     (missing-from-plan [] · plan-not-in-census []). Web, Android and wire are 0: the four patches touch only
     `runtimes/swiftui/`.
   - SAME: disclosure-styles (6 horizontal runs; its 12 vertical runs are declined), anonymous-table-ws-001 and
     boundary-shaping-010. DECLINED only: hyphens-vertical-001.
   - all-prop-001 is outside the radius: `All: INITIAL` is last in its own list. This confirms the lane's shrink finding.
   - The census does not grow, so the plan needs no regeneration.
   - The Catalyst SAME-controls log I re-ran matches the note's numbers exactly (`skeptic-same-controls.out.txt`: P8 plus
     29 SAME lines). all-prop-001 shows 0 runs, anonymous-table-ws-001 1, disclosure-styles 18 (6 horizontal, 12
     vertical), boundary-shaping-010 3 and hyphens-vertical-001 2 (vertical).
4. **Cells** (`node tools/titan/results/wave52-gate/cells.mjs '…' wave54-final`). Every "from" the note and plan cite
   matches:
   - hyphenate-character ios: -001 P 0.9611, -002 P 0.9534, -003 P 0.957, -004 f 0.9477;
   - boundary-shaping-001…008 ios: 0.9792 / 0.9695 / 0.9781 / 0.9774 / 0.9774 / 0.9714 / 0.9764 / 0.9789;
   - dotted ios: -001 f 0.5318, -002 f 0.5426;
   - controls ios: all-prop-001 P 0.9641, disclosure-styles f 0.6127, anonymous-table-ws-001 P 0.9988,
     boundary-shaping-010 P 0.965, hyphens-vertical-001 f 0.8801.

   The rows I could compare against wave55-open (the controls and dotted-001 / -002) are unchanged.
5. **Pictures.** I opened the ref, iOS and Android side by side for hyphenate-character-004, boundary-shaping-001,
   boundary-shaping-010 and text-decoration-dotted-001, and measured ink-row tops for 001 and 004.
   - hyphenate-character: iOS sits 0 / −1 / −2 (one row at −3) above the ref from the second group on. Android equals the
     ref row for row. This confirms the described "one to two rows HIGH" and the in-run −1s.
   - dotted-001: measured ink-band tops give a ref pitch of 151 / 151, Android 157 / 157 (111 + 46) and iOS 158 / 158
     (112 + 46). After NB-ios iOS reaches Android's 157, not the ref's: the f rows stay f, labelled correctly.
   - boundary-shaping-001: see nit 5.
6. **Geometry self-test** (`skeptic-geometry-selftest.out.txt`):
   - `--base wave55-open` → HOLDS, exit 0 (gating 3/3 FAIL, control 15/15 PASS, report 11/11 FAIL);
   - `--base wave54-final` → HOLDS identically;
   - `--base wave54-open`, the task text's base → exit 1 with 5 control FAILs, each `glyphs: 15 lines vs base 16`.

   The lane's explanation is right: `expectations.json` `base` is wave55-open, and wave54-open predates wave-54's
   hyphenate-character line fixes. This is not a lane defect.

   Caveat: these runs, and the lane's at 22:41, read the shared tree's WORKING-TREE probe. The parallel round-4 plan fix
   had left `hyphen-ios-linebox.geometry.py` modified and uncommitted at review time: its `ink:` XOR now counts two
   nested masks summed over the lines. That fix also touched `expectations.json` / PLAN.md, but for L1 only the `teeth`
   prose; L1's carriers, revert units and stage1Decision are unchanged. The verdicts above hold for that version.
7. **Revert states** (`skeptic-revert-states.out.txt`; each one built, then the five focused suites run):
   - NB-ios → 81/81;
   - NB-ios + NB-ios-b → 82/82;
   - contingency rule 2b (NB-ios + unit-NB-ios-b, seam-1b absent) → 82/82;
   - NB-ios-b reverted → NB-ios: `shasum -c` matches the NB-ios state on all three files;
   - rule 2b (unit-NB-ios only, ComponentRenderer.swift at base `417853ce…`) → 81/81;
   - 611c9f93 clean → 72/72.

   **Every `revertStatesCompiled:` line in the note is reproduced.**
8. **House rules.**
   - New logic sits in NEW files of 160 / 191 lines (178 / 199 with NB-ios-b).
   - Every code line is preceded by a WHY comment that cites CSS 2.1 §10.8.1 / §15.8, css-writing-modes-4 §7 or the
     platform API.
   - The seam carries one call site, one moved statement, and one one-line condition (seam-1b).
   - No scratchpad pointer appears in any patch, script or record; the lane note's only one is its TREES: line.
   - No probe leftovers: the two `print`s in the tests are the plan-required Catalyst log.
   - Rule 2b holds: the owned files compile without either seam patch.

## Defects (ranked)

1. **should-fix — the stage-1 decision rule can revert NB-ios in exactly the world NB-ios-b was built for.**
   - Where: `expectations.json` `lanes.L1-hyphen-ios-linebox.stage1Decision` and PLAN §2 L1, echoed by the note's window
     request 1.
   - What the rule says:
     - the anchor-only reason on the 3 gating rows → swap to NB-ios-b;
     - "any other WRONG on the 3 gating rows **or an iOS clause** → revert NB-ios";
     - and the iOS clauses include "boundary-shaping-010 … byte-identical".
   - Under R1 (no origin rounding, ceiled baseline: brief §9 R1), NB-ios's symmetric pad moves boundary-shaping-010's
     one-line Inter 36 run: half-leading 0.22 px, baseline 34.875 → 35.10, which can ceil one row lower. The brief names
     the in-radius SAME controls as the R1 tripwire. So the R1 signature is "anchor-only on the gating rows AND a SAME
     control one row low".
   - Read literally, that signature reverts NB-ios. Yet NB-ios-b's top pad of 0 puts every Blink-pinned run's glyphs back
     at y = 0, and would restore those controls too.
   - Fix: before stage 1 is read, append a restate row (or regenerate) so that this signature routes to
     `--contingency L1:NB-ios-b`. The signature is: gating reason `anchor: group 1 line 1 offset 1` only, iOS movers
     limited to the in-radius one-line SAME controls, each off by exactly one row. The pre-registered `wave55-pre-r1`
     re-probe must then require those controls byte-identical.
   - This is not a code defect. It must be settled before the stage-1 read, since the decisions are pre-registered.
2. **should-fix — the document-face branch of `NormalLineBox.faceMetrics` has no pin.** This is the face that 12 of the
   14 carriers paint: the mono-pin DejaVu for hyphenate-character-001…004 and Libertine `test` for boundary-shaping.
   - Evidence: SX1 (skip the face branch) survives all 9 NormalLineBoxTests.
     - P6 covers only the no-face designs.
     - P7 passes `text.fontFaceName`, which is nil on Catalyst, so its oracle `expectedBox(.monospaced, nil)` is the
       system face's box computed by the same code path: tautological for the 19 px.
   - A registered-face pin IS runnable on Catalyst. The lane note and the test header say "Catalyst cannot see the defect
     (integral UIFont metrics)", but for a registered TTF only `lineHeight` is integral (107.0). `ascender` / `descender`
     are raw (85.396 / −21.697). So the Blink box of the real face (16 px → 19, 92 px → 107) can be asserted, and SX1b
     turned such a probe red (repro 2).
   - On device, a regression of this branch would leave the gating rows green: DejaVu 16 and SF 16 both round to 19. It
     would show only in the report-class STACK keys, as boundary-shaping 41 → 44.
   - Fix: add a pin that registers `tools/titan/fonts/mono-pin/DejaVuSansMono.ttf` (pattern of `DocumentFaceMetricsTests`,
     with `DocumentFontRegistry.shared.clear()` in tearDown). It should assert that `faceMetrics(…, faceName:)` returns
     that UIFont's triple and that `usedLineHeight` at 16 px == 19 and at 92 px == 107, with SX1 executed red.
   - Ownership: NormalLineBoxTests.swift is at 191 lines, so the pin likely needs a NEW test file, which is outside L1's
     `own:` list. That is an ownership amendment for the orchestrator.
3. **nit — the PLAN §0 breadcrumb "the NormalLineBox log-once" is untested** (SX2 survived). Fix: a pin that calls
   `PropertyTracker._resetForTests()` and runs `usedLineHeight` on a below-raw face: on Catalyst, Inter's table at 92 px
   gives 111 < 111.32. It then asserts `PropertyTracker.logOnce(key: NormalLineBox.subNaturalLogKey, message: "")` returns
   false.
4. **nit — two more untested branches:**
   - the line-gap term's rounding mode (SX3; it matters only for a face whose gap is an exact half pixel at the size);
   - the size-0 guard (SX4).
5. **nit — honesty wording for the boundary-shaping rows.**
   - boundary-shaping-001…008 ios P (0.97–0.98) are DEGENERATE by picture. The ref paints ONE inline "office" line. All
     three runtimes stack three boxes, "of / f / ice".
   - NB-ios moves iOS's stack onto Android's 41-px stacking, not toward the ref. `STACK OK` is a cross-runtime stacking
     key, not picture-correctness.
   - The note never claims them, and `pictureCorrectTally` excludes them. The prediction table should still say DEGENERATE
     next to "P, the same to ±0.0001", so that no reader takes STACK OK for a fix.
   - The hyphenate-character-002 DEGENERATE label and the dotted "stays f" labels are correct.
6. **nit — stale plan prose; no regeneration need.** The `carrierRule` text ("60 text components in 19 documents … 5
   SAME") and §2 L1 R1 ("four in-radius controls") count all-prop-001. Both censuses place it outside the radius. The
   list shrinks, which is the safe direction. Reword at the next regeneration.

## What I could not check

- Anything on device:
  - the SwiftUI snapping model (R1 vs Rm);
  - whether the 0.19–0.22-px symmetric pad rounds away on the one-line SAME controls;
  - the predicted scores, Δs and STACK / LINEBOX rows;
  - NB-ios-b's `EdgeInsets` vs `.padding(.vertical,)` pixel identity for non-pinned runs.
- The Blink rounding rule's source. It is cited from memory and there is no Chromium checkout. I checked only its
  arithmetic against the ref boxes (Arial 92 → 105; DejaVu 16 → 19, matching the measured 114 / 95 / 76 group pitches).
- The census's two static assumptions:
  - route: every text run reaches PlaceholderLabel;
  - wrap width.

  Both are made in the superset direction, so they can only over-count. The hyphenate hosts' N ≥ 2 comes from a
  hyphenation flag, not a real dictionary break.
- The L5 Swift seams (seam 6 lands after L1 seam 1). They were not delivered at review time, so the cumulative apply with
  L5 is unchecked here. The cumulative apply with L4 passes.
- The full SwiftUI suite was not run, by rule; the focused suites only.

## Skeptic mutation brackets (append-only; written by skeptic-mutations.py BEFORE each edit / AFTER each byte-exact restore, in the skeptic worktree on the NB-ios state = 611c9f93 + unit-NB-ios.patch + seam-1.patch)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K1: round the SUM of the raw terms (variant: terms unrounded, one rounding at the return))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K1)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K2: ceil the sum (variant: terms unrounded, .up at the return))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K2)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K3: banker's rounding on the DESCENDER term only (variant of M3))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K3)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K4: drop the exactness guard)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K4)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K5: drop the WPT guard)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K5)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K6: Blink box overrides a non-nil effective (variant: effective ignored entirely))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K6)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K7: faceMetrics: every no-face design resolves to the Inter table (variant of M7))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K7)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K8: drop the horizontal-writing-mode guard (D22))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K8)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K0: drop the declared-normal guard (no lane mutation names it))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (K0)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX1: faceMetrics IGNORES the document face (branch 1 skipped) — the face 12 of the 14 carriers paint)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX1)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX2: the below-raw breadcrumb never fires (log-once removed))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX2)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX3: banker's rounding on the LINE-GAP term only)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX3)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX4: the size-0 guard removed (a 0 box returned instead of nil))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX4)
(contingency NB-ios-b state = NB-ios + unit-NB-ios-b.patch + seam-1b.patch; K9, --expect 10:)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 a5f073ee8491af36809f9dd7074049d224c68497b04968680b2954827bc806cf  (K9: NB-ios-b: verticalPads never splits (contingency state only))
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 a5f073ee8491af36809f9dd7074049d224c68497b04968680b2954827bc806cf  (K9)
(SX1 re-run against the skeptic's throwaway face-branch probe SkepticFaceBranchProbe.swift — worktree only, deleted after:)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX1b: faceMetrics ignores the document face, read by the throwaway probe)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/line/NormalLineBox.swift sha256 929108b6946f5141cd80c3c40070a6fed020b8943a337b9b5c63cc67cd66c637  (SX1b)
