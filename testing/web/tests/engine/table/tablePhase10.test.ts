// tablePhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyTablePhase10 } from '../../../src/style/engine/table/_dispatch';

describe('applyTablePhase10', () => {
  it('empty input → empty output', () => {
    expect(applyTablePhase10([])).toEqual({});
  });
  it('BorderCollapse → collapse', () => {
    expect(applyTablePhase10([{ type: 'BorderCollapse', data: 'COLLAPSE' }]))
      .toEqual({ borderCollapse: 'collapse' });
  });
  it('CaptionSide → bottom', () => {
    expect(applyTablePhase10([{ type: 'CaptionSide', data: 'BOTTOM' }]))
      .toEqual({ captionSide: 'bottom' });
  });
  it('EmptyCells → hide', () => {
    expect(applyTablePhase10([{ type: 'EmptyCells', data: 'HIDE' }]))
      .toEqual({ emptyCells: 'hide' });
  });
  it('TableLayout → fixed', () => {
    expect(applyTablePhase10([{ type: 'TableLayout', data: 'FIXED' }]))
      .toEqual({ tableLayout: 'fixed' });
  });
});
