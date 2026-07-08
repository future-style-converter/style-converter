// BorderEndEndRadiusApplier.ts — serialise BorderEndEndRadiusConfig to inline CSS.
// Emits the native `borderEndEndRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderEndEndRadiusConfig } from './BorderEndEndRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderEndEndRadiusStyles = Pick<CSSProperties, 'borderEndEndRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderEndEndRadius(config: BorderEndEndRadiusConfig): BorderEndEndRadiusStyles {
  return emitRadius('borderEndEndRadius', config.radius) as BorderEndEndRadiusStyles;
}
