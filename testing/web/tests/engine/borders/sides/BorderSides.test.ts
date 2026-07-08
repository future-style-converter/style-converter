// BorderSides — coverage for the 24 per-side Border*Width/Style/Color triplets.
// Fixtures sourced from examples/properties/borders/border-{widths,styles,colors}.json
// after ./gradlew run conversion; we assert the emitted CSS declaration matches
// what the browser would accept natively.
import { describe, it, expect } from 'vitest';
import { extractBorderTopWidth } from '../../../../src/style/engine/borders/sides/BorderTopWidthExtractor';
import { applyBorderTopWidth }   from '../../../../src/style/engine/borders/sides/BorderTopWidthApplier';
import { extractBorderRightWidth } from '../../../../src/style/engine/borders/sides/BorderRightWidthExtractor';
import { applyBorderRightWidth }   from '../../../../src/style/engine/borders/sides/BorderRightWidthApplier';
import { extractBorderBlockStartWidth } from '../../../../src/style/engine/borders/sides/BorderBlockStartWidthExtractor';
import { applyBorderBlockStartWidth }   from '../../../../src/style/engine/borders/sides/BorderBlockStartWidthApplier';
import { extractBorderInlineEndWidth } from '../../../../src/style/engine/borders/sides/BorderInlineEndWidthExtractor';
import { applyBorderInlineEndWidth }   from '../../../../src/style/engine/borders/sides/BorderInlineEndWidthApplier';
import { extractBorderTopStyle } from '../../../../src/style/engine/borders/sides/BorderTopStyleExtractor';
import { applyBorderTopStyle }   from '../../../../src/style/engine/borders/sides/BorderTopStyleApplier';
import { extractBorderBlockStartStyle } from '../../../../src/style/engine/borders/sides/BorderBlockStartStyleExtractor';
import { applyBorderBlockStartStyle }   from '../../../../src/style/engine/borders/sides/BorderBlockStartStyleApplier';
import { extractBorderTopColor } from '../../../../src/style/engine/borders/sides/BorderTopColorExtractor';
import { applyBorderTopColor }   from '../../../../src/style/engine/borders/sides/BorderTopColorApplier';
import { extractBorderInlineStartColor } from '../../../../src/style/engine/borders/sides/BorderInlineStartColorExtractor';
import { applyBorderInlineStartColor }   from '../../../../src/style/engine/borders/sides/BorderInlineStartColorApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('Border*Width', () => {
  it('resolves the thin/medium/thick keyword to px (parser pre-resolves)', () => {
    const cfg = extractBorderTopWidth([p('BorderTopWidth', { px: 1, original: 'thin' })]);
    expect(applyBorderTopWidth(cfg)).toEqual({ borderTopWidth: '1px' });
  });
  it('emits plain px', () => {
    expect(applyBorderTopWidth(extractBorderTopWidth([p('BorderTopWidth', { px: 8 })])))
      .toEqual({ borderTopWidth: '8px' });
  });
  it('emits zero', () => {
    expect(applyBorderTopWidth(extractBorderTopWidth([p('BorderTopWidth', { px: 0 })])))
      .toEqual({ borderTopWidth: '0px' });
  });
  it('emits em', () => {
    expect(applyBorderTopWidth(extractBorderTopWidth([
      p('BorderTopWidth', { original: { v: 0.5, u: 'EM' } }),
    ]))).toEqual({ borderTopWidth: '0.5em' });
  });
  it('emits rem', () => {
    expect(applyBorderTopWidth(extractBorderTopWidth([
      p('BorderTopWidth', { original: { v: 0.25, u: 'REM' } }),
    ]))).toEqual({ borderTopWidth: '0.25rem' });
  });
  it('emits calc()', () => {
    expect(applyBorderTopWidth(extractBorderTopWidth([
      p('BorderTopWidth', { expr: 'calc(2px + 4px)' }),
    ]))).toEqual({ borderTopWidth: 'calc(2px + 4px)' });
  });
  it('emits subpixel', () => {
    expect(applyBorderTopWidth(extractBorderTopWidth([p('BorderTopWidth', { px: 0.5 })])))
      .toEqual({ borderTopWidth: '0.5px' });
  });
  it('ignores unrelated types', () => {
    expect(extractBorderTopWidth([p('BorderLeftWidth', { px: 5 })])).toEqual({});
  });
  it('supports per-side mixing', () => {
    const props = [p('BorderTopWidth', { px: 2 }), p('BorderRightWidth', { px: 6 })];
    expect(applyBorderTopWidth(extractBorderTopWidth(props))).toEqual({ borderTopWidth: '2px' });
    expect(applyBorderRightWidth(extractBorderRightWidth(props))).toEqual({ borderRightWidth: '6px' });
  });
  it('emits native logical CSS keys (blockStart)', () => {
    expect(applyBorderBlockStartWidth(extractBorderBlockStartWidth([
      p('BorderBlockStartWidth', { px: 4 }),
    ]))).toEqual({ borderBlockStartWidth: '4px' });
  });
  it('emits native logical CSS keys (inlineEnd)', () => {
    expect(applyBorderInlineEndWidth(extractBorderInlineEndWidth([
      p('BorderInlineEndWidth', { px: 12 }),
    ]))).toEqual({ borderInlineEndWidth: '12px' });
  });
  it('empty when unset', () => {
    expect(applyBorderTopWidth({})).toEqual({});
  });
});

