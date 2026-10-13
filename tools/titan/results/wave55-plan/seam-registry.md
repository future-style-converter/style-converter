# Wave 55 seam registry (cross-lane audit, 2026-10-11)

Every seam hunk that the seven wave-55 briefs declare, grouped by seam file and ordered top to bottom. No lane edits a seam
file directly. Each hunk is delivered as `tools/titan/results/wave55-<lane>/seam-<n>.patch` and applies clean on 0d0d3a3d.
`integrate-seams.sh` applies the hunks by content anchor, not by line number.

- Tree: `campaign/applier-campaign` at HEAD `0d0d3a3d`.
- Line numbers are at that HEAD. Every anchor below was re-read with `sed -n` while auditing (`briefs-audit.anchors.out.txt`).
- Each base sha256 was recomputed with `shasum -a 256`.

## 1. `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`

Base: sha256 `2445d9ea2674576fc0946e3f630f48b88d48f0f9d8c98df3249ba67d7a026aa5`, 8488 lines. This is what the briefs quote: L2, L3, L4
and L5 all give `2445d9ea…`.

| order | lane · unit · seam | region (function) | anchor at 0d0d3a3d (verified text) | what the hunk does | patch-borne files |
|---|---|---|---|---|---|
| 1 | L4 · U1-android · seam 1 | `RenderComponent` cascade step | :1157 `val rawProperties = …UAElementFontRule.apply(` … :1165 `val unresolvedProperties = rawProperties` | inserts `val uaMarginProperties = …UaPositionedHeadingMargin.apply(component._tag, allReset.own, listUaProperties, rawProperties, enabled = LocalWptCaptureMode.current)` after :1162 and re-points :1165 to it | NEW `…/test/…/spacing/UaPositionedHeadingMarginSeamWiringTest.kt`; the `UAElementFontRuleSeamWiringTest.kt` handoff token |
| 2 | L4 · U2-android · seam 3 | same block | :1161 `standsDown = …UAElementFontRule.headingStandsDown(component),` | `standsDown =` → `UAHeadingFoldGate.standsDown(component, listUaProperties)` | the `UAElementFontRuleSeamWiringTest.kt` `fontStep` token |
| 3 | L2 · U1 (CBB-android) · seam 1 | `RenderComponent`, `val childContainingBlock = remember(…, wptCaptureModeForStretch)` | :1950-1951 `.childContainingBlock(effectiveProperties, containingBlock)` | adds the named argument `wptCaptureMode = wptCaptureModeForStretch` and 4 comment lines; re-cut from `wave54-oof-layout/seam-1.patch` (it still applies, `git apply --check` rc 0; its header names :1930 / :1933-1934, which are stale) | NEW `…/test/…/core/renderer/ChildContainingBlockSeamWiringTest.kt` |
| 4 | L3 · U1 · seam 1 | `RenderContent` → `renderBlockChild` | :3633-3661, the `outOfFlowSlotMount` val and its banner (:3650 `val outOfFlowSlotMount: Modifier =`) | the `LocalActive.current && applies(…)` if/else becomes one call, `PositionedParentSlotGate.slotModifier(child.properties, component.properties, hostActive = …LocalActive.current, composedCapture = LocalWptComposedMode.current, parentRtl = LocalLayoutDirection.current == LayoutDirection.Rtl)`; the "Host-gated exactly like RC1" comment is corrected | NEW `…/test/…/core/renderer/PositionedParentSlotSeamWiringTest.kt`; `SeamReachabilityTest.kt` gets the `layout/position/PositionedParentSlotGate.kt` line |
| 5 | L5 · U2o-android · seam 4 | `RenderListItemMarker`, the `ListMarkerOutsideHang.Hang(gapPx = …)` argument | :4482 `gapPx = with(markerSnapDensity) { ListMarkerRow.gapDp.roundToPx() },` | `ListMarkerRow.gapDp` → `ListMarkerSuffixGap.gapDp(marker)` (1 line) | none |
| 6 | L5 · U2i-android · seam 5 | `RenderListItemMarker`, the Row marker modifier | :4665 `.padding(end = ListMarkerRow.gapDp)` | → `.padding(end = ListMarkerSuffixGap.gapDp(marker))` (1 line) | none |

**Ordering.**
- Hunks from different lanes are at least 785 lines apart (:1165 → :1950 → :3633 → :4482). No context overlaps, so the
  CROSS-lane order is free. Apply them in unit landing order.
- Within L4, seam 1 and seam 3 share the 3-line context: :1161 is changed by seam 3, and seam 1 inserts after :1162.
  - They must apply in the order seam 1 → seam 3 (U1-android lands first).
  - They revert in the order seam 3 → seam 1.
  - The lane must dry-run both revert orders in a `git archive` export tree (its §9 R8).
