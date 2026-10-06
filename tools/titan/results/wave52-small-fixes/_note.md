# L4 · small-fixes — lane note (wave 52, 2026-09-25)

Tree: `campaign/wave52` @ HEAD `7d9c22a7` (dev `5d9ed628` + the committed plan). Briefs: `native-near-misses.md` §3.5 (T5), §3.7 (T7);
`compose-containing-block-level.md` §4 (b″, b′). No device was booted, no `test-all.sh`, no feed; every number below is a JVM /
Catalyst / node run or a read of the frozen `wave51-fix` captures and refs.

## What changed, and why

### T5 — `backface-visibility: hidden` no longer culls the SUBTREE under `transform-style: preserve-3d` (both natives)
- Target: `wave51-fix css-transforms/composited-under-rotateY-180deg-preserve-3d ios f 0.9565 · android f 0.9565` — blank canvas where the
  ref paints a green 100×100 at (16,16). Parent `rotateY(180deg); backface-visibility: hidden; transform-style: preserve-3d; width: 100`,
  child 100×100 green. css-transforms-2 §10 hides the ELEMENT; §4.1.2 says under preserve-3d the descendants are NOT flattened into its
  plane — each is its own plane with its own (initial `visible`) backface-visibility. Both natives alpha'd the whole subtree.
- Compose: new `transforms/BackfaceCull.kt` (145 lines) — `decide(interactions, transforms, transform3D)` → `NONE | HIDE_SUBTREE |
  CULL_OWN_FACE`; the element-local DEGREE rule (`isBackFacing`) is carried over VERBATIM from the StyleApplier banner (the matrix3d
  animated-001/002 rejection stays). `StyleApplier.applyConfig` step 1.5 now `alpha(0f)`s only on `HIDE_SUBTREE`; on `CULL_OWN_FACE` it
  builds `paint = BackfaceCull.stripOwnFace(config)` (background colour + image layers, border sides + outline removed; opacity, layout,
  radius, effects, overflow, padding untouched) and steps 5./6. (borders → colors, both radius routes) read `paint` instead of `config`
  (`paint === config` for NONE / HIDE_SUBTREE, so every other element is byte-identical).
- iOS: `BackfaceCulling.decide(agg:inherited:)` → `.none | .hideSubtree | .cullOwnFace` on top of the untouched wave-19 `isCulled`
  (accumulated-matrix predicate); `TransformsApplier` Step 4 switches on it: `.hideSubtree` → `opacity(0)` as before; `.cullOwnFace` →
  the content is left painted and a `PropertyTracker.logOnce("BackfaceVisibility[preserve-3d-own-face-approximated]")` breadcrumb fires.
  **Documented approximation (iOS only):** `content` reaching TransformsApplier is the fully composed element — its own `.engineBackground*`
  / `.engineBorder*` decoration is INNER of this modifier (StyleBuilder.applyStyle) and cannot be separated from the children by an outer
  modifier, so on iOS a culled preserve-3d element's own back face stays painted. The census shows 0 culled preserve-3d elements with own
  decoration (the one carrier has none), so no cell can observe it; Compose culls the own face for real. Stated in code, not hidden.
- Not culled on either native (named): the culled preserve-3d element's own TEXT and its box-shadow. 0 carriers (census, incl. BoxShadow and
  Outline since the fix pass). Since the fix pass Compose breadcrumbs a kept box-shadow (`BackfaceCull.SHADOW_KEPT_BREADCRUMB`); own text is
  not visible from StyleConfig and is named in the header only.

