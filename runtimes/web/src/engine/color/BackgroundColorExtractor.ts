// BackgroundColorExtractor.ts — folds IR BackgroundColor properties into a
// BackgroundColorConfig.  There's only ever one BackgroundColor on a component
// (CSS cascade already resolved) so the extractor picks the last one wins.

import { extractColor } from '../core/types/ColorValue';
import type {
  BackgroundColorConfig,
  BackgroundColorPropertyType,
} from './BackgroundColorConfig';
import { BACKGROUND_COLOR_PROPERTY_TYPE } from './BackgroundColorConfig';

// Minimal IRProperty shape — keep engine modules decoupled from IRModels.
interface IRPropertyLike { type: string; data: unknown; }

// Predicate used by the registry/renderer to gate dispatch.
export function isBackgroundColorProperty(type: string): type is BackgroundColorPropertyType {
  return type === BACKGROUND_COLOR_PROPERTY_TYPE;
}

// css-cascade-5 §7.3.2 — recognise the `inherit` CSS-wide keyword on the wire.
// The converter ships CSS-wide keywords it cannot pre-resolve in the same
// srgb-less envelope it uses for `currentColor`: `{"original":"inherit"}`
// (verified verbatim in tools/titan/runs/wave48-final/sections/css-color/
// per-test-ir/wpt__css-color__currentcolor-002.json and both
// color-mix-currentcolor-00{1,2}.json). A bare `"inherit"` string is accepted
// for symmetry with extractColor's bare-string branch. Deliberately narrow:
// `initial` / `unset` / `revert` are NOT matched here because on a
// non-inherited property they all compute to the initial value (transparent),
// which is precisely what emitting no declaration already produces — adding
// them would change nothing but would widen the surface silently.
function isInheritKeyword(data: unknown): boolean {
  const raw = typeof data === 'string'
    ? data                                                            // bare-string wire form
    : (data && typeof data === 'object'
        ? (data as Record<string, unknown>).original                  // {original: …} envelope
        : undefined);
  return typeof raw === 'string' && raw.toLowerCase() === 'inherit';
}

// Main entry — single pass, last write wins.  Returns empty config if none set.
export function extractBackgroundColor(properties: IRPropertyLike[]): BackgroundColorConfig {
  const cfg: BackgroundColorConfig = {};                              // blank accumulator
  for (const p of properties) {                                       // one pass over all props
    if (!isBackgroundColorProperty(p.type)) continue;                 // ignore unrelated types
    if (isInheritKeyword(p.data)) {                                   // css-cascade-5 §7.3.2 keyword
      cfg.inherit = true;                                             // record — last wins
      cfg.color = undefined;                                          // …and it displaces a colour
      continue;                                                       // never reaches extractColor
    }
    const color = extractColor(p.data);                               // parse the IR payload
    if (color.kind === 'unknown') continue;                           // skip unparseable
    cfg.color = color;                                                // record — last wins
    cfg.inherit = undefined;                                          // a colour displaces `inherit`
  }
  return cfg;
}
