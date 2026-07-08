// TextDecorationSkipApplier.ts — emits CSS declarations from a TextDecorationSkipConfig.
// Web is the privileged platform for typography: native CSS `textDecorationSkip`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextDecorationSkipConfig } from './TextDecorationSkipConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textDecorationSkip
export type TextDecorationSkipStyles = CSSProperties;

export function applyTextDecorationSkip(config: TextDecorationSkipConfig): TextDecorationSkipStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textDecorationSkip: config.value } as unknown) as TextDecorationSkipStyles;
}