### T7 — Compose `transform: inherit` (css-cascade-4 §7.3.2)
- Target: `wave51-fix css-transforms/css-transform-inherit-scale android f 0.9965` — red 200×200 with a 100×100 green centred on it
  (the child's own inherited scale(2) dropped; iOS P 0.9974 with the same wire).
- New `transforms/TransformInheritance.kt` (151 lines after the fix pass): `LocalInheritedTransform: CompositionLocal<JsonElement?>` carrying
  the PARENT'S COMPUTED `Transform` wire; `TransformInheritance.NONE` (`JsonNull`) = the parent computes `none`; Kotlin `null` (the default) =
  UNPROVIDED (fix pass, skeptic should-fix 2 — see below). `resolve(properties, inherited)` substitutes the `{"type":"keyword","keyword":
  "inherit"}` entry with the parent's wire, DROPS it under `NONE`, and leaves it in place when unprovided (so the extractor's breadcrumb
  fires) — identity, same instance, for every list without the keyword (1434 / 1435 documents); `published(resolved)` = the element's last
  `Transform` data, or `NONE`, never null; `carriesInherit(list)` gates the renderer's read. Why the WIRE and not the extracted
  function list: `TransformExtractor` is reached from `StyleApplier.extractConfig` (StyleConfig is not this lane's file and cannot read a
  CompositionLocal); the only composable that sees parent and child is `RenderComponent`, and the renderer already substitutes inherited
  `Color` for `currentColor` in the same way. Unlike the iOS twin's documented approximation (an untransformed wrapper forwards the nearest
  TRANSFORMED ancestor's list), the Compose channel is provided at every RenderComponent, so `inherit` under an untransformed parent reads
  the §7.3.2 `none` (`NONE`).
- `TransformExtractor.kt`: the `inherit` branch keeps its `PropertyTracker.markUnhandled("Transform")` breadcrumb — it is now the
  FALLBACK for a keyword the renderer did not resolve (seam not applied, or a path that renders without RenderComponent); comment updated.
- **Seam patch `seam-1.patch`** (v2 since the fix pass; ComponentRenderer.kt, 3 hunks, applies clean on HEAD): rename `val effectiveProperties`
  → `effectivePropertiesBeforeTransformInherit` (:1241), add `val effectiveProperties = if (TransformInheritance.carriesInherit(…))
  TransformInheritance.resolve(…, LocalInheritedTransform.current) else …` right after the out-of-flow branch (before `propertyPairs`), and
  `TransformInheritance.LocalInheritedTransform provides TransformInheritance.published(effectiveProperties)` in the `inheritanceWrappedContent`
  provider block (next to the wave-50 B2 line). v1 (2026-09-25, kept as `old-seam-1-v1-superseded.txt`) read the local unconditionally and was
  verified under the lock at 20:05:55–20:05:59 (34/34). v2 verification: see "Fix pass" below. Without the seam the tree is byte-for-byte
  today's behaviour (fallback + breadcrumb), and `TransformInheritance.kt` has NO live call site — seam-1 and `hunk-for-orchestrator-1.patch`
  must land in L4's commit together.

