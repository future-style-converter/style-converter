// BorderBlockEndWidthApplier.ts — serialise BorderBlockEndWidthConfig to inline CSS.
// Emits the native `borderBlockEndWidth` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                              // shared single-key emit helper
import type { BorderBlockEndWidthConfig } from './BorderBlockEndWidthConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBlockEndWidthStyles = Pick<CSSProperties, 'borderBlockEndWidth'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBlockEndWidth(config: BorderBlockEndWidthConfig): BorderBlockEndWidthStyles {
  return emitWidth('borderBlockEndWidth', config.width) as BorderBlockEndWidthStyles;
}
