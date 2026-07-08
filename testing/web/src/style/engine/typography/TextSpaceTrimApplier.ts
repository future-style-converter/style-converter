// TextSpaceTrimApplier.ts — emits CSS declarations from a TextSpaceTrimConfig.
// Web is the privileged platform for typography: native CSS `textSpaceTrim`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextSpaceTrimConfig } from './TextSpaceTrimConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textSpaceTrim
export type TextSpaceTrimStyles = CSSProperties;

export function applyTextSpaceTrim(config: TextSpaceTrimConfig): TextSpaceTrimStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textSpaceTrim: config.value } as unknown) as TextSpaceTrimStyles;
}
