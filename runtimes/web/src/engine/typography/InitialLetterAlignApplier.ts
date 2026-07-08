// InitialLetterAlignApplier.ts — emits CSS declarations from a InitialLetterAlignConfig.
// Web is the privileged platform for typography: native CSS `initialLetterAlign`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { InitialLetterAlignConfig } from './InitialLetterAlignConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/initialLetterAlign
export type InitialLetterAlignStyles = CSSProperties;

export function applyInitialLetterAlign(config: InitialLetterAlignConfig): InitialLetterAlignStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ initialLetterAlign: config.value } as unknown) as InitialLetterAlignStyles;
}
