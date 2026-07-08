// GlyphOrientationHorizontalApplier.ts — emits CSS declarations from a GlyphOrientationHorizontalConfig.
// Web is the privileged platform for typography: native CSS `glyphOrientationHorizontal`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { GlyphOrientationHorizontalConfig } from './GlyphOrientationHorizontalConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/glyphOrientationHorizontal
export type GlyphOrientationHorizontalStyles = CSSProperties;

export function applyGlyphOrientationHorizontal(config: GlyphOrientationHorizontalConfig): GlyphOrientationHorizontalStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ glyphOrientationHorizontal: config.value } as unknown) as GlyphOrientationHorizontalStyles;
}
