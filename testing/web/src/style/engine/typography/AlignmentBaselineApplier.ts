// AlignmentBaselineApplier.ts — emits CSS declarations from a AlignmentBaselineConfig.
// Web is the privileged platform for typography: native CSS `alignmentBaseline`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { AlignmentBaselineConfig } from './AlignmentBaselineConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/alignmentBaseline
export type AlignmentBaselineStyles = CSSProperties;

export function applyAlignmentBaseline(config: AlignmentBaselineConfig): AlignmentBaselineStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ alignmentBaseline: config.value } as unknown) as AlignmentBaselineStyles;
}
