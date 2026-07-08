// RubyMergeApplier.ts — emits CSS declarations from a RubyMergeConfig.
// Web is the privileged platform for typography: native CSS `rubyMerge`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { RubyMergeConfig } from './RubyMergeConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/rubyMerge
export type RubyMergeStyles = CSSProperties;

export function applyRubyMerge(config: RubyMergeConfig): RubyMergeStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ rubyMerge: config.value } as unknown) as RubyMergeStyles;
}
