// LineSnapApplier.ts — emits CSS declarations from a LineSnapConfig.
// Web is the privileged platform for typography: native CSS `lineSnap`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { LineSnapConfig } from './LineSnapConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/lineSnap
export type LineSnapStyles = CSSProperties;

export function applyLineSnap(config: LineSnapConfig): LineSnapStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ lineSnap: config.value } as unknown) as LineSnapStyles;
}
