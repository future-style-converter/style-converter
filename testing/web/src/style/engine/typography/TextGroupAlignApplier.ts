// TextGroupAlignApplier.ts — emits CSS declarations from a TextGroupAlignConfig.
// Web is the privileged platform for typography: native CSS `textGroupAlign`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextGroupAlignConfig } from './TextGroupAlignConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/textGroupAlign
export type TextGroupAlignStyles = CSSProperties;

export function applyTextGroupAlign(config: TextGroupAlignConfig): TextGroupAlignStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ textGroupAlign: config.value } as unknown) as TextGroupAlignStyles;
}
