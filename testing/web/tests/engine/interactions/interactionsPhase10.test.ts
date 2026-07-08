// interactionsPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyInteractionsPhase10 } from '../../../src/style/engine/interactions/_dispatch';

describe('applyInteractionsPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyInteractionsPhase10([])).toEqual({});
  });
  it('PointerEvents → none', () => {
    expect(applyInteractionsPhase10([{ type: 'PointerEvents', data: 'NONE' }]))
      .toEqual({ pointerEvents: 'none' });
  });
  it('UserSelect → none', () => {
    expect(applyInteractionsPhase10([{ type: 'UserSelect', data: 'NONE' }]))
      .toEqual({ userSelect: 'none' });
  });
  it('Resize → both', () => {
    expect(applyInteractionsPhase10([{ type: 'Resize', data: 'BOTH' }]))
      .toEqual({ resize: 'both' });
  });
  it('Interactivity → auto', () => {
    expect(applyInteractionsPhase10([{ type: 'Interactivity', data: 'AUTO' }]))
      .toEqual({ interactivity: 'auto' });
  });
  it('CaretShape → bar', () => {
    expect(applyInteractionsPhase10([{ type: 'CaretShape', data: 'BAR' }]))
      .toEqual({ caretShape: 'bar' });
  });
});
