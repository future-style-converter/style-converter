// scrollingPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyScrollingPhase10 } from '../../../src/style/engine/scrolling/_dispatch';

describe('applyScrollingPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyScrollingPhase10([])).toEqual({});
  });
  it('ScrollBehavior → smooth', () => {
    expect(applyScrollingPhase10([{ type: 'ScrollBehavior', data: 'SMOOTH' }]))
      .toEqual({ scrollBehavior: 'smooth' });
  });
  it('ScrollSnapType → x-mandatory', () => {
    expect(applyScrollingPhase10([{ type: 'ScrollSnapType', data: 'X_MANDATORY' }]))
      .toEqual({ scrollSnapType: 'x-mandatory' });
  });
  it('ScrollSnapAlign → center', () => {
    expect(applyScrollingPhase10([{ type: 'ScrollSnapAlign', data: 'CENTER' }]))
      .toEqual({ scrollSnapAlign: 'center' });
  });
  it('ScrollSnapStop → always', () => {
    expect(applyScrollingPhase10([{ type: 'ScrollSnapStop', data: 'ALWAYS' }]))
      .toEqual({ scrollSnapStop: 'always' });
  });
  it('OverscrollBehavior → contain', () => {
    expect(applyScrollingPhase10([{ type: 'OverscrollBehavior', data: 'CONTAIN' }]))
      .toEqual({ overscrollBehavior: 'contain' });
  });
  it('OverscrollBehaviorX → none', () => {
    expect(applyScrollingPhase10([{ type: 'OverscrollBehaviorX', data: 'NONE' }]))
      .toEqual({ overscrollBehaviorX: 'none' });
  });
  it('ScrollbarWidth → thin', () => {
    expect(applyScrollingPhase10([{ type: 'ScrollbarWidth', data: 'THIN' }]))
      .toEqual({ scrollbarWidth: 'thin' });
  });
  it('ScrollbarGutter → stable', () => {
    expect(applyScrollingPhase10([{ type: 'ScrollbarGutter', data: 'STABLE' }]))
      .toEqual({ scrollbarGutter: 'stable' });
  });
  it('OverflowAnchor → auto', () => {
    expect(applyScrollingPhase10([{ type: 'OverflowAnchor', data: 'AUTO' }]))
      .toEqual({ overflowAnchor: 'auto' });
  });
  it('ScrollMarkerGroup → before', () => {
    expect(applyScrollingPhase10([{ type: 'ScrollMarkerGroup', data: 'BEFORE' }]))
      .toEqual({ scrollMarkerGroup: 'before' });
  });
});
