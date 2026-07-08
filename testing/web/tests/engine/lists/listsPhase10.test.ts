// listsPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyListsPhase10 } from '../../../src/style/engine/lists/_dispatch';

describe('applyListsPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyListsPhase10([])).toEqual({});
  });
  it('ListStylePosition → inside', () => {
    expect(applyListsPhase10([{ type: 'ListStylePosition', data: 'INSIDE' }]))
      .toEqual({ listStylePosition: 'inside' });
  });
});
