// BackgroundSizeExtractor.ts — reconstructs per-layer CSS fragments for
// `background-size`.  Fixture variants observed:
//   "cover" | "contain" | "auto"                     (bare keyword string)
//   { w: {px:N} }                                    (explicit length, H = auto)
//   { w: N }                                         (bare number -> percentage, H = auto)
//   { w: {px:N}, h: {px:N} }                         (both explicit lengths)
//   { w: N, h: N }                                   (both bare numbers -> percentages)

import type {
  BackgroundSizeConfig,
  BackgroundSizePropertyType,
  BackgroundSizeLayer,
} from './BackgroundSizeConfig';
import { BACKGROUND_SIZE_PROPERTY_TYPE } from './BackgroundSizeConfig';

// Minimal IRProperty shape.
interface IRPropertyLike { type: string; data: unknown; }

// Set of keyword values we accept as bare strings.
const KEYWORDS = new Set(['cover', 'contain', 'auto']);

// Registry predicate.
export function isBackgroundSizeProperty(type: string): type is BackgroundSizePropertyType {
  return type === BACKGROUND_SIZE_PROPERTY_TYPE;
}

// Serialise a single w/h axis.  Bare number is a percentage per parser contract.
function axisToCss(v: unknown): string {
  if (v === null || v === undefined) return 'auto';                   // missing axis -> CSS 'auto'
  if (typeof v === 'number') return `${v}%`;                          // bare number == percentage
  if (typeof v === 'string') {                                        // pre-stringified (defensive)
    return KEYWORDS.has(v) ? v : v;
  }
  if (typeof v === 'object') {
    const o = v as Record<string, unknown>;
    if (typeof o.px === 'number') return `${o.px}px`;                 // canonical length
    if (typeof o.percent === 'number') return `${o.percent}%`;        // defensive variant
    if (typeof o.value === 'number' && o.type === 'percentage') return `${o.value}%`;
    if (typeof o.value === 'number' && o.unit) return `${o.value}${String(o.unit).toLowerCase()}`;
  }
  return 'auto';                                                      // unknown shape -> safe fallback
}

// Reduce one layer-entry to a CSS fragment.
function layerCss(entry: unknown): string {
  if (typeof entry === 'string' && KEYWORDS.has(entry)) return entry; // 'cover'/'contain'/'auto'
  if (entry && typeof entry === 'object') {
    const o = entry as Record<string, unknown>;
    const w = axisToCss(o.w);                                         // horizontal axis
    if (o.h === undefined) return w;                                  // single-value form
    const h = axisToCss(o.h);                                         // vertical axis
    return `${w} ${h}`;                                               // two-value form
  }
  return 'auto';                                                      // garbage -> 'auto'
}

// Parse one IR payload; wrap single values into an array for uniform handling.
function parseLayers(data: unknown): BackgroundSizeLayer[] {
  const arr = Array.isArray(data) ? data : [data];                    // treat scalar as one-layer
  return arr.map((e) => ({ css: layerCss(e) }));                      // one fragment per layer
}

// Entry point — last write wins.
export function extractBackgroundSize(properties: IRPropertyLike[]): BackgroundSizeConfig {
  const cfg: BackgroundSizeConfig = { layers: [] };                   // blank accumulator
  for (const p of properties) {
    if (!isBackgroundSizeProperty(p.type)) continue;                  // filter
    cfg.layers = parseLayers(p.data);                                 // last write wins
  }
  return cfg;
}
