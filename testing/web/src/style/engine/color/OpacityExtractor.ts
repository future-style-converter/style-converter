// OpacityExtractor.ts — pulls `Opacity` IR into a normalised 0..1 alpha.
// Handles every shape seen in fixtures:
//   { alpha: 0.5, original: {...} }  (normalised form)
//   { alpha: 0.5 }                    (minimal form)
//   0.5                               (bare number)
//   "0.5"                             (stringified number, defensive)

import type { OpacityConfig, OpacityPropertyType } from './OpacityConfig';
import { OPACITY_PROPERTY_TYPE } from './OpacityConfig';

// Minimal IRProperty shape.
interface IRPropertyLike { type: string; data: unknown; }

// Clamp helper — opacity is visually meaningful only on [0, 1].
function clamp01(n: number): number {
  if (n < 0) return 0;                                                // reject negatives
  if (n > 1) return 1;                                                // reject > 1
  return n;                                                           // passthrough
}

// Registry predicate.
export function isOpacityProperty(type: string): type is OpacityPropertyType {
  return type === OPACITY_PROPERTY_TYPE;                              // single property name
}

// Parse one IR payload into a 0..1 float, or null on failure.
function parseOpacity(data: unknown): number | null {
  if (data === null || data === undefined) return null;               // no data
  if (typeof data === 'number') return clamp01(data);                 // bare-number shape
  if (typeof data === 'string') {                                     // defensive: stringified number
    const n = Number(data);
    return Number.isFinite(n) ? clamp01(n) : null;
  }
  if (typeof data !== 'object') return null;                          // other primitives not accepted
  const obj = data as Record<string, unknown>;
  if (typeof obj.alpha === 'number') return clamp01(obj.alpha);       // canonical IR shape
  // Fallback: read from nested 'original' payload shapes.
  if (obj.original && typeof obj.original === 'object') {
    const orig = obj.original as Record<string, unknown>;
    if (typeof orig.value === 'number') {                             // {type:'number'|'percentage', value}
      const raw = orig.value;
      return orig.type === 'percentage' ? clamp01(raw / 100) : clamp01(raw);
    }
  }
  return null;                                                        // unrecognised shape
}

// Entry point — last Opacity wins (CSS cascade already handled upstream).
export function extractOpacity(properties: IRPropertyLike[]): OpacityConfig {
  const cfg: OpacityConfig = {};                                      // blank accumulator
  for (const p of properties) {                                       // single pass
    if (!isOpacityProperty(p.type)) continue;                         // filter
    const a = parseOpacity(p.data);                                   // parse numeric value
    if (a === null) continue;                                         // unparseable -> skip
    cfg.alpha = a;                                                    // last write wins
  }
  return cfg;
}
