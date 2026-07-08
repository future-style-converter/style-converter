// columnsPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyColumnsPhase10 } from '../../../src/style/engine/columns/_dispatch';

describe('applyColumnsPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyColumnsPhase10([])).toEqual({});
  });
  it('ColumnRuleStyle → dashed', () => {
    expect(applyColumnsPhase10([{ type: 'ColumnRuleStyle', data: 'DASHED' }]))
      .toEqual({ columnRuleStyle: 'dashed' });
  });
  it('ColumnSpan → all', () => {
    expect(applyColumnsPhase10([{ type: 'ColumnSpan', data: 'ALL' }]))
      .toEqual({ columnSpan: 'all' });
  });
  it('ColumnFill → balance', () => {
    expect(applyColumnsPhase10([{ type: 'ColumnFill', data: 'BALANCE' }]))
      .toEqual({ columnFill: 'balance' });
  });
});
