// AnimationsDispatch.test.ts — Phase-9 tripwire coverage for the 26
// animations/transitions/view-timeline/view-transition triplets.  Exercises
// each extractor+applier pair plus the _dispatch fold.

import { describe, it, expect } from 'vitest';
import { applyAnimationsPhase9 } from '../../../src/style/engine/animations/_dispatch';

import { extractAnimationName } from '../../../src/style/engine/animations/AnimationNameExtractor';
import { applyAnimationName } from '../../../src/style/engine/animations/AnimationNameApplier';
import { extractAnimationDuration } from '../../../src/style/engine/animations/AnimationDurationExtractor';
import { applyAnimationDuration } from '../../../src/style/engine/animations/AnimationDurationApplier';
import { extractAnimationDelay } from '../../../src/style/engine/animations/AnimationDelayExtractor';
import { applyAnimationDelay } from '../../../src/style/engine/animations/AnimationDelayApplier';
import { extractAnimationIterationCount } from '../../../src/style/engine/animations/AnimationIterationCountExtractor';
import { applyAnimationIterationCount } from '../../../src/style/engine/animations/AnimationIterationCountApplier';
import { extractAnimationDirection } from '../../../src/style/engine/animations/AnimationDirectionExtractor';
import { applyAnimationDirection } from '../../../src/style/engine/animations/AnimationDirectionApplier';
import { extractAnimationFillMode } from '../../../src/style/engine/animations/AnimationFillModeExtractor';
import { applyAnimationFillMode } from '../../../src/style/engine/animations/AnimationFillModeApplier';
import { extractAnimationPlayState } from '../../../src/style/engine/animations/AnimationPlayStateExtractor';
import { applyAnimationPlayState } from '../../../src/style/engine/animations/AnimationPlayStateApplier';
import { extractAnimationComposition } from '../../../src/style/engine/animations/AnimationCompositionExtractor';
import { applyAnimationComposition } from '../../../src/style/engine/animations/AnimationCompositionApplier';
import { extractAnimationTimingFunction } from '../../../src/style/engine/animations/AnimationTimingFunctionExtractor';
import { applyAnimationTimingFunction } from '../../../src/style/engine/animations/AnimationTimingFunctionApplier';
import { extractAnimationTimeline } from '../../../src/style/engine/animations/AnimationTimelineExtractor';
import { applyAnimationTimeline } from '../../../src/style/engine/animations/AnimationTimelineApplier';
import { extractAnimationRange } from '../../../src/style/engine/animations/AnimationRangeExtractor';
import { applyAnimationRange } from '../../../src/style/engine/animations/AnimationRangeApplier';
import { extractAnimationRangeStart } from '../../../src/style/engine/animations/AnimationRangeStartExtractor';
import { applyAnimationRangeStart } from '../../../src/style/engine/animations/AnimationRangeStartApplier';
import { extractAnimationRangeEnd } from '../../../src/style/engine/animations/AnimationRangeEndExtractor';
import { applyAnimationRangeEnd } from '../../../src/style/engine/animations/AnimationRangeEndApplier';

import { extractTransitionProperty } from '../../../src/style/engine/animations/TransitionPropertyExtractor';
import { applyTransitionProperty } from '../../../src/style/engine/animations/TransitionPropertyApplier';
import { extractTransitionDuration } from '../../../src/style/engine/animations/TransitionDurationExtractor';
import { applyTransitionDuration } from '../../../src/style/engine/animations/TransitionDurationApplier';
import { extractTransitionDelay } from '../../../src/style/engine/animations/TransitionDelayExtractor';
import { applyTransitionDelay } from '../../../src/style/engine/animations/TransitionDelayApplier';
import { extractTransitionTimingFunction } from '../../../src/style/engine/animations/TransitionTimingFunctionExtractor';
import { applyTransitionTimingFunction } from '../../../src/style/engine/animations/TransitionTimingFunctionApplier';
import { extractTransitionBehavior } from '../../../src/style/engine/animations/TransitionBehaviorExtractor';
import { applyTransitionBehavior } from '../../../src/style/engine/animations/TransitionBehaviorApplier';

