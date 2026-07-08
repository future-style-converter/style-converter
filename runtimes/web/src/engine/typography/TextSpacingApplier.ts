// TextSpacingApplier.ts — emits CSS declarations from a TextSpacingConfig.
// Web is the privileged platform for typography: native CSS `textSpacing`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextSpacingConfig } from './TextSpacingConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textSpacing
export type TextSpacingStyles = CSSProperties;

export function applyTextSpacing(config: TextSpacingConfig): TextSpacingStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textSpacing: config.value } as unknown) as TextSpacingStyles;
}
