TREES: /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/wave55-build-L4

# wave 55 · L4 ua-heading-margin — lane note

Builder lane L4 of wave 55 (PLAN `tools/titan/results/wave55-plan/PLAN.md` "### L4 · ua-heading-margin", brief
`wave55-plan/ua-heading-margin.md`, contract `expectations.json` lanes["L4-ua-heading-margin"], landing rows 13–16 of
`landing-manifest.json`). Worked in the lane worktree on the `TREES:` line (detached at 611c9f93, provisioned per §0:
both `local.properties`, the `node_modules` symlink, Swift `-derivedDataPath <wt>/.dd`). Nothing committed, nothing
checked out / stashed / reset outside the lane worktree; the only shared-tree path written is this directory. No device,
Chromium, emulator, simulator, gate, test-all or full suite ran: focused Compose / Catalyst suites only.

## What changed and why

**The defect (BACKLOG 0(h); looked at: ref vs iOS vs Android crops of inset-005 / -011 / -014 / -001, and the brief's
replay sheet `wave55-plan/ua-heading-margin-replay/inset-005.ref-M21android-M21ios-Dandroid.sbs.png`).** The four folded
abspos `<h1>`s of `css-text-decor/text-decoration-inset-005/-006/-011/-014` have no author margin; Chromium's UA sheet gives
them `margin-block: .67em` of their 2em face (21.44 px; 17.42 px for 014's 26 px monospace face). Both natives paint the
heading ~21 px high (iOS / Android first ink y109 / y107 where the ref has y134; 014 y111 vs y129), because a NESTED
out-of-flow box takes none of the three UA-margin routes (the §8.3.1 child plan bails the container on an out-of-flow
child; the root stack and the wave-52 T6 rule serve roots only). The baked-margin anchor inset-001 lands within 2 px of
the ref on both natives — the renderer already paints a DECLARED abspos margin.

**U1 — the margin, a cascade step after the UA font step (both natives).**
- Compose `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt`
  `UaPositionedHeadingMargin.apply(sourceTag, own, preUa, merged, enabled)` (184 lines): fires iff `enabled` (WPT capture)
  && the tag has a UA block margin (`UaBlockMarginFontBasis.uaBlockMarginEm`) && `ComponentRenderer.isOutOfFlowChild(own)`
  (css-position-3 §2.1, the renderer's own predicate over the post-reset OWN list) && ≥ 1 undeclared block edge
  (`declaresUaChildTop/Bottom`, css-cascade-4 §6.1 per edge) && the tag is h1–h6 && the writing mode is horizontal; it
  APPENDS `MarginTop` / `MarginBottom {"px": em × F}` on each undeclared edge (the T6 / anchor leaf shape) and returns the
  SAME list instance otherwise. F = `uaCascadeFacePx`: for an author-unsized heading `UAElementFontRule.sizeMultiplier(tag)
  × UAElementFontRule.emBasePx(preUa)` — the UA-CASCADE face, independent of the face gate (32 → 21.44; 014 26 → 17.42);
  for an author-sized heading the declared size when absolute (`UaBlockMarginFontBasis.ownFontSizePx` B3 / B4).
  Breadcrumbs (no silent fallthrough), once per key via `PropertyTracker.logUnhandled("MarginBlock", …)`: `tag:<t>` (an
  out-of-flow non-heading UA-margin tag: R9, queued), `vertical`, `size:<t>` (an author size this step cannot resolve).
- SwiftUI twin `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift`
  `UAPositionedHeadingMargin.apply(sourceTag:own:preUA:merged:enabled:)` (179 lines): the same rule; out-of-flow read
  = `ComponentRenderer.isOutOfFlow`'s `LayoutExtractor.extract(from:).position` over the post-reset own list; edges via
  `UABlockMargin.declaresBlockMarginTop/Bottom`; breadcrumbs via `PropertyTracker.logOnce` + a `hasLogged` test hook.
- Seams: Compose seam 1 inserts `val uaMarginProperties = …UaPositionedHeadingMargin.apply(component._tag, allReset.own,
  listUaProperties, rawProperties, enabled = LocalWptCaptureMode.current)` after the font step and re-points
  `val unresolvedProperties = uaMarginProperties` (so the margin reaches StyleApplier on the overlay child, before
  DynamicValueResolver). SwiftUI seam 2 hoists `let mergedPreUA` (the held seam's hoist, verbatim), binds the font step to
  `let fontProperties` and returns `UAPositionedHeadingMargin.apply(…, preUA: mergedPreUA, merged: fontProperties,
  enabled: wptCaptureMode)` from `mergedProperties(now:)`.
- Docs: `typography/UAElementFontRule.kt` header :26-27 names the out-of-flow owner (199 lines, unchanged count);
  `StyleEngine/typography/UAElementFontRule.swift` KNOWN GAPS margin bullet gains the out-of-flow owner (+2 lines).

**U2 — the held fold-aware face, restored behind U1.**
- `typography/UAHeadingFoldGate.kt` + `UAHeadingFoldGateTest.kt` and `StyleEngine/typography/UAHeadingFoldGate.swift` +
  `UAHeadingFoldGateTests.swift`: restored BYTE-IDENTICAL from `tools/titan/results/wave54-ua-heading-face/held/`
  (sha256 e0e3186d… / 0d99cd9a… / 2ca760a6… / 5c275b88… = `held/SHA256SUMS.txt`; their banners still say "wave 54 (lane
  L5)" — kept verbatim so the provenance sha256 holds). A heading with children stands down iff its runs do not fold:
  005 / 006 / 014 `Folded` (the face applies), 011 `Bailed member-prop:TextDecorationColor-divergence` (stands down).
- Compose seam 3: `standsDown = …UAHeadingFoldGate.standsDown(component, listUaProperties)` (+ the font-step comment
  re-trued); `UAElementFontRuleTest.kt` retargets the GO-SMALL pin to `fold gate - the folded hosts take the face (005, 006
  32, 014 26), the bailing 011 keeps its list`, the replay's `seam` to the gate, and drops the "gate forced open" framing.
- SwiftUI seam 4 (regenerated from `held/seam-2.foldgate.patch` on top of seam 2): the `hasElementChildren:` argument →
  `UAHeadingFoldGate.standsDown(component:inFlowChildren:mergedPreUA:)`. `UAElementFontRuleTests.swift`: the held diff
  (applies clean). `UAElementFontRule.swift`: TWIN STATUS + the `headingAppliesTo` record re-dated + the parameter doc
  (+4 lines; file 286 → 292, under the ~300 split line).

## Decisions taken against (or beyond) the brief — for the plan-level check

1. **`UAElementFontRule.headingStandsDown` is KEPT, not removed (PLAN §2 says "headingStandsDown removed").** HEAD's
   renderer line :1161 calls it; removing it in `unit-U2-android.patch` would leave the tree with the unit and without
   seam 3 uncompilable — the rule-2b failure the task forbids ("owned files must COMPILE without your seam patch"). Its
   kdoc now says it is superseded at the seam by `UAHeadingFoldGate` and kept while a renderer without seam 3 calls it.
   After the landing it has no caller in `runtimes/` (`grep` after seam 3: the renderer and the replay both call the
   gate). **Queue item for the docs pass / next wave:** delete `headingStandsDown` (one function + its kdoc) once seam 3
   has landed and survived its probes. Executed proof that the unit compiles without its seam: mutation AS1 / GS3 below
   (the renderer at HEAD bytes / at the HEAD gate line, everything else landed) BUILDs and turns only the seam pins red.
2. **An author-sized heading whose size is parent-relative (em / % / larger / smaller) is logged (`size:<tag>`) and
   left alone, rather than resolved over "the parent's published px, else 16".** At this cascade step the parent's px is
   not on the step's inputs (`mergeInherited` drops the inherited FontSize when the element declares its own); taking 16
   silently would be wrong under any sized ancestor. Absolute sizes (B3 px, B4 keyword) are used. 0 corpus carriers either
   way (census: every author-sized abspos heading declares both margins).
3. **Per-edge author rule = `uaChildBlockEdges`'s, not T6's.** One declared edge keeps the author's value and the other
   edge takes the UA value (pinned: "an abspos h1 declaring only MarginTop takes the UA bottom only"); T6 bails on any
   declared edge. The brief and PLAN say per edge; 0 corpus carriers with exactly one declared edge.
4. **U1-ios carries its own verbatim wire enum** (`UAPositionedHeadingWire` in `UAPositionedHeadingMarginTests.swift`,
   spliced by `gen-wire55.py --splice-swift`, `--check-swift` IDENTICAL-TO-GENERATOR): the Swift `UAHeadingFaceWire` lives
   in `UAHeadingFoldGateTests.swift`, which lands with U2-ios, so U1-ios could not use it without breaking the U1-ios-only
   state. Compose U1 reuses HEAD's `UAHeadingFaceWire` (UAElementFontRuleTest.kt) plus one generated constant
   (`S_11_1_1B_005`, `--check-kt` IDENTICAL-TO-GENERATOR). Every embedded constant's sha256 equals the wave54-final file
   (the HEAD constants name wave53-final paths; their sha256 are byte-identical to wave54-final's — checked).
5. **The SwiftUI seam-wiring pin reads `mergedProperties(now:)` only and does not read the `hasElementChildren:`
   argument,** so seam 4 needs no patch-borne edit (PLAN §3: seam 4 patch-borne "—").
6. The out-of-flow predicate is each renderer's own (`ComponentRenderer.isOutOfFlowChild` on Compose — first Position
   entry; `LayoutExtractor` position on Swift — as `ComponentRenderer.isOutOfFlow`), applied to the post-reset OWN list
   as the brief specifies; census: no out-of-flow UA-margin-tag component carries `All` or buckets (ANOMALY 0).

## Revert unit U1-android (landing row 13; revertOrder [U2-android, U1-android])

Paths (exactly):
- `unit-U1-android.patch`: `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt`
  (new), `runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginTest.kt` (new),
  `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` (header :26-27).
- `seam-1.patch`: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` (+17/−1
  after :1162, :1165 re-pointed); patch-borne
  `runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMarginSeamWiringTest.kt` (new),
  `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt` (handoff token),
  `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt` (module line after
  `"content/PseudoGeneratedBox.kt",` :159 — the pinned C10 anchor).
Captures it moves: android inset-005 / -006 / -011 / -014 (down 21.44 / 17.42 px as a block).

## Revert unit U1-ios (landing row 14; revertOrder [U2-ios, U1-ios])

Paths (exactly):
- `unit-U1-ios.patch`: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift`
  (new), `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginTests.swift` (new),
  `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift` (KNOWN GAPS bullet).
- `seam-2.patch`: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` (`mergedProperties(now:)`
  :340-356); patch-borne `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginSeamWiringTests.swift`
  (new).
Captures it moves: ios inset-005 / -006 / -011 / -014.

## Revert unit U2-android (landing row 15; revertOrder [U2-android]; `--hold L4:U2-android` = GO-SMALL)

Paths (exactly):
- `unit-U2-android.patch`: `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt`
  (restored, new), `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAHeadingFoldGateTest.kt`
  (restored, new), `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` (docs;
  `headingStandsDown` kept — decision 1), `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt`
  (retargets).
- `seam-3.patch` (cut on HEAD + U1-android): `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`
  (+8/−5 at :1152-1161); patch-borne `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt`
  (fontStep token) and `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt`
  (the `typography/UAHeadingFoldGate.kt` line directly under seam 1's).
Captures it moves: android inset-005 / -006 / -014 (011 stands down: identical under U2).

## Revert unit U2-ios (landing row 16; revertOrder [U2-ios])

Paths (exactly):
- `unit-U2-ios.patch`: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift`
  (restored, new), `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAHeadingFoldGateTests.swift` (restored, new),
  `runtimes/swiftui/Tests/StyleConverterRuntimeTests/UAElementFontRuleTests.swift` (the held diff),
  `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift` (docs).
- `seam-4.patch` (cut on HEAD + U1-ios): `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`
  (the `hasElementChildren:` argument + its comment); no patch-borne file.
Captures it moves: ios inset-005 / -006 / -014.

Line deltas: `ComponentRenderer.kt` 8488 → 8507 (seams only), `ComponentRenderer.swift` 6291 → 6315 (seams only),
`UAElementFontRule.kt` 199 → 197, `UAElementFontRule.swift` 286 → 292, `SeamReachabilityTest.kt` 239 → 245,
`UAElementFontRuleTest.kt` 243 → 252, `UAElementFontRuleTests.swift` 236 → 260; new logic files 184 / 179 lines.

## Census (own, behavioural) — cross-checked against expectations.json

Scripts (this directory): `census-jvm.kt.txt` + `run-census.sh` (Compose) and `census-swift.swift.txt` +
`run-census-swift.sh` (SwiftUI): each copies its harness into the lane worktree's test tree, runs ONLY it with the lane's
patches applied, deletes it, and walks all 1435 wave54-final per-test IR documents through the production decoder,
replaying the renderer's cascade step at every node (merge → ListStyleUaRule → UAElementFontRule under the HEAD gate AND
under UAHeadingFoldGate → the margin step; children inherit the post-step inheritable subset; a host whose runs fold
marks its members consumed). `census-check.py` compares the read-out with `expectations.json`.

- Outputs: `census-jvm.wave54-final.out.txt` → `census-check.wave54-final.out.txt`; `census-swift.wave54-final.out.txt`
  → `census-check-swift.wave54-final.out.txt`.
- Both platforms: 1435 documents; 22 out-of-flow UA-margin-tag components in 21 documents (the brief's 21 / 22); the
  step FIRES on 4 components in 4 documents — inset-005 / -006 / -011 (21.44) and -014 (17.42), none consumed by an
  ancestor fold, margin identical under either face gate (`faceIndependent=false: 0`); 14 declare both edges (identity:
  inset-001/002/003/004/007/008/009/012/013/015/016/024 + the two attachment-local `<p>`s); the abspos `<p>`s of
  s-11-1-1b-005 and anchor-center-safe-rtl ×2 and s-11-1-1b-006's ROOT `<p>` (raw IR; at capture T6 has already given
  it both margins) are non-headings → identity + the `tag:p` breadcrumb; UNEXPECTED-FIRE 0, DECODE-FAIL 0, ANOMALY 0.
- U2 flips (old has-children gate → fold gate) on exactly 3 heading hosts: inset-005 / -006 / -014 (011 stays down).
- **Verdict: IDENTICAL** to `lanes["L4-ua-heading-margin"]` on both platforms — U1-android 4 / U2-android 3 /
  captureCarriers.android 4; U1-ios 4 / U2-ios 3 / captureCarriers.ios 4; wire 0 (runtime-only; no converter, extractor or
  IR byte change). **censusDelta: identical — no plan regeneration needed.**
- Fixture net / 327-pair corpus: the step is capture-gated (`enabled` false off WPT capture) and keyed on `_tag`, which
  that corpus never carries → identity by construction (pinned: "capture off - the SAME list").

## Geometry probe self-test (pre-fix picture)

`/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave54-final --base wave54-open --lanes L4
--self-test` → **exit 0, `self-test: HOLDS — 0 violation(s), 0 self-check failure(s)`** (`geometry-gate.selftest.wave54-final.out.txt`):
54 keys — gating 12 all FAIL today (the 8 `[M]`, `[F]` 005/006/014 ios, `[F]` 014 android), control 34 all PASS (the 16
`[C]` declared-margin anchors, block-in-inline-015-print, s-11-1-1b-005, web / ref rows, the wave-54 probe's controls),
report 8 FAIL as registered; revert rule 4 names all four L4 units on the pre-fix picture, as it must.

## Export-tree dry runs (C12 / R8) — `export-check.sh` → `export-check.out.txt`

In a `git archive 611c9f93` export (never the shared tree): (1) the eight patches in landing order apply (`--check` first)
and reproduce the lane worktree byte-for-byte on all 18 touched paths; (2) both cross-platform revert interleavings
(U2-ios, U1-ios, U2-android, U1-android and the android-first one) give back the base byte-for-byte, and each U2 chain
reverts alone; (3) the WRONG intra-lane order refuses — seam 1 will not reverse while seam 3 is on the tree, seam 2 will
not while seam 4 is, unit-U1-android will not while U2-android is (shared `UAElementFontRule.kt` context): revertOrder
is load-bearing, exactly as registered; (4) seam 3 / seam 4 refuse on bare HEAD (they need their U1 seam).

## Pins (verbatim wire: wave54-final per-test IR) and their EXECUTED mutations

Focused suites, full lane state, green: Compose `UaPositionedHeadingMarginTest` 13/0, `UaPositionedHeadingMarginSeamWiringTest`
2/0, `UAElementFontRuleTest` 10/0, `UAElementFontRuleSeamWiringTest` 2/0, `UAHeadingFoldGateTest` 9/0, `SeamReachabilityTest`
3/0, `InlineRunFoldTest` 45/0; Catalyst `UAPositionedHeadingMarginTests` 12, `UAPositionedHeadingMarginSeamWiringTests` 2,
`UAElementFontRuleTests` 18, `UAHeadingFoldGateTests` 7 → Executed 39, 0 failures.

Every mutation below was executed by `mutate.py` (table in the script; outputs `mutations/<name>.out.txt`, logs
`mutations/<name>.{red,green}.txt`): one literal swap (or the file's bytes at 611c9f93 for `@HEAD`), the focused suite
RED with the named pin failing, the file restored byte-exact (post sha256 == pre), the suite GREEN again. The bracket
lines are in "## Mutation log" below, written before / after each edit. All 28 are **RED-THEN-GREEN**.

| # | pin (brief "Pins") | mutation executed | red (failing pins) |
|---|---|---|---|
| A1 / I1 | 005 / 006 / 011 → 21.44 | the PAINTED basis (F = the post-font list's FontSize, 16 while the face is held → 10.72) | K 5 (inset-005/-006/-011, inset-014, only-MarginTop, FIXED, author sizes) · S 5 (the same five pins) |
| A2 / I2 | 014 → 17.42 | em base 16 | K 2 (inset-014, seam replay) · S 1 (testInset014…) |
| A3 / I3 | 014 → 17.42 | the 16-px-root table (21) | K 6 · S 5 |
| A4 / I4 | inset-001 → same instance | drop the per-edge guard (`top = true`) | K 2 (inset-001, only-MarginTop) · S 2 |
| A5 / I5 | capture off → same | ignore `enabled` | K 1 · S 1 |
| A6 / I6 | in-flow leaf heading → same | drop the out-of-flow test | K 2 (text-decoration-color h3, RELATIVE/STICKY) · S 2 |
| A7 / I7 | s-11-1-1b-005's `<p>` → same, logged | widen past headings (heading guard off + multiplier 1 for non-headings) | K 1 · S 1 |
| AS1 / IS1 | seam wiring: the step exists once | the renderer at its 611c9f93 bytes | K 4 (both margin wiring pins + both font wiring pins) · S 2 |
| AS2 / IS2 | seam wiring | `enabled = true` / `enabled: true` | K 3 · S 2 |
| AS3 / IS3 | seam wiring | the step fed the pre-font list as the list it extends | K 3 · S 2 |
| G1 | 011 stands down | gate always false | K 5 (fold-gate pin, inset-011, stacked block, non-heading) |
| G2 | leaf / folded hosts apply | gate always true | K 8 (block-in-inline ×2, fold-gate pin, text-decoration-color …) |
| M3 / M3s | 005 / 006 / 014 apply (held M3 / M3s) | the old has-children gate | K 4 (fold-gate pin, inset-005/-006/-014) · S 3 |
| GS0 | the retargeted GO-SMALL pin goes red under seam 3's absence | the replay's `seam` on `headingStandsDown` | K 1 (`fold gate - the folded hosts take the face…`) |
| GS3 | seam-3 wiring | the renderer's `standsDown =` line back to `headingStandsDown` | K 2 (UAElementFontRuleSeamWiringTest) |
| M5 / M5s | fold-verdict invariance (held M5 / M5s) | the UA font step also writes Color | K 2 (soundness, inset-001 identity) · S 1 (testFoldVerdictIsInvariantUnderTheUAFace) |

Pins without a mutation (brief "—"): RELATIVE / STICKY / STATIC heading → same instance; vertical writing mode → same,
logged; fold verdicts unchanged by the margin step (005 / 006 / 014 Folded, 011 Bailed, both natives).

Process note, stated: the first launch of `mutate.py` crashed while PARSING A1's red run (it opened the results
directory `binary/`), after the red run had executed and with the mutated file still on disk; the file was restored by
the reverse literal swap to the recorded pre sha256 (bracket lines below say so), the parser and a `finally` restore were
fixed, and the whole Compose table was re-run from A1.

L2 cross-check (PLAN "If L2 changes `fontSizePxOf` semantics…"): L2's current `unit-U1.patch` touches
`DynamicValueResolver.kt` at :206 / :232 (`childContainingBlock`), not `fontSizePxOf` (:191) — no re-run owed today;
re-run `*UaPositionedHeadingMargin*` / `*UAElementFontRule*` if a later L2 cut changes :185-195.

## Predictions (wave54-final → the closing gate; restate against wave55-open — byte-identical per the opening gate)

| cell | from → predicted | confidence | gate | unit(s) | label |
|---|---|---|---|---|---|
| `wave54-final css-text-decor/text-decoration-inset-005 ios f 0.8995` | → P ≈0.966 (0.9631–0.9695) | MED (R1: iOS must wrap the FOLDED 2em paragraph at the CB) | [M] + [F] gating, floor 0.95 | U1-ios + U2-ios | picture-correct only if [M] and [F] print GEOMETRY OK |
| `wave54-final css-text-decor/text-decoration-inset-006 ios f 0.8987` | → P ≈0.966 (0.9632–0.9696) | MED | same | U1-ios + U2-ios | same |
| `wave54-final css-text-decor/text-decoration-inset-014 ios f 0.9238` | → P ≈0.967 (0.9635–0.9699) | MED-LOW (R6: no monospace anchor; 16ch re-resolved at 26 px) | same | U1-ios + U2-ios | same |
| `wave54-final css-text-decor/text-decoration-inset-014 android f 0.923` | → P ≈0.985 (0.9832–0.9861) | MED | same | U1-android + U2-android | faithful (definite 16ch) |
| `wave54-final css-text-decor/text-decoration-inset-005 android f 0.9` | → P ≈0.967 (0.9655–0.9684) | MED-LOW (R3 sup/sub per-span underline at 2em) | [M] gating; [F] REPORT | U1-android + U2-android | **DEGENERATE** (`stayDegenerateEvenIfPass`: Compose measures the abspos child unbounded, "fox" clipped on line 1) — never a gain |
| `wave54-final css-text-decor/text-decoration-inset-006 android f 0.8993` | → P ≈0.967 (0.9656–0.9685) | MED-LOW | same | same | **DEGENERATE** |
| `wave54-final css-text-decor/text-decoration-inset-011 ios f 0.6999` | → f ≈0.712 (0.7106–0.714), directed up | MED | [M] gating, floorNA | U1-ios | margin only (face stands down) |
| `wave54-final css-text-decor/text-decoration-inset-011 android f 0.6852` | → f ≈0.691 (0.6896–0.6933), directed up | MED | [M] gating, floorNA | U1-android | margin only |

U1 alone (the drop-L4-U2 arm = arm M): 005 ios ≈0.9167 / android ≈0.9121; 006 ≈0.9164 / ≈0.9117; 014 ≈0.9336 /
≈0.9328; 011 as above — every row up and f (MED-HIGH on direction; unexecuted on device). Lost 0, web 0, wire 0,
newly measured 0. Must not move (46 named, `watchlist.txt` block L4): the 24 declared-margin anchor natives
(identity: the step returns the same list — census `decl=TB`), every web cell of the 16 abspos-`<h1>` documents (no web
file touched), block-in-inline-015-print ios / android (in-flow leaf: identity; U2 leaves apply as before),
s-11-1-1b-005 / -006 natives (non-heading / T6-declared: identity).

## ORCHESTRATOR WINDOW REQUESTS

Each runs from the shared tree's root on the LANDED tree, after `beforeEveryDeviceRun` (PLAN §7); the exact strings are
`expectations.json` `stage1Probe` / `probeRun` / `abArms` (rendered in PLAN §7) — quoted, not re-invented:

1. **Stage 1 `wave55-pre`** — `tools/titan/gate-driver.sh wave55-pre --sections css-counter-styles,css-flexbox,css-grid,css-masking,css-text,css-text-decor,css-writing-modes,filter-effects,selectors --skip-fixture-net`
   with its `hashRead`, then the `readOut` (abDiff, score → floors, controls, geometry). L4's read:
   `/opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/geometry-gate.py wave55-pre --base wave55-open --sections css-counter-styles,css-flexbox,css-grid,css-masking,css-text,css-text-decor,css-writing-modes,filter-effects,selectors --json tools/titan/results/wave55-gate/stage1/wave55-pre/geometry.json`
   **Expected:** the 8 `[M]` native keys `→ GEOMETRY OK` (heading band 1 top ≈ ref 134 / 129 ±1 with the 2em face; 011
   every band = wave54-final + 21 ±1); `[F] 005 / 006 / 014 ios` and `[F] 014 android` `→ GEOMETRY OK`; `[F] 005 / 006
   android` WRONG `1 heading line band(s) vs ref 2` (report: the Compose no-wrap); every `[C]` (16 anchors) and ref / web
   row OK; the six carrier cells per the table above; css-text-decor natives outside the 8 carriers byte-identical.
   **Decides** (`stage1Decision`): a gating `[M]` FAIL reverts U2-<p> then U1-<p>; a gating `[F]` FAIL reverts U2-<p>; a
   fall ≥ 0.002 on a directed row reverts its unit; the two `[F]` android report rows decide nothing.
   **Plus (D33):** read `Δx1` on every face-present `[M]` / `[F]` native line and set `X1_TOL = max |Δ| + 2` by a probe
   edit committed with named paths and a regeneration BEFORE `wave55-probe` (§10) — no face-key decision before that re-read.
2. **The probe-time A/B arm `drop-L4-U2`** (FIRST after the stage-1 read-out, before any stage-1 revert commit): arms file
   line `drop-L4-U2 css-text-decor ios,android <sha of "wave55 L4 U2-ios…"> <sha of "wave55 L4 U2-android…">` (newest
   first; any `U2-*-S1` fix commits ahead of them), then
   `bash tools/titan/results/wave55-gate/ab-arms55.sh <arms-file> --dry-run --against wave55-pre` and
   `bash tools/titan/results/wave55-gate/ab-arms55.sh <arms-file> --run --against wave55-pre`; read
   `ab-diff.mjs wave55-ab-drop-L4-U2 wave55-pre --threshold 0.002 --platforms ios,android` and
   `node tools/titan/results/wave52-gate/cells.mjs 'text-decoration-inset-005|text-decoration-inset-006|text-decoration-inset-011|text-decoration-inset-014' wave55-open wave55-ab-drop-L4-U2 wave55-pre`.
   **Expected:** the arm = U1 alone (arm M): 005 ios ≈0.9167 / android ≈0.9121, 006 ≈0.9164 / ≈0.9117, 014 ≈0.9336 /
   ≈0.9328 — each up from wave55-open and f; the inset-011 natives identical between arm and wave55-pre (U2 stands down on
   011); on the arm the `[M]` keys stay OK (16-px face + margin) and the `[F]` keys WRONG. **Feeds:** U2's own
   contribution (Δ pre − arm on the six cells) — the D8 / D9 evidence that U2 is not carried by U1, and the GO-SMALL
   decision for U2-android.
3. **Stage 2 `wave55-probe`** (26 sections incl. css-break, CSS2): **expected** `[C] block-in-inline-015-print ios|android`
   and `[C] s-11-1-1b-005 ios|android` GEOMETRY OK, those cells and s-11-1-1b-006 natives byte-identical to wave55-open.
   **Feeds:** the must-not-move read of L4's controls outside css-text-decor.
4. **The closing gate and its arms** — L4's rows are read by `adjudicate.mjs` (R6 retires a degenerate gain only on
   `[F] 005 / 006 ios`; 005 / 006 android stay `stayDegenerateEvenIfPass` whatever they score).

No other window is needed by L4 (no Chromium, no converter step: wire 0).

## What was NOT verified (honest)

- Every device picture and score above: replays and pins only. That the margin lands at y130 / y128 on device (iOS may
  place 21.44 pt fractionally — R7), that iOS WRAPS a folded 2em paragraph at the containing block (R1: only inset-009's
  collapsed leaf is device evidence), that 014's 16ch re-resolves against 26 px on both natives (R6), and the sup/sub
  decoration class at 2em on Android (R3).
- That a correct-looking seam call produces the right paragraph: the seams are pinned at SOURCE level (patch-borne
  wiring tests), not executed as composables / SwiftUI views.
- Whether the folded paragraph's own text path ever applies `Margin*` from the host list (it would double the margin):
  traced — Compose `PlaceholderContent` reads only text styles from `runFold.properties`, and the baked-margin anchors
  (same overlay path) land within 2 px — but not executed for a folded host.
- The JVM census replays the cascade without `AllReset` / bucket folding / DynamicValueResolver: exact here because no
  out-of-flow UA-margin-tag component carries `All` or buckets (ANOMALY 0), not in general.
- Full suites (the orchestrator's single-writer sweep): only the focused classes above ran.

## Mutation log (bracketed; appended by mutate.py as each mutation executes — MUTATING before the edit, RESTORED after)

MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A1-painted-basis)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A1-painted-basis — restored by the reverse literal swap after mutate.py crashed parsing the red run's results dir; post == pre; A1 is re-run below)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A1-painted-basis)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A1-painted-basis)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A2-em-base-16)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A2-em-base-16)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A3-root-table)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A3-root-table)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A4-no-edge-guard)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A4-no-edge-guard)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A5-ignore-enabled)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A5-ignore-enabled)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A6-no-oof-test)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A6-no-oof-test)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A7-widen-table)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/UaPositionedHeadingMargin.kt sha256 3bb03d886fb06ced20e65dc170197cb27310068371d575c120554f809e998a65 (A7-widen-table)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (AS1-head-renderer)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (AS1-head-renderer)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (AS2-enabled-true)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (AS2-enabled-true)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (AS3-fed-preUa)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (AS3-fed-preUa)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt sha256 e0e3186dcbd7cddc05d80d32e3a4a582eee893ec40bee93889f47a66efccc578 (G1-gate-always-false)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt sha256 e0e3186dcbd7cddc05d80d32e3a4a582eee893ec40bee93889f47a66efccc578 (G1-gate-always-false)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt sha256 e0e3186dcbd7cddc05d80d32e3a4a582eee893ec40bee93889f47a66efccc578 (G2-gate-always-true)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt sha256 e0e3186dcbd7cddc05d80d32e3a4a582eee893ec40bee93889f47a66efccc578 (G2-gate-always-true)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt sha256 e0e3186dcbd7cddc05d80d32e3a4a582eee893ec40bee93889f47a66efccc578 (M3-old-has-children-gate)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAHeadingFoldGate.kt sha256 e0e3186dcbd7cddc05d80d32e3a4a582eee893ec40bee93889f47a66efccc578 (M3-old-has-children-gate)
MUTATING runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt sha256 950c6d00482b377c4bef8bf8f6668b9aed2c8c092532b6522444154ce37fbe2b (GS0-seam3-absent)
RESTORED runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt sha256 950c6d00482b377c4bef8bf8f6668b9aed2c8c092532b6522444154ce37fbe2b (GS0-seam3-absent)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (GS3-head-renderer-seam3)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt sha256 e30f443de13184cb1338bc7f38ef9ead192a312ab41d160ddb324a456bc6adef (GS3-head-renderer-seam3)
MUTATING runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt sha256 7129ff8e0f77cfe9a65b2f90dc95d7d3e08aa2d6361347c79bb96bdd49b27a6d (M5-ua-step-writes-color)
RESTORED runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt sha256 7129ff8e0f77cfe9a65b2f90dc95d7d3e08aa2d6361347c79bb96bdd49b27a6d (M5-ua-step-writes-color)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I1-painted-basis)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I1-painted-basis)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I2-em-base-16)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I2-em-base-16)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I3-root-table)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I3-root-table)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I4-no-edge-guard)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I4-no-edge-guard)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I5-ignore-enabled)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I5-ignore-enabled)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I6-no-oof-test)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I6-no-oof-test)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I7-widen-table)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/UAPositionedHeadingMargin.swift sha256 e9abac226c39167b1a1dc7d5b150a47ab3e963a2d252322da36e3bf6f2e18d80 (I7-widen-table)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 b5992e1a1b9b845702d6cf5693182b5e7b40df6c6a1b3c7a4999a6686f657906 (IS1-head-renderer)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 b5992e1a1b9b845702d6cf5693182b5e7b40df6c6a1b3c7a4999a6686f657906 (IS1-head-renderer)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 b5992e1a1b9b845702d6cf5693182b5e7b40df6c6a1b3c7a4999a6686f657906 (IS2-enabled-true)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 b5992e1a1b9b845702d6cf5693182b5e7b40df6c6a1b3c7a4999a6686f657906 (IS2-enabled-true)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 b5992e1a1b9b845702d6cf5693182b5e7b40df6c6a1b3c7a4999a6686f657906 (IS3-fed-preUA)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift sha256 b5992e1a1b9b845702d6cf5693182b5e7b40df6c6a1b3c7a4999a6686f657906 (IS3-fed-preUA)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift sha256 2ca760a686e7b8cb13c87e9ba6275c34987ddea77b923c94b64852c3a5cbea31 (M3s-old-has-children-gate)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAHeadingFoldGate.swift sha256 2ca760a686e7b8cb13c87e9ba6275c34987ddea77b923c94b64852c3a5cbea31 (M3s-old-has-children-gate)
MUTATING runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift sha256 911e6e1f2d54c902694122a0a172ca6af31974b9819c15c475aff67e6b3c2e81 (M5s-ua-step-writes-color)
RESTORED runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift sha256 911e6e1f2d54c902694122a0a172ca6af31974b9819c15c475aff67e6b3c2e81 (M5s-ua-step-writes-color)