import { extractTimelineScope } from '../../../src/style/engine/animations/TimelineScopeExtractor';
import { applyTimelineScope } from '../../../src/style/engine/animations/TimelineScopeApplier';
import { extractViewTimeline } from '../../../src/style/engine/animations/ViewTimelineExtractor';
import { applyViewTimeline } from '../../../src/style/engine/animations/ViewTimelineApplier';
import { extractViewTimelineAxis } from '../../../src/style/engine/animations/ViewTimelineAxisExtractor';
import { applyViewTimelineAxis } from '../../../src/style/engine/animations/ViewTimelineAxisApplier';
import { extractViewTimelineInset } from '../../../src/style/engine/animations/ViewTimelineInsetExtractor';
import { applyViewTimelineInset } from '../../../src/style/engine/animations/ViewTimelineInsetApplier';
import { extractViewTimelineName } from '../../../src/style/engine/animations/ViewTimelineNameExtractor';
import { applyViewTimelineName } from '../../../src/style/engine/animations/ViewTimelineNameApplier';
import { extractViewTransitionName } from '../../../src/style/engine/animations/ViewTransitionNameExtractor';
import { applyViewTransitionName } from '../../../src/style/engine/animations/ViewTransitionNameApplier';
import { extractViewTransitionClass } from '../../../src/style/engine/animations/ViewTransitionClassExtractor';
import { applyViewTransitionClass } from '../../../src/style/engine/animations/ViewTransitionClassApplier';
import { extractViewTransitionGroup } from '../../../src/style/engine/animations/ViewTransitionGroupExtractor';
import { applyViewTransitionGroup } from '../../../src/style/engine/animations/ViewTransitionGroupApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('AnimationName', () => {
  it('single ident', () => {
    expect(applyAnimationName(extractAnimationName([p('AnimationName', [{ type: 'identifier', name: 'slide-in' }])])))
      .toEqual({ animationName: 'slide-in' });
  });
  it('none keyword', () => {
    expect(applyAnimationName(extractAnimationName([p('AnimationName', [{ type: 'none' }])])))
      .toEqual({ animationName: 'none' });
  });
  it('multi-name list', () => {
    const r = applyAnimationName(extractAnimationName([p('AnimationName', [
      { type: 'identifier', name: 'fade-in' },
      { type: 'identifier', name: 'slide-up' },
    ])]));
    expect(r).toEqual({ animationName: 'fade-in, slide-up' });
  });
});

describe('AnimationDuration', () => {
  it('Durations list preserves seconds unit', () => {
    const r = applyAnimationDuration(extractAnimationDuration([p('AnimationDuration', {
      type: 'app.irmodels.properties.animations.AnimationDurationProperty.AnimationDurationValue.Durations',
      durations: [{ ms: 2000, original: { v: 2, u: 'S' } }],
    })]));
    expect(r).toEqual({ animationDuration: '2s' });
  });
  it('multi-ms list joins with comma', () => {
    const r = applyAnimationDuration(extractAnimationDuration([p('AnimationDuration', {
      type: 'app.irmodels.properties.animations.AnimationDurationProperty.AnimationDurationValue.Durations',
      durations: [{ ms: 500 }, { ms: 1000, original: { v: 1, u: 'S' } }],
    })]));
    expect(r).toEqual({ animationDuration: '500ms, 1s' });
  });
});

describe('AnimationDelay', () => {
  it('negative seconds', () => {
    expect(applyAnimationDelay(extractAnimationDelay([p('AnimationDelay',
      [{ ms: -1500, original: { v: -1.5, u: 'S' } }])])))
      .toEqual({ animationDelay: '-1.5s' });
  });
  it('multi-value with mixed units', () => {
    const r = applyAnimationDelay(extractAnimationDelay([p('AnimationDelay',
      [{ ms: 0, original: { v: 0, u: 'S' } }, { ms: 250 }, { ms: -125 }])]));
    expect(r).toEqual({ animationDelay: '0s, 250ms, -125ms' });
  });
});

