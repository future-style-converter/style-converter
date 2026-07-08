// DominantBaselineAdjustApplier.ts — emits CSS declarations from a DominantBaselineAdjustConfig.
// Web is the privileged platform for typography: native CSS `dominantBaselineAdjust`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { DominantBaselineAdjustConfig } from './DominantBaselineAdjustConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/dominantBaselineAdjust
export type DominantBaselineAdjustStyles = CSSProperties;

export function applyDominantBaselineAdjust(config: DominantBaselineAdjustConfig): DominantBaselineAdjustStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ dominantBaselineAdjust: config.value } as unknown) as DominantBaselineAdjustStyles;
}
