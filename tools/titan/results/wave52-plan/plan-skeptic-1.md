# plan-skeptic-1 — ownership-disjointness-and-constraints pass over `PLAN.md` (2026-09-25)

Lens: no two lanes own one file; the seam-patch rule covers the shared renderers and `extract-fixture.mjs`; no lane
touches the ring-fenced test by name; every new check has a mutation plan; every prediction names a cell and a
direction; the plan honours every "## Standing constraints" bullet in `docs/BACKLOG.md`. Everything below was read from
`tools/titan/runs/wave51-fix/sections/*/manifest.json`, the runtime sources under this tree, and `docs/BACKLOG.md`;
nothing was built or run. The re-derivation script is `plan-skeptic-1.census.mjs` beside this file (read-only; `node
tools/titan/results/wave52-plan/plan-skeptic-1.census.mjs`, with `WATCH=<file>` / `ABSENCE=1` / `PREFIX=<substr>`).

## 1. Verdict

**Refuted as written — on constraints and landing order, not on ownership.** The file-level ownership table is
disjoint and complete, the seam rule is respected, the ring-fence is respected, every brief's pins carry a named
mutation, and all 25 explicit gate-cell citations in `PLAN.md` match the manifests in verdict and score (headline
1215/1379 · 1089/1369 · 1080/1369 reproduces). What does not check out: (a) L12-B's "predicted P→f: 0" contradicts a
pre-registered BACKLOG expectation on two cells that are P 1.0000 today; (b) L12-A's `score-gate.mjs` rule inverts a
standing constraint; (c) three cross-lane hunks have an inverted or undefined landing order, one of which cannot compile;
(d) two seam hunks called "adjacent" overlap; (e) 11 of 176 watchlist lines match no scored cell, including L7's headline
flip; (f) four citations use paths the manifests do not contain. Concrete corrections follow, most consequential first.

## 2. Refutations and corrections

### C1 · L12-B "predicted P→f: 0" is contradicted by the BACKLOG's own pre-registration (−2 on gaps-033 natives)
- BACKLOG "## Instrument decisions pending" → "The erased-reference body background — a CALIBRATION" states, "so they
  cannot be re-narrated afterwards": "033 web f → P (+1) and both natives P → f (−2), which is the honest direction".
- Manifest today: `wave51-fix css-gaps/flex/flex-gap-decorations-033 ios P 1.0000`, `… android P 1.0000`, `… web f 0.9400`.
  After the re-freeze the ref carries the 10-px rules the natives do not paint → both natives f.
- `PLAN.md` L12 says "predicted P→f: 0" and §7 says "**lost = 0**"; §1's headline (iOS ≈ 1103 / Android ≈ 1093) omits the −2.
  The watchlist does list `css-gaps/flex/flex-gap-decorations-033`, so the scorer will attribute it — but the plan's own
  prediction is wrong in sign. Correction: L12-B pre-declares `gaps-033 ios/android P → f (−2, honest)`, §7 reads
  "lost = 2, both named and both the BACKLOG's pre-registered direction", and the headline drops by 2 on each native.

### C2 · L12-A's `diffRuns` rule ("prev absence-only → cur scored-f is LOST") violates a standing constraint
- BACKLOG "## Standing constraints" → "The scorer of record for lost/gained is `tools/titan/score-gate.mjs`": "**A cell
  whose `scoreExcluded` stamp was REMOVED between the two runs is NEWLY MEASURED, never lost**". Code today:
  `tools/titan/score-gate.mjs:122` `if (!a && b) { res.newlyMeasured.push(row); continue; }` (`isScored` at `:50` excludes
  any stamp).
- `absence-only-denominator.md` §4 item 3 and pin 5 make exactly that transition print LOST; `PLAN.md` L12 adopts it
  ("prev `absence-only` → cur scored-f is LOST"). The brief's own §3 admits the opposite reading two paragraphs earlier
  ("a regression on a stamped cell surfaces as NEWLY MEASURED f, not LOST").
- Why the constraint's reasoning applies here too: a stamp is removed whenever the REF changes — L12-B's re-freeze gives a
  blank ref ink, so a formerly-absence-only cell becomes scored-f with byte-identical capture bytes. Under the plan's rule
  the calibration lane's own ref change prints LOST with no render change — the exact mis-report the constraint exists to
  prevent. Correction: keep NEWLY MEASURED and list those rows under an `absence-only → scored` heading; if the orchestrator
  wants the LOST semantics, the constraint text is amended in the same PR with this reason — never a silent redefinition
  of the scorer of record. (Lanes do not edit BACKLOG per §0, so the plan must carry the amendment paragraph.)

