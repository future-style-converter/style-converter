// FontMinSizeApplier.ts — emits CSS declarations from a FontMinSizeConfig.
// Web is the privileged platform for typography: native CSS `fontMinSize`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontMinSizeConfig } from './FontMinSizeConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontMinSize
export type FontMinSizeStyles = CSSProperties;

export function applyFontMinSize(config: FontMinSizeConfig): FontMinSizeStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontMinSize: config.value } as unknown) as FontMinSizeStyles;
}
