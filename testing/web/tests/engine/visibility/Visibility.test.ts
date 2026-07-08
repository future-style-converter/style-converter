// Visibility.test.ts — Phase-8 visibility + overflow coverage.

import { describe, it, expect } from 'vitest';
import { applyVisibilityPhase8 } from '../../../src/style/engine/visibility/_dispatch';

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
  it('Overflow HIDDEN', () => {
    expect(applyVisibilityPhase8([p('Overflow', 'HIDDEN')])).toEqual({ overflow: 'hidden' });
  });
  it('OverflowX SCROLL + OverflowY AUTO', () => {
    expect(applyVisibilityPhase8([p('OverflowX', 'SCROLL'), p('OverflowY', 'AUTO')]))
      .toEqual({ overflowX: 'scroll', overflowY: 'auto' });
  });
  it('OverflowBlock + OverflowInline (logical)', () => {
    expect(applyVisibilityPhase8([p('OverflowBlock', 'HIDDEN'), p('OverflowInline', 'AUTO')]))
      .toEqual({ overflowBlock: 'hidden', overflowInline: 'auto' });
  });
  it('Overflow CLIP', () => {
    expect(applyVisibilityPhase8([p('Overflow', 'CLIP')])).toEqual({ overflow: 'clip' });
  });
  it('last-write-wins for Visibility', () => {
    const r = applyVisibilityPhase8([p('Visibility', 'HIDDEN'), p('Visibility', 'VISIBLE')]);
    expect(r.visibility).toBe('visible');
  });
});
