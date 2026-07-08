// BorderTopWidthApplier.ts — serialise BorderTopWidthConfig to inline CSS.
// Emits the native `borderTopWidth` CSS property; every CSS unit we accept
// from the LengthValue alphabet round-trips natively on the web.
// Documented output key matches React's CSSProperties `borderTopWidth`.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                                    // shared single-key emit
import type { BorderTopWidthConfig } from './BorderTopWidthConfig';

// Output type narrowed to the exactly one key this applier owns.
export type BorderTopWidthStyles = Pick<CSSProperties, 'borderTopWidth'>;

// Pure function — easy to spread into the parent style builder.
export function applyBorderTopWidth(config: BorderTopWidthConfig): BorderTopWidthStyles {
  return emitWidth('borderTopWidth', config.width) as BorderTopWidthStyles;
}
