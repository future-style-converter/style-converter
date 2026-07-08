// CaretColorExtractor.ts — parses `CaretColor` IR into a discriminated mode.
// Mirrors AccentColorExtractor; kept as its own module so downstream appliers
// can evolve independently (caret styling is different from form accents).

import { extractColor } from '../core/types/ColorValue';
import type {
  CaretColorConfig,
  CaretColorPropertyType,
  CaretColorMode,
} from './CaretColorConfig';
import { CARET_COLOR_PROPERTY_TYPE } from './CaretColorConfig';

// Minimal IRProperty shape for decoupling.
interface IRPropertyLike { type: string; data: unknown; }

// Registry/renderer gate.
export function isCaretColorProperty(type: string): type is CaretColorPropertyType {
  return type === CARET_COLOR_PROPERTY_TYPE;                          // exact match
}

// Parse one payload into a CaretColorMode or null on unrecognised shapes.
function parseCaret(data: unknown): CaretColorMode | null {
  if (data === null || data === undefined) return null;
  if (typeof data === 'string') {                                     // defensive bare keyword
    if (data === 'auto') return { kind: 'auto' };
    return null;
  }
  if (typeof data !== 'object') return null;
  const obj = data as Record<string, unknown>;
  if (obj.type === 'auto') return { kind: 'auto' };                   // canonical auto
  if (obj.type === 'color') {
    const color = extractColor(obj);                                  // reuse primitive parser
    if (color.kind !== 'unknown') return { kind: 'color', color };
  }
  // Last-ditch: treat bare color object as color variant.
  const fallback = extractColor(data);
  if (fallback.kind !== 'unknown') return { kind: 'color', color: fallback };
  return null;
}

// Entry point — last write wins.
export function extractCaretColor(properties: IRPropertyLike[]): CaretColorConfig {
  const cfg: CaretColorConfig = {};                                   // blank accumulator
  for (const p of properties) {
    if (!isCaretColorProperty(p.type)) continue;                      // filter
    const mode = parseCaret(p.data);                                  // parse payload
    if (!mode) continue;                                              // drop unrecognised
    cfg.mode = mode;                                                  // record
  }
  return cfg;
}
