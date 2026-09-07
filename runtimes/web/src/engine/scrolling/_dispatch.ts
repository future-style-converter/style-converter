// _dispatch.ts — Phase-9 + Phase-10 scrolling dispatch (45 dispatched
// properties: 3 scroll-timeline from Phase 9 + 42 long-tail from Phase 10).
// The six scroll-margin / scroll-padding SHORTHANDS (scroll-margin,
// -block, -inline and the scroll-padding trio) are expanded to their
// physical/logical longhands by ScrollMargin*/ScrollPadding*Expander
// (ShorthandRegistry.kt:91-96) before the longhand parser registry runs,
// exactly as css-scroll-snap-1 defines them (scroll-margin/scroll-padding
// and their logical block/inline forms are shorthands for the four side
// longhands), so their IR type names can never appear on the wire. Their
// triplets were unreachable and are deleted (A6#9); only the longhands
// below are dispatched.
import type { CSSProperties } from 'react';
// Phase-9 scroll-timeline (kept for back-compat with StyleBuilder.ts imports).
import { extractScrollTimeline } from './ScrollTimelineExtractor';
import { applyScrollTimeline } from './ScrollTimelineApplier';
import { extractScrollTimelineName } from './ScrollTimelineNameExtractor';
import { applyScrollTimelineName } from './ScrollTimelineNameApplier';
import { extractScrollTimelineAxis } from './ScrollTimelineAxisExtractor';
import { applyScrollTimelineAxis } from './ScrollTimelineAxisApplier';
import { extractScrollBehavior } from './ScrollBehaviorExtractor';
import { applyScrollBehavior } from './ScrollBehaviorApplier';
import { extractScrollSnapType } from './ScrollSnapTypeExtractor';
import { applyScrollSnapType } from './ScrollSnapTypeApplier';
import { extractScrollSnapAlign } from './ScrollSnapAlignExtractor';
import { applyScrollSnapAlign } from './ScrollSnapAlignApplier';
import { extractScrollSnapStop } from './ScrollSnapStopExtractor';
import { applyScrollSnapStop } from './ScrollSnapStopApplier';
import { extractScrollPaddingTop } from './ScrollPaddingTopExtractor';
import { applyScrollPaddingTop } from './ScrollPaddingTopApplier';
import { extractScrollPaddingRight } from './ScrollPaddingRightExtractor';
import { applyScrollPaddingRight } from './ScrollPaddingRightApplier';
import { extractScrollPaddingBottom } from './ScrollPaddingBottomExtractor';
import { applyScrollPaddingBottom } from './ScrollPaddingBottomApplier';
import { extractScrollPaddingLeft } from './ScrollPaddingLeftExtractor';
import { applyScrollPaddingLeft } from './ScrollPaddingLeftApplier';
import { extractScrollPaddingBlockStart } from './ScrollPaddingBlockStartExtractor';
import { applyScrollPaddingBlockStart } from './ScrollPaddingBlockStartApplier';
import { extractScrollPaddingBlockEnd } from './ScrollPaddingBlockEndExtractor';
import { applyScrollPaddingBlockEnd } from './ScrollPaddingBlockEndApplier';
import { extractScrollPaddingInlineStart } from './ScrollPaddingInlineStartExtractor';
import { applyScrollPaddingInlineStart } from './ScrollPaddingInlineStartApplier';
import { extractScrollPaddingInlineEnd } from './ScrollPaddingInlineEndExtractor';
import { applyScrollPaddingInlineEnd } from './ScrollPaddingInlineEndApplier';
import { extractScrollMarginTop } from './ScrollMarginTopExtractor';
import { applyScrollMarginTop } from './ScrollMarginTopApplier';
import { extractScrollMarginRight } from './ScrollMarginRightExtractor';
import { applyScrollMarginRight } from './ScrollMarginRightApplier';
import { extractScrollMarginBottom } from './ScrollMarginBottomExtractor';
import { applyScrollMarginBottom } from './ScrollMarginBottomApplier';
import { extractScrollMarginLeft } from './ScrollMarginLeftExtractor';
import { applyScrollMarginLeft } from './ScrollMarginLeftApplier';
import { extractScrollMarginBlockStart } from './ScrollMarginBlockStartExtractor';
import { applyScrollMarginBlockStart } from './ScrollMarginBlockStartApplier';
import { extractScrollMarginBlockEnd } from './ScrollMarginBlockEndExtractor';
import { applyScrollMarginBlockEnd } from './ScrollMarginBlockEndApplier';
import { extractScrollMarginInlineStart } from './ScrollMarginInlineStartExtractor';
import { applyScrollMarginInlineStart } from './ScrollMarginInlineStartApplier';
import { extractScrollMarginInlineEnd } from './ScrollMarginInlineEndExtractor';
import { applyScrollMarginInlineEnd } from './ScrollMarginInlineEndApplier';
import { extractOverscrollBehavior } from './OverscrollBehaviorExtractor';
import { applyOverscrollBehavior } from './OverscrollBehaviorApplier';
import { extractOverscrollBehaviorX } from './OverscrollBehaviorXExtractor';
import { applyOverscrollBehaviorX } from './OverscrollBehaviorXApplier';
import { extractOverscrollBehaviorY } from './OverscrollBehaviorYExtractor';
import { applyOverscrollBehaviorY } from './OverscrollBehaviorYApplier';
import { extractOverscrollBehaviorBlock } from './OverscrollBehaviorBlockExtractor';
import { applyOverscrollBehaviorBlock } from './OverscrollBehaviorBlockApplier';
import { extractOverscrollBehaviorInline } from './OverscrollBehaviorInlineExtractor';
import { applyOverscrollBehaviorInline } from './OverscrollBehaviorInlineApplier';
import { extractScrollbarWidth } from './ScrollbarWidthExtractor';
import { applyScrollbarWidth } from './ScrollbarWidthApplier';
import { extractScrollbarColor } from './ScrollbarColorExtractor';
import { applyScrollbarColor } from './ScrollbarColorApplier';
import { extractScrollbarGutter } from './ScrollbarGutterExtractor';
import { applyScrollbarGutter } from './ScrollbarGutterApplier';
import { extractOverflowAnchor } from './OverflowAnchorExtractor';
import { applyOverflowAnchor } from './OverflowAnchorApplier';
import { extractOverflowClipMargin } from './OverflowClipMarginExtractor';
import { applyOverflowClipMargin } from './OverflowClipMarginApplier';
import { extractScrollStart } from './ScrollStartExtractor';
import { applyScrollStart } from './ScrollStartApplier';
import { extractScrollStartX } from './ScrollStartXExtractor';
import { applyScrollStartX } from './ScrollStartXApplier';
import { extractScrollStartY } from './ScrollStartYExtractor';
import { applyScrollStartY } from './ScrollStartYApplier';
import { extractScrollStartBlock } from './ScrollStartBlockExtractor';
import { applyScrollStartBlock } from './ScrollStartBlockApplier';
import { extractScrollStartInline } from './ScrollStartInlineExtractor';
import { applyScrollStartInline } from './ScrollStartInlineApplier';
import { extractScrollStartTarget } from './ScrollStartTargetExtractor';
import { applyScrollStartTarget } from './ScrollStartTargetApplier';
// Issue #38: the four axis variants were coverage-only claims; now real triplets.
import { extractScrollStartTargetBlock } from './ScrollStartTargetBlockExtractor';
import { applyScrollStartTargetBlock } from './ScrollStartTargetBlockApplier';
import { extractScrollStartTargetInline } from './ScrollStartTargetInlineExtractor';
import { applyScrollStartTargetInline } from './ScrollStartTargetInlineApplier';
import { extractScrollStartTargetX } from './ScrollStartTargetXExtractor';
import { applyScrollStartTargetX } from './ScrollStartTargetXApplier';
import { extractScrollStartTargetY } from './ScrollStartTargetYExtractor';
import { applyScrollStartTargetY } from './ScrollStartTargetYApplier';
import { extractScrollMarkerGroup } from './ScrollMarkerGroupExtractor';
import { applyScrollMarkerGroup } from './ScrollMarkerGroupApplier';
import { extractScrollTargetGroup } from './ScrollTargetGroupExtractor';
import { applyScrollTargetGroup } from './ScrollTargetGroupApplier';
interface IRPropertyLike { type: string; data: unknown }

