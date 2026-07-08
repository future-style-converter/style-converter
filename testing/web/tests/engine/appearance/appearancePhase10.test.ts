// appearancePhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyAppearancePhase10 } from '../../../src/style/engine/appearance/_dispatch';

describe('applyAppearancePhase10', () => {
  it('empty input → empty output', () => {
    expect(applyAppearancePhase10([])).toEqual({});
  });
  it('Appearance → none', () => {
    expect(applyAppearancePhase10([{ type: 'Appearance', data: 'NONE' }]))
      .toEqual({ appearance: 'none' });
  });
  it('AppearanceVariant → auto', () => {
    expect(applyAppearancePhase10([{ type: 'AppearanceVariant', data: 'AUTO' }]))
      .toEqual({ appearanceVariant: 'auto' });
  });
  it('ColorAdjust → economy', () => {
    expect(applyAppearancePhase10([{ type: 'ColorAdjust', data: 'ECONOMY' }]))
      .toEqual({ colorAdjust: 'economy' });
  });
});
