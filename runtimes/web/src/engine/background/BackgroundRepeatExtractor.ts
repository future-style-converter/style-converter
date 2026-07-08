// BackgroundRepeatExtractor.ts — reduces each BackgroundRepeat layer-entry to a
// CSS fragment.  Accepts bare keywords or { x, y } objects.

import type {
  BackgroundRepeatConfig,
  BackgroundRepeatPropertyType,
  BackgroundRepeatLayer,
} from './BackgroundRepeatConfig';
import { BACKGROUND_REPEAT_PROPERTY_TYPE } from './BackgroundRepeatConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Keyword values recognised on a single axis.
const AXIS_WORDS = new Set(['repeat', 'no-repeat', 'space', 'round']);

// Registry predicate.
export function isBackgroundRepeatProperty(type: string): type is BackgroundRepeatPropertyType {
  return type === BACKGROUND_REPEAT_PROPERTY_TYPE;
}

// Normalise one axis keyword — lowercases and validates.
function axisKeyword(v: unknown): string {
  if (typeof v !== 'string') return 'repeat';                         // fallback to CSS default
  const lc = v.toLowerCase().replace('_', '-');                       // IR may emit snake-case UPPER
  return AXIS_WORDS.has(lc) ? lc : 'repeat';                          // reject unknowns
}

// Reduce one layer-entry to a CSS fragment.
function layerCss(entry: unknown): string {
  if (typeof entry === 'string') {                                    // bare keyword shape
    const lc = entry.toLowerCase().replace('_', '-');                 // normalise casing
    return lc;                                                         // 'repeat'|'no-repeat'|'repeat-x'|... — emit raw
  }
  if (entry && typeof entry === 'object') {                           // axis-pair shape
    const o = entry as Record<string, unknown>;
    const x = axisKeyword(o.x);                                       // horizontal axis
    const y = axisKeyword(o.y);                                       // vertical axis
    // Collapse identical axes — 'repeat repeat' is valid but 'repeat' is canonical.
    if (x === y) return x;                                            // single-token form
    return `${x} ${y}`;                                               // two-token form
  }
  return 'repeat';                                                    // unknown -> CSS default
}

// Parse one IR payload; wrap scalar into an array for uniformity.
function parseLayers(data: unknown): BackgroundRepeatLayer[] {
  const arr = Array.isArray(data) ? data : [data];                    // scalar -> single-layer
  return arr.map((e) => ({ css: layerCss(e) }));                      // one fragment per layer
}

// Entry point — last write wins.
export function extractBackgroundRepeat(properties: IRPropertyLike[]): BackgroundRepeatConfig {
  const cfg: BackgroundRepeatConfig = { layers: [] };                 // blank accumulator
  for (const p of properties) {
    if (!isBackgroundRepeatProperty(p.type)) continue;                // filter
    cfg.layers = parseLayers(p.data);                                 // replace
  }
  return cfg;
}