## Revert states compiled (`revert-states.sh` → `revert-states.out.txt`; each state built on a clean 611c9f93 checkout of the lane worktree: `git checkout -- runtimes`, `git clean -fd` of the lane's new files, `git apply` of the kept patches; the full state restored last from the patches)

revertStatesCompiled: A(U2-android-reverted) compose: BUILD OK, GREEN [SeamReachabilityTest 3/0;UaPositionedHeadingMarginSeamWiringTest 2/0;UaPositionedHeadingMarginTest 13/0;UAElementFontRuleSeamWiringTest 2/0;UAElementFontRuleTest 10/0;InlineRunFoldTest 45/0;]
revertStatesCompiled: A(U2-ios-reverted) swiftui: BUILD OK, GREEN [Executed 30 tests, with 0 failures (0 unexpected) in 0.104 (0.114) seconds]
revertStatesCompiled: B(U2-android+U1-android-reverted=HEAD) compose: BUILD OK, GREEN [SeamReachabilityTest 3/0;UAElementFontRuleSeamWiringTest 2/0;UAElementFontRuleTest 10/0;InlineRunFoldTest 45/0;]
revertStatesCompiled: B(U2-ios+U1-ios-reverted=HEAD) swiftui: BUILD OK, GREEN [Executed 16 tests, with 0 failures (0 unexpected) in 0.044 (0.060) seconds]
revertStatesCompiled: F(full) compose: BUILD OK, GREEN [SeamReachabilityTest 3/0;UaPositionedHeadingMarginSeamWiringTest 2/0;UaPositionedHeadingMarginTest 13/0;UAElementFontRuleSeamWiringTest 2/0;UAElementFontRuleTest 10/0;UAHeadingFoldGateTest 9/0;InlineRunFoldTest 45/0;]
revertStatesCompiled: F(full) swiftui: BUILD OK, GREEN [Executed 39 tests, with 0 failures (0 unexpected) in 2.026 (3.836) seconds]

