// renderingPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyRenderingPhase10 } from '../../../src/style/engine/rendering/_dispatch';

describe('applyRenderingPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyRenderingPhase10([])).toEqual({});
  });
  it('ContentVisibility → auto', () => {
    expect(applyRenderingPhase10([{ type: 'ContentVisibility', data: 'AUTO' }]))
      .toEqual({ contentVisibility: 'auto' });
  });
  it('FieldSizing → content', () => {
    expect(applyRenderingPhase10([{ type: 'FieldSizing', data: 'CONTENT' }]))
      .toEqual({ fieldSizing: 'content' });
  });
  it('ForcedColorAdjust → auto', () => {
    expect(applyRenderingPhase10([{ type: 'ForcedColorAdjust', data: 'AUTO' }]))
      .toEqual({ forcedColorAdjust: 'auto' });
  });
  it('PrintColorAdjust → exact', () => {
    expect(applyRenderingPhase10([{ type: 'PrintColorAdjust', data: 'EXACT' }]))
      .toEqual({ printColorAdjust: 'exact' });
  });
  it('InputSecurity → none', () => {
    expect(applyRenderingPhase10([{ type: 'InputSecurity', data: 'NONE' }]))
      .toEqual({ inputSecurity: 'none' });
  });
  it('InterpolateSize → allow-keywords', () => {
    expect(applyRenderingPhase10([{ type: 'InterpolateSize', data: 'ALLOW_KEYWORDS' }]))
      .toEqual({ interpolateSize: 'allow-keywords' });
  });
});