- Within L5, seam 4 and seam 5 are 183 lines apart and independent. They land U2o → U2i and revert U2i → U2o.
- **Ruled out:** L2's §3 / §9 R4 worry that "L3's seam may sit near :1979" does not apply. L3's only Compose seam is :3633-3661,
  1682 lines below L2's seam.
- **No hidden source pins.** No existing test pins renderer text inside the L2, L3 or L5 regions. Checked with a grep of
  every `runtimes/compose/src/test` file that reads `ComponentRenderer.kt` (`StaticPositionSeamWiringTest`,
  `RunFoldBreadcrumbSeamTest`, `FloatAvoidSeamWiringTest`, `TableCellHugSeamWiringTest`,
  `VerticalBlockFlowSeamWiringTest`, `TransformInheritanceSeamTest`, `WptBoxSizingDefaultTest`, `LineBoxCensusTest`):
  - their pinned strings sit at :944-945, :1324, :1377, :1404, :2655-2729, :2889-2960, :3704-3735, :3905-3906 and :8265;
  - none of those is in a wave-55 region.
  - The only pin inside a wave-55 region is `UAElementFontRuleSeamWiringTest.kt` (:1165, :1228). L4 owns its edits, which
    ride in L4's seam patches.

## 2. `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`

Base: sha256 `417853cefdc7627fadc21003efa735d99d47ed0c545b6c1747d7292403fd26f3`, 6291 lines. This is what the briefs quote: L1, L4
and L5 all give `417853ce…`.

| order | lane · unit · seam | region (function) | anchor at 0d0d3a3d (verified text) | what the hunk does | patch-borne files |
|---|---|---|---|---|---|
| 1 | L4 · U1-ios · seam 2 | `mergedProperties(now:)` | :340-356 (:343 `return UAElementFontRule.apply(`) | hoists `ListStyleUaRule.apply(…InheritedText.merge(…))` into `let mergedPreUA` (verbatim from the held `seam-2.foldgate.patch`, which applies at HEAD: rc 0); binds the font step to `let fontProperties`; returns `UAPositionedHeadingMargin.apply(sourceTag:own:preUA: mergedPreUA, merged: fontProperties, enabled: wptCaptureMode)` (`@Environment` :138) | NEW `Tests/StyleConverterRuntimeTests/UAPositionedHeadingMarginSeamWiringTests.swift` |
| 2 | L4 · U2-ios · seam 4 | same block | :352-356 (comment plus `hasElementChildren: component.children?.isEmpty == false)`) | → `UAHeadingFoldGate.standsDown(component: component, inFlowChildren: inFlowChildren, mergedPreUA: mergedPreUA)`. `inFlowChildren` is declared at :781; the brief's ":780" is its doc line | none |
| 3 | L5 · U1-ios · seam 1 | the marker branch's call, and `markerPlacement` | :3973 `markerPlacement(child: child, markerText: markerText,`; :4561 `private func markerPlacement(`; the three `Text(ScriptFallbackFonts.annotate(markerText,` at :4602 / :4626 / :4650 | adds `itemIsRTL: Bool = false`; passes `ListMarkerBidi.itemIsRTL(own: child.properties, inherited: childInherited)` (`childInherited` is bound at :3596 in the same scope); binds `let painted = ListMarkerBidi.painted(…)` once; the three Text strings → `painted` (`.listMarkerSymbol` keeps the raw string) | none |
| 4 | L5 · U2o-ios · seam 2 | `markerPlacement`, hang branch | :4618 `gap: ListMarkerRow.gapPt,` | → `ListMarkerSuffixGap.points(forMarker: markerText)` (1 line) | none |
| 5 | L5 · U2i-ios · seam 3 | `markerPlacement`, HStack branch | :4646 `spacing: ListMarkerRow.gapPt) {` | the same call (1 line) | none |
| 6 | L1 · NB-ios · seam 1 | `PlaceholderLabel.body` line-box block (struct :4737, body :4870) | (i) cut :5055-5069 (the `leading` statement and its banner); (ii) insert `let usedLineHeight = NormalLineBox.usedLineHeight(…)` after :5151-5152 `let lineCountIsExact = …`; (iii) re-insert the `leading` statement reading `usedLineHeight`; (iv) :5154 `lineHeightPx: effectiveLineHeight,` → `usedLineHeight` | `leading.` is read only at :5295 / :5335 / :5385 (grep), all after the new site, so the move is legal | none |
| 6b | L1 · NB-ios-b · seam 1b (CONTINGENCY) | same struct | :5335 `.padding(.vertical, leading.halfLeading)` | top 0 / bottom 2× half-leading for a pinned declared-`normal` run only. Lands ONLY if the probe prints `anchor: … offset 1` and nothing else | none |
| 7 | L5 · U3-ios · seam 6 | `PlaceholderLabel.labelFont(for:)` (:5430-5539) | :5537 `if textConfig.smallCaps { f = f.smallCaps() }` | adds `if textConfig.tabularNums { f = f.monospacedDigit() }` (1 line) | none |