## Pre-landing rehearsal — `land-units.sh --check --only L4 --root <lane worktree>` → `land-units.check.only-L4.out.txt`

Run on the lane worktree brought back to clean 611c9f93 (`git checkout -- runtimes` + `git clean` of the new files), with
this lane dir copied in (then removed, and the full state re-applied from the patches; `export-check.sh` (1) re-run after:
IDENTICAL on all 18 paths). Result: **0 error(s), 2 warning(s) — landable**: every patch named with its sha256; 18 touched
paths clean at HEAD (10 created); the 8 patches apply cumulatively in landing order; all four revertOrder dry-runs
reverse-apply on the landed series (export tree). The two warnings: the raw `mutations/*.{red,green}.log` files were
gitignored (`*.log`) — the orchestrator renamed them to `.{red,green}.txt` before the records commit (wave-54 lesson 12), so they
are tracked beside each mutation's `mutations/<name>.out.txt` (verdict, sha256 triple,
failing pins, counts), which the records commit carries; and files written < 120 s before the check (timing only).

## Delivered patches (sha256 of the file bytes, written after the final restore)

patch: unit-U1-android.patch sha256 bc6358a0edf6eb04d0c753d371a19e0f43454dbeb4a656b7c0415e76204a4089
patch: seam-1.patch sha256 c9f3a12abe6557d08495e43723bb97424f5cd06877e6f93b2806f21ae3126de6
patch: unit-U1-ios.patch sha256 71ecfb163db841bf0eee09fbb5890063176e126d5b16846a171bbdf4e0a62bda
patch: seam-2.patch sha256 a46b64d58f73f6b756748637c96d57d9ffd04ecdab2633898c2088e440096378
patch: unit-U2-android.patch sha256 6af61ba4cb1a290bd06e24bc230f7d7e93e5f880b4c0136c8c1215d000636c6f
patch: seam-3.patch sha256 3faf4c60c28fc15d7694ca05a81ce8c2907a8b183d3b72ece47dad94d249df3e
patch: unit-U2-ios.patch sha256 c385491022578df5477bb7e79c81dbfcb8e5aa32e391d0792523d2eeb6e64f6f
patch: seam-4.patch sha256 b84fdf590a6d59debdde884f4d5b36cac67393cf643bb60484dc20242e7c7b1c

STATUS: COMPLETE
