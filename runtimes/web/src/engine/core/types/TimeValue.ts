// TimeValue.ts - Time primitive extractor.
// Handles the shape documented in examples/primitives/times.json:
//   { ms: <normalized>, original?: { v:N, u:'S'|'MS' } }
// 'original' is omitted when the source unit was already 'ms'.
// Quirk #7: transition/animation properties often carry ARRAYS of times; callers
// should map(extractTime) over the list themselves — this function handles a single element.

// Normalised time value — always milliseconds so animation APIs can consume directly.
export interface TimeValue { milliseconds: number }

// Convert raw unit+magnitude into milliseconds. Returns null on unknown units.
function unitToMs(v: number, u: unknown): number | null {
  if (typeof u !== 'string') return null;                           // guard
  switch (u.toUpperCase()) {
    case 'MS': return v;                                            // already canonical
    case 'S':  return v * 1000;                                     // seconds -> ms
    default:   return null;                                         // unknown: caller decides fallback
  }
}

// Main entrypoint. Returns null on parse failure.
export function extractTime(data: unknown): TimeValue | null {
  if (data === null || data === undefined) return null;

  // Bare number: assume milliseconds.
  if (typeof data === 'number') return { milliseconds: data };

  if (typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;

  // Canonical shape: { ms: N }.
  if (typeof obj.ms === 'number') return { milliseconds: obj.ms };

  // Fallback via 'original' wrapper.
  if (obj.original && typeof obj.original === 'object') {
    const orig = obj.original as Record<string, unknown>;
    if (typeof orig.v === 'number') {
      const ms = unitToMs(orig.v, orig.u);
      if (ms !== null) return { milliseconds: ms };
    }
  }

  // Defensive: raw {v,u}.
  if (typeof obj.v === 'number') {
    const ms = unitToMs(obj.v, obj.u);
    if (ms !== null) return { milliseconds: ms };
  }

  return null;
}
