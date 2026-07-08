// BorderTopLeftRadiusApplier.ts — serialise BorderTopLeftRadiusConfig to inline CSS.
// Emits the native `borderTopLeftRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderTopLeftRadiusConfig } from './BorderTopLeftRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderTopLeftRadiusStyles = Pick<CSSProperties, 'borderTopLeftRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderTopLeftRadius(config: BorderTopLeftRadiusConfig): BorderTopLeftRadiusStyles {
  return emitRadius('borderTopLeftRadius', config.radius) as BorderTopLeftRadiusStyles;
}
