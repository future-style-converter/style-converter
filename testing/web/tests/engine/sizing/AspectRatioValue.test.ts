// Tests for the AspectRatio extractor — every IR shape observed after running
// the Kotlin converter against examples/properties/sizing/aspect-ratio.json.
import { describe, it, expect } from 'vitest';
import { extractAspectRatio } from '../../../src/style/engine/sizing/AspectRatioValue';

describe('extractAspectRatio', () => {
  it('returns isAuto for bare "auto"', () => {
    // Shape: Ratio_Auto fixture -> {"data":"auto","type":"AspectRatio"}.
    expect(extractAspectRatio('auto')).toEqual({ ratio: 0, isAuto: true });
  });

  it('prefers normalizedRatio for {w,h} form', () => {
    // Shape: Ratio_16_9 -> {ratio:{w:16,h:9}, normalizedRatio:1.777...}.
    const out = extractAspectRatio({ ratio: { w: 16, h: 9 }, normalizedRatio: 16 / 9 });
    expect(out).toEqual({ ratio: 16 / 9, isAuto: false });
  });

  it('handles {ratio:{value:N}} numeric form', () => {
    // Shape: Ratio_Numeric_1_5 -> {ratio:{value:1.5}, normalizedRatio:1.5}.
    expect(extractAspectRatio({ ratio: { value: 1.5 }, normalizedRatio: 1.5 }))
      .toEqual({ ratio: 1.5, isAuto: false });
  });

  it('treats {ratio:{auto:true,w,h}} as a concrete ratio (not auto)', () => {
    // Shape: Ratio_Auto_With_Explicit -> ratio has `auto:true` AND w/h; CSS
    // still applies the explicit ratio when height isn't constrained.
    const out = extractAspectRatio({
      ratio: { auto: true, w: 16, h: 9 }, normalizedRatio: 16 / 9,
    });
    expect(out).toEqual({ ratio: 16 / 9, isAuto: false });
  });

  it('falls back to w/h division when normalizedRatio is missing', () => {
    // Defensive: if normalizedRatio got dropped somewhere, recompute locally.
    expect(extractAspectRatio({ ratio: { w: 4, h: 3 } }))
      .toEqual({ ratio: 4 / 3, isAuto: false });
  });

  it('returns null for unrecognised shapes', () => {
    // Nothing to emit -> caller should skip the aspectRatio style entirely.
    expect(extractAspectRatio(null)).toBeNull();
    expect(extractAspectRatio({})).toBeNull();
    expect(extractAspectRatio({ ratio: {} })).toBeNull();
    expect(extractAspectRatio('banana')).toBeNull();
  });

  it('returns null when h is zero (guards against divide-by-zero)', () => {
    // Kotlin should never emit h:0, but be defensive: don't return Infinity.
    expect(extractAspectRatio({ ratio: { w: 1, h: 0 } })).toBeNull();
  });
});
