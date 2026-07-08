// BorderTopRightRadiusApplier.ts — serialise BorderTopRightRadiusConfig to inline CSS.
// Emits the native `borderTopRightRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderTopRightRadiusConfig } from './BorderTopRightRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderTopRightRadiusStyles = Pick<CSSProperties, 'borderTopRightRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderTopRightRadius(config: BorderTopRightRadiusConfig): BorderTopRightRadiusStyles {
  return emitRadius('borderTopRightRadius', config.radius) as BorderTopRightRadiusStyles;
}
