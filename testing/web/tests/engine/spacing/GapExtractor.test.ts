// Tests for GapExtractor & GapApplier — covers RowGap/ColumnGap/Gap shorthand.
import { describe, it, expect } from 'vitest';
import { extractGap } from '../../../src/style/engine/spacing/GapExtractor';
import { applyGap } from '../../../src/style/engine/spacing/GapApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('extractGap', () => {
  it('handles RowGap + ColumnGap (post-expansion from gap: 10px)', () => {
    const cfg = extractGap([
      p('RowGap',    { type: 'length', px: 10 }),
      p('ColumnGap', { type: 'length', px: 10 }),
    ]);
    expect(cfg.rowGap).toEqual({ kind: 'exact', px: 10 });
    expect(cfg.columnGap).toEqual({ kind: 'exact', px: 10 });
  });

  it('handles different row vs column gap (gap: 10px 40px)', () => {
    const cfg = extractGap([
      p('RowGap',    { type: 'length', px: 10 }),
      p('ColumnGap', { type: 'length', px: 40 }),
    ]);
    expect(cfg.rowGap?.kind).toBe('exact');
    expect(cfg.columnGap).toEqual({ kind: 'exact', px: 40 });
  });

  it('handles only ColumnGap (grid column-gap only)', () => {
    const cfg = extractGap([p('ColumnGap', { type: 'length', px: 16 })]);
    expect(cfg.rowGap).toBeUndefined();
    expect(cfg.columnGap).toEqual({ kind: 'exact', px: 16 });
  });

  it('handles percentage gap', () => {
    const cfg = extractGap([p('ColumnGap', { type: 'percentage', value: 5 })]);
    expect(cfg.columnGap).toEqual({ kind: 'relative', value: 5, unit: 'percent' });
  });

  it('handles Gap shorthand (if un-expanded) by copying to both axes', () => {
    const cfg = extractGap([p('Gap', { px: 8 })]);
    expect(cfg.rowGap).toEqual({ kind: 'exact', px: 8 });
    expect(cfg.columnGap).toEqual({ kind: 'exact', px: 8 });
  });
});

describe('applyGap', () => {
  it('collapses equal axes into gap shorthand', () => {
    const css = applyGap({
      rowGap:    { kind: 'exact', px: 10 },
      columnGap: { kind: 'exact', px: 10 },
    });
    expect(css).toEqual({ gap: '10px' });
  });

  it('emits two longhands when axes differ', () => {
    const css = applyGap({
      rowGap:    { kind: 'exact', px: 10 },
      columnGap: { kind: 'exact', px: 40 },
    });
    expect(css).toEqual({ rowGap: '10px', columnGap: '40px' });
  });

  it('emits only the set axis', () => {
    const css = applyGap({ columnGap: { kind: 'exact', px: 16 } });
    expect(css).toEqual({ columnGap: '16px' });
  });
});
