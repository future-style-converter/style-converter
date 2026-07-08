// BorderStartEndRadiusApplier.ts — serialise BorderStartEndRadiusConfig to inline CSS.
// Emits the native `borderStartEndRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderStartEndRadiusConfig } from './BorderStartEndRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderStartEndRadiusStyles = Pick<CSSProperties, 'borderStartEndRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderStartEndRadius(config: BorderStartEndRadiusConfig): BorderStartEndRadiusStyles {
  return emitRadius('borderStartEndRadius', config.radius) as BorderStartEndRadiusStyles;
}