### b″ — percent margin / padding read the ELEMENT-level containing block (Compose, correctness, 0 flips by design)
- `MarginApplier.kt:93` / `PaddingApplier.kt:58` read `LocalContainingBlock.current` inside `Modifier.composed { }`, which materialises
  INSIDE the element's own provider → the block the element publishes for its CHILDREN (the wave-50 B2 defect, one level too deep). Both now
  read `ElementContainingBlock.containingBlockFor(element = LocalElementContainingBlock.current, ambient = LocalContainingBlock.current,
  breadcrumb = SPACING_UNPUBLISHED_BREADCRUMB)`; the WPT gate and the P10/P11/P12 tri-state are untouched, so outside WPT capture nothing
  changes and every committed baseline is byte-identical by construction. `ElementContainingBlock.kt`: additive `breadcrumb: String =
  UNPUBLISHED_BREADCRUMB` parameter (the wave-50 call site and its L1–L6 pins unchanged — re-run green) + `SPACING_UNPUBLISHED_BREADCRUMB =
  "Spacing[containing-block-level-unpublished]"`; header lists the three readers. The stale `MarginApplier.kt:79-82` claim ("this is what
  makes `margin-left: -50%` … collapse to 0") is replaced by what the code actually did and does.
- Used value on the four carriers moves −50 / +50 px → 0 px on a box that paints nothing and has no children: **predicted pixel movement
  zero**. The A/B at the gate is the orchestrator's: `cmp` the four `css-sizing/abspos-auto-sizing-fit-content-percentage-001…004` Android
  PNGs against `wave52-open`, 0 movers on `css-position/position-relative-002/-008 android`, and record the installed `base.apk` sha1
  (`adb shell pm path` → `adb pull` → `shasum -a 1`) against `apps/android-harness/app/build/outputs/apk/debug/app-debug.apk`. Any movement
  on those six cells falsifies the port (or means `element`/`ambient` were swapped — S4 pins the direction).

### b′ — NOT landed (the brief's "zero corpus movement" is refuted by this lane's census)
The brief's parent scan covered the 11 bare-number INSET + SPACING carriers only. `census.mjs` also counts percentage SIZES (Width/Height/
Min*/Max*/InlineSize/BlockSize `{type:"percentage"}`), which consume the same `LocalContainingBlock` channel through DynamicValueResolver:
under NON-block-container parents there are **9 consumers in 7 tests, and the §10.1 republish would change the BASE on 8** — seven
`Width: 100%` children of a `<span>` in `css-break/block-in-inline-000…004` (today the span publishes (null, null) → unresolved; republished
they would resolve against the span's own 100×100 block) plus `position-relative-002` (level-insensitive by USED value, as the brief says).
Those cells are `wave51-fix … block-in-inline-000 android P 0.9966 / ios P 0.988`, `-001/-002/-003 android P 0.9966 / ios f 0.9459`,
`-004 android P 0.9967 / ios P 0.9974` — an unmeasured blast radius on 6 passing Android cells for a change that is predicted to move
nothing else. Landing the `BlockContainer.kt` predicate alone is not an option either: it would be dead runtime code with no live call site (the wave-49
S5 class). (Corrected in the fix pass: the 2026-09-25 text claimed `SeamReachabilityTest` fails ANY module with no live call site — false;
it audits a hard-coded wave-49 module list, as the skeptic showed.) b′ stays open in the BACKLOG with this census as its new starting point (`census.json` → `b1`); the five css-break lines
are recorded, commented out, in `watchlist-additions.txt`.

## Census (this lane's own script — `census.mjs` → `census.json`, 1435 wave51-fix per-test IR docs)
- **T5**: 18 elements declare `BackfaceVisibility HIDDEN`; 17 are FLAT (11 of them back-facing by the degree rule — they keep
  `HIDE_SUBTREE`, today's answer); **1 is PRESERVE_3D + back-facing + has children** = the target. `ownDecorationOnCulledPreserve3d = 0`.
- **T7**: **1** `Transform {type: keyword, keyword: inherit}` (the target, parent has a Transform); 0 other CSS-wide keywords on Transform.
- **b″**: **4 carriers in 4 tests**, cb differs on 4, used px differs on 4 (element 0 vs child −50/−50/+50/+50), all 4 `paints=false
  children=0 text=false`. Shape histogram: only 4 bare-number spacing values in the corpus; no `{type:"percentage"}` spacing.
- **b′**: 105 percent consumers (sizes + insets + spacing); 9 under non-block-container parents (8 `span`, 1 `tbody`); base would change on 8.
Matches the planning censuses on T5/T7/b″ (1 / 1 / 4) and corrects b′.

## PNG replay (`replay.mjs` → `replay.json`; scored with the titan gate's own `diffWebVsRef`, so comparable to the manifest)
- `composited-under-rotateY-180deg-preserve-3d`: ref green 10000 px [16,16→115,115]; web ssim **1.0000**, 0 % mismatch (the predicted
  native picture — residual 0 px); ios/android today 0.9565, 4.274 % mismatch, no ink at all.
- `css-transform-inherit-scale`: ref green 40000 px [16,88→215,287] + caption; web ssim **0.9990** (66 px residual = caption glyph AA);
  ios 0.9974 (already the fixed picture); android today 0.9965 with red 30000 px + green 10000 px at [66,138→165,237] — the scale-once box.
  Predicted Android after T7: green 200×200 at [16,88→215,287], ≈ 0.997–0.999 (the iOS/web residual class).
- b″ controls 001–004: web 1.0000 / ios 0.9992 / android 0.9984, green 10000 px [16,68→115,167] on every platform — the picture the port
  must keep byte-identical.

## Predicted flips (confidence)
- `css-transforms/composited-under-rotateY-180deg-preserve-3d ios f 0.9565 → P ≈ 1.0000` — **HIGH** (Catalyst raster pin on the verbatim wire
  paints the 100×100 green at (0,0)-(99,99); web ≡ ref).
- `… android f 0.9565 → P ≈ 1.0000` — **HIGH** (decision pin on the verbatim wire; same web ≡ ref replay). Falsified if the Compose
  `rotationY = 180` graphicsLayer does something other than mirror the square onto its own footprint.
- `css-transforms/css-transform-inherit-scale android f 0.9965 → P ≈ 0.997` — **MED-HIGH**: requires seam-1 applied; the child's static
  position under the parent's own offset (the wave-49 iOS note's caveat) is what keeps this below HIGH — iOS passes with the same
  substitution, and the Android capture's green is already centred where the 200×200 must be.
- b″ / b′: **none** (b″ correctness — the four css-sizing Android PNGs must `cmp` identical; b′ not landed).
- At risk (must not move): `css-transforms/backface-visibility-hidden-animated-001/002 ios P 0.9646` (degree rule kept — pinned on the
  verbatim matrix3d wire, NONE); the 11 flat back-facing carriers (hidden-001's red face pinned HIDE_SUBTREE; iOS flat raster control blank).
  Movers, not at-risk: `backface-visibility-hidden-003/004 android f` (flat — the rule they hit is unchanged, so no movement predicted).
  HOLD tripwires 0(c) untouched: `css-transform-3d-transform-style android P 0.9577`, the two `filter-effects/backdrop-filter-*3d*`.
All on `watchlist.txt` lines 85–93 already; `watchlist-additions.txt` adds no live line.

## Pins and executed mutations (each restored byte-exact, sha-verified)
| pin | file | mutation | result |
|---|---|---|---|
| T5 Compose (8 tests) | `transforms/BackfaceCullTest.kt` | `BackfaceCull.decide` last line → `return Decision.HIDE_SUBTREE` | 1/8 red: `the preserve-3d culled parent culls its OWN face only` (expected CULL_OWN_FACE, was HIDE_SUBTREE); 7 flat/degree pins green; sha `9cd04fad…` before == after (re-run in the fix pass on `381000da…`: 4/13 red incl. W1–W3) |
| T5 iOS (4 tests) | `Tests/…/BackfacePreserve3DTests.swift` | `BackfaceCulling.decide` → `return .hideSubtree` | 2/4 red: truth-table preserve-3d row + raster pin (`no green ink — … culled with its parent again`); flat raster control still green; sha `bc425394…` before == after |
| T7 (6 tests in `TransformExtractorTest`) | `transforms/TransformExtractorTest.kt` | `TransformInheritance.resolve` body → `return properties` | 4/17 red (scale-2 pin: expected `[Scale(2,2)]` was `[]`; chain; none; breadcrumb negative control); sha `86a62ac0…` before == after |
| b″ S1–S6 (6 tests) | `spacing/PercentSpacingContainingBlockLevelTest.kt` | both appliers restored to pre-port HEAD bytes (`git show HEAD:`) | 1/6 red: `S3` — "MarginApplier's composed lane must read the element-level channel"; re-applied, shas `f14833c2…` / `a71962b2…` before == after |
Regression sweeps: Compose `transforms.* spacing.* layout.position.* core.renderer.* sizing.* interactions.*` = **835 / 0 failures**;
Catalyst backface + transforms classes = **65 / 0 failures** (incl. Wave19InkClampBackfaceTests, Backface3DSubtreeRasterTests,
TransformInheritanceTests, TransformsTests, TransformContainingBlockTests).

## Design deviations from PLAN.md §2 L4 (honest)
1. `StyleApplier.kt`: besides the backface region (`:532-557` at HEAD; diff hunk `@@ -529,33 +529,33 @@`), steps 5./6. (`@@ -712,34 +712,37 @@`) were edited — 8
   `config.borders`/`config.colors` reads became `paint.*`. Needed so the culled own face is actually suppressed without editing the borders/
   colour appliers (not mine). No textual or semantic overlap with L8's `buildSpacingContext` `:805-830` hunk (≥ 50 lines apart; L8 lands
   after this lane per §4 step 3).
2. New files not in the `own:` list, all in this lane's categories and touched by no other lane: `transforms/BackfaceCull.kt`,
   `transforms/TransformInheritance.kt`, `transforms/BackfaceCullTest.kt`, and (fix pass) `transforms/BackfaceCullChainTest.kt` +
   `transforms/TransformInheritanceSeamTest.kt` (Compose), `Tests/…/BackfacePreserve3DTests.swift` (iOS). The
   T7 pins went into the listed `TransformExtractorTest.kt`; b″'s into the listed new `PercentSpacingContainingBlockLevelTest.kt`.
3. T7's channel carries the parent's computed `Transform` WIRE (JsonElement) rather than "the parent's extracted function list" — the
   extractor is reached from StyleConfig (not this lane's file) and cannot take a parameter without it; the pin is the same assertion
   (`inherit` under `scale(2)` → `[Scale 2]`) on the verbatim payloads.
4. iOS T5 culls the subtree-vs-own-face DECISION but leaves the own decoration painted (approximation, breadcrumbed); Compose strips it.
5. b′ not built (see above) — a refutation, not a shortfall.

## Could not verify
- Any pixel on a device: the +2/+1 flips and the b″ `cmp` are the gate's. The Compose T5 pin is a decision pin, not a raster (JVM JUnit4,
  no Robolectric); the iOS raster pin ran under Catalyst, which is not the simulator's rasteriser (memory: assert properties, not bytes —
  the pin asserts presence + bbox ±2 px, not exact bytes).
