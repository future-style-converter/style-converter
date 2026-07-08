// mathPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyMathPhase10 } from '../../../src/style/engine/math/_dispatch';

describe('applyMathPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyMathPhase10([])).toEqual({});
  });
  it('MathStyle → compact', () => {
    expect(applyMathPhase10([{ type: 'MathStyle', data: 'COMPACT' }]))
      .toEqual({ mathStyle: 'compact' });
  });
  it('MathShift → normal', () => {
    expect(applyMathPhase10([{ type: 'MathShift', data: 'NORMAL' }]))
      .toEqual({ mathShift: 'normal' });
  });
});
