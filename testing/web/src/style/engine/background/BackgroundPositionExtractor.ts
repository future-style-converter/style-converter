// BackgroundPositionExtractor.ts — collects BackgroundPositionX + Y IR
// properties into one config.  Shapes observed in fixtures:
//   { type: 'keyword', value: 'LEFT'|'RIGHT'|'TOP'|'BOTTOM'|'CENTER' }
//   { type: 'percentage', percentage: N }
//   { type: 'length', px: N }

import type {
  BackgroundPositionConfig,
  BackgroundPositionPropertyType,
} from './BackgroundPositionConfig';
import {
  BACKGROUND_POSITION_X,
  BACKGROUND_POSITION_Y,
  BACKGROUND_POSITION_BLOCK,
  BACKGROUND_POSITION_INLINE,
} from './BackgroundPositionConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Registry predicate — matches either physical or logical axis.
export function isBackgroundPositionProperty(
  type: string,
): type is BackgroundPositionPropertyType {
  return type === BACKGROUND_POSITION_X
      || type === BACKGROUND_POSITION_Y
      || type === BACKGROUND_POSITION_BLOCK
      || type === BACKGROUND_POSITION_INLINE;
}

// Parse a single axis payload into a CSS token, or null on unknown shapes.
function parseAxis(data: unknown): string | null {
  if (data === null || data === undefined) return null;               // missing
  if (typeof data === 'string') return data.toLowerCase();            // defensive bare string
  if (typeof data === 'number') return `${data}%`;                    // bare number -> percentage
  if (typeof data !== 'object') return null;                          // other primitives rejected
  const obj = data as Record<string, unknown>;
  if (obj.type === 'keyword' && typeof obj.value === 'string') {      // LEFT/RIGHT/TOP/BOTTOM/CENTER
    return obj.value.toLowerCase();                                   // CSS expects lowercase keywords
  }
  if (obj.type === 'percentage' && typeof obj.percentage === 'number') {
    return `${obj.percentage}%`;                                      // explicit percentage payload
  }
  if (obj.type === 'percentage' && typeof obj.value === 'number') {
    return `${obj.value}%`;                                           // alternate 'value' key
  }
  if (obj.type === 'length' && typeof obj.px === 'number') {
    return `${obj.px}px`;                                             // canonical length
  }
  if (typeof obj.px === 'number') return `${obj.px}px`;               // bare {px:N}
  return null;                                                        // unknown shape
}

// Entry point — last X/Y write wins (CSS cascade).
export function extractBackgroundPosition(
  properties: IRPropertyLike[],
): BackgroundPositionConfig {
  const cfg: BackgroundPositionConfig = {};                           // blank accumulator
  for (const p of properties) {
    if (!isBackgroundPositionProperty(p.type)) continue;              // filter
    const token = parseAxis(p.data);                                  // parse one axis
    if (token === null) continue;                                     // unknown shape -> drop
    // Logical → physical fold: block→Y, inline→X (LTR horizontal mode).
    if (p.type === BACKGROUND_POSITION_X || p.type === BACKGROUND_POSITION_INLINE) cfg.x = token;
    else cfg.y = token;
  }
  return cfg;
}
