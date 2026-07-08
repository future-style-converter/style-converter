// FontPaletteApplier.ts — emits CSS declarations from a FontPaletteConfig.
// Web is the privileged platform for typography: native CSS `fontPalette`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontPaletteConfig } from './FontPaletteConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontPalette
export type FontPaletteStyles = CSSProperties;

export function applyFontPalette(config: FontPaletteConfig): FontPaletteStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontPalette: config.value } as unknown) as FontPaletteStyles;
}
