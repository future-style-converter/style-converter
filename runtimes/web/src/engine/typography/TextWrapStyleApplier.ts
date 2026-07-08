// TextWrapStyleApplier.ts — emits CSS declarations from a TextWrapStyleConfig.
// Web is the privileged platform for typography: native CSS `textWrapStyle`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextWrapStyleConfig } from './TextWrapStyleConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textWrapStyle
export type TextWrapStyleStyles = CSSProperties;

export function applyTextWrapStyle(config: TextWrapStyleConfig): TextWrapStyleStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textWrapStyle: config.value } as unknown) as TextWrapStyleStyles;
}
