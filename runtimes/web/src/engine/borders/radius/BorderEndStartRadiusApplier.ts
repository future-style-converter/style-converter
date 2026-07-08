// BorderEndStartRadiusApplier.ts — serialise BorderEndStartRadiusConfig to inline CSS.
// Emits the native `borderEndStartRadius` CSS property.  Elliptical pairs round-trip as
// the space-separated "Xpx Ypx" form which CSS interprets per-axis.

import type { CSSProperties } from 'react';
import { emitRadius } from './_shared';                                 // shared emit helper
import type { BorderEndStartRadiusConfig } from './BorderEndStartRadiusConfig';

// Output type narrowed to the one key this applier owns.
export type BorderEndStartRadiusStyles = Pick<CSSProperties, 'borderEndStartRadius'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderEndStartRadius(config: BorderEndStartRadiusConfig): BorderEndStartRadiusStyles {
  return emitRadius('borderEndStartRadius', config.radius) as BorderEndStartRadiusStyles;
}
