// AccentColor + CaretColor share the auto/color discriminated shape.
import { describe, it, expect } from 'vitest';
import { extractAccentColor } from '../../../src/style/engine/color/AccentColorExtractor';
import { applyAccentColor } from '../../../src/style/engine/color/AccentColorApplier';
import { extractCaretColor } from '../../../src/style/engine/color/CaretColorExtractor';
import { applyCaretColor } from '../../../src/style/engine/color/CaretColorApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('AccentColor', () => {
  it('parses {type:"auto"}', () => {
    const cfg = extractAccentColor([p('AccentColor', { type: 'auto' })]);
    expect(cfg.mode).toEqual({ kind: 'auto' });
  });

  it('parses {type:"color", srgb}', () => {
    const cfg = extractAccentColor([
      p('AccentColor', { type: 'color', srgb: { r: 1, g: 0, b: 0 }, original: 'red' }),
    ]);
    expect(cfg.mode?.kind).toBe('color');
  });

  it('auto -> omits accentColor', () => {
    expect(applyAccentColor({ mode: { kind: 'auto' } })).toEqual({});
  });

  it('color -> emits accentColor rgba', () => {
    const out = applyAccentColor({
      mode: { kind: 'color', color: { kind: 'srgb', r: 1, g: 0, b: 0, a: 1 } },
    });
    expect(out.accentColor).toBe('rgba(255, 0, 0, 1)');
  });
});

describe('CaretColor', () => {
  it('parses {type:"auto"}', () => {
    const cfg = extractCaretColor([p('CaretColor', { type: 'auto' })]);
    expect(cfg.mode).toEqual({ kind: 'auto' });
  });

  it('parses {type:"color", srgb}', () => {
    const cfg = extractCaretColor([
      p('CaretColor', { type: 'color', srgb: { r: 0, g: 0, b: 1 }, original: 'blue' }),
    ]);
    expect(cfg.mode?.kind).toBe('color');
  });

  it('auto -> omits caretColor', () => {
    expect(applyCaretColor({ mode: { kind: 'auto' } })).toEqual({});
  });

  it('color -> emits caretColor', () => {
    const out = applyCaretColor({
      mode: { kind: 'color', color: { kind: 'srgb', r: 0, g: 0, b: 1, a: 1 } },
    });
    expect(out.caretColor).toBe('rgba(0, 0, 255, 1)');
  });
});
