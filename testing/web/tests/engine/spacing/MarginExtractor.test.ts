// Tests for MarginExtractor — covers every IR shape from the margin fixtures.
import { describe, it, expect } from 'vitest';
import { extractMargin } from '../../../src/style/engine/spacing/MarginExtractor';
import { applyMargin } from '../../../src/style/engine/spacing/MarginApplier';

// Helper to wrap data into IRProperty-ish record.
const p = (type: string, data: unknown) => ({ type, data });

describe('extractMargin', () => {
  it('handles absolute px margin', () => {
    const cfg = extractMargin([p('MarginTop', { px: 20 })]);
    expect(cfg.top).toEqual({ kind: 'exact', px: 20 });
  });

  it('handles negative px margin (shifts layout)', () => {
    const cfg = extractMargin([p('MarginLeft', { px: -10 })]);
    expect(cfg.left).toEqual({ kind: 'exact', px: -10 });
  });

  it('handles bare "auto" string (flex centering)', () => {
    const cfg = extractMargin([p('MarginLeft', 'auto'), p('MarginRight', 'auto')]);
    expect(cfg.left).toEqual({ kind: 'auto' });
    expect(cfg.right).toEqual({ kind: 'auto' });
  });

  it('handles mixed auto + px (margin: auto 0 scenario)', () => {
    const cfg = extractMargin([
      p('MarginTop', 'auto'), p('MarginRight', { px: 0 }),
      p('MarginBottom', 'auto'), p('MarginLeft', { px: 0 }),
    ]);
    expect(cfg.top).toEqual({ kind: 'auto' });
    expect(cfg.right).toEqual({ kind: 'exact', px: 0 });
  });

  it('handles bare number as percentage (Margin_Percent_10 fixture)', () => {
    const cfg = extractMargin([p('MarginTop', 10)]);
    expect(cfg.top).toEqual({ kind: 'relative', value: 10, unit: 'percent' });
  });

  it('handles em margin (runtime-dependent)', () => {
    const cfg = extractMargin([p('MarginRight', { original: { v: 2, u: 'EM' } })]);
    expect(cfg.right).toEqual({ kind: 'relative', value: 2, unit: 'em' });
  });

  it('handles all logical margin sides', () => {
    const cfg = extractMargin([
      p('MarginBlockStart',  { px: 1 }),
      p('MarginInlineEnd',   'auto'),
    ]);
    expect(cfg.blockStart?.kind).toBe('exact');
    expect(cfg.inlineEnd).toEqual({ kind: 'auto' });
  });

  it('ignores non-margin IR properties', () => {
    const cfg = extractMargin([p('PaddingTop', { px: 10 })]);
    expect(cfg).toEqual({});
  });
});

describe('applyMargin', () => {
  it('emits auto keyword verbatim', () => {
    const css = applyMargin({ left: { kind: 'auto' }, right: { kind: 'auto' } });
    expect(css).toEqual({ marginLeft: 'auto', marginRight: 'auto' });
  });

  it('emits negative px', () => {
    const css = applyMargin({ top: { kind: 'exact', px: -10 } });
    expect(css).toEqual({ marginTop: '-10px' });
  });

  it('emits percent and logical sides', () => {
    const css = applyMargin({
      top: { kind: 'relative', value: 10, unit: 'percent' },
      blockEnd: { kind: 'exact', px: 4 },
    });
    expect(css.marginTop).toBe('10%');
    expect(css.marginBlockEnd).toBe('4px');
  });
});
