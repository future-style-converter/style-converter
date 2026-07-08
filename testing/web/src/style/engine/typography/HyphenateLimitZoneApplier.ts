// HyphenateLimitZoneApplier.ts — emits CSS declarations from a HyphenateLimitZoneConfig.
// Web is the privileged platform for typography: native CSS `hyphenateLimitZone`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { HyphenateLimitZoneConfig } from './HyphenateLimitZoneConfig';

// Output widened via `CSSProperties` escape hatch because csstype's
// strict key set doesn't always include newer typography properties.
// See MDN for the exact support matrix:
//   https://developer.mozilla.org/docs/Web/CSS/hyphenateLimitZone
export type HyphenateLimitZoneStyles = CSSProperties;

export function applyHyphenateLimitZone(config: HyphenateLimitZoneConfig): HyphenateLimitZoneStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  // Cast via `unknown` so callers can still spread the object without
  // TypeScript rejecting the extended key.
  return ({ hyphenateLimitZone: config.value } as unknown) as HyphenateLimitZoneStyles;
}
