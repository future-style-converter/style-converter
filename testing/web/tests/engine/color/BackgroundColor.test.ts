// BackgroundColor — extractor + applier coverage against the fixture shapes
// observed under examples/properties/colors/*.
import { describe, it, expect } from 'vitest';
import { extractBackgroundColor } from '../../../src/style/engine/color/BackgroundColorExtractor';
import { applyBackgroundColor } from '../../../src/style/engine/color/BackgroundColorApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('extractBackgroundColor', () => {
  it('handles static sRGB (hex)', () => {
    const cfg = extractBackgroundColor([
      p('BackgroundColor', { srgb: { r: 1, g: 0, b: 0.666 }, original: '#f0a' }),
    ]);
    expect(cfg.color?.kind).toBe('srgb');
  });

  it('handles hex with alpha', () => {
    const cfg = extractBackgroundColor([
      p('BackgroundColor', { srgb: { r: 1, g: 0.2, b: 0.4, a: 0.5 }, original: '#ff336680' }),
    ]);
    expect(cfg.color).toEqual({ kind: 'srgb', r: 1, g: 0.2, b: 0.4, a: 0.5 });
  });

  it('handles transparent', () => {
    const cfg = extractBackgroundColor([p('BackgroundColor', 'transparent')]);
    expect(cfg.color).toEqual({ kind: 'srgb', r: 0, g: 0, b: 0, a: 0 });
  });

  it('handles currentColor dynamic', () => {
    const cfg = extractBackgroundColor([p('BackgroundColor', 'currentColor')]);
    expect(cfg.color).toEqual({ kind: 'dynamic', dynamicKind: 'currentColor', raw: 'currentColor' });
  });

  it('handles color-mix dynamic', () => {
    const cfg = extractBackgroundColor([
      p('BackgroundColor', {
        original: { type: 'color-mix', colorSpace: 'oklch', color1: 'red', color2: 'blue', percent2: 30 },
      }),
    ]);
    expect(cfg.color?.kind).toBe('dynamic');
  });

  it('last write wins', () => {
    const cfg = extractBackgroundColor([
      p('BackgroundColor', { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }),
      p('BackgroundColor', { srgb: { r: 0, g: 0, b: 1 }, original: 'blue' }),
    ]);
    expect(cfg.color).toMatchObject({ kind: 'srgb', b: 1 });
  });

  it('ignores unrelated properties', () => {
    const cfg = extractBackgroundColor([p('Color', { srgb: { r: 1, g: 0, b: 0 } })]);
    expect(cfg.color).toBeUndefined();
  });
});

describe('applyBackgroundColor', () => {
  it('emits rgba() for static colors', () => {
    const out = applyBackgroundColor({ color: { kind: 'srgb', r: 1, g: 0, b: 0, a: 1 } });
    expect(out.backgroundColor).toBe('rgba(255, 0, 0, 1)');
  });

  it('reconstructs color-mix() CSS', () => {
    const out = applyBackgroundColor({
      color: {
        kind: 'dynamic', dynamicKind: 'color-mix',
        raw: { type: 'color-mix', colorSpace: 'oklch', color1: 'red', color2: 'blue', percent2: 30 },
      },
    });
    expect(out.backgroundColor).toBe('color-mix(in oklch, red, blue 30%)');
  });

  it('reconstructs light-dark() CSS', () => {
    const out = applyBackgroundColor({
      color: {
        kind: 'dynamic', dynamicKind: 'light-dark',
        raw: { type: 'light-dark', lightColor: '#ffeedd', darkColor: '#221100' },
      },
    });
    expect(out.backgroundColor).toBe('light-dark(#ffeedd, #221100)');
  });

  it('reconstructs relative color CSS', () => {
    const out = applyBackgroundColor({
      color: {
        kind: 'dynamic', dynamicKind: 'relative',
        raw: {
          type: 'relative', function: 'rgb', baseColor: 'red',
          components: ['calc(r - 50)', 'g', 'b'],
        },
      },
    });
    expect(out.backgroundColor).toBe('rgb(from red calc(r - 50) g b)');
  });

  it('emits nothing when unset', () => {
    expect(applyBackgroundColor({})).toEqual({});
  });
});
