// pagingPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyPagingPhase10 } from '../../../src/style/engine/paging/_dispatch';

describe('applyPagingPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyPagingPhase10([])).toEqual({});
  });
  it('BreakBefore → page', () => {
    expect(applyPagingPhase10([{ type: 'BreakBefore', data: 'PAGE' }]))
      .toEqual({ breakBefore: 'page' });
  });
  it('BreakAfter → column', () => {
    expect(applyPagingPhase10([{ type: 'BreakAfter', data: 'COLUMN' }]))
      .toEqual({ breakAfter: 'column' });
  });
  it('BreakInside → avoid', () => {
    expect(applyPagingPhase10([{ type: 'BreakInside', data: 'AVOID' }]))
      .toEqual({ breakInside: 'avoid' });
  });
  it('PageBreakBefore → always', () => {
    expect(applyPagingPhase10([{ type: 'PageBreakBefore', data: 'ALWAYS' }]))
      .toEqual({ pageBreakBefore: 'always' });
  });
  it('PageBreakInside → avoid', () => {
    expect(applyPagingPhase10([{ type: 'PageBreakInside', data: 'AVOID' }]))
      .toEqual({ pageBreakInside: 'avoid' });
  });
  it('MarginBreak → auto', () => {
    expect(applyPagingPhase10([{ type: 'MarginBreak', data: 'AUTO' }]))
      .toEqual({ marginBreak: 'auto' });
  });
});
