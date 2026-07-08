// AccentColorExtractor.ts — parses `AccentColor` IR into a discriminated mode.

import { extractColor } from '../core/types/ColorValue';
import type {
  AccentColorConfig,
  AccentColorPropertyType,
  AccentColorMode,
} from './AccentColorConfig';
import { ACCENT_COLOR_PROPERTY_TYPE } from './AccentColorConfig';

// Minimal IRProperty shape for decoupling.
interface IRPropertyLike { type: string; data: unknown; }

// Registry/renderer gate.
export function isAccentColorProperty(type: string): type is AccentColorPropertyType {
  return type === ACCENT_COLOR_PROPERTY_TYPE;
}

// Parse a single IR payload into an AccentColorMode or null if unrecognised.
function parseAccent(data: unknown): AccentColorMode | null {
  if (data === null || data === undefined) return null;               // no data
  if (typeof data === 'string') {                                     // bare keyword shape (defensive)
    if (data === 'auto') return { kind: 'auto' };
    return null;
  }
  if (typeof data !== 'object') return null;                          // other primitives rejected
  const obj = data as Record<string, unknown>;
  if (obj.type === 'auto') return { kind: 'auto' };                   // canonical auto variant
  if (obj.type === 'color') {                                         // canonical color variant
    const color = extractColor(obj);                                  // reuse primitive color extractor
    if (color.kind !== 'unknown') return { kind: 'color', color };    // keep the parsed color
  }
  // Some IR shapes may omit 'type' and be a bare color object — try that too.
  const fallback = extractColor(data);                                // last-ditch attempt
  if (fallback.kind !== 'unknown') return { kind: 'color', color: fallback };
  return null;                                                        // nothing matched
}

// Entry point — last write wins.
export function extractAccentColor(properties: IRPropertyLike[]): AccentColorConfig {
  const cfg: AccentColorConfig = {};                                  // blank accumulator
  for (const p of properties) {                                       // single pass
    if (!isAccentColorProperty(p.type)) continue;                     // filter
    const mode = parseAccent(p.data);                                 // parse IR payload
    if (!mode) continue;                                              // drop unrecognised
    cfg.mode = mode;                                                  // record — last wins
  }
  return cfg;
}
