// FontSynthesisStyleApplier.ts — emits CSS declarations from a FontSynthesisStyleConfig.
// Web is the privileged platform for typography: native CSS `fontSynthesisStyle`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontSynthesisStyleConfig } from './FontSynthesisStyleConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontSynthesisStyle
export type FontSynthesisStyleStyles = CSSProperties;

export function applyFontSynthesisStyle(config: FontSynthesisStyleConfig): FontSynthesisStyleStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontSynthesisStyle: config.value } as unknown) as FontSynthesisStyleStyles;
}
