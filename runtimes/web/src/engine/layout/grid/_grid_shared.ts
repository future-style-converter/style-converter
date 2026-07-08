// _grid_shared.ts — helpers shared by the grid/ subfolder triplets.
// Focus: rendering track-list IR arrays into CSS strings that the browser
// grid engine accepts verbatim.  Mirrors TrackSize.kt / GridTemplate.kt.

import { layoutLength, spaceList } from '../_shared';

// A single track size IR payload renders as one of:
//   { fr:N }                              → 'Nfr'
//   { px:N }                              → 'Npx'
//   number (non-object)                   → 'N%' (parser emits bare number for %)
//   'auto' / 'min-content' / 'max-content'→ kebab keyword passthrough
//   { repeat: N, tracks: [...] }          → 'repeat(N, <tracks>)'
//   { min: <size>, max: <size> }          → 'minmax(<min>, <max>)'
//   { fit: <length> }                     → 'fit-content(<length>)'
// Any unexpected shape returns undefined so the caller can drop it safely.
export function trackSize(data: unknown): string | undefined {
  if (typeof data === 'string') {                                                 // 'auto', 'min-content', 'max-content'
    return data.toLowerCase();                                                    // normalise casing
  }
  if (typeof data === 'number' && Number.isFinite(data)) {                        // bare number → %
    return `${data}%`;                                                            // matches parser's percentage path
  }
  if (!data || typeof data !== 'object') return undefined;                        // other primitives = drop
  const o = data as Record<string, unknown>;                                      // narrowed object access
  if (typeof o.fr === 'number') return `${o.fr}fr`;                               // fractional track
  if (typeof o.px === 'number') return `${o.px}px`;                               // absolute track
  if (typeof o.repeat === 'number' && Array.isArray(o.tracks)) {                  // repeat() function
    const inner = (o.tracks as unknown[]).map(trackSize).filter(Boolean).join(' ');
    return inner ? `repeat(${o.repeat}, ${inner})` : undefined;                   // drop empty repeats
  }
  if ('min' in o && 'max' in o) {                                                 // minmax() function
    const min = trackSize(o.min);
    const max = trackSize(o.max);
    if (min && max) return `minmax(${min}, ${max})`;                              // CSS grammar
  }
  if ('fit' in o) {                                                               // fit-content(<length>)
    const len = layoutLength(o.fit);
    if (len) return `fit-content(${len})`;
  }
  return undefined;                                                               // unknown
}

// Render a GridTemplate(Columns|Rows) IR payload.  Three observed shapes:
//   [track, track, …]                    → 'track track …'
//   { expr: '<raw CSS>' }                → verbatim — used by the parser's
//                                          isComplexExpression fallback (see
//                                          examples/properties/layout/README.md)
//   anything else                        → undefined (drop)
export function renderTrackList(data: unknown): string | undefined {
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (typeof o.expr === 'string') return o.expr;                                // raw escape hatch
  }
  if (!Array.isArray(data)) return undefined;                                     // only arrays / expr allowed
  return spaceList(data.map(trackSize));                                          // join with spaces
}

// Render a grid line: `<number>` | `span <number>` | `<name>` | `auto`.
// Shape catalogue is in /tmp/layout_ir/grid-{area,column,row}-start-end.
export function gridLine(data: unknown): string | undefined {
  if (typeof data === 'string') return data;                                      // bare ident
  if (!data || typeof data !== 'object') return undefined;                        // need a typed payload
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';                                           // keyword
  if (o.type === 'number' && typeof o.number === 'number') return String(o.number); // integer line index
  if (o.type === 'name' && typeof o.name === 'string') return o.name;             // named line
  if (o.type === 'span' && typeof o.count === 'number') return `span ${o.count}`; // span N
  return undefined;                                                               // unknown shape
}
