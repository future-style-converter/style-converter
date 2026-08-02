// gapDecorations.test.ts — CSS Gap Decorations Level 1 web pass-through.
//
// Web recovery for this family is parser + emission only: Chromium paints
// gap decorations natively in flex containers, so the engine's whole job is
// to turn each IR leaf back into the CSS declaration the browser already
// understands. Every payload below is copied from a LIVE converter run of
// the wave-24 probe (row-rule / row-rule-* / *-rule-break / *-rule-inset /
// rule-overlap), so these tests pin the actual wire, not an invented shape.
import { describe, it, expect } from 'vitest';
import { applyColumnsPhase10 } from '../../src/engine/columns/_dispatch';
import { cssPropertyName } from '../../src/core/renderer/CssText';
import { migratedProperties } from '../../src/engine/PropertyRegistry';

// The eight IR type names the wave-24 wire contract froze.
const GAP_DECORATION_TYPES = [
  'RowRuleStyle', 'RowRuleWidth', 'RowRuleColor',
  'ColumnRuleBreak', 'RowRuleBreak',
  'ColumnRuleInset', 'RowRuleInset', 'RuleOverlap',
];

describe('gap decorations — registry + CSS naming', () => {
  it('every gap-decoration IR type is claimed by the web PropertyRegistry', () => {
    // Unclaimed types are invisible to coverage-audit.mjs and, worse, are
    // reported as unhandled by the runtime PropertyTracker.
    const missing = GAP_DECORATION_TYPES.filter((t) => !migratedProperties.has(t));
    expect(missing).toEqual([]);
  });

  it('camelCase applier keys re-spell as the draft-spec CSS names', () => {
    // The appliers emit React-style camelCase; CssText is the single place
    // that turns those into declaration text, so pin the round-trip here.
    expect(cssPropertyName('rowRuleColor')).toBe('row-rule-color');
    expect(cssPropertyName('columnRuleBreak')).toBe('column-rule-break');
    expect(cssPropertyName('rowRuleInset')).toBe('row-rule-inset');
    expect(cssPropertyName('ruleOverlap')).toBe('rule-overlap');
  });
});

describe('gap decorations — row-axis twins', () => {
  it('RowRuleStyle → row-rule-style keyword', () => {
    expect(applyColumnsPhase10([{ type: 'RowRuleStyle', data: 'SOLID' }]))
      .toEqual({ rowRuleStyle: 'solid' });
  });

  it('RowRuleWidth length and keyword forms both emit', () => {
    // {type:'length',px:5} — the deep-flattened sealed variant.
    expect(applyColumnsPhase10([{ type: 'RowRuleWidth', data: { type: 'length', px: 5 } }]))
      .toEqual({ rowRuleWidth: '5px' });
    // {type:'keyword',value:'THIN'} — kebabbed by the shared helper.
    expect(applyColumnsPhase10([{ type: 'RowRuleWidth', data: { type: 'keyword', value: 'THIN' } }]))
      .toEqual({ rowRuleWidth: 'thin' });
  });

  it('RowRuleColor emits the same CSS the ColumnRuleColor twin does', () => {
    // Identical IR payload on both axes must yield identical colour text —
    // that is the byte-identity the wave-24 contract is built on.
    const data = { srgb: { r: 1, g: 0.8431372549019608, b: 0 }, original: 'gold' };
    // csstype has no `rowRuleColor` key (css-gaps-1 is a draft), so index
    // through a widened record — the same cast the appliers themselves use.
    const row = applyColumnsPhase10([{ type: 'RowRuleColor', data }]) as Record<string, string>;
    const col = applyColumnsPhase10([{ type: 'ColumnRuleColor', data }]) as Record<string, string>;
    expect(row.rowRuleColor).toBe(col.columnRuleColor);
    expect(typeof row.rowRuleColor).toBe('string');
  });
});

describe('gap decorations — the four axis knobs', () => {
  it('break keywords kebab back to the CSS idents', () => {
    expect(applyColumnsPhase10([
      { type: 'ColumnRuleBreak', data: 'INTERSECTION' },
      { type: 'RowRuleBreak', data: 'SPANNING_ITEM' },
    ])).toEqual({ columnRuleBreak: 'intersection', rowRuleBreak: 'spanning-item' });
  });

  it('insets emit lengths and keep the sign', () => {
    // Bare {px:N} objects (no type wrapper) — negatives are legal per §6.
    expect(applyColumnsPhase10([
      { type: 'ColumnRuleInset', data: { px: -2 } },
      { type: 'RowRuleInset', data: { px: 0 } },
    ])).toEqual({ columnRuleInset: '-2px', rowRuleInset: '0px' });
  });

  it('percentage inset survives as a percentage', () => {
    // pixels was null in the IR (runtime-dependent), so only `original` is
    // present; the shared length helper re-spells it as -25%.
    expect(applyColumnsPhase10([
      { type: 'RowRuleInset', data: { original: { v: -25, u: 'PERCENT' } } },
    ])).toEqual({ rowRuleInset: '-25%' });
  });

  it('RuleOverlap emits the paint-order ident', () => {
    expect(applyColumnsPhase10([{ type: 'RuleOverlap', data: 'COLUMN_OVER_ROW' }]))
      .toEqual({ ruleOverlap: 'column-over-row' });
  });
});

describe('gap decorations — cascade + absence', () => {
  it('absent properties emit nothing (browser keeps the CSS initial value)', () => {
    // Regression guard: an applier that emitted a default would override a
    // stylesheet rule and silently change the rendering.
    expect(applyColumnsPhase10([{ type: 'ColumnRuleStyle', data: 'SOLID' }]))
      .toEqual({ columnRuleStyle: 'solid' });
  });

  it('last write wins across repeated declarations', () => {
    expect(applyColumnsPhase10([
      { type: 'RowRuleBreak', data: 'NORMAL' },
      { type: 'RowRuleBreak', data: 'INTERSECTION' },
    ])).toEqual({ rowRuleBreak: 'intersection' });
  });

  it('a full css-gaps container declaration round-trips in one pass', () => {
    // Shape lifted from the wave-24 probe conversion of a flex container
    // carrying both axes plus all four knobs.
    const out = applyColumnsPhase10([
      { type: 'ColumnRuleWidth', data: { type: 'length', px: 10 } },
      { type: 'ColumnRuleStyle', data: 'SOLID' },
      { type: 'RowRuleWidth', data: { type: 'length', px: 5 } },
      { type: 'RowRuleStyle', data: 'SOLID' },
      { type: 'ColumnRuleBreak', data: 'INTERSECTION' },
      { type: 'RowRuleBreak', data: 'NORMAL' },
      { type: 'ColumnRuleInset', data: { px: -2 } },
      { type: 'RowRuleInset', data: { px: 0 } },
      { type: 'RuleOverlap', data: 'COLUMN_OVER_ROW' },
    ]);
    expect(out).toEqual({
      columnRuleWidth: '10px', columnRuleStyle: 'solid',
      rowRuleWidth: '5px', rowRuleStyle: 'solid',
      columnRuleBreak: 'intersection', rowRuleBreak: 'normal',
      columnRuleInset: '-2px', rowRuleInset: '0px',
      ruleOverlap: 'column-over-row',
    });
  });
});