- The T7 flip depends on seam-1 being applied by the orchestrator; without it the cell stays f 0.9965 with the `Transform` breadcrumb.
- (Fix pass) BackfaceCullChainTest W1–W3 assert the Compose MODIFIER ELEMENT CHAIN (class names + androidx's internal `GraphicsLayerElement.alpha`
  read reflectively), not pixels; a Compose upgrade that renames that field makes W1 ERROR loudly, never pass silently. TransformInheritanceSeamTest
  U3 is vacuously green until seam-1 lands (its non-vacuous branch was run only under the lock); `hunk-for-orchestrator-1.patch` is what makes a
  seam-less L4 commit fail. Nothing in the fix pass was rasterised; iOS was not re-run (unchanged files).
- Whether `PropertyTracker` breadcrumbs (`Spacing[…]`, `BackfaceVisibility[preserve-3d-own-face-approximated]`) surface in the capture logs.
- The b′ blast radius is a static transcription (childContainingBlock over px / percentage-of-known-parent); it says the BASE changes on 8
  consumers, not what each picture would do — reason enough not to land it blind, not a measurement of the movement.

## Fix pass (2026-10-05) — skeptic `skeptic.md`: 1 must-fix, 1 should-fix, 6 nits
Shared tree, no device; StyleApplier.kt NOT edited (sha `b29f240d…` = the skeptic's). Drivers + logs committed beside this note:
`mutate-t5-wiring.py/.log`, `mutate-t7-sentinel.py/.log`, `mutate-refresh.py/.log` — each holds the original bytes in memory, restores in
`finally`, sha256-compares, and runs only the named classes.

**Must-fix 1 — the Compose T5 applier wiring is now pinned.** New `transforms/BackfaceCullChainTest.kt` (166 lines) drives the REAL
`extractConfig → applyConfig` chain (the StyleApplierTransformPivotSeamTest foldIn idiom) on the verbatim `…preserve-3d__0-091` wire:
W1 no alpha-0 `GraphicsLayerElement` on the target (FLAT control = exactly 1, `visible` control = 0); W2 legacy route and W3 self-paint
route (4×10px radius) — target + a synthetic red fill / uniform 2px band / 2px outline (0 corpus carriers, so synthetic by necessity) yields
the element list of the undecorated target, while the `visible` control installs Background/Border elements; W4 source pin in the S3 idiom
(exactly one `.alpha(0f)` in applyConfig's code lines, directly under `if (backface == …Decision.HIDE_SUBTREE)`; `val paint = if (… ==
CULL_OWN_FACE) …stripOwnFace(config) else config`; 0 decoration calls reading `config.`, 5 reading `paint.`).
| mutant (StyleApplier.kt) | result |
|---|---|
| M4 = the skeptic's (gate `!= NONE` + `val paint = config`) | **4/12 red**: W1 (expected 0 alpha-0 layers, was 1), W2, W3, W4 |
| M4a gate only | 2 red: W1, W4 |
| M4b `val paint = config` only | 3 red: W2, W3, W4 ("paint must be stripOwnFace(config) under CULL_OWN_FACE") |
| M4c self-paint route's 3 calls back to `config.` | 2 red: W3, W4 ("no decoration call may read config", was 3) |
sha256 `b29f240dc103104c…` before == after; restored run 12/12.

**Should-fix 2 — T7 unprovided vs `none`, seam wiring pinned.** `TransformInheritance`: Kotlin `null` = UNPROVIDED → `resolve` is the
identity and the extractor's `Transform` breadcrumb fires (a mis-merged seam is loud, not a silently dropped transform); `NONE` (`JsonNull`)
= parent computes `none` → entry dropped; `published` never returns null; header corrected ("identity on every list" was true only because
nothing calls it). `TransformExtractorTest` "none" pin now expects `NONE`. New `transforms/TransformInheritanceSeamTest.kt` (101 lines):
U1 unprovided ≠ none (verbatim `.child` wire), U2 `published`/`carriesInherit`, U3 source pin: ComponentRenderer carries `carriesInherit(`,
`resolve(` AND `LocalInheritedTransform provides …published(` together or none of them (vacuous before seam-1 — stated in the test).
seam-1 re-cut as v2 (gated read, nit 7; provider comment names NONE): `git apply --check` exit 0 on HEAD; L11 all-reset seam-1, L6 counters
seam-1 + seam-3, L3 failure-ink seam-1, L9 inline-run-wall seam-1, L8 vertical-wedges seam-1 + seam-3 each `--check` exit 0 on HEAD + v2.
`hunk-for-orchestrator-1.patch` adds BackfaceCull.kt + TransformInheritance.kt to SeamReachabilityTest's module list (same commit as seam-1).
| mutant | result |
|---|---|
| MU1 `inherited == null` → `resolve(properties, NONE)` | 1/20 red: U1 ("unprovided must be the identity") |
| M2 re-run (identity on every non-empty list; a bare `return properties` no longer compiles — smart cast lost) | 5/20 red: T7 scale-2, chain, none, breadcrumb + U1 |
| M6 (under the CR.kt lock) seam-1 v2 WITHOUT the provider hunk | U3 red ("seam must provide LocalInheritedTransform") |
| orchestrator hunk WITHOUT seam-1 (under the lock) | SeamReachabilityTest red: "no live call site: [transforms/TransformInheritance.kt]" |
TransformInheritance.kt sha `ece17e95…` before == after. **Seam verification** (lock `tools/titan/runs/wave52-lock/ComponentRenderer.kt`,
three holds 18:14:36–18:15:02, 18:15:44–18:15:48, 18:23:25–18:23:42): seam-1 v2 applied → compiled (the rebuilt `ComponentRenderer.class`,
461085 B vs 459914 at HEAD, references `carriesInherit` / `LocalInheritedTransform`) → transforms.* + layout.position.* +
SeamReachabilityTest + WptBoxSizingDefaultTest **379 / 0**; + orchestrator hunk → SeamReachabilityTest 3/3, TransformInheritanceSeamTest 3/3;
CR restored via `git show HEAD:`, sha `c4369165…` before == after every hold, `git diff --quiet` clean; SeamReachabilityTest likewise restored
(`0f2a4914…`).

**Nits.** 3: PercentSpacingContainingBlockLevelTest.kt 212 → 200 lines (a `ctx()` helper, merged one-line asserts, no assertion changed);
M3 re-run → 1/6 red (S3), appliers `f14833c2…`/`a71962b2…` before == after. 4: the four note inaccuracies corrected above (+3 not +23;
hunk `@@ -529,33 +529,33 @@`; `9cd04fad…`; L8's hunk received). 5: Compose kept box-shadow → `PropertyTracker.markUnhandled(
"BackfaceVisibility[preserve-3d-own-box-shadow-kept]")` in `stripOwnFace` + pin; MS (`if (false)` guard) → that pin red; M1 re-run on the new
BackfaceCull.kt (`381000da…`) → 4/13 red. iOS keeping its own background/border stays the documented divergence (unchanged, 0 carriers).
6: every import in BackfaceCull.kt and TransformInheritance.kt commented. 7: gated read (seam v2). 8: the orchestrator's (commit this dir).
**Census** (`census.mjs` re-run): `ownDecoration` now also counts BoxShadow + visible Outline; output identical apart from the two new
fields; `t5.ownDecorationOnCulledPreserve3d` still **0** (the sole carrier: all six decoration flags false).
**Regression sweep** (no seam): transforms.* spacing.* layout.position.* interactions.* effects.* borders.* color.* StyleApplier* —
**122 classes, 1131 tests, 0 failing**; final focused run 18 classes 141 / 0. iOS untouched this pass (TransformsApplier `086bd8e1…`,
BackfaceCulling `bc425394…`, BackfacePreserve3DTests `52af4567…`), so no Catalyst re-run was owed.
**Predictions unchanged**: the sentinel and the gated read change no pixel (the sole carrier's parent is a RenderComponent that provides the
channel; unprovided and `none` both paint `none`); the shadow breadcrumb fires on 0 corpus elements. No watchlist additions.

## Hand-offs
- Delivered: `seam-1.patch` v2 (orchestrator, ComponentRenderer.kt, §3 row "`:1983-2030` LocalInheritedTransform provider" + the `:1241`
  substitution — one patch, applies on HEAD; §3 row "`:1886-1890` BlockContainer gate (b′)" is WITHDRAWN — nothing to apply).
- Delivered: `hunk-for-orchestrator-1.patch` (fix pass) — adds `transforms/BackfaceCull.kt` + `transforms/TransformInheritance.kt` to
  `core/renderer/SeamReachabilityTest.kt`'s module list (a wave-49 test no wave-52 lane owns). Apply in the SAME commit as seam-1, never alone.
- To L8: `StyleApplier.kt` is edited at `@@ -529,33 +529,33 @@` and `@@ -712,34 +712,37 @@`; `buildSpacingContext` (`:805-830` at HEAD,
  shifted by +3 lines) is untouched — L8's hunk applies by context after this lane merges (§4 step 3). The fix pass did not touch StyleApplier.kt
  (sha `b29f240d…` unchanged).
- To the orchestrator (BACKLOG paragraph, verbatim): "0(b′) — the §10.1 republish is NOT zero-movement: L4's `census.json` (wave 52)
  finds 8 percentage consumers in 6 tests whose base changes, 7 of them `Width: 100%` children of a `<span>` in css-break/block-in-inline-
  000…004 (android P 0.9966 ×4 + P 0.9967, ios f 0.9459 ×3). Needs its own brief with a size-aware census before any lane picks it up."
- Received: `wave52-vertical-wedges/hunk-for-L4-1.patch` (L8, 2026-10-05 17:17 — `buildSpacingContext` + its test). Per PLAN C4 it SHIPS IN
  L8's PR after L4 merges; L4 does not apply it. `git apply --check` on L4's StyleApplier.kt: passes, "Hunk #1 succeeded at 826 (offset 3 lines)".

(Orchestrator, 2026-10-05: this lane returned its full report on 2026-09-25 before the usage limit killed the other lanes; its skeptic had not run yet.)

STATUS: COMPLETE
