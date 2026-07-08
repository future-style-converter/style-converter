// ScrollingDispatch.test.ts — Phase-9 scroll-timeline tripwires.

import { describe, it, expect } from 'vitest';
import { applyScrollingPhase9 } from '../../src/engine/scrolling/_dispatch';
import { extractScrollTimeline } from '../../src/engine/scrolling/ScrollTimelineExtractor';
import { applyScrollTimeline } from '../../src/engine/scrolling/ScrollTimelineApplier';
import { extractScrollTimelineName } from '../../src/engine/scrolling/ScrollTimelineNameExtractor';
import { applyScrollTimelineName } from '../../src/engine/scrolling/ScrollTimelineNameApplier';
import { extractScrollTimelineAxis } from '../../src/engine/scrolling/ScrollTimelineAxisExtractor';
import { applyScrollTimelineAxis } from '../../src/engine/scrolling/ScrollTimelineAxisApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('ScrollTimeline shorthand pair', () => {
  it('name + BLOCK axis', () => {
    expect(applyScrollTimeline(extractScrollTimeline(
      [p('ScrollTimeline', { name: { name: '--my-scroll' }, axis: 'BLOCK' })])))
      .toEqual({ scrollTimeline: '--my-scroll block' });
  });
  it('name + X axis', () => {
    expect(applyScrollTimeline(extractScrollTimeline(
      [p('ScrollTimeline', { name: { name: '--my-scroll' }, axis: 'X' })])))
      .toEqual({ scrollTimeline: '--my-scroll x' });
  });
});

describe('ScrollTimelineName', () => {
  it('custom ident', () => {
    expect(applyScrollTimelineName(extractScrollTimelineName(
      [p('ScrollTimelineName', { name: '--page-scroll' })])))
      .toEqual({ scrollTimelineName: '--page-scroll' });
  });
  it('none literal', () => {
    // Parser stores `none` as the literal string (no sentinel).
    expect(applyScrollTimelineName(extractScrollTimelineName(
      [p('ScrollTimelineName', { name: 'none' })])))
      .toEqual({ scrollTimelineName: 'none' });
  });
});

describe('ScrollTimelineAxis', () => {
  it('INLINE -> inline', () => {
    expect(applyScrollTimelineAxis(extractScrollTimelineAxis(
      [p('ScrollTimelineAxis', 'INLINE')])))
      .toEqual({ scrollTimelineAxis: 'inline' });
  });
  it('Y -> y', () => {
    expect(applyScrollTimelineAxis(extractScrollTimelineAxis(
      [p('ScrollTimelineAxis', 'Y')])))
      .toEqual({ scrollTimelineAxis: 'y' });
  });
});

describe('applyScrollingPhase9 fold', () => {
  it('empty -> empty', () => {
    expect(applyScrollingPhase9([])).toEqual({});
  });
  it('all three longhands at once', () => {
    const r = applyScrollingPhase9([
      p('ScrollTimelineName', { name: '--page' }),
      p('ScrollTimelineAxis', 'BLOCK'),
      p('ScrollTimeline', { name: { name: '--page' }, axis: 'BLOCK' }),
    ]);
    expect(r).toEqual({
      scrollTimeline: '--page block',
      scrollTimelineName: '--page',
      scrollTimelineAxis: 'block',
    });
  });
});
