// Outline — coverage for the 4 Outline* triplets.
// Fixture: examples/properties/borders/outline.json.
import { describe, it, expect } from 'vitest';
import { extractOutlineWidth }  from '../../../../src/style/engine/borders/outline/OutlineWidthExtractor';
import { applyOutlineWidth }    from '../../../../src/style/engine/borders/outline/OutlineWidthApplier';
import { extractOutlineStyle }  from '../../../../src/style/engine/borders/outline/OutlineStyleExtractor';
import { applyOutlineStyle }    from '../../../../src/style/engine/borders/outline/OutlineStyleApplier';
import { extractOutlineColor }  from '../../../../src/style/engine/borders/outline/OutlineColorExtractor';
import { applyOutlineColor }    from '../../../../src/style/engine/borders/outline/OutlineColorApplier';
import { extractOutlineOffset } from '../../../../src/style/engine/borders/outline/OutlineOffsetExtractor';
import { applyOutlineOffset }   from '../../../../src/style/engine/borders/outline/OutlineOffsetApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('OutlineWidth', () => {
  it('thin → 1px (CSS UI §4.3.1 default)', () => {
    expect(applyOutlineWidth(extractOutlineWidth([
      p('OutlineWidth', { type: 'keyword', value: 'THIN' }),
    ]))).toEqual({ outlineWidth: '1px' });
  });
  it('medium → 3px', () => {
    expect(applyOutlineWidth(extractOutlineWidth([
      p('OutlineWidth', { type: 'keyword', value: 'MEDIUM' }),
    ]))).toEqual({ outlineWidth: '3px' });
  });
  it('thick → 5px', () => {
    expect(applyOutlineWidth(extractOutlineWidth([
      p('OutlineWidth', { type: 'keyword', value: 'THICK' }),
    ]))).toEqual({ outlineWidth: '5px' });
  });
  it('plain px', () => {
    expect(applyOutlineWidth(extractOutlineWidth([
      p('OutlineWidth', { type: 'length', px: 6 }),
    ]))).toEqual({ outlineWidth: '6px' });
  });
  it('rem', () => {
    expect(applyOutlineWidth(extractOutlineWidth([
      p('OutlineWidth', { type: 'length', original: { v: 0.5, u: 'REM' } }),
    ]))).toEqual({ outlineWidth: '0.5rem' });
  });
});

describe('OutlineStyle', () => {
  it.each(['none', 'dotted', 'dashed', 'solid', 'double', 'groove', 'ridge', 'inset', 'outset', 'auto'])(
    'accepts %s keyword',
    (kw) => {
      expect(applyOutlineStyle(extractOutlineStyle([p('OutlineStyle', kw.toUpperCase())])))
        .toEqual({ outlineStyle: kw });
    },
  );
});

describe('OutlineColor', () => {
  it('hex → rgba()', () => {
    expect(applyOutlineColor(extractOutlineColor([
      p('OutlineColor', { srgb: { r: 1, g: 0.2, b: 0.4 }, original: '#ff3366' }),
    ]))).toEqual({ outlineColor: 'rgba(255, 51, 102, 1)' });
  });
  it('rgba with alpha', () => {
    expect(applyOutlineColor(extractOutlineColor([
      p('OutlineColor', {
        srgb: { r: 0, g: 0.5, b: 1, a: 0.5 },
        original: { r: 0, g: 128, b: 255, a: 0.5 },
      }),
    ]))).toEqual({ outlineColor: 'rgba(0, 128, 255, 0.5)' });
  });
  it('currentColor passthrough', () => {
    expect(applyOutlineColor(extractOutlineColor([
      p('OutlineColor', { original: 'currentColor' }),
    ]))).toEqual({ outlineColor: 'currentColor' });
  });
});

describe('OutlineOffset', () => {
  it('zero', () => {
    expect(applyOutlineOffset(extractOutlineOffset([p('OutlineOffset', { px: 0 })])))
      .toEqual({ outlineOffset: '0px' });
  });
  it('positive', () => {
    expect(applyOutlineOffset(extractOutlineOffset([p('OutlineOffset', { px: 8 })])))
      .toEqual({ outlineOffset: '8px' });
  });
  it('negative (insets the outline)', () => {
    expect(applyOutlineOffset(extractOutlineOffset([p('OutlineOffset', { px: -4 })])))
      .toEqual({ outlineOffset: '-4px' });
  });
  it('rem', () => {
    expect(applyOutlineOffset(extractOutlineOffset([
      p('OutlineOffset', { original: { v: 0.5, u: 'REM' } }),
    ]))).toEqual({ outlineOffset: '0.5rem' });
  });
});
