// _dispatch.ts — Phase-9 animations/transitions/view-timeline/view-transition dispatch.
// Centralises the 26 Extractor+Applier calls so StyleBuilder.ts stays small.
// Follows the same shape as typography/_dispatch.ts and layout/_dispatch.ts:
// one import per extractor, one per applier, one exported fold that calls them
// all and returns the merged CSS object.
//
// Every applier here returns either `Pick<CSSProperties, ...>` (for the small
// set of natively-typed keys — animation-name/duration/delay/direction/
// fill-mode/play-state/timing-function/iteration-count, plus transition-
// property/duration/delay/timing-function) OR `Record<string, string>` for
// the CSS L2 / View Transitions API L2 keys csstype doesn't ship yet.  Both
// shapes are Object.assign-compatible with CSSProperties.

import type { CSSProperties } from 'react';

// ---- Animations (13) ---------------------------------------------------
import { extractAnimationName } from './AnimationNameExtractor';
import { applyAnimationName } from './AnimationNameApplier';
import { extractAnimationDuration } from './AnimationDurationExtractor';
import { applyAnimationDuration } from './AnimationDurationApplier';
import { extractAnimationDelay } from './AnimationDelayExtractor';
import { applyAnimationDelay } from './AnimationDelayApplier';
import { extractAnimationIterationCount } from './AnimationIterationCountExtractor';
import { applyAnimationIterationCount } from './AnimationIterationCountApplier';
import { extractAnimationDirection } from './AnimationDirectionExtractor';
import { applyAnimationDirection } from './AnimationDirectionApplier';
import { extractAnimationFillMode } from './AnimationFillModeExtractor';
import { applyAnimationFillMode } from './AnimationFillModeApplier';
import { extractAnimationPlayState } from './AnimationPlayStateExtractor';
import { applyAnimationPlayState } from './AnimationPlayStateApplier';
import { extractAnimationComposition } from './AnimationCompositionExtractor';
import { applyAnimationComposition } from './AnimationCompositionApplier';
import { extractAnimationTimingFunction } from './AnimationTimingFunctionExtractor';
import { applyAnimationTimingFunction } from './AnimationTimingFunctionApplier';
import { extractAnimationTimeline } from './AnimationTimelineExtractor';
import { applyAnimationTimeline } from './AnimationTimelineApplier';
import { extractAnimationRange } from './AnimationRangeExtractor';
import { applyAnimationRange } from './AnimationRangeApplier';
import { extractAnimationRangeStart } from './AnimationRangeStartExtractor';
import { applyAnimationRangeStart } from './AnimationRangeStartApplier';
import { extractAnimationRangeEnd } from './AnimationRangeEndExtractor';
import { applyAnimationRangeEnd } from './AnimationRangeEndApplier';

// ---- Transitions (5) ---------------------------------------------------
import { extractTransitionProperty } from './TransitionPropertyExtractor';
import { applyTransitionProperty } from './TransitionPropertyApplier';
import { extractTransitionDuration } from './TransitionDurationExtractor';
import { applyTransitionDuration } from './TransitionDurationApplier';
import { extractTransitionDelay } from './TransitionDelayExtractor';
import { applyTransitionDelay } from './TransitionDelayApplier';
import { extractTransitionTimingFunction } from './TransitionTimingFunctionExtractor';
import { applyTransitionTimingFunction } from './TransitionTimingFunctionApplier';
import { extractTransitionBehavior } from './TransitionBehaviorExtractor';
import { applyTransitionBehavior } from './TransitionBehaviorApplier';

// ---- View timeline + view transition + timeline scope (8) --------------
import { extractTimelineScope } from './TimelineScopeExtractor';
import { applyTimelineScope } from './TimelineScopeApplier';
import { extractViewTimeline } from './ViewTimelineExtractor';
import { applyViewTimeline } from './ViewTimelineApplier';
import { extractViewTimelineAxis } from './ViewTimelineAxisExtractor';
import { applyViewTimelineAxis } from './ViewTimelineAxisApplier';
import { extractViewTimelineInset } from './ViewTimelineInsetExtractor';
import { applyViewTimelineInset } from './ViewTimelineInsetApplier';
import { extractViewTimelineName } from './ViewTimelineNameExtractor';
import { applyViewTimelineName } from './ViewTimelineNameApplier';
import { extractViewTransitionName } from './ViewTransitionNameExtractor';
import { applyViewTransitionName } from './ViewTransitionNameApplier';
import { extractViewTransitionClass } from './ViewTransitionClassExtractor';
import { applyViewTransitionClass } from './ViewTransitionClassApplier';
import { extractViewTransitionGroup } from './ViewTransitionGroupExtractor';
import { applyViewTransitionGroup } from './ViewTransitionGroupApplier';

interface IRPropertyLike { type: string; data: unknown }

// Fold every Phase-9 extractor over the input property list and return the
// merged CSS object.  Order is irrelevant because no two properties share a
// CSS key.  Called once per component from StyleBuilder.buildStyles.
export function applyAnimationsPhase9(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};

  // Animations: native CSS keys (plus composition + timeline L2 widened).
  Object.assign(out, applyAnimationName(extractAnimationName(properties)));
  Object.assign(out, applyAnimationDuration(extractAnimationDuration(properties)));
  Object.assign(out, applyAnimationDelay(extractAnimationDelay(properties)));
  Object.assign(out, applyAnimationIterationCount(extractAnimationIterationCount(properties)));
  Object.assign(out, applyAnimationDirection(extractAnimationDirection(properties)));
  Object.assign(out, applyAnimationFillMode(extractAnimationFillMode(properties)));
  Object.assign(out, applyAnimationPlayState(extractAnimationPlayState(properties)));
  Object.assign(out, applyAnimationComposition(extractAnimationComposition(properties)));
  Object.assign(out, applyAnimationTimingFunction(extractAnimationTimingFunction(properties)));
  Object.assign(out, applyAnimationTimeline(extractAnimationTimeline(properties)));
  Object.assign(out, applyAnimationRange(extractAnimationRange(properties)));
  Object.assign(out, applyAnimationRangeStart(extractAnimationRangeStart(properties)));
  Object.assign(out, applyAnimationRangeEnd(extractAnimationRangeEnd(properties)));

  // Transitions: all native except TransitionBehavior (CSS L2, widened).
  Object.assign(out, applyTransitionProperty(extractTransitionProperty(properties)));
  Object.assign(out, applyTransitionDuration(extractTransitionDuration(properties)));
  Object.assign(out, applyTransitionDelay(extractTransitionDelay(properties)));
  Object.assign(out, applyTransitionTimingFunction(extractTransitionTimingFunction(properties)));
  Object.assign(out, applyTransitionBehavior(extractTransitionBehavior(properties)));

  // View/timeline + View-Transitions API (all CSS L2, widened).
  Object.assign(out, applyTimelineScope(extractTimelineScope(properties)));
  Object.assign(out, applyViewTimeline(extractViewTimeline(properties)));
  Object.assign(out, applyViewTimelineAxis(extractViewTimelineAxis(properties)));
  Object.assign(out, applyViewTimelineInset(extractViewTimelineInset(properties)));
  Object.assign(out, applyViewTimelineName(extractViewTimelineName(properties)));
  Object.assign(out, applyViewTransitionName(extractViewTransitionName(properties)));
  Object.assign(out, applyViewTransitionClass(extractViewTransitionClass(properties)));
  Object.assign(out, applyViewTransitionGroup(extractViewTransitionGroup(properties)));

  return out;
}
