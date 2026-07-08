// KerningApplier.ts — emits CSS declarations from a KerningConfig.
// Web is the privileged platform for typography: native CSS `kerning`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { KerningConfig } from './KerningConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/kerning
export type KerningStyles = CSSProperties;

export function applyKerning(config: KerningConfig): KerningStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ kerning: config.value } as unknown) as KerningStyles;
}