// Back-compat export used by StyleBuilder (Phase 9 only knew these three).
export function applyScrollingPhase9(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyScrollTimeline(extractScrollTimeline(properties)));
  Object.assign(out, applyScrollTimelineName(extractScrollTimelineName(properties)));
  Object.assign(out, applyScrollTimelineAxis(extractScrollTimelineAxis(properties)));
  return out;
}

export function applyScrollingPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyScrollBehavior(extractScrollBehavior(properties)));
  Object.assign(out, applyScrollSnapType(extractScrollSnapType(properties)));
  Object.assign(out, applyScrollSnapAlign(extractScrollSnapAlign(properties)));
  Object.assign(out, applyScrollSnapStop(extractScrollSnapStop(properties)));
  Object.assign(out, applyScrollPaddingTop(extractScrollPaddingTop(properties)));
  Object.assign(out, applyScrollPaddingRight(extractScrollPaddingRight(properties)));
  Object.assign(out, applyScrollPaddingBottom(extractScrollPaddingBottom(properties)));
  Object.assign(out, applyScrollPaddingLeft(extractScrollPaddingLeft(properties)));
  Object.assign(out, applyScrollPaddingBlockStart(extractScrollPaddingBlockStart(properties)));
  Object.assign(out, applyScrollPaddingBlockEnd(extractScrollPaddingBlockEnd(properties)));
  Object.assign(out, applyScrollPaddingInlineStart(extractScrollPaddingInlineStart(properties)));
  Object.assign(out, applyScrollPaddingInlineEnd(extractScrollPaddingInlineEnd(properties)));
  Object.assign(out, applyScrollMarginTop(extractScrollMarginTop(properties)));
  Object.assign(out, applyScrollMarginRight(extractScrollMarginRight(properties)));
  Object.assign(out, applyScrollMarginBottom(extractScrollMarginBottom(properties)));
  Object.assign(out, applyScrollMarginLeft(extractScrollMarginLeft(properties)));
  Object.assign(out, applyScrollMarginBlockStart(extractScrollMarginBlockStart(properties)));
  Object.assign(out, applyScrollMarginBlockEnd(extractScrollMarginBlockEnd(properties)));
  Object.assign(out, applyScrollMarginInlineStart(extractScrollMarginInlineStart(properties)));
  Object.assign(out, applyScrollMarginInlineEnd(extractScrollMarginInlineEnd(properties)));
  Object.assign(out, applyOverscrollBehavior(extractOverscrollBehavior(properties)));
  Object.assign(out, applyOverscrollBehaviorX(extractOverscrollBehaviorX(properties)));
  Object.assign(out, applyOverscrollBehaviorY(extractOverscrollBehaviorY(properties)));
  Object.assign(out, applyOverscrollBehaviorBlock(extractOverscrollBehaviorBlock(properties)));
  Object.assign(out, applyOverscrollBehaviorInline(extractOverscrollBehaviorInline(properties)));
  Object.assign(out, applyScrollbarWidth(extractScrollbarWidth(properties)));
  Object.assign(out, applyScrollbarColor(extractScrollbarColor(properties)));
  Object.assign(out, applyScrollbarGutter(extractScrollbarGutter(properties)));
  Object.assign(out, applyOverflowAnchor(extractOverflowAnchor(properties)));
  Object.assign(out, applyOverflowClipMargin(extractOverflowClipMargin(properties)));
  Object.assign(out, applyScrollStart(extractScrollStart(properties)));
  Object.assign(out, applyScrollStartX(extractScrollStartX(properties)));
  Object.assign(out, applyScrollStartY(extractScrollStartY(properties)));
  Object.assign(out, applyScrollStartBlock(extractScrollStartBlock(properties)));
  Object.assign(out, applyScrollStartInline(extractScrollStartInline(properties)));
  Object.assign(out, applyScrollStartTarget(extractScrollStartTarget(properties)));
  Object.assign(out, applyScrollStartTargetBlock(extractScrollStartTargetBlock(properties)));
  Object.assign(out, applyScrollStartTargetInline(extractScrollStartTargetInline(properties)));
  Object.assign(out, applyScrollStartTargetX(extractScrollStartTargetX(properties)));
  Object.assign(out, applyScrollStartTargetY(extractScrollStartTargetY(properties)));
  Object.assign(out, applyScrollMarkerGroup(extractScrollMarkerGroup(properties)));
  Object.assign(out, applyScrollTargetGroup(extractScrollTargetGroup(properties)));
  return out;
}
