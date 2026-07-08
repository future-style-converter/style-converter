// CornerShapeExtractor.ts — folds `CornerShape` IR properties into a config.
// IR shape flavors (from examples/properties/borders/corner-shape.json):
//   "ROUND" / "ANGLE" / "NOTCH" / "BEVEL" / "SCOOP" / "SQUIRCLE"
// The parser emits the CornerShapeValue enum name UPPERCASE; we lowercase + validate.

import { extractKeyword } from '../core/types/KeywordValue';                // keyword normaliser
import type {
  CornerShapeConfig, CornerShapePropertyType, CornerShapeValue,
} from './CornerShapeConfig';
import { CORNER_SHAPE_PROPERTY_TYPE } from './CornerShapeConfig';

// Minimal IRProperty shape — decouples engine from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Set for O(1) validation against the CSS Borders L4 enum.
const VALID = new Set<CornerShapeValue>(['round', 'angle', 'notch', 'bevel', 'scoop', 'squircle']);

// Type-narrowing predicate — drives registry/renderer dispatch.
export function isCornerShapeProperty(type: string): type is CornerShapePropertyType {
  return type === CORNER_SHAPE_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractCornerShape(properties: IRPropertyLike[]): CornerShapeConfig {
  const cfg: CornerShapeConfig = {};                                       // blank accumulator
  for (const p of properties) {                                            // single pass
    if (!isCornerShapeProperty(p.type)) continue;                          // skip unrelated
    const kw = extractKeyword(p.data)?.normalized as CornerShapeValue | undefined;
    if (kw && VALID.has(kw)) cfg.value = kw;                               // validate
  }
  return cfg;
}
