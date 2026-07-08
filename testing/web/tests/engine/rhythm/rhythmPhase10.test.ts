// rhythmPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyRhythmPhase10 } from '../../../src/style/engine/rhythm/_dispatch';

describe('applyRhythmPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyRhythmPhase10([])).toEqual({});
  });
  it('BlockStepAlign → center', () => {
    expect(applyRhythmPhase10([{ type: 'BlockStepAlign', data: 'CENTER' }]))
      .toEqual({ blockStepAlign: 'center' });
  });
  it('BlockStepInsert → margin-box', () => {
    expect(applyRhythmPhase10([{ type: 'BlockStepInsert', data: 'MARGIN_BOX' }]))
      .toEqual({ blockStepInsert: 'margin-box' });
  });
  it('BlockStepRound → up', () => {
    expect(applyRhythmPhase10([{ type: 'BlockStepRound', data: 'UP' }]))
      .toEqual({ blockStepRound: 'up' });
  });
});
