// _dispatch.ts — Phase-7 layout dispatch.
// Centralises the 55 Extractor+Applier calls so StyleBuilder.ts stays readable.
// Mirrors engine/typography/_dispatch.ts (Phase 6) and engine/borders/_dispatch.ts
// (Phase 5): one explicit import per extractor and per applier, one single
// exported function that folds every triplet into a merged CSS accumulator.
//
// Order is last-write-wins but none of the layout properties share a CSS key,
// so ordering is mechanically irrelevant — this file simply mirrors the
// on-disk folder layout (root → advanced → flexbox → grid → position) so that
// `tsc --noEmit` catches import drift and `git diff` stays reviewable.

import type { CSSProperties } from 'react';

// ---- Root-level (flow + top-layer) — 4 triplets ------------------------
import { extractClear } from './ClearExtractor';
import { applyClear } from './ClearApplier';
import { extractFloat } from './FloatExtractor';
import { applyFloat } from './FloatApplier';
import { extractOverlay } from './OverlayExtractor';
import { applyOverlay } from './OverlayApplier';
import { extractReadingFlow } from './ReadingFlowExtractor';
import { applyReadingFlow } from './ReadingFlowApplier';

// ---- Advanced (anchor / offset-path / position-try) — 16 triplets ------
import { extractAnchorName } from './advanced/AnchorNameExtractor';
import { applyAnchorName } from './advanced/AnchorNameApplier';
import { extractAnchorScope } from './advanced/AnchorScopeExtractor';
import { applyAnchorScope } from './advanced/AnchorScopeApplier';
import { extractInsetArea } from './advanced/InsetAreaExtractor';
import { applyInsetArea } from './advanced/InsetAreaApplier';
import { extractOffsetAnchor } from './advanced/OffsetAnchorExtractor';
import { applyOffsetAnchor } from './advanced/OffsetAnchorApplier';
import { extractOffsetDistance } from './advanced/OffsetDistanceExtractor';
import { applyOffsetDistance } from './advanced/OffsetDistanceApplier';
import { extractOffsetPath } from './advanced/OffsetPathExtractor';
import { applyOffsetPath } from './advanced/OffsetPathApplier';
import { extractOffsetPosition } from './advanced/OffsetPositionExtractor';
import { applyOffsetPosition } from './advanced/OffsetPositionApplier';
import { extractOffsetRotate } from './advanced/OffsetRotateExtractor';
import { applyOffsetRotate } from './advanced/OffsetRotateApplier';
import { extractPositionAnchor } from './advanced/PositionAnchorExtractor';
import { applyPositionAnchor } from './advanced/PositionAnchorApplier';
import { extractPositionArea } from './advanced/PositionAreaExtractor';
import { applyPositionArea } from './advanced/PositionAreaApplier';
import { extractPositionFallback } from './advanced/PositionFallbackExtractor';
import { applyPositionFallback } from './advanced/PositionFallbackApplier';
import { extractPositionTry } from './advanced/PositionTryExtractor';
import { applyPositionTry } from './advanced/PositionTryApplier';
import { extractPositionTryFallbacks } from './advanced/PositionTryFallbacksExtractor';
import { applyPositionTryFallbacks } from './advanced/PositionTryFallbacksApplier';
import { extractPositionTryOptions } from './advanced/PositionTryOptionsExtractor';
import { applyPositionTryOptions } from './advanced/PositionTryOptionsApplier';
import { extractPositionTryOrder } from './advanced/PositionTryOrderExtractor';
import { applyPositionTryOrder } from './advanced/PositionTryOrderApplier';
import { extractPositionVisibility } from './advanced/PositionVisibilityExtractor';
import { applyPositionVisibility } from './advanced/PositionVisibilityApplier';

// ---- Flexbox — 11 triplets ---------------------------------------------
import { extractAlignContent } from './flexbox/AlignContentExtractor';
import { applyAlignContent } from './flexbox/AlignContentApplier';
import { extractAlignItems } from './flexbox/AlignItemsExtractor';
import { applyAlignItems } from './flexbox/AlignItemsApplier';
import { extractAlignSelf } from './flexbox/AlignSelfExtractor';
import { applyAlignSelf } from './flexbox/AlignSelfApplier';
import { extractDisplay } from './flexbox/DisplayExtractor';
import { applyDisplay } from './flexbox/DisplayApplier';
import { extractFlexBasis } from './flexbox/FlexBasisExtractor';
import { applyFlexBasis } from './flexbox/FlexBasisApplier';
import { extractFlexDirection } from './flexbox/FlexDirectionExtractor';
import { applyFlexDirection } from './flexbox/FlexDirectionApplier';
import { extractFlexGrow } from './flexbox/FlexGrowExtractor';
import { applyFlexGrow } from './flexbox/FlexGrowApplier';
import { extractFlexShrink } from './flexbox/FlexShrinkExtractor';
import { applyFlexShrink } from './flexbox/FlexShrinkApplier';
import { extractFlexWrap } from './flexbox/FlexWrapExtractor';
import { applyFlexWrap } from './flexbox/FlexWrapApplier';
import { extractJustifyContent } from './flexbox/JustifyContentExtractor';
import { applyJustifyContent } from './flexbox/JustifyContentApplier';
import { extractOrder } from './flexbox/OrderExtractor';
import { applyOrder } from './flexbox/OrderApplier';

