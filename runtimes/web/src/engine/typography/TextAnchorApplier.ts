// TextAnchorApplier.ts — emits CSS declarations from a TextAnchorConfig.
// Web is the privileged platform for typography: native CSS `textAnchor`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextAnchorConfig } from './TextAnchorConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textAnchor
export type TextAnchorStyles = CSSProperties;

export function applyTextAnchor(config: TextAnchorConfig): TextAnchorStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textAnchor: config.value } as unknown) as TextAnchorStyles;
}
