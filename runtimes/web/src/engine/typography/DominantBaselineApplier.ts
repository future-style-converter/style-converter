// DominantBaselineApplier.ts — emits CSS declarations from a DominantBaselineConfig.
// Web is the privileged platform for typography: native CSS `dominantBaseline`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { DominantBaselineConfig } from './DominantBaselineConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/dominantBaseline
export type DominantBaselineStyles = CSSProperties;

export function applyDominantBaseline(config: DominantBaselineConfig): DominantBaselineStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ dominantBaseline: config.value } as unknown) as DominantBaselineStyles;
}
