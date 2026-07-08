// WordWrapApplier.ts — emits CSS declarations from a WordWrapConfig.
// Web is the privileged platform for typography: native CSS `wordWrap`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WordWrapConfig } from './WordWrapConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/wordWrap
export type WordWrapStyles = CSSProperties;

export function applyWordWrap(config: WordWrapConfig): WordWrapStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ wordWrap: config.value } as unknown) as WordWrapStyles;
}
