// printPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyPrintPhase10 } from '../../../src/style/engine/print/_dispatch';

describe('applyPrintPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyPrintPhase10([])).toEqual({});
  });
  it('BookmarkState → open', () => {
    expect(applyPrintPhase10([{ type: 'BookmarkState', data: 'OPEN' }]))
      .toEqual({ bookmarkState: 'open' });
  });
  it('FootnoteDisplay → block', () => {
    expect(applyPrintPhase10([{ type: 'FootnoteDisplay', data: 'BLOCK' }]))
      .toEqual({ footnoteDisplay: 'block' });
  });
  it('FootnotePolicy → auto', () => {
    expect(applyPrintPhase10([{ type: 'FootnotePolicy', data: 'AUTO' }]))
      .toEqual({ footnotePolicy: 'auto' });
  });
});