// ---- Grid — 14 triplets ------------------------------------------------
import { extractAlignTracks } from './grid/AlignTracksExtractor';
import { applyAlignTracks } from './grid/AlignTracksApplier';
import { extractGridAutoColumns } from './grid/GridAutoColumnsExtractor';
import { applyGridAutoColumns } from './grid/GridAutoColumnsApplier';
import { extractGridAutoFlow } from './grid/GridAutoFlowExtractor';
import { applyGridAutoFlow } from './grid/GridAutoFlowApplier';
import { extractGridAutoRows } from './grid/GridAutoRowsExtractor';
import { applyGridAutoRows } from './grid/GridAutoRowsApplier';
import { extractGridColumnEnd } from './grid/GridColumnEndExtractor';
import { applyGridColumnEnd } from './grid/GridColumnEndApplier';
import { extractGridColumnStart } from './grid/GridColumnStartExtractor';
import { applyGridColumnStart } from './grid/GridColumnStartApplier';
import { extractGridRowEnd } from './grid/GridRowEndExtractor';
import { applyGridRowEnd } from './grid/GridRowEndApplier';
import { extractGridRowStart } from './grid/GridRowStartExtractor';
import { applyGridRowStart } from './grid/GridRowStartApplier';
import { extractGridTemplateAreas } from './grid/GridTemplateAreasExtractor';
import { applyGridTemplateAreas } from './grid/GridTemplateAreasApplier';
import { extractGridTemplateColumns } from './grid/GridTemplateColumnsExtractor';
import { applyGridTemplateColumns } from './grid/GridTemplateColumnsApplier';
import { extractGridTemplateRows } from './grid/GridTemplateRowsExtractor';
import { applyGridTemplateRows } from './grid/GridTemplateRowsApplier';
import { extractJustifyItems } from './grid/JustifyItemsExtractor';
import { applyJustifyItems } from './grid/JustifyItemsApplier';
import { extractJustifySelf } from './grid/JustifySelfExtractor';
import { applyJustifySelf } from './grid/JustifySelfApplier';
import { extractJustifyTracks } from './grid/JustifyTracksExtractor';
import { applyJustifyTracks } from './grid/JustifyTracksApplier';
import { extractMasonryAutoFlow } from './grid/MasonryAutoFlowExtractor';
import { applyMasonryAutoFlow } from './grid/MasonryAutoFlowApplier';

// ---- Position / edges — 10 triplets ------------------------------------
import { extractBottom } from './position/BottomExtractor';
import { applyBottom } from './position/BottomApplier';
import { extractInsetBlockEnd } from './position/InsetBlockEndExtractor';
import { applyInsetBlockEnd } from './position/InsetBlockEndApplier';
import { extractInsetBlockStart } from './position/InsetBlockStartExtractor';
import { applyInsetBlockStart } from './position/InsetBlockStartApplier';
import { extractInsetInlineEnd } from './position/InsetInlineEndExtractor';
import { applyInsetInlineEnd } from './position/InsetInlineEndApplier';
import { extractInsetInlineStart } from './position/InsetInlineStartExtractor';
import { applyInsetInlineStart } from './position/InsetInlineStartApplier';
import { extractLeft } from './position/LeftExtractor';
import { applyLeft } from './position/LeftApplier';
import { extractPosition } from './position/PositionExtractor';
import { applyPosition } from './position/PositionApplier';
import { extractRight } from './position/RightExtractor';
import { applyRight } from './position/RightApplier';
import { extractTop } from './position/TopExtractor';
import { applyTop } from './position/TopApplier';
import { extractZIndex } from './position/ZIndexExtractor';
import { applyZIndex } from './position/ZIndexApplier';

// Minimal IR property shape — decoupled from IRModels.ts (same pattern
// as typography/_dispatch.ts) so the engine doesn't pin an IR version.
interface IRPropertyLike { type: string; data: unknown }

