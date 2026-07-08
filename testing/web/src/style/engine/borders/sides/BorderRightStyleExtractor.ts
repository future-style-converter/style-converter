// BorderRightStyleExtractor.ts — folds `BorderRightStyle` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-styles.json):
//   "SOLID" / "DASHED" / "DOTTED" / "DOUBLE" / "GROOVE" / "RIDGE" / "INSET" / "OUTSET" / "NONE" / "HIDDEN"
// Parser emits UPPERCASE bare strings; we lowercase + validate against the
// CSS Backgrounds & Borders §5 keyword set (see _shared.ts).

import { extractBorderSideStyle } from './_shared';                       // shared parse/validate logic
import type { BorderRightStyleConfig, BorderRightStylePropertyType } from './BorderRightStyleConfig';
import { BORDER_RIGHT_STYLE_PROPERTY_TYPE } from './BorderRightStyleConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by the registry/renderer for dispatch gating.
export function isBorderRightStyleProperty(type: string): type is BorderRightStylePropertyType {
  return type === BORDER_RIGHT_STYLE_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved upstream).
export function extractBorderRightStyle(properties: IRPropertyLike[]): BorderRightStyleConfig {
  const cfg: BorderRightStyleConfig = {};                                   // blank accumulator
  for (const p of properties) {                                         // single pass
    if (!isBorderRightStyleProperty(p.type)) continue;                                // skip unrelated
    const v = extractBorderSideStyle(p.data);                                     // validate & parse
    if (v) cfg.style = v;                                           // last recognised value wins
  }
  return cfg;
}
