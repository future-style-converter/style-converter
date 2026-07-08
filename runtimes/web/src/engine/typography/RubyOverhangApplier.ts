// RubyOverhangApplier.ts — emits CSS declarations from a RubyOverhangConfig.
// Web is the privileged platform for typography: native CSS `rubyOverhang`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { RubyOverhangConfig } from './RubyOverhangConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/rubyOverhang
export type RubyOverhangStyles = CSSProperties;

export function applyRubyOverhang(config: RubyOverhangConfig): RubyOverhangStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ rubyOverhang: config.value } as unknown) as RubyOverhangStyles;
}
