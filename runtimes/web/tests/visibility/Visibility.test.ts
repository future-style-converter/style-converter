// Visibility.test.ts — Phase-8 visibility + overflow coverage.

import { describe, it, expect } from 'vitest';
import { applyVisibilityPhase8 } from '../../src/engine/visibility/_dispatch';

const p = (type: string, data: unknown) => ({ type, data });

describe('applyVisibilityPhase8', () => {
  it('empty → {}', () => {
    expect(applyVisibilityPhase8([])).toEqual({});
  });
  it('Visibility VISIBLE', () => {
    expect(applyVisibilityPhase8([p('Visibility', 'VISIBLE')])).toEqual({ visibility: 'visible' });
  });
  it('Visibility COLLAPSE', () => {
    expect(applyVisibilityPhase8([p('Visibility', 'COLLAPSE')])).toEqual({ visibility: 'collapse' });
  });
  it('OverflowX SCROLL + OverflowY AUTO', () => {
    expect(applyVisibilityPhase8([p('OverflowX', 'SCROLL'), p('OverflowY', 'AUTO')]))
      .toEqual({ overflowX: 'scroll', overflowY: 'auto' });
  });
  it('OverflowBlock + OverflowInline (logical)', () => {
    expect(applyVisibilityPhase8([p('OverflowBlock', 'HIDDEN'), p('OverflowInline', 'AUTO')]))
      .toEqual({ overflowBlock: 'hidden', overflowInline: 'auto' });
  });
  // The `overflow` SHORTHAND is deliberately NOT exercised: css-overflow-3
  // defines it as a shorthand for overflow-x/-y and ShorthandRegistry.kt:88
  // expands it, so no `Overflow` IR type can ever reach this dispatch. The
  // two cases that used to synthesise one tested a code path the converter
  // cannot produce (A6#9). Instead pin that an unknown/never-emitted type is
  // ignored rather than leaking a bogus declaration.
  it('an IR type the converter cannot emit (the expanded `Overflow` shorthand) is ignored', () => {
    expect(applyVisibilityPhase8([p('Overflow', 'HIDDEN')])).toEqual({});
  });
  it('last-write-wins for Visibility', () => {
    const r = applyVisibilityPhase8([p('Visibility', 'HIDDEN'), p('Visibility', 'VISIBLE')]);
    expect(r.visibility).toBe('visible');
  });
});
