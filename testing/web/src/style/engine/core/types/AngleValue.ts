// AngleValue.ts - Angle primitive extractor.
// Handles the shape documented in examples/primitives/angles.json:
//   { deg: <normalized>, original?: { v:N, u:'DEG'|'RAD'|'GRAD'|'TURN' } }
// 'original' is omitted when the source unit was already 'deg'.
// Quirk #7: Some properties (transitions, filter drop-shadow) can carry lists of angles,
// but per-element extraction is still the single-object form below.

// Normalised angle value — always in degrees so downstream code can rotate() directly.
export interface AngleValue { degrees: number }

// Convert a raw unit+magnitude pair into degrees. Returns null on unrecognised units.
function unitToDegrees(v: number, u: unknown): number | null {
  if (typeof u !== 'string') return null;                           // guard
  switch (u.toUpperCase()) {                                        // IR uppercases but accept any case
    case 'DEG':  return v;                                          // already canonical
    case 'RAD':  return v * (180 / Math.PI);                        // radians -> degrees
    case 'GRAD': return v * 0.9;                                    // 400grad == 360deg
    case 'TURN': return v * 360;                                    // one turn == 360deg
    default:     return null;                                       // unknown unit: caller treats as failure
  }
}

// Main entrypoint. Returns null on parse failure (caller decides default).
export function extractAngle(data: unknown): AngleValue | null {
  if (data === null || data === undefined) return null;

  // Bare number: assume degrees (compose/svg conventions).
  if (typeof data === 'number') return { degrees: data };

  if (typeof data !== 'object') return null;                        // strings/other types not accepted here
  const obj = data as Record<string, unknown>;

  // Canonical shape: { deg: N } already pre-normalised by the Kotlin parser.
  if (typeof obj.deg === 'number') return { degrees: obj.deg };

  // Fall-back: read the 'original' wrapper and convert from the original unit.
  if (obj.original && typeof obj.original === 'object') {
    const orig = obj.original as Record<string, unknown>;
    if (typeof orig.v === 'number') {
      const deg = unitToDegrees(orig.v, orig.u);
      if (deg !== null) return { degrees: deg };
    }
  }

  // Defensive: raw {v,u} without 'original' wrapper.
  if (typeof obj.v === 'number') {
    const deg = unitToDegrees(obj.v, obj.u);
    if (deg !== null) return { degrees: deg };
  }

  return null;                                                      // nothing recognised
}
