TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-skeptic-L4

# wave 55 · L4 ua-heading-margin — skeptic

This is the adversarial skeptic of builder lane L4. Its sources:
- the contract: PLAN §2 "### L4 · ua-heading-margin", `expectations.json` lanes["L4-ua-heading-margin"], and landing rows
  13–16 of `landing-manifest.json`;
- the deliverables: the lane's `_note.md` and its 8 patches.

**Where the work ran.**
- All work ran in the skeptic's own worktree, named on the `TREES:` line. It is detached at 611c9f93 and provisioned per
  PLAN §0: both `local.properties`, the `node_modules` symlink, and Swift `-derivedDataPath <wt>/.dd`.
- The lane's patches were applied there with `git apply`, in landing order.
- Nothing was committed, checked out, stashed or reset outside that worktree.
- No device, Chromium, emulator, simulator, gate or full suite ran. Only focused Compose and Catalyst classes ran.

**Files written.** The only shared-tree paths written are new skeptic files in this lane dir:
- `skeptic.md` (this file);
- `skeptic-census.py` and its output `skeptic-census.wave54-final.out.txt`;
- `skeptic-mutate.py` and its log `skeptic-mutations.txt` (bracketed MUTATING / RESTORED lines plus each verdict);
- `skeptic-revert-states.sh` and its output `skeptic-revert-states.out.txt`;
- `skeptic-landcheck.L4.txt` and `skeptic-landcheck.cross.txt`, with scratch paths replaced by placeholders.

**Files not touched.** The lane's patches and `_note.md` were not edited. The task's rule (1) asks for a `TREES:` line as
the FIRST line of `_note.md`, but the task also says "never edit the lane's patches or note". So the skeptic's
`TREES:` line heads this file instead. `_note.md` keeps the builder's line, which is the one `land-units.sh --check`
reads.

## Verdict: CLAIM-HOLDS (one should-fix test gap, three nits; nothing blocks the gate)

Every claim I could check host-side holds:
- **Patches.** All 8 patches apply on 611c9f93 in landing order. The result is byte-identical to the builder worktree on
  all 18 touched paths.
- **Mutations.** All 28 of the lane's pin mutations replay RED-THEN-GREEN, and the failing-test counts match the note's
  table.
- **Census.** My independent census equals `expectations.json` exactly.
- **Revert states.** All six `revertStatesCompiled:` states rebuild green, with the lane's test counts.
- **Landing rehearsal.** `land-units.sh --check --only L4` reports "0 error(s), 2 warning(s) — landable". L4 also lands
  cumulatively with the other COMPLETE lanes (L7, L6, L2, L1: 24 patches) and every revertOrder reverse-applies.
- **Records.** Every `patch:` sha256 equals the file bytes, and the geometry self-test HOLDS.

My 12 extra mutations found one unpinned branch: seam 4, which is U2-ios's renderer wiring (defect 1).

## Executed repros (with outputs)

### R1 — patches, headers, sha256, paths (g, e)
- **sha256.** `shasum -a 256` of each patch equals its `patch:` line in `_note.md`: all 8 MATCH.
- **Seam headers.** All four name base tree 611c9f93 and the seam file's sha256 at base: `2445d9ea…` for .kt and
  `417853ce…` for .swift, both re-computed with `git show 611c9f93:<file> | shasum`. Each also names its content anchor.
  Seam 3 and seam 4 additionally name their cut tree (HEAD + its U1 seam).
- **Path lists.** Each patch's `diff --git` list equals the note's unit section exactly:
  - U1-android: 3 unit paths + 4 seam-1 paths;
  - U1-ios: 3 + 2;
  - U2-android: 4 + 3;
  - U2-ios: 4 + 1.
- **Ownership.** Every path is in PLAN §2's `own:` list or is a seam file. No unit patch shares a path with its own seam
  patch.
- **Scratch pointers.** No scratchpad pointer appears in any patch (grep for `private/tmp|scratchpad|claude-501`: 0 hits).
  The word "engine" does not appear, nor any TODO / print leftover.
- **Restored held files.** The four files equal `wave54-ua-heading-face/held/SHA256SUMS.txt`: e0e3186d…, 0d99cd9a…,
  2ca760a6…, 5c275b88….
- **Held test diff.** `UAElementFontRuleTests.swift` equals HEAD plus `held/UAElementFontRuleTests.swift.foldgate.diff`
  byte for byte. The held `UAElementFontRule.swift.foldgate.diff` no longer applies at HEAD. It is comments only (0 code
  lines), and the lane's re-written docs replace it.
