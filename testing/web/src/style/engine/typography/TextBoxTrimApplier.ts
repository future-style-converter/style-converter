// TextBoxTrimApplier.ts — emits CSS declarations from a TextBoxTrimConfig.
// Web is the privileged platform for typography: native CSS `textBoxTrim`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextBoxTrimConfig } from './TextBoxTrimConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textBoxTrim
export type TextBoxTrimStyles = CSSProperties;

export function applyTextBoxTrim(config: TextBoxTrimConfig): TextBoxTrimStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textBoxTrim: config.value } as unknown) as TextBoxTrimStyles;
}
