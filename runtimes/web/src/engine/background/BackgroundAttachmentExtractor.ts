// BackgroundAttachmentExtractor.ts — pulls attachment keywords out of the IR
// array form.  Accepts { type: 'scroll' } objects and bare 'scroll' strings.

import type {
  BackgroundAttachmentConfig,
  BackgroundAttachmentPropertyType,
  BackgroundAttachmentLayer,
} from './BackgroundAttachmentConfig';
import { BACKGROUND_ATTACHMENT_PROPERTY_TYPE } from './BackgroundAttachmentConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Recognised output keywords.
const ALLOWED = new Set(['scroll', 'fixed', 'local']);

// Registry predicate.
export function isBackgroundAttachmentProperty(type: string): type is BackgroundAttachmentPropertyType {
  return type === BACKGROUND_ATTACHMENT_PROPERTY_TYPE;
}

// Normalise one entry to a CSS token.
function layerCss(entry: unknown): string {
  if (typeof entry === 'string') {                                    // bare keyword
    const lc = entry.toLowerCase();
    return ALLOWED.has(lc) ? lc : 'scroll';                           // CSS default
  }
  if (entry && typeof entry === 'object') {                           // wrapped shape
    const o = entry as Record<string, unknown>;
    if (typeof o.type === 'string') {
      const lc = o.type.toLowerCase();
      return ALLOWED.has(lc) ? lc : 'scroll';                         // CSS default
    }
  }
  return 'scroll';                                                    // unknown -> default
}

// Parse one IR payload.
function parseLayers(data: unknown): BackgroundAttachmentLayer[] {
  const arr = Array.isArray(data) ? data : [data];                    // scalar wrap
  return arr.map((e) => ({ css: layerCss(e) }));                      // one fragment per layer
}

// Entry point — last write wins.
export function extractBackgroundAttachment(
  properties: IRPropertyLike[],
): BackgroundAttachmentConfig {
  const cfg: BackgroundAttachmentConfig = { layers: [] };             // blank accumulator
  for (const p of properties) {
    if (!isBackgroundAttachmentProperty(p.type)) continue;            // filter
    cfg.layers = parseLayers(p.data);                                 // replace
  }
  return cfg;
}
