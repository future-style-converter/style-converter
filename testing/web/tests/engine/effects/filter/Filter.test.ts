// Filter.test.ts — Phase-8 filter + backdrop-filter.

import { describe, it, expect } from 'vitest';
import { extractFilter } from '../../../../src/style/engine/effects/filter/FilterExtractor';
import { applyFilter } from '../../../../src/style/engine/effects/filter/FilterApplier';
import { extractBackdropFilter } from '../../../../src/style/engine/effects/filter/BackdropFilterExtractor';
import { applyBackdropFilter } from '../../../../src/style/engine/effects/filter/BackdropFilterApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('Filter', () => {
  it("'none'", () => {
    expect(applyFilter(extractFilter([p('Filter', 'none')]))).toEqual({ filter: 'none' });
  });
  it('blur', () => {
    expect(applyFilter(extractFilter([p('Filter', [{ fn: 'blur', r: { px: 4 } }])])))
      .toEqual({ filter: 'blur(4px)' });
  });
  it('brightness percent', () => {
    expect(applyFilter(extractFilter([p('Filter', [{ fn: 'brightness', v: 150 }])])))
      .toEqual({ filter: 'brightness(150%)' });
  });
  it('hue-rotate angle', () => {
    expect(applyFilter(extractFilter([p('Filter', [{ fn: 'hue-rotate', a: { deg: 90 } }])])))
      .toEqual({ filter: 'hue-rotate(90deg)' });
  });
  it('drop-shadow with color', () => {
    const r = applyFilter(extractFilter([p('Filter', [
      { fn: 'drop-shadow', x: { px: 2 }, y: { px: 2 }, r: { px: 4 },
        c: { srgb: { r: 0, g: 0, b: 0, a: 0.5 } } },
    ])]));
    expect(r.filter).toContain('drop-shadow(');
    expect(r.filter).toContain('2px 2px 4px');
  });
  it('multi-fn chain', () => {
    expect(applyFilter(extractFilter([p('Filter', [
      { fn: 'brightness', v: 120 }, { fn: 'contrast', v: 80 },
    ])]))).toEqual({ filter: 'brightness(120%) contrast(80%)' });
  });
  it('url() SVG filter ref', () => {
    expect(applyFilter(extractFilter([p('Filter', { url: '#mono' })])))
      .toEqual({ filter: 'url("#mono")' });
  });
  it('empty list -> none', () => {
    expect(applyFilter(extractFilter([p('Filter', [])]))).toEqual({ filter: 'none' });
  });
});

describe('BackdropFilter', () => {
  it('emits native + -webkit- prefix', () => {
    const r = applyBackdropFilter(extractBackdropFilter([p('BackdropFilter',
      [{ fn: 'blur', r: { px: 8 } }])]));
    expect(r).toEqual({ backdropFilter: 'blur(8px)', WebkitBackdropFilter: 'blur(8px)' });
  });
  it('multi-fn chain', () => {
    const r = applyBackdropFilter(extractBackdropFilter([p('BackdropFilter', [
      { fn: 'blur', r: { px: 6 } }, { fn: 'saturate', v: 150 },
    ])]));
    expect(r.backdropFilter).toBe('blur(6px) saturate(150%)');
    expect(r.WebkitBackdropFilter).toBe('blur(6px) saturate(150%)');
  });
  it('empty list -> none', () => {
    const r = applyBackdropFilter(extractBackdropFilter([p('BackdropFilter', [])]));
    expect(r.backdropFilter).toBe('none');
  });
});
