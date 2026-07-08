// OutlineStyleExtractor.ts — folds `OutlineStyle` IR properties into a config.
// IR shape flavors: UPPERCASE bare strings from the parser
//   "SOLID" / "DASHED" / ... / "AUTO" / "NONE" / "HIDDEN" / ...
// See CSS UI §4.3.2 — includes the outline-only `auto` keyword.

import { parseOutlineStyle } from './_shared';                             // shared parse/validate
import type { OutlineStyleConfig, OutlineStylePropertyType } from './OutlineStyleConfig';
import { OUTLINE_STYLE_PROPERTY_TYPE } from './OutlineStyleConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isOutlineStyleProperty(type: string): type is OutlineStylePropertyType {
  return type === OUTLINE_STYLE_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractOutlineStyle(properties: IRPropertyLike[]): OutlineStyleConfig {
  const cfg: OutlineStyleConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isOutlineStyleProperty(p.type)) continue;                              // skip unrelated
    const v = parseOutlineStyle(p.data);                                     // validate & parse
    if (v) cfg.style = v;                                           // last recognised wins
  }
  return cfg;
}
