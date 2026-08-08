// shapesPhase10.test.ts — wave-36 lane M5 pins for the css-shapes category.
//
// The regression these lock: `shape-outside` folded through the generic
// `keywordOrRaw` helper, which does not understand the sealed-variant wire the
// converter emits, so EVERY basic shape and EVERY <shape-box> keyword was
// silently dropped and the browser never ran float exclusion. Each `it` below
// is a wire shape taken verbatim from a wave-35 TITAN capture of
// tools/titan/runs/wave35-webmap/sections/css-shapes/out/tmpOutput.json.
import { describe, it, expect } from 'vitest';
import { applyShapesPhase10 } from '../../src/engine/shapes/_dispatch';
import { shapeValueToCss, irUrlToCss } from '../../src/engine/shapes/_shared';

describe('shapeValueToCss — the ShapeOutsideProperty.kt sealed wire', () => {
  it('basic-shape survives VERBATIM (the wave-35 drop)', () => {
    // Exact node from shape-outside/assorted/float-retry-push-circle.
    expect(shapeValueToCss({ type: 'basic-shape', shape: 'circle(70.710678px at 0px 0px)' }))
      .toBe('circle(70.710678px at 0px 0px)');
  });
  it('basic-shape keeps a trailing <shape-box>', () => {
    expect(shapeValueToCss({ type: 'basic-shape', shape: 'circle(50% at left 40px top 40px) border-box' }))
      .toBe('circle(50% at left 40px top 40px) border-box');
  });
  it('every <shape-box> tag maps to its CSS keyword', () => {
    for (const box of ['margin-box', 'border-box', 'padding-box', 'content-box']) {
      expect(shapeValueToCss({ type: box })).toBe(box);
    }
  });
  it('none / auto tags pass through', () => {
    expect(shapeValueToCss({ type: 'none' })).toBe('none');
    expect(shapeValueToCss({ type: 'auto' })).toBe('auto');
  });
  it('raw is NOT lower-cased (gradients + case-sensitive paths)', () => {
    // Exact node from shape-outside/shape-image/gradients/shape-outside-linear-gradient-004.
    const raw = 'linear-gradient(135deg, black, black 50%, transparent 50%)';
    expect(shapeValueToCss({ type: 'raw', value: raw })).toBe(raw);
    expect(shapeValueToCss({ type: 'raw', value: 'var(--Shape_A)' })).toBe('var(--Shape_A)');
  });
  it('CSS-wide keyword variant lands lower-case', () => {
    expect(shapeValueToCss({ type: 'keyword', keyword: 'Inherit' })).toBe('inherit');
  });
  it('image-url quotes the href; data: URLs use the object form', () => {
    expect(shapeValueToCss({ type: 'image-url', url: 'shape.png' })).toBe('url("shape.png")');
    expect(shapeValueToCss({ type: 'image-url', url: { url: 'data:image/png;base64,AA', data: true } }))
      .toBe('url("data:image/png;base64,AA")');
    expect(irUrlToCss('a"b')).toBe('url("a\\"b")');                 // quote escaping
  });
  it('unwraps a non-flattened {value:{…}} wrapper', () => {
    expect(shapeValueToCss({ value: { type: 'basic-shape', shape: 'inset(20px)' } })).toBe('inset(20px)');
  });
  it('unknown / empty wire is skipped, never emitted', () => {
    expect(shapeValueToCss({ type: 'made-up' })).toBeUndefined();
    expect(shapeValueToCss({ type: 'basic-shape', shape: '   ' })).toBeUndefined();
    expect(shapeValueToCss(undefined)).toBeUndefined();
    expect(shapeValueToCss(42)).toBeUndefined();
  });
});

describe('applyShapesPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyShapesPhase10([])).toEqual({});
  });
  it('emits shapeOutside for a basic shape', () => {
    expect(applyShapesPhase10([
      { type: 'ShapeOutside', data: { type: 'basic-shape', shape: 'circle(50% at left bottom)' } },
    ])).toEqual({ shapeOutside: 'circle(50% at left bottom)' });
  });
  it('emits shapeOutside for a <shape-box>', () => {
    expect(applyShapesPhase10([{ type: 'ShapeOutside', data: { type: 'content-box' } }]))
      .toEqual({ shapeOutside: 'content-box' });
  });
  it('last write wins across a cascade', () => {
    expect(applyShapesPhase10([
      { type: 'ShapeOutside', data: { type: 'border-box' } },
      { type: 'ShapeOutside', data: { type: 'basic-shape', shape: 'inset(10px)' } },
    ])).toEqual({ shapeOutside: 'inset(10px)' });
  });
  it('shape-margin still folds through the length helper', () => {
    expect(applyShapesPhase10([{ type: 'ShapeMargin', data: { px: 12 } }]))
      .toEqual({ shapeMargin: '12px' });
  });
  it('shape-inside decodes its {type:shape} variant', () => {
    expect(applyShapesPhase10([{ type: 'ShapeInside', data: { type: 'shape', shape: 'circle(40%)' } }]))
      .toEqual({ shapeInside: 'circle(40%)' });
  });
  it('a full float-exclusion node emits shape + margin together', () => {
    expect(applyShapesPhase10([
      { type: 'ShapeOutside', data: { type: 'basic-shape', shape: 'polygon(0 0, 100px 0, 0 100px)' } },
      { type: 'ShapeMargin', data: { px: 5 } },
    ])).toEqual({ shapeOutside: 'polygon(0 0, 100px 0, 0 100px)', shapeMargin: '5px' });
  });
});
