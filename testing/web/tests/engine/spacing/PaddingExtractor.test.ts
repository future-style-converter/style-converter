// Tests for PaddingExtractor — covers every IR shape seen in the
// padding-* fixtures under examples/properties/spacing/.
import { describe, it, expect } from 'vitest';
import { extractPadding } from '../../../src/style/engine/spacing/PaddingExtractor';
import { applyPadding } from '../../../src/style/engine/spacing/PaddingApplier';

// Helper to wrap data into IRProperty-ish record.
const p = (type: string, data: unknown) => ({ type, data });

describe('extractPadding', () => {
  it('handles raw {px:N} shape (absolute px)', () => {
    const cfg = extractPadding([p('PaddingTop', { px: 20 })]);
    expect(cfg.top).toEqual({ kind: 'exact', px: 20 });
  });

  it('handles {type:"length", px:N} wrapper', () => {
    const cfg = extractPadding([p('PaddingRight', { type: 'length', px: 13.333 })]);
    expect(cfg.right).toEqual({ kind: 'exact', px: 13.333 });
  });

  it('handles em (no px) as runtime-dependent relative length', () => {
    const cfg = extractPadding([p('PaddingBottom', { original: { v: 2, u: 'EM' } })]);
    expect(cfg.bottom).toEqual({ kind: 'relative', value: 2, unit: 'em' });
  });

  it('handles {type:"percentage", value:N}', () => {
    const cfg = extractPadding([p('PaddingLeft', { type: 'percentage', value: 10 })]);
    expect(cfg.left).toEqual({ kind: 'relative', value: 10, unit: 'percent' });
  });

  it('handles bare number as percentage (Phase-2 convention)', () => {
    const cfg = extractPadding([p('PaddingTop', 10)]);
    expect(cfg.top).toEqual({ kind: 'relative', value: 10, unit: 'percent' });
  });

  it('handles {expr:"calc(...)"} shape', () => {
    const cfg = extractPadding([p('PaddingRight', { expr: 'calc(10px + 5px)' })]);
    expect(cfg.right).toEqual({ kind: 'calc', expression: '10px + 5px' });
  });

  it('handles all logical sides', () => {
    const cfg = extractPadding([
      p('PaddingBlockStart',  { px: 1 }),
      p('PaddingBlockEnd',    { px: 2 }),
      p('PaddingInlineStart', { px: 3 }),
      p('PaddingInlineEnd',   { px: 4 }),
    ]);
    expect(cfg.blockStart?.kind).toBe('exact');
    expect(cfg.inlineEnd).toEqual({ kind: 'exact', px: 4 });
  });

  it('drops {kind:"auto"} for padding (CSS disallows it)', () => {
    const cfg = extractPadding([p('PaddingTop', 'auto')]);
    expect(cfg.top).toBeUndefined();
  });

  it('ignores non-padding IR properties', () => {
    const cfg = extractPadding([p('MarginTop', { px: 10 }), p('Color', 'red')]);
    expect(cfg).toEqual({});
  });
});

describe('applyPadding', () => {
  it('emits canonical CSS pixel values', () => {
    const css = applyPadding({ top: { kind: 'exact', px: 20 }, right: { kind: 'exact', px: 10 } });
    expect(css).toEqual({ paddingTop: '20px', paddingRight: '10px' });
  });

  it('emits percent and em as-is for CSS to resolve', () => {
    const css = applyPadding({
      top:    { kind: 'relative', value: 10, unit: 'percent' },
      bottom: { kind: 'relative', value: 2,  unit: 'em' },
    });
    expect(css.paddingTop).toBe('10%');
    expect(css.paddingBottom).toBe('2em');
  });

  it('emits logical sides unchanged', () => {
    const css = applyPadding({ inlineStart: { kind: 'exact', px: 8 } });
    expect(css).toEqual({ paddingInlineStart: '8px' });
  });
});
