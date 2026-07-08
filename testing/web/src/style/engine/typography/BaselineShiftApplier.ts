// BaselineShiftApplier.ts — emits CSS declarations from a BaselineShiftConfig.
// Web is the privileged platform for typography: native CSS `baselineShift`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { BaselineShiftConfig } from './BaselineShiftConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/baselineShift
export type BaselineShiftStyles = CSSProperties;

export function applyBaselineShift(config: BaselineShiftConfig): BaselineShiftStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ baselineShift: config.value } as unknown) as BaselineShiftStyles;
}
