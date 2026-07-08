// TextWrapModeApplier.ts — emits CSS declarations from a TextWrapModeConfig.
// Web is the privileged platform for typography: native CSS `textWrapMode`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextWrapModeConfig } from './TextWrapModeConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textWrapMode
export type TextWrapModeStyles = CSSProperties;

export function applyTextWrapMode(config: TextWrapModeConfig): TextWrapModeStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textWrapMode: config.value } as unknown) as TextWrapModeStyles;
}
