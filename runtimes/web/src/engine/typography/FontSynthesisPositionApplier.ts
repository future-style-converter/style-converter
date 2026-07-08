// FontSynthesisPositionApplier.ts — emits CSS declarations from a FontSynthesisPositionConfig.
// Web is the privileged platform for typography: native CSS `fontSynthesisPosition`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontSynthesisPositionConfig } from './FontSynthesisPositionConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontSynthesisPosition
export type FontSynthesisPositionStyles = CSSProperties;

export function applyFontSynthesisPosition(config: FontSynthesisPositionConfig): FontSynthesisPositionStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontSynthesisPosition: config.value } as unknown) as FontSynthesisPositionStyles;
}
