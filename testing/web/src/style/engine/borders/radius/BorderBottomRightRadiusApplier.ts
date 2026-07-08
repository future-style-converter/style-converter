// BorderBottomRightRadiusApplier.ts — serialise BorderBottomRightRadiusConfig to inline CSS.
// Emits the native `borderBottomRightRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderBottomRightRadiusConfig } from './BorderBottomRightRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderBottomRightRadiusStyles = Pick<CSSProperties, 'borderBottomRightRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderBottomRightRadius(config: BorderBottomRightRadiusConfig): BorderBottomRightRadiusStyles {
  return emitRadius('borderBottomRightRadius', config.radius) as BorderBottomRightRadiusStyles;
}
