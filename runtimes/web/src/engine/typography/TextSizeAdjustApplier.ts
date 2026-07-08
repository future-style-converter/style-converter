// TextSizeAdjustApplier.ts — emits CSS declarations from a TextSizeAdjustConfig.
// Web is the privileged platform for typography: native CSS `textSizeAdjust`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextSizeAdjustConfig } from './TextSizeAdjustConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textSizeAdjust
export type TextSizeAdjustStyles = CSSProperties;

export function applyTextSizeAdjust(config: TextSizeAdjustConfig): TextSizeAdjustStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textSizeAdjust: config.value } as unknown) as TextSizeAdjustStyles;
}
