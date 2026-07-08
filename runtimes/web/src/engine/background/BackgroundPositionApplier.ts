// BackgroundPositionApplier.ts — converts a BackgroundPositionConfig into the
// appropriate React inline-style fields.  When both axes are set we emit the
// shorthand `backgroundPosition` ('X Y'); when only one is set we use the
// per-axis longhand so the other keeps its default (0%/0%).

import type { CSSProperties } from 'react';
import type { BackgroundPositionConfig } from './BackgroundPositionConfig';

// Union of the three React keys we can populate.
export type BackgroundPositionStyles = Pick<
  CSSProperties,
  'backgroundPosition' | 'backgroundPositionX' | 'backgroundPositionY'
>;

// Pure emitter — chooses shorthand vs longhand based on axes present.
export function applyBackgroundPosition(
  config: BackgroundPositionConfig,
): BackgroundPositionStyles {
  const out: BackgroundPositionStyles = {};                           // blank accumulator
  if (config.x !== undefined && config.y !== undefined) {             // both axes -> shorthand
    out.backgroundPosition = `${config.x} ${config.y}`;               // e.g. 'left top' / '20px 30%'
    return out;
  }
  if (config.x !== undefined) out.backgroundPositionX = config.x;     // only X -> longhand
  if (config.y !== undefined) out.backgroundPositionY = config.y;     // only Y -> longhand
  return out;                                                         // may be {} when unset
}
