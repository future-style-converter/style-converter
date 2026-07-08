// BorderBottomLeftRadiusApplier.ts — serialise BorderBottomLeftRadiusConfig to inline CSS.
// Emits the native `borderBottomLeftRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderBottomLeftRadiusConfig } from './BorderBottomLeftRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderBottomLeftRadiusStyles = Pick<CSSProperties, 'borderBottomLeftRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderBottomLeftRadius(config: BorderBottomLeftRadiusConfig): BorderBottomLeftRadiusStyles {
  return emitRadius('borderBottomLeftRadius', config.radius) as BorderBottomLeftRadiusStyles;
}
