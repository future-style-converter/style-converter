# L4 · small-fixes — skeptic review (wave 52, 2026-10-05)

Tree: shared `campaign/wave52` @ HEAD `7d9c22a7`, other lanes' uncommitted work present. Nothing in the shared tree was
mutated. Every mutation and the seam application ran in a PRIVATE byte-copy of the tree (`rsync` of `apps/android-harness`,
`runtimes/compose`, `runtimes/swiftui`, `Package.swift`, `schema/`, `fixtures/`, without build dirs, into the session scratchpad
`l4/x`). `diff -rq` showed the copy equal to the tree before the first run. The lane's 14 files were sha-checked in the shared tree
at the start and at the end, and all are unchanged. Gradle ran with JDK 21 against the copy's `apps/android-harness`. Catalyst
used its own `-derivedDataPath`. No device.

## 0. Completeness
`_note.md` ends with `STATUS: COMPLETE`. No "lane incomplete" defect.

## 1. Ownership (git status, filtered to what the note claims)
Modified files, all on L4's `own:` list: `StyleApplier.kt`, `transforms/TransformExtractor.kt` + `TransformExtractorTest.kt`,
`spacing/MarginApplier.kt`, `spacing/PaddingApplier.kt`, `layout/position/ElementContainingBlock.kt`,
`TransformsApplier.swift`, `BackfaceCulling.swift`.
New files: `PercentSpacingContainingBlockLevelTest.kt` is on the list. Four are not, and the note discloses them as deviation 2:
`transforms/BackfaceCull.kt`, `transforms/TransformInheritance.kt`, `transforms/BackfaceCullTest.kt` and
`BackfacePreserve3DTests.swift`. No other lane claims any of these paths (grep over PLAN.md and every `wave52-*/_note.md`).
`ComponentRenderer.kt` equals HEAD in the tree (sha256 `c4369165…`). `BlockContainer.kt` was not created, because b′ was withdrawn.
**No foreign edit.** `ChUnitMetrics.kt` (modified) belongs to L8 and L4 did not touch it. L8's
`vertical-wedges/hunk-for-L4-1.patch` is still a patch and has not been applied to L4's file.

## 2. Census — re-derived with my OWN script (python, never the lane's JSON)
The script is `scratchpad/l4/skeptic_census.py`, inlined in essence here. It walks every
`tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json` (**1435 docs**) and does three things:
- T5: for each `BackfaceVisibility "HIDDEN"` element, sums the `rotateY`/`rotateX` `a.deg` values in `Transform` function lists,
  normalises to [-180, 180], classes back-facing as |θ| > 90, and also reads `TransformStyle "PRESERVE_3D"`, child count via
  `slot.parent`, and own decoration (Background*/Border* without Radius/Outline*/BoxShadow, plus `text`).
- T7: every `Transform` whose `data.type == "keyword"`.
- b″: every Margin*/Padding* datum classed by shape. A bare number is the percent wire.

| item | lane (`census.json`) | skeptic | match |
|---|---|---|---|
| T5 `hidden` elements | 18 | 18 | ✓ |
| T5 flat | 17 | 17 (front+leaf 2, back+leaf 4, front+kids 4, back+kids 7) | ✓ |
| T5 flat with children | 11 | 4 + 7 = 11 | ✓ |
| T5 back-facing hidden | 12 | 4 + 7 + 1 = 12 | ✓ |
| T5 preserve-3d + back + children | 1 (the target) | 1 = `composited-under-rotatey-180deg-preserve-3d__0-091` | ✓ |
| T5 own decoration on the culled preserve-3d element | 0 | 0 | ✓ |
| T7 `Transform` keyword carriers | 1 inherit, 0 other | `{inherit: 1}`, parent carries `scale(2,2)` | ✓ |
| b″ spacing shape histogram | px 24274 · object 544 · string 91 · bare-number 4 | px 24274 · `{original}` 540 (EM 521 / CH 19 — no PERCENT) · `{expr}` 4 · string 91 · bare-number 4 | ✓ |
| b″ carriers | 4 in 4 tests, all unpainted, childless, no text | 4 in 4 tests (001 MarginLeft −50, 002 MarginRight −50, 003 PaddingLeft 50, 004 PaddingRight 50), all `nopaint`, 0 kids, no text | ✓ |
| b′ (not landed) span-parent `Width:100%` | 7 in css-break/block-in-inline-000…004 | 7 in the same 5 tests; cells 000 android 0.9966 / ios 0.988, 001–003 android 0.9966 / ios 0.9459, 004 android 0.9967 / ios 0.9974 | ✓ |

