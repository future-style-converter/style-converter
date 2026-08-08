// renderingPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyRenderingPhase10 } from '../../src/engine/rendering/_dispatch';

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

// wave-36 lane M2 — `zoom` stopped being a deliberate no-op. Every branch
// is pinned against the exact IR bytes ZoomPropertyParser.kt emits (see
// ZoomExtractor.ts's variant table), because the percentage branch used to
// come out of the shared keywordOrRaw fold as a bare "150" — a 150× zoom.
describe('applyRenderingPhase10 — Zoom', () => {
  it('number → the bare number token', () => {
    expect(applyRenderingPhase10([{ type: 'Zoom', data: { type: 'number', value: 2 } }]))
      .toEqual({ zoom: '2' });
  });
  it('fractional number keeps its precision', () => {
    expect(applyRenderingPhase10([{ type: 'Zoom', data: { type: 'number', value: 0.75 } }]))
      .toEqual({ zoom: '0.75' });
  });
  it('percentage KEEPS its % suffix (150% is 1.5×, not 150×)', () => {
    expect(applyRenderingPhase10([{ type: 'Zoom', data: { type: 'percentage', value: 150 } }]))
      .toEqual({ zoom: '150%' });
  });
  it('normal → normal', () => {
    expect(applyRenderingPhase10([{ type: 'Zoom', data: { type: 'normal' } }]))
      .toEqual({ zoom: 'normal' });
  });
  it('reset → reset (verbatim legacy keyword, never rewritten to normal)', () => {
    expect(applyRenderingPhase10([{ type: 'Zoom', data: { type: 'reset' } }]))
      .toEqual({ zoom: 'reset' });
  });
  it('last write wins across repeated Zoom properties', () => {
    expect(applyRenderingPhase10([
      { type: 'Zoom', data: { type: 'number', value: 2 } },
      { type: 'Zoom', data: { type: 'percentage', value: 50 } },
    ])).toEqual({ zoom: '50%' });
  });
  it('unmodelled payload emits nothing (and is tracked, not silent)', () => {
    expect(applyRenderingPhase10([{ type: 'Zoom', data: { type: 'future-variant' } }]))
      .toEqual({});
  });
  it('no Zoom property → no zoom declaration', () => {
    expect(applyRenderingPhase10([{ type: 'FieldSizing', data: 'CONTENT' }]))
      .toEqual({ fieldSizing: 'content' });
  });
});
