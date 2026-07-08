// BoxShadowExtractor.ts — folds `BoxShadow` IR properties into a BoxShadowConfig.
// IR shape flavors (from examples/properties/borders/box-shadow.json after conversion):
//   []                                  the `none` keyword (parser emits empty array)
//   [ {x, y, c}, ... ]                  one or more layers (offsets + color)
//   [ {x, y, blur, c} ]                 with blur
//   [ {x, y, blur, spread, c} ]         with blur + spread
//   [ {..., inset:true} ]               inner shadow
//   "calc(...) calc(...) 8px #111"      raw CSS string — parser couldn't decompose
// Color uses legacy `c` key (see core/types/ColorValue pickAlpha quirk #6).
// PARSER GAP (documented per CLAUDE.md, do not fix here):
//   - `box-shadow:none` decomposes to [] rather than a `{none:true}` marker.
//   - Shadows containing `calc(...)` offsets are returned as the whole CSS
//     string (not per-layer decomposed) — we preserve them as `raw`.

import { extractLength } from '../../core/types/LengthValue';              // offset/blur/spread parser
import { extractColor } from '../../core/types/ColorValue';                // color parser
import type { BoxShadowConfig, BoxShadowLayer, BoxShadowPropertyType } from './BoxShadowConfig';
import { BOX_SHADOW_PROPERTY_TYPE } from './BoxShadowConfig';

// Minimal IRProperty shape.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isBoxShadowProperty(type: string): type is BoxShadowPropertyType {
  return type === BOX_SHADOW_PROPERTY_TYPE;
}

// Parse one layer object; returns undefined if x/y missing (minimal CSS validity).
function parseLayer(raw: unknown): BoxShadowLayer | undefined {
  if (!raw || typeof raw !== 'object') return undefined;                  // require object
  const o = raw as Record<string, unknown>;                               // widen
  const x = extractLength(o.x);                                           // mandatory X offset
  const y = extractLength(o.y);                                           // mandatory Y offset
  if (x.kind === 'unknown' || y.kind === 'unknown') return undefined;     // invalid layer
  const layer: BoxShadowLayer = { x, y };                                 // start with offsets
  if (o.blur !== undefined) {                                             // optional blur
    const b = extractLength(o.blur);
    if (b.kind !== 'unknown') layer.blur = b;
  }
  if (o.spread !== undefined) {                                           // optional spread
    const s = extractLength(o.spread);
    if (s.kind !== 'unknown') layer.spread = s;
  }
  // Color: IR uses 'c' (legacy) or 'color' (defensive).
  const colorRaw = o.c ?? o.color;                                        // try both keys
  if (colorRaw !== undefined) {
    const c = extractColor(colorRaw);                                     // shared parser
    if (c.kind !== 'unknown') layer.color = c;
  }
  if (o.inset === true) layer.inset = true;                               // inner shadow flag
  return layer;
}

// Main entrypoint — fold BoxShadow IR properties into a single config.
export function extractBoxShadow(properties: IRPropertyLike[]): BoxShadowConfig {
  const cfg: BoxShadowConfig = {};                                        // blank accumulator
  for (const p of properties) {                                           // last write wins
    if (!isBoxShadowProperty(p.type)) continue;                           // skip unrelated
    // Parser gap: raw string means layers contain calc() that weren't decomposed.
    if (typeof p.data === 'string') { cfg.raw = p.data; cfg.layers = undefined; cfg.none = undefined; continue; }
    // Parser gap: empty array = `box-shadow:none`.  Treat as explicit none.
    if (Array.isArray(p.data) && p.data.length === 0) {
      cfg.none = true; cfg.layers = undefined; cfg.raw = undefined; continue;
    }
    if (Array.isArray(p.data)) {                                          // normal array of layers
      const layers: BoxShadowLayer[] = [];                                // collect parsed layers
      for (const raw of p.data) {                                         // per-layer parse
        const layer = parseLayer(raw);
        if (layer) layers.push(layer);                                    // drop invalid layers silently
      }
      if (layers.length > 0) {                                            // at least one valid layer
        cfg.layers = layers; cfg.raw = undefined; cfg.none = undefined;
      }
    }
  }
  return cfg;
}