### C3 · L6 → L12 hand-off lands two steps early and breaks "exactly 19 unmeasured-now"
- `tools/titan/score-gate.mjs:185` `UNMEASURED NOW (scored before, not now — excluded or missing)` counts every previously
  scored cell regardless of its verdict. `wave51-fix css-counter-styles/armenian/css3-counter-styles-008 ios f 0.9420`,
  `… android f 0.9423` are scored today; re-adding the test to `NATIVE_FONT_PARITY_REFUSED_TESTS`
  (`tools/titan/inject-wpt-block.mjs:1308`, consulted at `:1520`) excludes both → closing-gate unmeasured-now = 21, not
  the "exactly 19" of L12 and §7.
- §4 lands L12 at step 2 and L6 at step 5, yet §3 says the hunk is "applied by the owner before its PR" — the adjudication
  (whose reason is L6's T3 web fix) would ship three steps before the lane that motivates it. Correction: the one-line
  re-add ships in L6's PR (L6 edits the L12-owned line after L12 has merged; L12 is told), and §7 reads "unmeasured-now =
  21: the 19 absence-only cells + armenian-008 ios/android (font-parity adjudication A)".

### C4 · L8 → L4 hunk (`StyleApplier.kt:805-830`) cannot compile in L4's PR
- `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/ChUnitMetrics.kt:38` is
  `fun measure(fontFamily: FontFamily?, fontSizePx: Float): Float?` — no `inlineAxisUpright` parameter exists until L8
  changes `ChUnitMetrics.kt` (L8 owns it; lands at step 5). `StyleApplier.kt:826-828` calls
  `ChUnitMetrics.measure(config.typography.fontFamily, fontSizePx)`; the hunk L8 delivers passes a third argument.
- §3 "non-seam cross-lane hunks … applied by the owner before its PR" + §4 "L4 … must precede L8's hunk" makes L4's JVM
  build red. Correction: the `buildSpacingContext` hunk ships in L8's PR (L8 touches L4's file after L4 has merged), or
  L8 first lands `inlineAxisUpright: Boolean = false` with a default in `ChUnitMetrics.kt` ahead of L4. The registry's
  arrow points the wrong way for this hunk.

