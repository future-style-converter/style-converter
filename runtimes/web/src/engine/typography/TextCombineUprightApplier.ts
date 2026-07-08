// TextCombineUprightApplier.ts — emits CSS declarations from a TextCombineUprightConfig.
// Web is the privileged platform for typography: native CSS `textCombineUpright`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextCombineUprightConfig } from './TextCombineUprightConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textCombineUpright
export type TextCombineUprightStyles = CSSProperties;

export function applyTextCombineUpright(config: TextCombineUprightConfig): TextCombineUprightStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textCombineUpright: config.value } as unknown) as TextCombineUprightStyles;
}
