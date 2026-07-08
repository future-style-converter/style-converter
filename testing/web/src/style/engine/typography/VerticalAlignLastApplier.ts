// VerticalAlignLastApplier.ts — emits CSS declarations from a VerticalAlignLastConfig.
// Web is the privileged platform for typography: native CSS `verticalAlignLast`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { VerticalAlignLastConfig } from './VerticalAlignLastConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/verticalAlignLast
export type VerticalAlignLastStyles = CSSProperties;

export function applyVerticalAlignLast(config: VerticalAlignLastConfig): VerticalAlignLastStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ verticalAlignLast: config.value } as unknown) as VerticalAlignLastStyles;
}
