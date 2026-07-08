// FontSmoothApplier.ts — emits CSS declarations from a FontSmoothConfig.
// Web is the privileged platform for typography: native CSS `fontSmooth`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontSmoothConfig } from './FontSmoothConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontSmooth
export type FontSmoothStyles = CSSProperties;

export function applyFontSmooth(config: FontSmoothConfig): FontSmoothStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontSmooth: config.value } as unknown) as FontSmoothStyles;
}