The blast radius is measured, and my census agrees with the lane's: T5 = 1 element on both natives, T7 = 1, b″ = 4 carriers on
the percent lane. The only spacing values that enter `usesContainingBlockPercent` are the 4 bare numbers. None of the
`{original}` shapes is a PERCENT.

## 3. Pins re-run (isolated copy)
- Compose: BackfaceCullTest 8, TransformExtractorTest 17, PercentSpacingContainingBlockLevelTest 6,
  PercentInsetContainingBlockLevelTest 10 and SeamReachabilityTest 3 → **44 / 0**.
- Compose, seam-1 applied: `:runtime:compileDebugKotlin` green. transforms.*, layout.position.*, PercentSpacing,
  SeamReachability and WptBoxSizingDefault → **372 / 0**.
- Compose sweep without the seam: spacing.*, sizing.*, core.renderer.*, layout.position.*, transforms.* and interactions.* →
  **847 run, 3 failing, none of them L4's**. The 3 are `LineClampUnderPreWave39Test` "a clamped soft-wrapping run draws the UA
  marker" and `PlaceholderOverflowMarkerTest` ×2, each "expected Ellipsis but was Clip". Both test files are modified by L9
  (inline-run-wall) and depend on L9's `placeholderOverflow` seam. L4 touches nothing on that path.
- Catalyst: BackfacePreserve3DTests 4, Wave19InkClampBackfaceTests, Backface3DSubtreeRasterTests, TransformInheritanceTests,
  TransformsTests and TransformContainingBlockTests → **38 / 0**.

