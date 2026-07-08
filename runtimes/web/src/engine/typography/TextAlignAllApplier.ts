// TextAlignAllApplier.ts — emits CSS declarations from a TextAlignAllConfig.
// Web is the privileged platform for typography: native CSS `textAlignAll`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextAlignAllConfig } from './TextAlignAllConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textAlignAll
export type TextAlignAllStyles = CSSProperties;

export function applyTextAlignAll(config: TextAlignAllConfig): TextAlignAllStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textAlignAll: config.value } as unknown) as TextAlignAllStyles;
}
