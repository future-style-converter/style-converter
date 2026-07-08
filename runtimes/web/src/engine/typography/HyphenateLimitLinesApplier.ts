// HyphenateLimitLinesApplier.ts — emits CSS declarations from a HyphenateLimitLinesConfig.
// Web is the privileged platform for typography: native CSS `hyphenateLimitLines`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { HyphenateLimitLinesConfig } from './HyphenateLimitLinesConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/hyphenateLimitLines
export type HyphenateLimitLinesStyles = CSSProperties;

export function applyHyphenateLimitLines(config: HyphenateLimitLinesConfig): HyphenateLimitLinesStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ hyphenateLimitLines: config.value } as unknown) as HyphenateLimitLinesStyles;
}
