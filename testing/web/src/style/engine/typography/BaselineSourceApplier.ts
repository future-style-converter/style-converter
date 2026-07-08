// BaselineSourceApplier.ts — emits CSS declarations from a BaselineSourceConfig.
// Web is the privileged platform for typography: native CSS `baselineSource`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { BaselineSourceConfig } from './BaselineSourceConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/baselineSource
export type BaselineSourceStyles = CSSProperties;

export function applyBaselineSource(config: BaselineSourceConfig): BaselineSourceStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ baselineSource: config.value } as unknown) as BaselineSourceStyles;
}
