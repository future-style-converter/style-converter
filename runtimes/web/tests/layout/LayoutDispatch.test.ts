// LayoutDispatch.test.ts — Phase-7 tripwire coverage.
// Asserts the _dispatch.ts wiring actually folds IR properties into CSS and
// that the Phase 7 migration reaches StyleBuilder.  Exhaustive per-property
// coverage lives (or will live) next to each triplet; these tests exist so
// any accidental removal of a dispatch-table row trips immediately.

import { describe, it, expect } from 'vitest';
import { applyLayoutPhase7 } from '../../src/engine/layout/_dispatch';

describe('applyLayoutPhase7', () => {
  it('returns an empty object for no layout properties', () => {
    expect(applyLayoutPhase7([])).toEqual({});
  });

  it('maps Display to display', () => {
    expect(applyLayoutPhase7([{ type: 'Display', data: 'flex' }]))
      .toEqual({ display: 'flex' });
  });

  it('kebab-cases enum keywords from SHOUTY_SNAKE IR', () => {
    expect(applyLayoutPhase7([{ type: 'Display', data: 'INLINE_BLOCK' }]))
      .toEqual({ display: 'inline-block' });
  });

  it('maps FlexDirection', () => {
    expect(applyLayoutPhase7([{ type: 'FlexDirection', data: 'row-reverse' }]))
      .toEqual({ flexDirection: 'row-reverse' });
  });

  it('maps FlexWrap', () => {
    expect(applyLayoutPhase7([{ type: 'FlexWrap', data: 'wrap' }]))
      .toEqual({ flexWrap: 'wrap' });
  });

  it('maps JustifyContent', () => {
    expect(applyLayoutPhase7([{ type: 'JustifyContent', data: 'SPACE_BETWEEN' }]))
      .toEqual({ justifyContent: 'space-between' });
  });

  it('maps AlignItems', () => {
    expect(applyLayoutPhase7([{ type: 'AlignItems', data: 'center' }]))
      .toEqual({ alignItems: 'center' });
  });

  it('maps GridTemplateColumns for a simple fr-unit track list', () => {
    const r = applyLayoutPhase7([
      { type: 'GridTemplateColumns', data: [{ fr: 1 }, { fr: 2 }, { fr: 1 }] },
    ]);
    expect(r).toEqual({ gridTemplateColumns: '1fr 2fr 1fr' });
  });

  it('maps Position', () => {
    expect(applyLayoutPhase7([{ type: 'Position', data: 'absolute' }]))
      .toEqual({ position: 'absolute' });
  });

  it('maps Top as px length', () => {
    expect(applyLayoutPhase7([{ type: 'Top', data: { px: 12 } }]))
      .toEqual({ top: '12px' });
  });

  it('maps Left with bare-number-as-percentage', () => {
    expect(applyLayoutPhase7([{ type: 'Left', data: 50 }]))
      .toEqual({ left: '50%' });
  });

  it('maps ZIndex as integer', () => {
    expect(applyLayoutPhase7([{ type: 'ZIndex', data: 10 }]))
      .toEqual({ zIndex: 10 });
  });

  it('maps Clear and Float root keywords', () => {
    const r = applyLayoutPhase7([
      { type: 'Clear', data: 'both' },
      { type: 'Float', data: 'inline-start' },
    ]);
    expect(r.clear).toBe('both');
    expect(r.float).toBe('inline-start');
  });

  it('merges multiple property types in one call', () => {
    const r = applyLayoutPhase7([
      { type: 'Display', data: 'grid' },
      { type: 'JustifyContent', data: 'center' },
      { type: 'Top', data: { px: 4 } },
    ]);
    expect(r).toEqual({ display: 'grid', justifyContent: 'center', top: '4px' });
  });

  it('last-write-wins when the same property appears twice', () => {
    const r = applyLayoutPhase7([
      { type: 'Display', data: 'block' },
      { type: 'Display', data: 'flex' },
    ]);
    expect(r).toEqual({ display: 'flex' });
  });

  // CSS Anchor Positioning Level 1 §6 — `align-self: anchor-center` MUST
  // collapse to `center` when there is no default anchor in scope.  SC has no
  // anchor-positioning runtime today, so the fold is unconditional in the
  // AlignSelfApplier.  This test pins the spec behaviour: the IR enum value
  // ANCHOR_CENTER kebab-cases to 'anchor-center', and the applier replaces it
  // with the literal CSS keyword 'center'.  Mirrors the equivalent fold on
  // Android (FlexExtractor.parseAlignSelf) and iOS (FlexboxExtractor.mapAlignment).
  it('AlignSelf ANCHOR_CENTER folds to center per spec', () => {
    expect(applyLayoutPhase7([{ type: 'AlignSelf', data: 'ANCHOR_CENTER' }]))
      .toEqual({ alignSelf: 'center' });
  });
});
