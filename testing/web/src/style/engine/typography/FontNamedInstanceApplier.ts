// FontNamedInstanceApplier.ts — emits CSS declarations from a FontNamedInstanceConfig.
// Web is the privileged platform for typography: native CSS `fontNamedInstance`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontNamedInstanceConfig } from './FontNamedInstanceConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/fontNamedInstance
export type FontNamedInstanceStyles = CSSProperties;

export function applyFontNamedInstance(config: FontNamedInstanceConfig): FontNamedInstanceStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ fontNamedInstance: config.value } as unknown) as FontNamedInstanceStyles;
}