- **Embedded wire.** All 16 constants equal the wave54-final per-test IR bytes: 8 in the Kotlin tests, including HEAD's
  `UAHeadingFaceWire`, and 8 in `UAPositionedHeadingMarginTests.swift`. The only difference is the file's trailing
  newline, and every claimed sha256 equals the file's.

### R2 — focused suites on the full lane state
- **Compose:** `SeamReachabilityTest 3/0 · UaPositionedHeadingMarginSeamWiringTest 2/0 · UaPositionedHeadingMarginTest
  13/0 · UAElementFontRuleSeamWiringTest 2/0 · UAElementFontRuleTest 10/0 · UAHeadingFoldGateTest 9/0 · InlineRunFoldTest
  45/0`. BUILD SUCCESSFUL.
- **Catalyst** (4 classes): `Executed 39 tests, with 0 failures` and `** TEST SUCCEEDED **`.

Both equal the note.

### R3 — every pin mutation replayed (a), plus 12 skeptic mutations
**Method.** `skeptic-mutate.py` is the skeptic's own runner. It re-types the note's literal swaps and adds 12 more. For
each mutation it:
1. writes `MUTATING <file> sha256 <pre>` before the edit;
2. runs the focused suite (Compose `*UaPositionedHeadingMargin*`, `*UAElementFontRule*` and `*UAHeadingFoldGate*`; or
   the 4 Catalyst classes) and parses the per-test failures;
3. restores the file byte-exact;
4. writes `RESTORED <file> sha256 <post>`;
5. runs the suite again.

**Log.** `skeptic-mutations.txt` has 40 MUTATING and 40 RESTORED lines, and every post equals its pre (checked by script).
After both chains the worktree is again byte-identical to the patched export on all 18 paths.

**The lane's mutations: all 28 are RED-THEN-GREEN.** The failing tests below match the note's "K n · S n" column.

| mutation | red: failing tests |
|---|---|
| A1 | 5 |
| A2 | 2 (seam-replay, inset-014) |
| A3 | 6 |
| A4 | 2 (inset-001, only-MarginTop) |
| A5 | 1 (capture off) |
| A6 | 2 (in flow, RELATIVE / STICKY) |
| A7 | 1 (s-11-1-1b-005) |
| AS1 | 4 (both margin wiring pins, both font wiring pins; compiled — rule 2b holds) |
| AS2 | 3 |
| AS3 | 3 |
| G1 | 5 (incl. inset-011) |
| G2 | 8 |
| M3 | 4 (fold-gate pin, inset-005 / -006 / -014) |
| GS0 | 1 (`fold gate - the folded hosts take the face…`) |
| GS3 | 2 (UAElementFontRuleSeamWiringTest) |
| M5 | 2 (inset-001, soundness) |
| I1 | 5 tests (14 assertion failures) |
| I2 | 1 (testInset014) |
| I3 | 5 |
| I4 | 2 |
| I5 | 1 |
| I6 | 2 |
| I7 | 1 |
| IS1 | 2 (both seam-wiring tests; compiled — rule 2b holds) |
| IS2 | 2 |
| IS3 | 2 |
| M3s | 3 (005 / 006 / 014 FoldedHostTakesTheFace) |
| M5s | 1 (testFoldVerdictIsInvariantUnderTheUAFace) |

**The skeptic's own mutations: 11 RED-THEN-GREEN, 1 SURVIVED.**

| mutation | what it does | result |
|---|---|---|
| XK1 / XS1 | bottom-edge guard forced `true` | inset-001 identity pin red |
| XK2 / XS2 | a parent-relative size resolved over 16 (`return a`) | author-sizes pin red |
| XK3 / XS3 | vertical check removed | vertical pin red |
| XK4 / XS5 | any Position counts as out of flow (RELATIVE fires) | RELATIVE / STICKY pin red |
| XK5 / XS6 | breadcrumb never recorded | 3 logged-pins red |
| XK6 | margin rounded to whole px | 6 red |
| **XS4** | **seam 4 removed**: ComponentRenderer.swift `hasElementChildren:` back to HEAD's `component.children?.isEmpty == false`, everything else landed | **SURVIVED — `Executed 39 tests, with 0 failures` under the mutation (defect 1)** |

