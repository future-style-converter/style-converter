// BoxDecorationBreakExtractor.ts — folds `BoxDecorationBreak` IR properties into a config.
// IR shape flavors (from examples/properties/borders/box-decoration-break.json):
//   "SLICE" / "CLONE"                UPPERCASE bare strings from the parser
// The parser enum lives at BoxDecorationBreakValue in the IR module.

import { extractKeyword } from '../core/types/KeywordValue';                // keyword normaliser
import type { BoxDecorationBreakConfig, BoxDecorationBreakPropertyType } from './BoxDecorationBreakConfig';
import { BOX_DECORATION_BREAK_PROPERTY_TYPE } from './BoxDecorationBreakConfig';

// Minimal IRProperty shape — decouples engine from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — drives registry/renderer dispatch gating.
export function isBoxDecorationBreakProperty(type: string): type is BoxDecorationBreakPropertyType {
  return type === BOX_DECORATION_BREAK_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractBoxDecorationBreak(properties: IRPropertyLike[]): BoxDecorationBreakConfig {
  const cfg: BoxDecorationBreakConfig = {};                                // blank accumulator
  for (const p of properties) {                                            // single pass
    if (!isBoxDecorationBreakProperty(p.type)) continue;                   // skip unrelated
    const kw = extractKeyword(p.data)?.normalized;                          // normalise to lowercase
    if (kw === 'slice' || kw === 'clone') cfg.value = kw;                   // validate against spec set
  }
  return cfg;
}
