// appearancePhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyAppearancePhase10 } from '../../src/engine/appearance/_dispatch';

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

// wave-36 lane M2 — the sixteen tag-only widget keywords of AppearanceValue.
// Fourteen of them used to fall off the shared keywordOrRaw allow-list
// (none/auto/normal) and vanish; `menulist-button` in particular is the
// value a styled <select> degrades to per css-ui-4 §appearance-switching.
describe('applyAppearancePhase10 — Appearance keyword coverage', () => {
  const KEYWORDS = [
    'none', 'auto', 'button', 'checkbox', 'listbox', 'menulist',
    'menulist-button', 'meter', 'progress-bar', 'push-button', 'radio',
    'searchfield', 'slider-horizontal', 'square-button', 'textarea', 'textfield',
  ];
  for (const kw of KEYWORDS) {
    it(`tag-only variant {type:'${kw}'} → appearance: ${kw}`, () => {
      expect(applyAppearancePhase10([{ type: 'Appearance', data: { type: kw } }]))
        .toEqual({ appearance: kw });
    });
  }
  it('CSS-wide keyword variant passes through', () => {
    expect(applyAppearancePhase10([{ type: 'Appearance', data: { type: 'keyword', keyword: 'revert' } }]))
      .toEqual({ appearance: 'revert' });
  });
  it('raw variant is VERBATIM — var() custom names are case-sensitive', () => {
    expect(applyAppearancePhase10([{ type: 'Appearance', data: { type: 'raw', value: 'var(--Foo)' } }]))
      .toEqual({ appearance: 'var(--Foo)' });
  });
  it('last write wins', () => {
    expect(applyAppearancePhase10([
      { type: 'Appearance', data: { type: 'auto' } },
      { type: 'Appearance', data: { type: 'menulist-button' } },
    ])).toEqual({ appearance: 'menulist-button' });
  });
  it('unmodelled payload emits nothing (tracked, not silent)', () => {
    expect(applyAppearancePhase10([{ type: 'Appearance', data: { type: 'base-select' } }]))
      .toEqual({});
  });
});