describe('AnimationIterationCount', () => {
  it('infinite keyword', () => {
    expect(applyAnimationIterationCount(extractAnimationIterationCount(
      [p('AnimationIterationCount', ['infinite'])])))
      .toEqual({ animationIterationCount: 'infinite' });
  });
  it('multi numeric', () => {
    expect(applyAnimationIterationCount(extractAnimationIterationCount(
      [p('AnimationIterationCount', [1, 3, 0.5])])))
      .toEqual({ animationIterationCount: '1, 3, 0.5' });
  });
});

describe('AnimationDirection / FillMode / PlayState', () => {
  it('direction kebabs ALTERNATE_REVERSE', () => {
    expect(applyAnimationDirection(extractAnimationDirection(
      [p('AnimationDirection', ['ALTERNATE_REVERSE'])])))
      .toEqual({ animationDirection: 'alternate-reverse' });
  });
  it('fillMode multi-list', () => {
    expect(applyAnimationFillMode(extractAnimationFillMode(
      [p('AnimationFillMode', ['NONE', 'FORWARDS', 'BOTH'])])))
      .toEqual({ animationFillMode: 'none, forwards, both' });
  });
  it('playState paused', () => {
    expect(applyAnimationPlayState(extractAnimationPlayState(
      [p('AnimationPlayState', ['PAUSED'])])))
      .toEqual({ animationPlayState: 'paused' });
  });
});

describe('AnimationComposition (csstype-widened)', () => {
  it('singleton accumulate', () => {
    expect(applyAnimationComposition(extractAnimationComposition(
      [p('AnimationComposition', { type: 'accumulate' })])))
      .toEqual({ animationComposition: 'accumulate' });
  });
  it('list of three', () => {
    expect(applyAnimationComposition(extractAnimationComposition(
      [p('AnimationComposition', { type: 'list', values: ['replace', 'add', 'accumulate'] })])))
      .toEqual({ animationComposition: 'replace, add, accumulate' });
  });
});

describe('AnimationTimingFunction', () => {
  it('keyword via original', () => {
    expect(applyAnimationTimingFunction(extractAnimationTimingFunction(
      [p('AnimationTimingFunction', [{ cb: [0.25, 0.1, 0.25, 1], original: 'ease' }])])))
      .toEqual({ animationTimingFunction: 'ease' });
  });
  it('explicit cubic-bezier with negative controls', () => {
    expect(applyAnimationTimingFunction(extractAnimationTimingFunction(
      [p('AnimationTimingFunction', [{ cb: [0.68, -0.55, 0.27, 1.55], original: { cb: [0.68, -0.55, 0.27, 1.55] } }])])))
      .toEqual({ animationTimingFunction: 'cubic-bezier(0.68, -0.55, 0.27, 1.55)' });
  });
  it('steps(n, jump-none)', () => {
    expect(applyAnimationTimingFunction(extractAnimationTimingFunction(
      [p('AnimationTimingFunction', [{ steps: { n: 3, pos: 'jump-none' }, original: { steps: 3, pos: 'jump-none' } }])])))
      .toEqual({ animationTimingFunction: 'steps(3, jump-none)' });
  });
  it('linear(0, 0.25 25%, 1) stops', () => {
    expect(applyAnimationTimingFunction(extractAnimationTimingFunction(
      [p('AnimationTimingFunction', [{ linear: [{ v: 0 }, { v: 0.25, p: 25 }, { v: 1 }], original: { linear: [] } }])])))
      .toEqual({ animationTimingFunction: 'linear(0, 0.25 25%, 1)' });
  });
  it('multi-fn comma list', () => {
    const r = applyAnimationTimingFunction(extractAnimationTimingFunction([p('AnimationTimingFunction', [
      { cb: [0.42, 0, 1, 1], original: 'ease-in' },
      { steps: { n: 4, pos: 'end' }, original: { steps: 4, pos: 'end' } },
    ])]));
    expect(r).toEqual({ animationTimingFunction: 'ease-in, steps(4, end)' });
  });
});

