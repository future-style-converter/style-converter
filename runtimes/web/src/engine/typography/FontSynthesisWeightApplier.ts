// FontSynthesisWeightApplier.ts — emits CSS declarations from a FontSynthesisWeightConfig.
// Web is the privileged platform for typography: native CSS `fontSynthesisWeight`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontSynthesisWeightConfig } from './FontSynthesisWeightConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontSynthesisWeight
export type FontSynthesisWeightStyles = CSSProperties;

export function applyFontSynthesisWeight(config: FontSynthesisWeightConfig): FontSynthesisWeightStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontSynthesisWeight: config.value } as unknown) as FontSynthesisWeightStyles;
}
