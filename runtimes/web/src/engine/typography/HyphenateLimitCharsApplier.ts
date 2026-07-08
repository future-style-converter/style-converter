// HyphenateLimitCharsApplier.ts — emits CSS declarations from a HyphenateLimitCharsConfig.
// Web is the privileged platform for typography: native CSS `hyphenateLimitChars`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { HyphenateLimitCharsConfig } from './HyphenateLimitCharsConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/hyphenateLimitChars
export type HyphenateLimitCharsStyles = CSSProperties;

export function applyHyphenateLimitChars(config: HyphenateLimitCharsConfig): HyphenateLimitCharsStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ hyphenateLimitChars: config.value } as unknown) as HyphenateLimitCharsStyles;
}