### R4 — the blast radius re-derived independently (b)
`skeptic-census.py` is my own static Python replay over the 1435 wave54-final per-test IR documents. It never uses the
lane's JVM / Swift harness. It reports every out-of-flow UA-margin-tag component and checks for these hazards:
- Position read first vs last;
- Position, margin or `All` declared inside a media or selector bucket;
- root (T6) placement.

Output: `skeptic-census.wave54-final.out.txt`.

- **Out-of-flow UA-margin-tag components: 22 in 21 documents.** U1 FIRES on **4 components in 4 documents**:
  - inset-005 / -006 / -011 → 21.44 px on both edges;
  - inset-014 → 17.42 px on both edges (base 13: its own `FontFamily: monospace`).
- **Identity cases.**
  - 14 declare both edges: inset-001 / 002 / 003 / 004 / 007 / 008 / 009 / 012 / 013 / 015 / 016 / 024 and the two
    attachment-local `<p>`s.
  - 4 non-heading `<p>`s are identity with the `tag:p` breadcrumb: s-11-1-1b-005, anchor-center-safe-rtl ×2, and
    s-11-1-1b-006's ROOT `<p>`, which is T6's at capture.
- **Hazards:** 0 Position-first-vs-last disagreements, 0 multi-Position, 0 bucket Position or margin, 0 `All`. The one
  ROOT is the non-heading `<p>` above.
- **U2 population:** every h1–h6 with a child is exactly 4 hosts, inset-005 / -006 / -011 / -014. The old has-children
  gate stood all 4 down, and the fold gate applies the face to 005 / 006 / 014 only (011 Bails, pinned by G1 and M3).
  So U2 changes exactly 3 hosts per native.
- **Compared with `expectations.json`.**
  - `captureCarriers` ios = android = {005, 006, 011, 014} (4 / 4): **IDENTICAL**.
  - `revertUnits`: U1-android 4, U1-ios 4, U2-android {005, 006, 014}, U2-ios {005, 006, 014}: **IDENTICAL**.
  - `wireCarriers` []: no converter or extractor file touched (0 paths outside `runtimes/{compose,swiftui}`): **IDENTICAL**.
  - `carrierRule.U1` "7 components in 6 documents, the heading scope keeps 4" equals my 4 headings plus the 3 non-root
    `<p>`s. **No growth: no plan regeneration needed.**
- **Outside WPT.** No tracked fixture outside the gitignored `fixtures/wpt/` carries a `sourceTag` / `_tag` (grep: 0).
  U1 is capture-gated as well. So the fixture net and the 327-pair corpus are identity by construction.
- **Root double margin: checked, not a defect.** Could a root-level abspos heading take the UA margin twice? No, on both
  natives:
  - a hoisted root gets T6's margins baked into its own properties, so U1 is the identity;
  - an RC1 static-slot root contributes 0 / 0 to the root stack (`composedRootStackPlan`, Swift and Kotlin), so U1's
    margin is its only one.

### R5 — revert states re-run (f): `skeptic-revert-states.out.txt`
Each state was rebuilt on a clean 611c9f93 checkout of that platform's tree, with only the kept patches applied:
- Compose:
  - `A(U2-android-reverted)` rc 0: 7 touched paths; 13/0 · 2/0 · 2/0 · 10/0 · 3/0 · 45/0;
  - `B(=HEAD)` rc 0;
  - `F(full)` rc 0: adds UAHeadingFoldGateTest 9/0.
- SwiftUI:
  - `A(U2-ios-reverted)`: `Executed 30 tests, with 0 failures`;
  - `B(=HEAD)`: 16 / 0;
  - `F(full)`: 39 / 0.

All six equal the lane's `revertStatesCompiled:` lines, and the full state was re-applied afterwards.

**Export-tree dry runs** (my own `git apply` export of the 18 paths):
- the landing order applies;
- both interleavings reverse back to the base byte for byte;
- seam-1 refuses to reverse while seam-3 is on the tree, and seam-2 while seam-4 is;
- unit-U1-android refuses to reverse while U2-android is on the tree;
- seam-3 and seam-4 refuse on bare HEAD.

One observation: unit-U1-ios alone DOES reverse with U2-ios on the tree. The lane never claimed otherwise, and the U1-ios
commit still refuses because seam-2 is part of it.

### R6 — landing rehearsal: `skeptic-landcheck.{L4,cross}.txt`
- **L4 only.** `land-units.sh --check --only L4 --root <skeptic worktree at clean 611c9f93>`: "0 error(s), 2 warning(s) —
  landable". The warnings are the gitignored `mutations/*.log` and the file-age timing. 18 touched paths are clean; the
  cumulative series of 8 patches applies; the four C12 revert dry-runs are ok.
