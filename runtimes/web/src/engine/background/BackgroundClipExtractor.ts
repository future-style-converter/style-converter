// BackgroundClipExtractor.ts — converts uppercase IR enum tokens to CSS
// keywords.  Handles both bare-string layers and object-wrapped `{type}`
// layers for symmetry with BackgroundOrigin / BackgroundAttachment.

import type {
  BackgroundClipConfig,
  BackgroundClipPropertyType,
  BackgroundClipLayer,
} from './BackgroundClipConfig';
import { BACKGROUND_CLIP_PROPERTY_TYPE } from './BackgroundClipConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Accepted CSS output tokens.
const ALLOWED = new Set(['border-box', 'padding-box', 'content-box', 'text']);

// Registry predicate.
export function isBackgroundClipProperty(type: string): type is BackgroundClipPropertyType {
  return type === BACKGROUND_CLIP_PROPERTY_TYPE;
}

// Normalise 'BORDER_BOX' -> 'border-box' and validate.
function toCssKeyword(raw: string): string {
  const lc = raw.toLowerCase().replace(/_/g, '-');                    // canonical kebab-case
  return ALLOWED.has(lc) ? lc : 'border-box';                         // fall back to CSS default
}

// Reduce one layer-entry to a CSS token.
function layerCss(entry: unknown): string {
  if (typeof entry === 'string') return toCssKeyword(entry);          // bare UPPER enum string
  if (entry && typeof entry === 'object') {                           // object-wrapped alt shape
    const o = entry as Record<string, unknown>;
    if (typeof o.type === 'string') return toCssKeyword(o.type);      // { type: 'border-box' }
  }
  return 'border-box';                                                // unknown -> CSS default
}

// Parse one IR payload.
function parseLayers(data: unknown): BackgroundClipLayer[] {
  const arr = Array.isArray(data) ? data : [data];                    // scalar -> single
  return arr.map((e) => ({ css: layerCss(e) }));                      // one fragment per layer
}

// Entry point — last write wins.
export function extractBackgroundClip(properties: IRPropertyLike[]): BackgroundClipConfig {
  const cfg: BackgroundClipConfig = { layers: [] };                   // blank accumulator
  for (const p of properties) {
    if (!isBackgroundClipProperty(p.type)) continue;                  // filter
    cfg.layers = parseLayers(p.data);                                 // replace
  }
  return cfg;
}
