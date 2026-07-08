// FontDisplayApplier.ts — emits CSS declarations from a FontDisplayConfig.
// Web is the privileged platform for typography: native CSS `fontDisplay`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontDisplayConfig } from './FontDisplayConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontDisplay
export type FontDisplayStyles = CSSProperties;

export function applyFontDisplay(config: FontDisplayConfig): FontDisplayStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontDisplay: config.value } as unknown) as FontDisplayStyles;
}
