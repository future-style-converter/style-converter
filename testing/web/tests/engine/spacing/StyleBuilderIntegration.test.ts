// Integration tests — end-to-end buildStyles invocation with real fixture IR
// shapes, ensuring the engine path is correctly wired into StyleBuilder.
import { describe, it, expect } from 'vitest';
import { buildStyles } from '../../../src/style/core/renderer/StyleBuilder';

describe('buildStyles (spacing engine integration)', () => {
  it('emits padding in px from raw {px:N}', () => {
    const css = buildStyles([
      { type: 'PaddingTop',    data: { px: 20 } },
      { type: 'PaddingRight',  data: { px: 20 } },
      { type: 'PaddingBottom', data: { px: 20 } },
      { type: 'PaddingLeft',   data: { px: 20 } },
    ]);
    expect(css.paddingTop).toBe('20px');
    expect(css.paddingLeft).toBe('20px');
  });

  it('preserves em (runtime-dependent) padding', () => {
    const css = buildStyles([
      { type: 'PaddingTop', data: { original: { v: 2, u: 'EM' } } },
    ]);
    expect(css.paddingTop).toBe('2em');
  });

  it('handles bare-number margin as percentage (Margin_Percent_10 fixture)', () => {
    const css = buildStyles([
      { type: 'MarginTop',   data: 10 },
      { type: 'MarginRight', data: 10 },
    ]);
    expect(css.marginTop).toBe('10%');
    expect(css.marginRight).toBe('10%');
  });

  it('handles bare "auto" margin (flex centering)', () => {
    const css = buildStyles([
      { type: 'MarginLeft',  data: 'auto' },
      { type: 'MarginRight', data: 'auto' },
    ]);
    expect(css.marginLeft).toBe('auto');
    expect(css.marginRight).toBe('auto');
  });

  it('collapses matched rowGap/columnGap into gap shorthand', () => {
    const css = buildStyles([
      { type: 'RowGap',    data: { type: 'length', px: 10 } },
      { type: 'ColumnGap', data: { type: 'length', px: 10 } },
    ]);
    expect(css.gap).toBe('10px');
    expect(css.rowGap).toBeUndefined();
    expect(css.columnGap).toBeUndefined();
  });

  it('preserves differing row vs column gap', () => {
    const css = buildStyles([
      { type: 'RowGap',    data: { type: 'length', px: 10 } },
      { type: 'ColumnGap', data: { type: 'length', px: 40 } },
    ]);
    expect(css.gap).toBeUndefined();
    expect(css.rowGap).toBe('10px');
    expect(css.columnGap).toBe('40px');
  });

  it('emits margin-trim CSS from MarginTrim keyword', () => {
    const css = buildStyles([
      { type: 'MarginTrim', data: 'BLOCK_START' },
    ]);
    // marginTrim is a non-standard React key; accessed via index lookup.
    expect((css as Record<string, unknown>).marginTrim).toBe('block-start');
  });

  it('does not double-apply: migrated spacing props skip legacy switch', () => {
    // If the legacy path still ran for MarginTop, it would overwrite `10px`
    // with the string-returning legacy extractLength; assert engine wins.
    const css = buildStyles([
      { type: 'MarginTop', data: { px: 15 } },
    ]);
    expect(css.marginTop).toBe('15px');
  });
});