- **Cross-lane.** `--only L7,L6,L4,L2,L1`: 24 patches apply cumulatively and every revertOrder (L4's included)
  reverse-applies. Its 6 errors are all "force-add MISSING tools/titan/results/wave54-oof-layout/…/out/*". Those are
  gitignored wave-54 files that exist only on the shared tree, so this is an artifact of my rehearsal root, not an L4
  defect.

### R7 — scores (c) and geometry self-test
- **Scores.** `cells.mjs … wave54-final` gives these values:

  | cell | iOS | Android |
  |---|---|---|
  | inset-005 | f 0.8995 | f 0.9 |
  | inset-006 | f 0.8987 | f 0.8993 |
  | inset-014 | f 0.9238 | f 0.923 |
  | inset-011 | f 0.6999 | f 0.6852 |

  The anchors are:
  - inset-001 ios P 0.9707 / android P 0.9848;
  - inset-009 ios P 0.9585 / android P 0.9638;
  - block-in-inline-015-print ios P 0.9894 / android P 0.9986;
  - s-11-1-1b-005 ios f 0.9536 / android f 0.9307.

  wave55-open prints the same 8 carrier rows. **Every "from" value in the note and PLAN is correct.**
- **Geometry.** `geometry-gate.py wave54-final --base wave54-open --lanes L4 --self-test` exits 0 with "self-test: HOLDS — 0
  violation(s)": 54 keys, gating 12 (all FAIL today), control 34 (all PASS), report 8 FAIL. Revert rule 4 names all four
  L4 units. This equals the note.

### R8 — the PNGs (d)
I looked at ref | iOS | Android crops of inset-005 / -011 / -014 / -001 from the frozen refs and the wave54-final
captures, plus the lane's replay sheet `ua-heading-margin-replay/inset-005.ref-M21android-M21ios-Dandroid.sbs.png`.

Measured ink bands (y):

| test | ref | iOS | Android |
|---|---|---|---|
| inset-005 heading | 134–173 + "fox" 188–212 | 109–131 | 106–128 |
| inset-014 heading | 129–154 / 163–182 | 110–122 / 127–137 | 111–123 / 127–136 |
| inset-001 | 115–145 | 117–146 | 116–145 (declared margin lands within 2 px) |

The note's geometry (y109 / y107 vs y134; 014 y111 vs y129; 001 within 2 px) holds.

The replay sheet's panel D (Android, face plus margin) shows "fox" running off at x≈390, which is the DEGENERATE picture
the note labels. **inset-011:** the natives paint no blue or green underline at all, and their paragraph wraps to 4
lines where the ref's has 3 (see the risk note below).

### R9 — honesty (h)
- **Android 005 / 006.** These predicted P cells are labelled **DEGENERATE**, and they sit on `stayDegenerateEvenIfPass`
  and `degenerateByConstruction` (checked in `expectations.json`).
- **iOS 005 / 006 / 014.** These are claimed only behind `[M]` + `[F]` GEOMETRY OK, with R1 / R6 stated.
- **inset-011.** This is "margin only", f→f with `floorNA`.
- **Not verified.** Every device number is listed as unverified, in the note's "What was NOT verified".

No over-claim found.

## Defects (ranked)

1. **should-fix — the U2-ios renderer wiring (seam 4) has no host-side pin.**
   - **Evidence.** XS4 was executed. It put ComponentRenderer.swift's `hasElementChildren:` argument back to
     `component.children?.isEmpty == false`, which is HEAD's line, and left every other L4 patch landed. The Catalyst
     focused suites stayed `Executed 39 tests, with 0 failures`.
   - **Why nothing catches it.** No Swift test reads that argument. `UAPositionedHeadingMarginSeamWiringTests` says
     outright that it does not, `UAHeadingFoldGateTests` is a replay, and a grep of the test tree finds no other reader.
     Compose's twin is pinned: GS3 turns `UAElementFontRuleSeamWiringTest` red.
   - **Impact.** Seam 4 is the only line that makes U2-ios do anything. A dropped or mis-anchored seam 4, or a later edit
     that undoes it, passes every host suite. Only the stage-1 `[F] … ios` device keys would catch it, at the cost of a
     device window and a U2-ios revert.
   - **Fix.** Ship a patch-borne pin in `seam-4.patch`. Either add a test to `UAPositionedHeadingMarginSeamWiringTests.swift`
     or create `UAHeadingFoldGateSeamWiringTests.swift`. It should assert that this token occurs once, inside
     `mergedProperties(now:)`:

     ```
     hasElementChildren: UAHeadingFoldGate.standsDown( component: component, inFlowChildren: inFlowChildren, mergedPreUA: mergedPreUA))
     ```

     Prove it red under XS4. This changes PLAN §3 .swift row 2's patch-borne "—", so record it in §10. It does not block
     the gate.
2. **nit — the U2-android delivery deviates from the plan text.**
   - **The deviation.** PLAN §2 and `expectations.json` revertUnits["U2-android"].commit say "headingStandsDown removed".
     The patch keeps the function (the note's decision 1).
   - **The lane is right.** Under rule 2b, removing it would leave "U2-android without seam 3" uncompilable. The GS3 replay
     compiles only because the function exists.
   - **What is false.** The new kdoc says "its removal is queued (BACKLOG)", but `docs/BACKLOG.md` has no such item yet.
   - **Fix.** The orchestrator records the deviation in PLAN §10 and adds the queue item at ship. Otherwise the kdoc claim
     is false.
3. **nit — the out-of-flow predicate's input differs from the overlay split's input.**
   - **The difference.** The step tests the POST-bucket, post-`AllReset` OWN list. The parent's overlay split tests the
     RAW `child.properties`:
     - Compose RenderContent: `extractPositionType(child.properties)` → `RenderAbsoluteChild`;
     - Swift: `ComponentRenderer.isOutOfFlow(child)`.
   - **The overstated comment.** The Kotlin import comment says the step "fires exactly where RenderAbsoluteChild mounts
     the box". That is not exact for a Position that a media or selector bucket supplies, or that `all` resets. Such a
     box could be margined in flow on top of the §8.3.1 plan, or lose its UA margin.
   - **Impact.** No corpus element is affected: my census counts 0 such UA-margin-tag elements and 0 with more than one
     Position.
   - **Fix.** Re-word the comment, or test `component.properties` the way the mount does. Not urgent.
4. **nit — size and comments.**
   - Two test files exceed 200 lines: `UAPositionedHeadingMarginTests.swift` (271) and `UaPositionedHeadingMarginTest.kt`
     (225). They are tests, not logic; the new logic files are 179 and 184 lines.
   - Three lines carry no comment of their own: the two Swift imports in `UAPositionedHeadingMargin.swift`, and seam-2's
     `let fontProperties = UAElementFontRule.apply(` binding.
   - `UAElementFontRuleTests.swift` grows 236 → 260 lines (the held diff). It is a touched file over 200 lines that is
     missing from PLAN §0's list, although its ownership entry allows the edit.

**Risk note for the stage-1 reader (not a defect; the lane labels it).**
- **The mechanism vs the picture.** `[M] text-decoration-inset-011 ios|android` is a MECHANISM key (every band =
  wave54-final + 21 ±1). In absolute terms the native 011 heading moves further from the ref:
  - today its first ink is y136 against the ref's y144, about 8 px high;
  - after U1 it is ≈y157, about 13 px low.

  The reason is that the natives' Noto-serif paragraph wraps to 4 lines where the ref's Times has 3.
- **The prediction.** The f→f "up" prediction (+0.0124 iOS / +0.0061 Android) rests on the M21 replay.
- **What a miss costs.** A device fall ≥ 0.002 on either 011 row reverts U1-<p> as a whole, which also takes 005 / 006 /
  014's margin off that platform. Read 011 first.

## What I could not check
- Any device picture or score: no device step is allowed here. The predictions, R1 (iOS wrap of a folded 2em paragraph),
  R3 (sup / sub at 2em on Android) and R6 (014's 16ch at 26 px) are unexecuted, as in the note.
- That StyleApplier / MarginApplier paint the appended `MarginTop` on the overlay child exactly like inset-001's declared
  margin. I traced it: `RenderAbsoluteChild` calls `RenderComponent(child, …)` on the child's own merged list, and iOS
  returns the step from `mergedProperties(now:)`. But no composable or SwiftUI view was executed.
- The lane's JVM / Swift census harnesses themselves: by design I used my own static replay instead. Mine does not run
  `AllReset`, bucket folding or DynamicValueResolver. It flags their inputs instead, and finds none.
- The full Compose / Catalyst / converter suites, which are the orchestrator's single-writer sweep.
- Swift `LayoutExtractor`'s Position read order (first vs last). It is moot here: the corpus has 0 multi-Position
  elements.
