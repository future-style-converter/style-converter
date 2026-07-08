// GlyphOrientationVerticalApplier.ts — emits CSS declarations from a GlyphOrientationVerticalConfig.
// Web is the privileged platform for typography: native CSS `glyphOrientationVertical`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { GlyphOrientationVerticalConfig } from './GlyphOrientationVerticalConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/glyphOrientationVertical
export type GlyphOrientationVerticalStyles = CSSProperties;

export function applyGlyphOrientationVertical(config: GlyphOrientationVerticalConfig): GlyphOrientationVerticalStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ glyphOrientationVertical: config.value } as unknown) as GlyphOrientationVerticalStyles;
}
