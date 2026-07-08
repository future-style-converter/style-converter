// TextAutospaceApplier.ts — emits CSS declarations from a TextAutospaceConfig.
// Web is the privileged platform for typography: native CSS `textAutospace`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextAutospaceConfig } from './TextAutospaceConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textAutospace
export type TextAutospaceStyles = CSSProperties;

export function applyTextAutospace(config: TextAutospaceConfig): TextAutospaceStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textAutospace: config.value } as unknown) as TextAutospaceStyles;
}
