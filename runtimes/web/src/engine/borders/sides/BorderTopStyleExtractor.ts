// BorderTopStyleExtractor.ts — folds `BorderTopStyle` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-styles.json):
//   "SOLID" / "DASHED" / "DOTTED" / "DOUBLE" / "GROOVE" / "RIDGE" / "INSET" / "OUTSET" / "NONE" / "HIDDEN"
// Parser emits UPPERCASE bare strings; we lowercase + validate against the
// CSS Backgrounds & Borders §5 keyword set (see _shared.ts).

import { extractBorderSideStyle } from './_shared';                       // shared parse/validate logic
import type { BorderTopStyleConfig, BorderTopStylePropertyType } from './BorderTopStyleConfig';
import { BORDER_TOP_STYLE_PROPERTY_TYPE } from './BorderTopStyleConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by the registry/renderer for dispatch gating.
export function isBorderTopStyleProperty(type: string): type is BorderTopStylePropertyType {
  return type === BORDER_TOP_STYLE_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved upstream).
export function extractBorderTopStyle(properties: IRPropertyLike[]): BorderTopStyleConfig {
  const cfg: BorderTopStyleConfig = {};                                   // blank accumulator
  for (const p of properties) {                                         // single pass
    if (!isBorderTopStyleProperty(p.type)) continue;                                // skip unrelated
    const v = extractBorderSideStyle(p.data);                                     // validate & parse
    if (v) cfg.style = v;                                           // last recognised value wins
  }
  return cfg;
}
