// Opacity — every IR shape seen in fixtures.
import { describe, it, expect } from 'vitest';
import { extractOpacity } from '../../../src/style/engine/color/OpacityExtractor';
import { applyOpacity } from '../../../src/style/engine/color/OpacityApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('extractOpacity', () => {
  it('reads canonical {alpha}', () => {
    const cfg = extractOpacity([p('Opacity', { alpha: 0.5, original: { type: 'number', value: 0.5 } })]);
    expect(cfg.alpha).toBe(0.5);
  });

  it('reads bare number', () => {
    expect(extractOpacity([p('Opacity', 0.25)]).alpha).toBe(0.25);
  });

  it('reads percentage original', () => {
    const cfg = extractOpacity([p('Opacity', { original: { type: 'percentage', value: 50 } })]);
    expect(cfg.alpha).toBe(0.5);
  });

  it('clamps > 1', () => {
    expect(extractOpacity([p('Opacity', 2)]).alpha).toBe(1);
  });

  it('clamps < 0', () => {
    expect(extractOpacity([p('Opacity', -0.5)]).alpha).toBe(0);
  });

  it('preserves 0 (not undefined)', () => {
    const cfg = extractOpacity([p('Opacity', { alpha: 0.0 })]);
    expect(cfg.alpha).toBe(0);
  });
});

describe('applyOpacity', () => {
  it('emits opacity for 0', () => {
    expect(applyOpacity({ alpha: 0 })).toEqual({ opacity: 0 });
  });
  it('emits opacity for 1', () => {
    expect(applyOpacity({ alpha: 1 })).toEqual({ opacity: 1 });
  });
  it('omits when unset', () => {
    expect(applyOpacity({})).toEqual({});
  });
});