// Run every layout extractor/applier and return a merged style object.
// Called once per component from `StyleBuilder.buildStyles` — replaces the
// legacy Display/Flex*/Justify*/Grid*/Position/Top/Left/ZIndex switch cases.
// Appliers return either `Pick<CSSProperties, K>` (native keys) or
// `Record<string,string>` (draft-level keys csstype doesn't ship yet) —
// both shapes are spread-compatible with `CSSProperties` via Object.assign.
export function applyLayoutPhase7(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};

  // Root.  Clear / Float block-formatting + top-layer + a11y reading order.
  Object.assign(out, applyClear(extractClear(properties)));
  Object.assign(out, applyFloat(extractFloat(properties)));
  Object.assign(out, applyOverlay(extractOverlay(properties)));
  Object.assign(out, applyReadingFlow(extractReadingFlow(properties)));

  // Advanced anchor-positioning + motion-path.  All draft-level — appliers
  // widen to Record<string,string> because csstype lacks these keys today.
  Object.assign(out, applyAnchorName(extractAnchorName(properties)));
  Object.assign(out, applyAnchorScope(extractAnchorScope(properties)));
  Object.assign(out, applyInsetArea(extractInsetArea(properties)));
  Object.assign(out, applyOffsetAnchor(extractOffsetAnchor(properties)));
  Object.assign(out, applyOffsetDistance(extractOffsetDistance(properties)));
  Object.assign(out, applyOffsetPath(extractOffsetPath(properties)));
  Object.assign(out, applyOffsetPosition(extractOffsetPosition(properties)));
  Object.assign(out, applyOffsetRotate(extractOffsetRotate(properties)));
  Object.assign(out, applyPositionAnchor(extractPositionAnchor(properties)));
  Object.assign(out, applyPositionArea(extractPositionArea(properties)));
  Object.assign(out, applyPositionFallback(extractPositionFallback(properties)));
  Object.assign(out, applyPositionTry(extractPositionTry(properties)));
  Object.assign(out, applyPositionTryFallbacks(extractPositionTryFallbacks(properties)));
  Object.assign(out, applyPositionTryOptions(extractPositionTryOptions(properties)));
  Object.assign(out, applyPositionTryOrder(extractPositionTryOrder(properties)));
  Object.assign(out, applyPositionVisibility(extractPositionVisibility(properties)));

  // Flexbox container + item properties.
  Object.assign(out, applyDisplay(extractDisplay(properties)));
  Object.assign(out, applyFlexDirection(extractFlexDirection(properties)));
  Object.assign(out, applyFlexWrap(extractFlexWrap(properties)));
  Object.assign(out, applyJustifyContent(extractJustifyContent(properties)));
  Object.assign(out, applyAlignItems(extractAlignItems(properties)));
  Object.assign(out, applyAlignContent(extractAlignContent(properties)));
  Object.assign(out, applyAlignSelf(extractAlignSelf(properties)));
  Object.assign(out, applyFlexBasis(extractFlexBasis(properties)));
  Object.assign(out, applyFlexGrow(extractFlexGrow(properties)));
  Object.assign(out, applyFlexShrink(extractFlexShrink(properties)));
  Object.assign(out, applyOrder(extractOrder(properties)));

  // Grid container + item properties (incl. masonry draft extensions).
  Object.assign(out, applyGridTemplateColumns(extractGridTemplateColumns(properties)));
  Object.assign(out, applyGridTemplateRows(extractGridTemplateRows(properties)));
  Object.assign(out, applyGridTemplateAreas(extractGridTemplateAreas(properties)));
  Object.assign(out, applyGridAutoColumns(extractGridAutoColumns(properties)));
  Object.assign(out, applyGridAutoRows(extractGridAutoRows(properties)));
  Object.assign(out, applyGridAutoFlow(extractGridAutoFlow(properties)));
  Object.assign(out, applyGridColumnStart(extractGridColumnStart(properties)));
  Object.assign(out, applyGridColumnEnd(extractGridColumnEnd(properties)));
  Object.assign(out, applyGridRowStart(extractGridRowStart(properties)));
  Object.assign(out, applyGridRowEnd(extractGridRowEnd(properties)));
  Object.assign(out, applyJustifyItems(extractJustifyItems(properties)));
  Object.assign(out, applyJustifySelf(extractJustifySelf(properties)));
  Object.assign(out, applyAlignTracks(extractAlignTracks(properties)));
  Object.assign(out, applyJustifyTracks(extractJustifyTracks(properties)));
  Object.assign(out, applyMasonryAutoFlow(extractMasonryAutoFlow(properties)));

  // Position + physical/logical edges + stacking.
  Object.assign(out, applyPosition(extractPosition(properties)));
  Object.assign(out, applyTop(extractTop(properties)));
  Object.assign(out, applyRight(extractRight(properties)));
  Object.assign(out, applyBottom(extractBottom(properties)));
  Object.assign(out, applyLeft(extractLeft(properties)));
  Object.assign(out, applyInsetBlockStart(extractInsetBlockStart(properties)));
  Object.assign(out, applyInsetBlockEnd(extractInsetBlockEnd(properties)));
  Object.assign(out, applyInsetInlineStart(extractInsetInlineStart(properties)));
  Object.assign(out, applyInsetInlineEnd(extractInsetInlineEnd(properties)));
  Object.assign(out, applyZIndex(extractZIndex(properties)));

  return out;
}