### C5 · L11 → L3 hunk (`ContentsUnboxingTests.swift:175-183`) has no red-free landing order
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/ContentsUnboxingTests.swift:175-183`
  `testAllResetDropsEveryOtherDeclaration` feeds `[All INITIAL, Width 160, BackgroundColor blue]` and asserts
  `applyingAllReset(...).isEmpty`. Under L11's order-aware rule ("drop own[0,i) before the LAST `All`, keep own(i,end]")
  `Width` and `BackgroundColor` follow `all` and are KEPT — the existing pin goes red the moment L11's
  `GlobalExtractor.swift` lands, and the rewritten pin is red until it does.
- §4 step 4 orders "L3 before L7" and "L11 hands its hunk to L3 before L3's PR" but not L11 relative to L3. Whichever merges
  first leaves the Catalyst suite red on `dev` until the other lands. Correction: the `:175-183` rewrite ships in L11's PR
  (transfer that one test function to L11), or L3 and L11 land as one PR. This is the general shape of C3–C5: a hunk whose
  pin and runtime change are split across two PRs by the single-owner rule needs "same PR", not "owner applies it".

### C6 · Compose seam hunks L2 `:911-948` and L7 `:927-950` overlap; iOS L3 `~:2050` and L7 `:2098-2112` share a function
- `runtimes/compose/…/core/renderer/ComponentRenderer.kt:921-948` is one expression: `val itemModifier = if
  (hoistHostActive && CanvasRootHoist.rendersInFlowAsStaticPosition(component.properties, hoistHasPositionedAncestor,
  hoistHasTransformedAncestor, hoistHasClippingAncestor,)) { itemModifier.then(zeroFlowAnchor(…)) } else itemModifier`.
  L7's fifth argument lands inside the call (`:929-935`); L2's `zIndex` wraps the then-branch (`:940-948`). Lines 927–948
  are in both hunks — §3 calls them "ADJACENT". On iOS, `positionedChildren` begins at
  `runtimes/swiftui/…/Renderer/ComponentRenderer.swift:2050` and L7's `:2098-2112` is inside it.
- §0 requires a seam patch to "apply clean on the tree it names". A patch cut against HEAD by L7 will not apply after L2's
  (or L3's) patch. Correction: §3 states that L7 cuts both patches against the L2-applied (Compose) and L3-applied (iOS)
  trees and names those trees; §4 adds "L2 before L7" (today it has only "L3 before L7" and "L2 before L10").

### C7 · Eleven watchlist lines match zero scored cells (replayed with `score-gate.mjs`'s own rule)
- `tools/titan/score-gate.mjs:138-145`: `pat` must be a substring of `${sectionDir}/${manifestKey}` where the key is
  `css/<section>/<path>.html`. Replay over the 4117 scored cells of wave51-fix — 176 lines, 855 distinct cells matched,
  **11 dead**:
  `css-flexbox/position-absolute-containing-block-002 ios` (**L7's headline flip**), `…-containing-block-001 ios`,
  `css-flexbox/flex-abspos-staticpos-justify-self-001 ios`, `…-margin-00 ios`, `…-fallback-justify-content-001 ios`,
  `…-align-self-safe-00 ios` — all live under `css-flexbox/abspos/…`;
  `css-overflow/line-clamp/discard-multicol-003 android` → `css-overflow/line-clamp/discard/discard-multicol-003`;
  `css-anchor-position/anchor-position-colspan-003` → `css-anchor-position/anchor-position-multicol-colspan-003`;
  `css-counter-styles/broken-symbols`, `css-counter-styles/descriptor-suffix` → `css-counter-styles/counter-style-at-rule/…`;
  `selectors/any-link-attribute-removal` → `selectors/invalidation/any-link-attribute-removal`.
- Correction: replace the 11 patterns with the manifest paths above; re-run the replay (it must print `dead 0`).

### C8 · Four citations in `PLAN.md` are not manifest paths (values right, paths wrong)
- `wave51-fix css-grid/grid-abspos-staticpos-align-self-center android P 0.9808` and
  `css-grid/grid-abspos-staticpos-align-self-rtl-last-baseline-002 web P 0.9979` → section path is `css-grid/abspos/…`
  (values confirmed: android P 0.9808; web P 0.9979). `wave51-fix css-flexbox/position-absolute-containing-block-002 ios f
  0.9373` → `css-flexbox/abspos/position-absolute-containing-block-002` (confirmed f 0.9373). The range form
  `abspos-auto-sizing-fit-content-percentage-001..004 android P 0.9984` should be four cells (all P 0.9984; note the corpus
  has 001–008 at P 0.9984, and `percent-spacing-census.json` names only 001–004 as carriers — the b″ `cmp` A/B is on those
  four, which the plan says, and the watchlist pattern `…-percentage-00 android` harmlessly also watches 005–008).

### C9 · Smaller constraint items
- **"Every artifact is committed the moment it exists"** (obligation #0; standing "Evidence pointers … never a session
  scratchpad"): at this session's start `git status` in the gate tree showed `?? tools/titan/results/wave52-plan/` —
  the fourteen briefs, `PLAN.md` and `watchlist.txt` are untracked. Commit the directory before the opening gate.
- **Device A/B hash rule** ("A device A/B that claims to EXCLUDE a code change records the installed `base.apk` sha1 …
  the iOS twin hashes the installed `.app`"): L4's b″ records it; L6 T5, L8 M-A and L9 F2 are described as "device A/B"
  with pins but no hash sentence. State the APK/.app hash for every device A/B, include-direction or exclude-direction.
- **BACKLOG "The blank-capture guard"** says "Ship it as a CAPTURE FAILURE in the exit-7 family"; L12-A DECISION B declines
  that form with a recorded reason. This is permitted under "decide, then execute or Park", and the plan's substantive
  claim holds — my re-derivation of `absence-only-denominator.census.json` `populations.blankCaptureVsInkedRef` shows 36
  rows: 33 scored, all `f`, 3 already excluded — but the decision must reach the BACKLOG as an explicit amendment
  paragraph (orchestrator-owned file), not only as a lane note.

## 3. What checked out (so it is not re-litigated)

- **Ownership disjointness**: 166 `own:` entries across L1–L12; zero files owned twice (the single collision the census
  flagged is the directory shorthand `runtimes/swiftui/Tests/` used by L2 and L10 for different test files). Every
  non-new path resolves in the tree; 17 are declared new (`BakedLayoutSignature.kt`, `FlexNowrapLine.kt`, `AllReset.kt`,
  `KoreanHangulFormal.kt`, `BlockContainer.kt`, `MulticolSpannerContainingBlock.swift`, the new tests/fixtures,
  `corpus-v6-18.json`). `runtimes/compose/…/lists/ListMarkerRow.kt` (written as `lists/ListMarkerRow.kt`) exists.
- **Seam rule**: none of the four seam files appears in any `own:` list; every seam hunk in §3 has exactly one lane except
  the two hot regions of C6, which the registry already orders. Two shared dispatchers are outside the seam list but
  single-owner this wave — `runtimes/web/src/core/renderer/StyleBuilder.ts` (L11; L7 explicitly told not to widen
  `applyGeneric`) and `runtimes/swiftui/…/Renderer/StyleBuilder.swift` (L8, the ch block `:349-387` only). No conflict;
  noted so a wave-53 plan puts them on the seam list if a second lane needs them.
- **Line pointers**: every seam range in §3 was read and matches its description — Compose `:1134-1137` (`allReset`),
  `:1886-1890` (`childContainingBlock`), `:1983-2030` (provider block), `:2213`/`:2508` (two `Row(` routes),
  `:2781-2785` (`anyBakedChild` = Width ∧ Height), `:4021` (`RenderListItemMarker`), `:6973-6981` (`placeholderOverflow`
  call), `:7622-7637` (`uprightStack`), `:7760-7804` (function; `:7801-7803` returns `declared`), `:7931-7939`
  (`AlignItems` fold without SELF_END); iOS `:340` (`GlobalExtractor.applyingAllReset(to: InheritedText.merge(...))`),
  `:765` (`isOutOfFlow`), `:2050` (`positionedChildren`), `:2098-2112` (`staticOffset(containerW: childCB, containerH:
  childCBH)`), `:4354-4361` (Width ∧ Height guard), `:4405` (`markerPlacement`), `:4744` (`broken`), `:4969-4987`
  (`VerticalUprightTextFlow`), `:5071`/`:5076` (`lineLimit`/`fixedSize`), `:5458` (`styledSpanText`).
- **Ring-fence**: `filter-effects/backdrop-filter-basic-blur` appears in no `own:`, fix or pin sentence; it is a bare
  "REPORT" line in `watchlist.txt` and an "incidentally moves … report plainly" sentence in L2/§7 — the precedent form.
  Today: web P 0.9943 · ios f 0.9469 · android f 0.8990.
- **Mutation plans**: the briefs whose word-count for "mutation" is lowest (inline-run-wall 1, compose-containing-block
  1, vertical-wedges 4, failure-ink 4) still name a failure mode per pin in their §7 ("fails if the `HangingPunctuation`
  arm is removed", "Dies if the resolver starts subtracting bare-number padding", "Swapping the arguments → red", …);
  static-position §7, page-padding §7, absence-only pins 1–8 likewise. No pin without a named mutation was found.
- **Citations**: 25/25 explicit `wave51-fix <sec>/<test> <plat> <P|f> <ssim>` citations in `PLAN.md` match the manifests
  (tolerance 0.0006; the four of C8 match once the path is corrected). Spot-checked continuations (`· android f 0.9480`
  etc.) also match. Headline totals reproduce exactly; excluded cells web 56 / ios 66 / android 66.
- **L12-A's 19**: re-derived with my own script from `semanticPresence.bCoveragePct < 0.02` (`inject-wpt-block.mjs:743`)
  ∧ `wptPass === true` — exactly 19 cells / 7 tests, web 7 / ios 6 / android 6, the same names as the census.
- **Falsifiability**: every lane names cells and a direction, including the 0-flip lanes (L4 b″ "byte-identical PNGs",
  L11 "12 cells rise", L6 T2 "band MOVED to rows 161–163 / 171–175"). Only C1's sign is wrong.
- **Queue pointers**: every BACKLOG heading + phrase the plan quotes was found verbatim (0(a′), 0(a″), 0(a‴), 0(b″), 0(c),
  0(e), 0(j)–0(n), 1, 2, 3(b), 4(c), 5, 7(e³)).

## 4. What I could not verify

- Whether the two hot-region patches (C6) apply after re-cutting — no patch files exist yet.
- L2's claim that "197 P cells rise, none < 0.95" under Fix A (`overrun-ssim-prediction.json`) — a pixel simulation I did
  not replay; my lens is ownership/constraints. Same for L5's F-D differential (correctly marked UNMEASURED by the plan).
- Whether `buildSpacingContext` (`StyleApplier.kt:805`) has access to the merged `WritingMode` + `TextOrientation` L8's
  hunk needs (it takes only `config: StyleConfig`); the compile break of C4 stands regardless.
- The gaps-033 ref's ink fraction (it is not in the absence-only 19, so its `bCoveragePct` ≥ 0.02 — the natives' P 1.0000
  is therefore a same-ink-both-sides pass on an erased ref, not a blank-vs-blank; C1's direction is unchanged).
