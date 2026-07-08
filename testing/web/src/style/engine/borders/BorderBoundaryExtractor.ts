// BorderBoundaryExtractor.ts — folds `BorderBoundary` IR properties into a config.
// IR shape flavors (parser enum BorderBoundaryValue):
//   "NONE" / "PARENT" / "DISPLAY"                UPPERCASE bare strings
// No fixture yet in examples/properties/borders/ — added defensively to keep
// triplet coverage at 1:1 with irmodels/properties/borders/.

import { extractKeyword } from '../core/types/KeywordValue';                // normaliser
import type { BorderBoundaryConfig, BorderBoundaryPropertyType } from './BorderBoundaryConfig';
import { BORDER_BOUNDARY_PROPERTY_TYPE } from './BorderBoundaryConfig';

// Minimal IRProperty shape — decouples engine from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — drives registry/renderer dispatch.
export function isBorderBoundaryProperty(type: string): type is BorderBoundaryPropertyType {
  return type === BORDER_BOUNDARY_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractBorderBoundary(properties: IRPropertyLike[]): BorderBoundaryConfig {
  const cfg: BorderBoundaryConfig = {};                                    // blank accumulator
  for (const p of properties) {                                            // single pass
    if (!isBorderBoundaryProperty(p.type)) continue;                       // skip unrelated
    const kw = extractKeyword(p.data)?.normalized;                          // normalise
    if (kw === 'none' || kw === 'parent' || kw === 'display') cfg.value = kw;
  }
  return cfg;
}
