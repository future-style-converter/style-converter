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

// Main entry — single pass, last write wins.  Returns empty config if none set.
export function extractBackgroundColor(properties: IRPropertyLike[]): BackgroundColorConfig {
  const cfg: BackgroundColorConfig = {};                              // blank accumulator
  for (const p of properties) {                                       // one pass over all props
    if (!isBackgroundColorProperty(p.type)) continue;                 // ignore unrelated types
    const color = extractColor(p.data);                               // parse the IR payload
    if (color.kind === 'unknown') continue;                           // skip unparseable
    cfg.color = color;                                                // record — last wins
  }
  return cfg;
}
