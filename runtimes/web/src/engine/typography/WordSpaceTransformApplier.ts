// WordSpaceTransformApplier.ts — emits CSS declarations from a WordSpaceTransformConfig.
// Web is the privileged platform for typography: native CSS `wordSpaceTransform`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WordSpaceTransformConfig } from './WordSpaceTransformConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/wordSpaceTransform
export type WordSpaceTransformStyles = CSSProperties;

export function applyWordSpaceTransform(config: WordSpaceTransformConfig): WordSpaceTransformStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ wordSpaceTransform: config.value } as unknown) as WordSpaceTransformStyles;
}