describe('Border*Style', () => {
  it.each(['solid', 'dashed', 'dotted', 'double', 'groove', 'ridge', 'inset', 'outset', 'none', 'hidden'])(
    'accepts %s keyword (parser emits UPPERCASE)',
    (kw) => {
      const cfg = extractBorderTopStyle([p('BorderTopStyle', kw.toUpperCase())]);
      expect(applyBorderTopStyle(cfg)).toEqual({ borderTopStyle: kw });
    },
  );
  it('rejects unknown keywords', () => {
    expect(applyBorderTopStyle(extractBorderTopStyle([p('BorderTopStyle', 'WAVY')]))).toEqual({});
  });
  it('emits logical blockStartStyle', () => {
    expect(applyBorderBlockStartStyle(extractBorderBlockStartStyle([
      p('BorderBlockStartStyle', 'DASHED'),
    ]))).toEqual({ borderBlockStartStyle: 'dashed' });
  });
});

describe('Border*Color', () => {
  it('emits static rgba() from sRGB', () => {
    expect(applyBorderTopColor(extractBorderTopColor([
      p('BorderTopColor', { srgb: { r: 1, g: 0.2, b: 0.4 }, original: '#ff3366' }),
    ]))).toEqual({ borderTopColor: 'rgba(255, 51, 102, 1)' });
  });
  it('preserves alpha', () => {
    expect(applyBorderTopColor(extractBorderTopColor([
      p('BorderTopColor', { srgb: { r: 1, g: 0.2, b: 0.4, a: 0.5 }, original: '#ff336680' }),
    ]))).toEqual({ borderTopColor: 'rgba(255, 51, 102, 0.5)' });
  });
  it('transparent -> zero-alpha black', () => {
    expect(applyBorderTopColor(extractBorderTopColor([p('BorderTopColor', 'transparent')])))
      .toEqual({ borderTopColor: 'rgba(0, 0, 0, 0)' });
  });
  it('currentColor is preserved as CSS', () => {
    expect(applyBorderTopColor(extractBorderTopColor([p('BorderTopColor', 'currentColor')])))
      .toEqual({ borderTopColor: 'currentColor' });
  });
  it('color-mix() is preserved', () => {
    expect(applyBorderTopColor(extractBorderTopColor([
      p('BorderTopColor', { original: { type: 'color-mix', colorSpace: 'srgb', color1: 'red', color2: 'blue', percent1: 50 } }),
    ]))).toEqual({ borderTopColor: 'color-mix(in srgb, red 50%, blue)' });
  });
  it('emits logical inlineStartColor', () => {
    expect(applyBorderInlineStartColor(extractBorderInlineStartColor([
      p('BorderInlineStartColor', { srgb: { r: 0, g: 0, b: 0 }, original: '#000' }),
    ]))).toEqual({ borderInlineStartColor: 'rgba(0, 0, 0, 1)' });
  });
});