## 4. Mutations (each in the private copy, restored, sha256-verified)
| # | mutation | result | restore |
|---|---|---|---|
| M1 (lane's T5) | `BackfaceCull.decide` tail → `return Decision.HIDE_SUBTREE` | **1/8 red**: "the preserve-3d culled parent culls its OWN face only", expected CULL_OWN_FACE, was HIDE_SUBTREE | `9cd04fade245da94` before = after |
| M2 (lane's T7) | `TransformInheritance.resolve` → `return properties` first | **4/17 red**: scale-2, chain, none, breadcrumb | `86a62ac0a5051649` before = after |
| M3 (lane's b″) | Margin/PaddingApplier → `git show HEAD:` bytes | **1/6 red**: S3 "MarginApplier's composed lane must read the element-level channel" | `f14833c2…/a71962b2…` before = after |
| **M4 (mine, Compose T5 wiring)** | StyleApplier: `if (backface == HIDE_SUBTREE)` → `if (backface != NONE)` and `val paint = config`. This restores pre-wave-52 `alpha(0f)` on the target | **SURVIVES: 414 / 0** across transforms.*, interactions.*, effects.* and TransformContainingBlockTest | `b29f240dc103104c` before = after |
| M6 (mine, T7 seam wiring) | seam-1 applied WITHOUT hunk 3 (the provider). `LocalInheritedTransform` is then null everywhere, so `resolve` silently drops `inherit` and the child renders today's failing picture with no breadcrumb | **SURVIVES: 117 / 0** (transforms.* + SeamReachabilityTest) | CR `c4369165…` before = after |
| M7 (mine, iOS T5 wiring) | TransformsApplier `case .cullOwnFace:` → `v = AnyView(v.opacity(0))` | **killed**: raster pin "no green ink — the preserve-3d child was culled with its parent again" | `086bd8e14f63956c` before = after |

Without the seam, `TransformInheritance.kt` has **zero live call sites** in runtime or harness code (only comments name it), and
SeamReachabilityTest is green (baseline run). That pin audits a hard-coded wave-49 module list. The note's claim that it "fails
any runtime module with no live call site" is false.

## 5. Seam patch
`git apply --check -v seam-1.patch` passes in the shared tree (read-only) and on pristine HEAD bytes in an isolated repo. The patch
index `c25311b` is the HEAD blob. In landing order (§4: L4 before the others) I applied L4 first and checked every other Compose
`ComponentRenderer.kt` patch on top: L11 seam-1, L6 seam-1, L3 seam-1, L9 seam-1, L8 seam-1 and L8 seam-3 all **pass
`--check`**. The shared-tree lock was not needed: the full apply, compile and pin run happened in the private copy, so the shared
file was never written. L8's `hunk-for-L4-1.patch` passes `--check` on L4's StyleApplier.kt "at 826 (offset 3 lines)".

## 6. PNGs (wave51-fix captures vs frozen ref `tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins/`)
- `composited-under-rotateY-180deg-preserve-3d`:
  - Ref and web show green 10000 px at [16,16→115,115]. iOS and Android are blank white (0 ink).
  - The fix leaves the parent's `rotateY(180)` (Compose: OrthographicFlatten scaleX = −1 about the 100-px parent's centre; iOS:
    the rotation3D the raster pin exercises) mirroring the 100×100 green child onto its own footprint, so the predicted picture is
    the ref.
  - **Plausible.** The iOS raster pin measures it: bbox (0,0)-(99,99) ±2, > 9000 px.
- `css-transform-inherit-scale`:
  - Ref, web and iOS: green 40000 px at [16,88→215,287] and no red.
  - Android: red 30000 px at [16,88→215,287] plus green 10000 px at [66,138→165,237], i.e. the child placed at the parent's static
    top-left and scaled once.
  - Arithmetic: own scale(2) about (25,25) gives local (−25..75); the parent's scale(2) about (25,25) gives (−75..125); with the
    parent at (75,75) that is page (0..200), which with the capture offset is exactly [16,88→215,287].
  - **Plausible, given seam-1 applied and the child not hoisted.** `CanvasRootHoist` vetoes the hoist under a transformed ancestor,
    and the capture already shows the parent's scale reaching the child.
  - The Android caption is greyer than iOS, but iOS passes at 0.9974 with the same caption. MED-HIGH is fair.
- b″ 001–004: ref, web, iOS and Android all show green 10000 px at [16,68→115,167] with 0 red. In Compose a negative margin rides
  `Modifier.offset` and the % padding sits inside the P13-uninflated 100-px frame, so −50/+50 → 0 cannot change `.abs`'s size.
  **Zero movement is plausible.**

## 7. Rules
- Ring-fence: `backdrop-filter-basic-blur` is not touched or named.
- No test-name carve-outs. A grep of the added non-comment lines for test-name string literals found only a log message and a
  pre-existing property list.
- Device-A/B hash sentence: present for b″ (`adb shell pm path` → `adb pull` → `shasum -a 1` vs `app-debug.apk`).
- Watchlist: lines 85–93 cover every L4 cell, and `watchlist-check.mjs` prints `unmatched 0`.
- New files: BackfaceCull.kt 145, TransformInheritance.kt 119, BackfaceCullTest.kt 179, BackfacePreserve3DTests.swift 182, and
  **PercentSpacingContainingBlockLevelTest.kt 212 (> 200)**.
- Silent fallthrough: iOS logs its own-face approximation. Compose's un-culled box-shadow and own text under CULL_OWN_FACE are
  stated only in a comment (0 carriers).

## 8. Defects
1. **must-fix — the Compose T5 applier wiring is unpinned.**
   - Evidence: M4 restores the exact pre-wave-52 `alpha(0f)` on the target, which brings back the blank canvas, and 414 Compose
     tests stay green. PLAN §2 L4 names the T5 pin's mutation as "mutation restores `alpha(0)`". On Compose that mutation survives
     in its applier form. Only the decision function is pinned (M1).
   - Why it matters: L8 edits this same file (`StyleApplier.kt:805-830`) in its own PR after L4 merges (C4). Nothing would catch a
     reverted gate or `paint` rename.
   - Fix (about 15 lines): a source-level pin in the S3 idiom inside BackfaceCullTest.
     - Assert that the only `alpha(0f)` in the backface step is gated on `Decision.HIDE_SUBTREE`.
     - Assert that the step-5/6 decoration calls read `paint.` (`BordersFacade.apply(result, paint.borders)`,
       `ColorApplier.applyColors(result, paint.colors)`, `applySelfPaint(… paint.borders…)`).
     - Prove the pin fails under M4.
   - iOS is fine: M7 is killed by the raster pin.
2. **should-fix — the T7 seam wiring is unpinned, the "unprovided" and "parent computed none" cases are conflated, and the note's
   SeamReachability claim is false.**
   - Evidence: M6 (seam without the provider) gives 117/0 green. In that state `resolve(…, null)` DROPS `inherit`, so the
     extractor's breadcrumb never fires and the failing picture returns silently.
   - Without seam-1, TransformInheritance.kt is dead runtime code that no pin flags. This is the wave-49 S5 failure class.
   - Fix:
     - Hand the orchestrator a one-commit-with-seam pin: add `transforms/TransformInheritance.kt` to SeamReachabilityTest's module
       list and/or add a source pin that ComponentRenderer calls `TransformInheritance.resolve(` and provides
       `LocalInheritedTransform`.
     - Give the channel an "unprovided" sentinel distinct from null, or skip resolution when unprovided, so a mis-merge falls back
       to the breadcrumb.
   - Correct the TransformInheritance header ("resolve is the identity on every list" before the seam is true only because
     nothing calls it).
3. nit — PercentSpacingContainingBlockLevelTest.kt is 212 lines, over the 200-line new-file bar.
4. nit — four inaccuracies in the note:
   - The L8 hand-off says `buildSpacingContext` shifted "+23 lines". It shifted +3; L8's hunk applies at offset 3.
   - The backface region is given as "now `:506-580`". The diff hunk is `@@ -529,33 +529,33 @@`.
   - The BackfaceCull sha is quoted as `9cd04faf…`. The actual sha256 is `9cd04fad…`.
   - "Received: none" is out of date. `vertical-wedges/hunk-for-L4-1.patch` (2026-10-05 17:17) now exists, and it ships in L8's
     PR per C4.
5. nit — under CULL_OWN_FACE, Compose leaves box-shadow and own text painted with no PropertyTracker breadcrumb, while iOS logs
   its approximation. iOS also keeps the own background and border, which is a documented divergence from the plan's Fix text.
   There are 0 carriers.
6. nit — every-line comments: the imports in BackfaceCull.kt (44–48) and TransformInheritance.kt (52–55) are uncommented.
7. nit — performance: every RenderComponent reads `LocalInheritedTransform` (`compositionLocalOf`) unconditionally, so all
   children of an element whose transform animates recompose on every frame. Read it only when the list carries the keyword.
8. nit — `tools/titan/results/wave52-small-fixes/` is untracked, against the §0 "committed the moment it exists" rule. The
   orchestrator should commit it; the same is true of every lane's directory.

## Verdict
The substance holds:
- The census is reproduced exactly.
- All three of the lane's mutations reproduce.
- The seam applies cleanly at HEAD and ahead of every other Compose seam.
- The predicted pictures match their refs (T5 web ≡ ref; T7 arithmetic lands exactly on the ref's green box).
- b″ is byte-neutral by construction.
- Ownership is clean.

One must-fix before the gate: the Compose T5 fix sits in StyleApplier lines that no test reaches, and the plan-named
`alpha(0)`-restore mutation survives 414 tests. It is a cheap source pin. The T7 seam-wiring gap is should-fix.

## Re-verify (2026-10-05, after the lane's fix pass)

Same discipline as before. The shared tree was not written. Every mutant and seam application ran in a fresh private byte-copy
(session scratchpad `rv/x`: `runtimes/compose`, `apps/android-harness`, `schema/`, `fixtures/`, no build dirs, `diff -rq` equal
before the first run). Because the shared `ComponentRenderer.kt` was never written, the per-file lock was not needed and
`tools/titan/runs/wave52-lock/` is empty. Shared-tree shas at the end equal those at the start: StyleApplier `b29f240d…`,
BackfaceCull `381000da…`, TransformInheritance `ece17e95…`, BackfaceCullChainTest `9629798f…`, ComponentRenderer `c4369165…` (= HEAD),
SeamReachabilityTest `0f2a4914…` (= HEAD). JDK 21. No device.

### Must-fix 1 — the Compose T5 applier wiring is unpinned → **FIXED (executed)**
A new `transforms/BackfaceCullChainTest.kt` (166 lines, imports commented) drives the real `extractConfig → applyConfig` chain on
the verbatim `…__0-091` wire:
- W1 counts `GraphicsLayerElement`s whose `alpha` is 0. The target has 0; the FLAT control has exactly 1, so the detector does see
  the step; the `visible` control has 0.
- W2 and W3 check that target + a synthetic face gives the same element list as the undecorated target, on the legacy route and
  on the self-paint route respectively. Both have controls showing that the face does install Background and Border elements.
- W4 is a source pin in the S3 idiom.

Pins re-run in the copy (BackfaceCull*, TransformInheritanceSeamTest, TransformExtractorTest, PercentSpacing/PercentInset
ContainingBlockLevel, SeamReachabilityTest): **52 / 0**.

My own mutation driver (`rv/mut.py`, not the lane's `mutate-t5-wiring.py`) types each mutant fresh, restores it, and sha256-verifies:

| # | mutant | result | restore |
|---|---|---|---|
| K1 | **M4, the exact defect mutation**: gate `== HIDE_SUBTREE` → `!= NONE` + `val paint = config` | **killed: 4 red / 422** across transforms.*, interactions.*, effects.* and TransformContainingBlockTest (W1 "expected:<0> but was:<1>", W2, W3, W4 "the alpha(0f) must be gated on Decision.HIDE_SUBTREE"). The same sweep was **414 / 0** before the fix | `b29f240d…` == before |
| K2 | only the legacy `BordersFacade.apply(result, paint.borders)` → `config.borders` | killed: W2 + W4 ("no decoration call may read config expected 0 was 1") | `b29f240d…` == before |
| K3 | `stripOwnFace` returns `config` unchanged (BackfaceCull.kt) | killed: W2, W3 + BackfaceCullTest ×2 (strip + shadow breadcrumb) | `381000da…` == before |
| K5 | the subtree hidden under CULL_OWN_FACE via `graphicsLayer(alpha = 0f)` (parameter form) | killed: W1 | `b29f240d…` == before |
| K4 | the subtree hidden under CULL_OWN_FACE via `graphicsLayer { alpha = 0f }` (block form, no `.alpha(0f)` text) | **SURVIVES 13 / 13**. W1's detector reads only `GraphicsLayerElement`, not `BlockGraphicsLayerElement`. W2 and W3 compare two chains that both carry the extra layer. W4 matches the text `.alpha(0f)` | `b29f240d…` == before |

The plan-named mutation ("restores `alpha(0)`") and its two halves now die. A mis-merge by L8 that brings back the old
`if (flipped) result = result.alpha(0f)` block gives two `.alpha(0f)` and turns W4 red. K4 is a different spelling that no revert
would produce, so it is a **nit**, not a must-fix. Hardening it would mean also counting `BlockGraphicsLayerElement`s whose
`layerBlock` sets alpha 0 on a recording `GraphicsLayerScope`, or asserting that applyConfig has no `graphicsLayer {` under a
`CULL_OWN_FACE` branch.

### Regressions — none found
- Sweep with no seam (transforms.*, spacing.*, layout.position.*, interactions.*, effects.*, borders.*, color.*, StyleApplier*,
  `--rerun`, fresh XML timestamps): **122 classes, 1131 / 0**. This matches the note's count.
- Seams:
  - `git apply --check` exits 0 for `seam-1.patch` (v2) and `hunk-for-orchestrator-1.patch` on HEAD bytes. Tree blob == HEAD blob:
    CR `c25311b`, SR `84da651`.
  - Both applied in the copy compile: transforms.*, layout.position.*, SeamReachability, WptBoxSizingDefault and PercentSpacing
    give **386 / 0**. This includes U3, now non-vacuous, at 3/3.
  - K6, my re-execution of the lane's M6: seam v2 with the three provider lines removed turns U3 red ("seam must provide
    LocalInheritedTransform"). Restored to `a9d87153…` (seam-applied), then `git show HEAD:` → `c4369165…` / `0f2a4914…` verified.
  - The orchestrator hunk WITHOUT seam-1 turns SeamReachabilityTest red: "no live call site: [transforms/TransformInheritance.kt]".
    So the earlier should-fix 2 is closed too.
- Census, re-derived by my own python over the 1435 wave51-fix IR docs: exactly **1** element is `BackfaceVisibility HIDDEN` +
  `TransformStyle PRESERVE_3D` (`…__0-091`). It has 1 child, no text and **0** Background/Border/Outline/BoxShadow properties. The
  `ownDecorationOnCulledPreserve3d = 0` claim holds under the widened definition.
- PNG `composited-under-rotateY-180deg-preserve-3d`: the wave51-fix Android capture has 0 non-white pixels. The frozen ref
  (`…/white-black-ink-font-lh-imgpad-htmlpins/`) has 10000 green pixels, bbox (16,16)-(115,115). W1 now pins that the target
  installs no alpha-0 layer, so the green child paints. The predicted flip stays **plausible (HIGH)**.
- Ownership: the L4 paths in `git status` are the original 14 plus `BackfaceCullChainTest.kt` and `TransformInheritanceSeamTest.kt`.
  The two new files are disclosed in deviation 2 and claimed by no other lane (grep over PLAN.md and every `wave52-*/_note.md`).
  ComponentRenderer and SeamReachabilityTest equal HEAD in the tree. iOS files are unchanged (TransformsApplier `086bd8e1…`,
  BackfaceCulling `bc425394…`, BackfacePreserve3DTests `52af4567…`), so no Catalyst re-run was owed. **No foreign edit.**
- Rules:
  - New files are all ≤ 200 lines: BackfaceCull.kt 164, TransformInheritance.kt 151, BackfaceCullChainTest 166, BackfaceCullTest 195,
    TransformInheritanceSeamTest 101, PercentSpacing… 200, BackfacePreserve3DTests 182.
  - No test-name literals in the non-comment runtime lines; the only strings are breadcrumb and keyword constants.
  - The ring-fence is untouched.
  - The b″ device-A/B hash sentence is still present.

### Re-verify verdict
Must-fix 1 is **resolved**: the exact defect mutation (K1 = M4) went from surviving 414/0 to 4 red, and every sub-mutation I typed
independently that hits a pinned line dies. One new nit: K4, the block-form `graphicsLayer { alpha = 0f }`, survives. Nothing
blocks the gate.
