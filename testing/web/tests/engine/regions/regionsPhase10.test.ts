// regionsPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyRegionsPhase10 } from '../../../src/style/engine/regions/_dispatch';

describe('applyRegionsPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyRegionsPhase10([])).toEqual({});
  });
  it('RegionFragment → auto', () => {
    expect(applyRegionsPhase10([{ type: 'RegionFragment', data: 'AUTO' }]))
      .toEqual({ regionFragment: 'auto' });
  });
  it('Continue → discard', () => {
    expect(applyRegionsPhase10([{ type: 'Continue', data: 'DISCARD' }]))
      .toEqual({ continue: 'discard' });
  });
  it('WrapFlow → both', () => {
    expect(applyRegionsPhase10([{ type: 'WrapFlow', data: 'BOTH' }]))
      .toEqual({ wrapFlow: 'both' });
  });
  it('WrapThrough → wrap', () => {
    expect(applyRegionsPhase10([{ type: 'WrapThrough', data: 'WRAP' }]))
      .toEqual({ wrapThrough: 'wrap' });
  });
  it('WrapBefore → auto', () => {
    expect(applyRegionsPhase10([{ type: 'WrapBefore', data: 'AUTO' }]))
      .toEqual({ wrapBefore: 'auto' });
  });
});
