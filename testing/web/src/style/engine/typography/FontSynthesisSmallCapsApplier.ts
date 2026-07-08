// FontSynthesisSmallCapsApplier.ts — emits CSS declarations from a FontSynthesisSmallCapsConfig.
// Web is the privileged platform for typography: native CSS `fontSynthesisSmallCaps`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontSynthesisSmallCapsConfig } from './FontSynthesisSmallCapsConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontSynthesisSmallCaps
export type FontSynthesisSmallCapsStyles = CSSProperties;

export function applyFontSynthesisSmallCaps(config: FontSynthesisSmallCapsConfig): FontSynthesisSmallCapsStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontSynthesisSmallCaps: config.value } as unknown) as FontSynthesisSmallCapsStyles;
}
