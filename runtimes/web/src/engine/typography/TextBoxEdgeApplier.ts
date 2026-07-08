// TextBoxEdgeApplier.ts — emits CSS declarations from a TextBoxEdgeConfig.
// Web is the privileged platform for typography: native CSS `textBoxEdge`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextBoxEdgeConfig } from './TextBoxEdgeConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textBoxEdge
export type TextBoxEdgeStyles = CSSProperties;

export function applyTextBoxEdge(config: TextBoxEdgeConfig): TextBoxEdgeStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textBoxEdge: config.value } as unknown) as TextBoxEdgeStyles;
}