**Ordering.**
- **L4.** Seam 4 is cut ON TOP of seam 2: it reads seam 2's `mergedPreUA`, and the two overlap at :352-356. The order is
  strict: seam 2 → seam 4 to apply, seam 4 → seam 2 to revert (L4 `revertOrder`: U2-ios before U1-ios).
- **L5, apply order.** Seam 3 (:4646) sits 4 lines above seam 1's :4650 hunk. Their 3-line contexts meet at :4647-4649, but
  neither changes a line the other uses as context. Seam 2 (:4618) and seam 1's :4626 hunk are 8 lines apart. So:
  - land U1-ios → U2o-ios → U2i-ios;
  - every patch is re-cut against the tree it actually lands on;
  - `git apply --check` is run before each.
- **L5, revert order.** The brief declares U2i before U2o. The lane must also dry-run reverting **U1-ios with U2i-ios still
  applied** (the :4646 / :4650 neighbourhood) in an export tree, as L4 does for its pair.
- **L1 and L5 inside `PlaceholderLabel`.**
  - L1's seam 1 cuts 15 lines and re-inserts them with at least one new line (a net +1 or more). L5's seam 6 anchor therefore moves to :5538.
  - The regions are 380 lines apart (:5160 vs :5537), so the order is free. Land L1 first and re-anchor seam 6 by content.
  - L1's contingency seam 1b (:5335) is 200 lines from seam 6 and independent.
- **Other lanes.** L4's region (:340-356), L5's (:3973-4650) and L1's (:5055-5160) do not touch each other, or the
  wave-54 anchors :343 / :4716 / :5011 / :5026 beyond reading them.
- **No hidden source pins.** No existing Swift test pins text inside these regions. The Swift tests that read
  `ComponentRenderer.swift` source are `AbsposStaticOffsetSeamWiringTests`, `MulticolDiscardThreadingTests`,
  `MulticolFloatStripSeamTests` and `VerticalBlockFlowSeamWiringTests`. Each pins flex, multicol or vertical-flow text
  outside every wave-55 region.

## 3. `apps/web-harness/src/sdui/ComponentRenderer.tsx`

Base: sha256 `c780ac1c76c28e74…`, 1479 lines. **No hunk in any brief.** L7 U4 edits `runtimes/web/src/renderer/InlineRuns.ts` and
`InertOutOfFlowWordJoin.ts` (owned, not seams). Its optional U4c is a one-line TODO in `apps/web-harness/src/ui/ComposedRootSeparator.ts`,
a harness file but not a seam file.

## 4. `tools/titan/extract-fixture.mjs`

Base: sha256 `6ab6f2b8ee7bc2e5…`, 12268 lines. **No hunk in any brief.**
- L1's U3b-exact (`brHostLineBoxPx`, 19 instead of 19.2) is queued and not staffed (`hyphen-ios-linebox.md:311-325`).
- L7's U5 reaches extraction through `bidi-bake.mjs` → `bidi-marker-paint.mjs`, which is owned and is not a seam file.
  It is gated by the [W-L7a] wire differential.

## 5. Shared test files that ride with seams

| file | claimant | what rides | conflict |
|---|---|---|---|
| `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt` (239 lines) | L3 only | U1: `layout/position/PositionedParentSlotGate.kt`, patch-borne in seam 1. U2: `…/PositionedParentRtlOrigin.kt`, in U2's commit | None today. Three other Compose modules are also reachable ONLY through a seam: L4 `spacing/UaPositionedHeadingMargin.kt` (seam 1) and `typography/UAHeadingFoldGate.kt` (seam 3), and L5 `lists/ListMarkerSuffixGap.kt` (seams 4 / 5). If the plan applies the wave-52 "the module line lands in the SAME commit as that seam" rule to them, each line rides patch-borne in its own lane's seam patch. The file then has three writers adding to one list: land L4 → L3 → L5 and re-cut each on the tree it lands on. |
| `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt` (76 lines, sha `1c67fe44…`) | L4 only (seam 1 and seam 3 tokens) | patch-borne only | L7 claims no test-pin file here, so L4's "if L7 claims it" condition is void. Order: seam 1 → seam 3. |

## 6. One-line landing sequence (for `integrate-seams.sh`)

`ComponentRenderer.kt`:
L4 seam 1 → L4 seam 3 → L2 seam 1 → L3 seam 1 → L5 seam 4 → L5 seam 5.

`ComponentRenderer.swift`:
L4 seam 2 → L4 seam 4 → L5 seam 1 → L5 seam 2 → L5 seam 3 → L1 seam 1 → L5 seam 6 (→ L1 seam 1b only on the probe's
trigger).

Run `git apply --check` before every hunk. Each lane's revert orders are dry-run in an export tree before stage 1:
- L4: seam 3 / seam 1, and seam 4 / seam 2;
- L5: seam 1 with seam 3 applied.
