// BorderStartStartRadiusApplier.ts — serialise BorderStartStartRadiusConfig to inline CSS.
// Emits the native `borderStartStartRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderStartStartRadiusConfig } from './BorderStartStartRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderStartStartRadiusStyles = Pick<CSSProperties, 'borderStartStartRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderStartStartRadius(config: BorderStartStartRadiusConfig): BorderStartStartRadiusStyles {
  return emitRadius('borderStartStartRadius', config.radius) as BorderStartStartRadiusStyles;
}
