// LineHeightStepApplier.ts — emits CSS declarations from a LineHeightStepConfig.
// Web is the privileged platform for typography: native CSS `lineHeightStep`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { LineHeightStepConfig } from './LineHeightStepConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/lineHeightStep
export type LineHeightStepStyles = CSSProperties;

export function applyLineHeightStep(config: LineHeightStepConfig): LineHeightStepStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ lineHeightStep: config.value } as unknown) as LineHeightStepStyles;
}
