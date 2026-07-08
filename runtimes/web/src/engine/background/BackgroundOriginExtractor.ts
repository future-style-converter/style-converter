// BackgroundOriginExtractor.ts — pulls { type: 'border-box' | ... } entries
// out of a BackgroundOrigin IR array and renders each as a CSS token.

import type {
  BackgroundOriginConfig,
  BackgroundOriginPropertyType,
  BackgroundOriginLayer,
} from './BackgroundOriginConfig';
import { BACKGROUND_ORIGIN_PROPERTY_TYPE } from './BackgroundOriginConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Accepted output tokens.
const ALLOWED = new Set(['border-box', 'padding-box', 'content-box']);

// Registry predicate.
export function isBackgroundOriginProperty(type: string): type is BackgroundOriginPropertyType {
  return type === BACKGROUND_ORIGIN_PROPERTY_TYPE;
}

// Reduce one entry to a CSS keyword with defensive fallbacks.
function layerCss(entry: unknown): string {
  if (typeof entry === 'string') {                                    // bare string shape
    const lc = entry.toLowerCase().replace(/_/g, '-');                // canonical kebab
    return ALLOWED.has(lc) ? lc : 'padding-box';                      // default per CSS spec
  }
  if (entry && typeof entry === 'object') {                           // canonical object shape
    const o = entry as Record<string, unknown>;
    if (typeof o.type === 'string') {
      const lc = o.type.toLowerCase().replace(/_/g, '-');             // canonical kebab
      return ALLOWED.has(lc) ? lc : 'padding-box';                    // spec default
    }
  }
  return 'padding-box';                                               // unknown -> CSS default
}

// Parse one IR payload.
function parseLayers(data: unknown): BackgroundOriginLayer[] {
  const arr = Array.isArray(data) ? data : [data];                    // scalar wrap
  return arr.map((e) => ({ css: layerCss(e) }));                      // one fragment per layer
}

// Entry point — last write wins.
export function extractBackgroundOrigin(properties: IRPropertyLike[]): BackgroundOriginConfig {
  const cfg: BackgroundOriginConfig = { layers: [] };                 // blank accumulator
  for (const p of properties) {
    if (!isBackgroundOriginProperty(p.type)) continue;                // filter
    cfg.layers = parseLayers(p.data);                                 // replace
  }
  return cfg;
}