describe('AnimationTimeline (csstype-widened)', () => {
  it('auto keyword', () => {
    expect(applyAnimationTimeline(extractAnimationTimeline(
      [p('AnimationTimeline', { type: 'auto' })])))
      .toEqual({ animationTimeline: 'auto' });
  });
  it('named ident', () => {
    expect(applyAnimationTimeline(extractAnimationTimeline(
      [p('AnimationTimeline', { type: 'named', name: '--my-timeline' })])))
      .toEqual({ animationTimeline: '--my-timeline' });
  });
  it('scroll() empty', () => {
    expect(applyAnimationTimeline(extractAnimationTimeline(
      [p('AnimationTimeline', { type: 'scroll' })])))
      .toEqual({ animationTimeline: 'scroll()' });
  });
  it('scroll(root inline)', () => {
    expect(applyAnimationTimeline(extractAnimationTimeline(
      [p('AnimationTimeline', { type: 'scroll', scroller: 'root', axis: 'inline' })])))
      .toEqual({ animationTimeline: 'scroll(root inline)' });
  });
  it('view(inline 10px 20px)', () => {
    expect(applyAnimationTimeline(extractAnimationTimeline(
      [p('AnimationTimeline', { type: 'view', axis: 'inline', insetStart: '10px', insetEnd: '20px' })])))
      .toEqual({ animationTimeline: 'view(inline 10px 20px)' });
  });
});

describe('AnimationRange / Start / End', () => {
  it('range pct pair', () => {
    expect(applyAnimationRange(extractAnimationRange(
      [p('AnimationRange', { start: 0, end: 100 })])))
      .toEqual({ animationRange: '0% 100%' });
  });
  it('range named pair (pre-serialised)', () => {
    expect(applyAnimationRange(extractAnimationRange(
      [p('AnimationRange', { start: 'entry 0%', end: 'cover 100%' })])))
      .toEqual({ animationRange: 'entry 0% cover 100%' });
  });
  it('range-start named pair', () => {
    expect(applyAnimationRangeStart(extractAnimationRangeStart(
      [p('AnimationRangeStart', { name: 'cover', offset: 25 })])))
      .toEqual({ animationRangeStart: 'cover 25%' });
  });
  it('range-end length', () => {
    expect(applyAnimationRangeEnd(extractAnimationRangeEnd(
      [p('AnimationRangeEnd', { px: 200 })])))
      .toEqual({ animationRangeEnd: '200px' });
  });
});

describe('Transition* properties', () => {
  it('transition-property list (all + ident)', () => {
    const r = applyTransitionProperty(extractTransitionProperty(
      [p('TransitionProperty', [{ type: 'all' }, { type: 'property-name', name: 'opacity' }])]));
    expect(r).toEqual({ transitionProperty: 'all, opacity' });
  });
  it('transition-property none', () => {
    expect(applyTransitionProperty(extractTransitionProperty(
      [p('TransitionProperty', [{ type: 'none' }])])))
      .toEqual({ transitionProperty: 'none' });
  });
  it('transition-duration ms + s round-trip', () => {
    const r = applyTransitionDuration(extractTransitionDuration([p('TransitionDuration',
      [{ ms: 150 }, { ms: 1000, original: { v: 1, u: 'S' } }])]));
    expect(r).toEqual({ transitionDuration: '150ms, 1s' });
  });
  it('transition-delay negative', () => {
    expect(applyTransitionDelay(extractTransitionDelay(
      [p('TransitionDelay', [{ ms: -500 }])])))
      .toEqual({ transitionDelay: '-500ms' });
  });
  it('transition-timing-function steps', () => {
    expect(applyTransitionTimingFunction(extractTransitionTimingFunction(
      [p('TransitionTimingFunction', [{ steps: { n: 3, pos: 'jump-start' }, original: { steps: 3, pos: 'jump-start' } }])])))
      .toEqual({ transitionTimingFunction: 'steps(3, jump-start)' });
  });
  it('transition-behavior allow-discrete (csstype-widened)', () => {
    expect(applyTransitionBehavior(extractTransitionBehavior(
      [p('TransitionBehavior', { type: 'allow-discrete' })])))
      .toEqual({ transitionBehavior: 'allow-discrete' });
  });
});

