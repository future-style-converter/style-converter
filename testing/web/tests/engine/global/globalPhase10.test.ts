// globalPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyGlobalPhase10 } from '../../../src/style/engine/global/_dispatch';

describe('applyGlobalPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyGlobalPhase10([])).toEqual({});
  });
  it('All → unset', () => {
    expect(applyGlobalPhase10([{ type: 'All', data: 'UNSET' }]))
      .toEqual({ all: 'unset' });
  });
});
