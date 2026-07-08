// Tests for extractColor — one per shape in examples/primitives/colors-*.json.
import { describe, it, expect } from 'vitest';
import { extractColor, toCssColor } from '../../../../src/style/engine/core/types/ColorValue';

describe('extractColor', () => {
  // legacy hex/rgb/hsl all land as { srgb:{r,g,b,a?}, original:... }
  it('handles hex -> srgb + original string', () => {
    expect(extractColor({ srgb: { r: 1, g: 0.2, b: 0.4 }, original: '#ff3366' }))
      .toEqual({ kind: 'srgb', r: 1, g: 0.2, b: 0.4, a: 1.0 });
  });

  it('reads legacy alpha under "a"', () => {
    expect(extractColor({ srgb: { r: 1, g: 0, b: 0, a: 0.5 }, original: { r: 255, g: 0, b: 0, a: 0.5 } }))
      .toEqual({ kind: 'srgb', r: 1, g: 0, b: 0, a: 0.5 });
  });

  it('reads modern alpha under "alpha" when present', () => {
    expect(extractColor({ srgb: { r: 0.5, g: 0.5, b: 0.5, alpha: 0.3 } }))
      .toEqual({ kind: 'srgb', r: 0.5, g: 0.5, b: 0.5, a: 0.3 });
  });

  it('defaults alpha to 1.0 when missing', () => {
    expect(extractColor({ srgb: { r: 0.1, g: 0.2, b: 0.3 } }).kind).toBe('srgb');
    const out = extractColor({ srgb: { r: 0.1, g: 0.2, b: 0.3 } });
    if (out.kind === 'srgb') expect(out.a).toBe(1.0);
  });

  // modern colors with srgb present are still srgb-kind (static)
  it('handles hwb/lab/lch/oklab/oklch/color() as srgb when pre-resolved', () => {
    const modern = { srgb: { r: 0.8, g: 0.2, b: 0.3, alpha: 1 }, original: { type: 'oklch', l: 0.63, c: 0.26, h: 29 } };
    expect(extractColor(modern).kind).toBe('srgb');
  });

  // dynamic: no srgb, original has type discriminator
  it('handles color-mix dynamic (no srgb)', () => {
    const out = extractColor({ original: { type: 'color-mix', colorSpace: 'srgb', color1: 'red', color2: 'blue' } });
    expect(out).toMatchObject({ kind: 'dynamic', dynamicKind: 'color-mix' });
  });

  it('handles light-dark dynamic', () => {
    const out = extractColor({ original: { type: 'light-dark', lightColor: '#fff', darkColor: '#000' } });
    expect(out).toMatchObject({ kind: 'dynamic', dynamicKind: 'light-dark' });
  });

  it('handles relative color syntax dynamic', () => {
    const out = extractColor({ original: { type: 'relative', function: 'rgb', baseColor: 'red', components: ['calc(r - 50)','g','b'] } });
    expect(out).toMatchObject({ kind: 'dynamic', dynamicKind: 'relative' });
  });

  it('handles currentColor bare string in original', () => {
    const out = extractColor({ original: 'currentColor' });
    expect(out).toMatchObject({ kind: 'dynamic', dynamicKind: 'currentColor' });
  });

  it('handles currentColor passed as raw string data', () => {
    expect(extractColor('currentColor').kind).toBe('dynamic');
  });

  it('handles transparent passed as raw string data', () => {
    expect(extractColor('transparent')).toEqual({ kind: 'srgb', r: 0, g: 0, b: 0, a: 0 });
  });

  // edge cases
  it('returns unknown on null/undefined/empty', () => {
    expect(extractColor(null).kind).toBe('unknown');
    expect(extractColor(undefined).kind).toBe('unknown');
    expect(extractColor({}).kind).toBe('unknown');
  });

  it('returns unknown on malformed srgb', () => {
    expect(extractColor({ srgb: { r: 'x', g: 1, b: 1 } }).kind).toBe('unknown');
  });
});

describe('toCssColor', () => {
  it('emits rgba for srgb', () => {
    expect(toCssColor({ kind: 'srgb', r: 1, g: 0, b: 0, a: 0.5 })).toBe('rgba(255, 0, 0, 0.5)');
  });
  it('emits currentColor for dynamic currentColor', () => {
    expect(toCssColor({ kind: 'dynamic', dynamicKind: 'currentColor', raw: 'currentColor' })).toBe('currentColor');
  });
  it('emits transparent for unknown', () => {
    expect(toCssColor({ kind: 'unknown' })).toBe('transparent');
  });
});