describe('TimelineScope / View timeline / View transition', () => {
  it('timeline-scope names list', () => {
    expect(applyTimelineScope(extractTimelineScope(
      [p('TimelineScope', { type: 'names', names: ['--tl-a', '--tl-b'] })])))
      .toEqual({ timelineScope: '--tl-a, --tl-b' });
  });
  it('timeline-scope all keyword', () => {
    expect(applyTimelineScope(extractTimelineScope(
      [p('TimelineScope', { type: 'all' })])))
      .toEqual({ timelineScope: 'all' });
  });
  it('view-timeline name only', () => {
    expect(applyViewTimeline(extractViewTimeline(
      [p('ViewTimeline', '--my-view')])))
      .toEqual({ viewTimeline: '--my-view' });
  });
  it('view-timeline name + INLINE axis', () => {
    expect(applyViewTimeline(extractViewTimeline(
      [p('ViewTimeline', { name: '--my-view', axis: 'INLINE' })])))
      .toEqual({ viewTimeline: '--my-view inline' });
  });
  it('view-timeline-axis Y -> y', () => {
    expect(applyViewTimelineAxis(extractViewTimelineAxis(
      [p('ViewTimelineAxis', 'Y')])))
      .toEqual({ viewTimelineAxis: 'y' });
  });
  it('view-timeline-inset auto pair collapses', () => {
    expect(applyViewTimelineInset(extractViewTimelineInset(
      [p('ViewTimelineInset', { start: { type: 'auto' }, end: { type: 'auto' } })])))
      .toEqual({ viewTimelineInset: 'auto' });
  });
  it('view-timeline-inset mixed auto + length', () => {
    expect(applyViewTimelineInset(extractViewTimelineInset(
      [p('ViewTimelineInset', { start: { type: 'auto' }, end: { type: 'length', px: 30 } })])))
      .toEqual({ viewTimelineInset: 'auto 30px' });
  });
  it('view-timeline-name passthrough', () => {
    expect(applyViewTimelineName(extractViewTimelineName(
      [p('ViewTimelineName', '--reveal')])))
      .toEqual({ viewTimelineName: '--reveal' });
  });
  it('view-transition-name ident', () => {
    expect(applyViewTransitionName(extractViewTransitionName(
      [p('ViewTransitionName', { type: 'named', name: '--hero-image' })])))
      .toEqual({ viewTransitionName: '--hero-image' });
  });
  it('view-transition-class space-separated list', () => {
    expect(applyViewTransitionClass(extractViewTransitionClass(
      [p('ViewTransitionClass', { type: 'classes', names: ['slide', 'fade', 'fast'] })])))
      .toEqual({ viewTransitionClass: 'slide fade fast' });
  });
  it('view-transition-group nearest keyword', () => {
    expect(applyViewTransitionGroup(extractViewTransitionGroup(
      [p('ViewTransitionGroup', { type: 'nearest' })])))
      .toEqual({ viewTransitionGroup: 'nearest' });
  });
  it('view-transition-group raw ident', () => {
    expect(applyViewTransitionGroup(extractViewTransitionGroup(
      [p('ViewTransitionGroup', { type: 'Raw', value: '--my-group' })])))
      .toEqual({ viewTransitionGroup: '--my-group' });
  });
});

describe('applyAnimationsPhase9', () => {
  it('empty input -> empty object', () => {
    expect(applyAnimationsPhase9([])).toEqual({});
  });
  it('folds multiple declarations', () => {
    const r = applyAnimationsPhase9([
      p('AnimationName', [{ type: 'identifier', name: 'fade' }]),
      p('AnimationDuration', {
        type: 'app.irmodels.properties.animations.AnimationDurationProperty.AnimationDurationValue.Durations',
        durations: [{ ms: 300 }],
      }),
      p('TransitionProperty', [{ type: 'property-name', name: 'opacity' }]),
      p('ViewTransitionName', { type: 'named', name: 'hero' }),
    ]);
    expect(r).toEqual({
      animationName: 'fade',
      animationDuration: '300ms',
      transitionProperty: 'opacity',
      viewTransitionName: 'hero',
    });
  });
  it('last-write-wins cascade', () => {
    const r = applyAnimationsPhase9([
      p('AnimationName', [{ type: 'identifier', name: 'one' }]),
      p('AnimationName', [{ type: 'identifier', name: 'two' }]),
    ]);
    expect(r.animationName).toBe('two');
  });
});
