// Tests for extractAngle — shapes from examples/primitives/angles.json.
import { describe, it, expect } from 'vitest';
import { extractAngle } from '../../../../src/style/engine/core/types/AngleValue';

describe('extractAngle', () => {
  it('handles canonical { deg:N } shape (deg input)', () => {
    expect(extractAngle({ deg: 45 })).toEqual({ degrees: 45 });
  });

  it('handles { deg:N, original:{v,u:"RAD"} } shape', () => {
    // Canonical deg wins — original is informational.
    expect(extractAngle({ deg: 45, original: { v: 0.7854, u: 'RAD' } })).toEqual({ degrees: 45 });
  });

  it('converts radians to degrees via original when deg missing', () => {
    const out = extractAngle({ original: { v: Math.PI, u: 'RAD' } });
    expect(out!.degrees).toBeCloseTo(180, 5);
  });

  it('converts gradians (400grad == 360deg)', () => {
    const out = extractAngle({ original: { v: 100, u: 'GRAD' } });
    expect(out!.degrees).toBeCloseTo(90, 5);
  });

  it('converts turns (0.25turn == 90deg)', () => {
    const out = extractAngle({ original: { v: 0.25, u: 'TURN' } });
    expect(out!.degrees).toBeCloseTo(90, 5);
  });

  it('accepts bare number as degrees', () => {
    expect(extractAngle(135)).toEqual({ degrees: 135 });
  });

  it('returns null on null/empty/unknown-unit', () => {
    expect(extractAngle(null)).toBeNull();
    expect(extractAngle({})).toBeNull();
    expect(extractAngle({ original: { v: 1, u: 'NOPE' } })).toBeNull();
  });
});
