// ColorExtractor.ts — folds IR `Color` properties into a ColorConfig.
// Exactly one text-color wins per component; the last Color property overrides.

import { extractColor } from '../core/types/ColorValue';
import type { ColorConfig, ColorPropertyType } from './ColorConfig';
import { COLOR_PROPERTY_TYPE } from './ColorConfig';

// Minimal IR property shape — keeps this module independent of IRModels.
interface IRPropertyLike { type: string; data: unknown; }

// Predicate used by registry + renderer to gate the migrated dispatch path.
export function isColorProperty(type: string): type is ColorPropertyType {
  return type === COLOR_PROPERTY_TYPE;
}

// Entry point: last write wins, unknown shapes dropped silently.
export function extractTextColor(properties: IRPropertyLike[]): ColorConfig {
  const cfg: ColorConfig = {};                                        // blank accumulator
  for (const p of properties) {                                       // single pass
    if (!isColorProperty(p.type)) continue;                           // skip unrelated types
    const color = extractColor(p.data);                               // parse IR payload
    if (color.kind === 'unknown') continue;                           // drop unparseable
    cfg.color = color;                                                // record — last wins
  }
  return cfg;
}
