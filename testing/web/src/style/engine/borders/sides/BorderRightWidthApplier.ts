// BorderRightWidthApplier.ts — serialise BorderRightWidthConfig to inline CSS.
// Emits the native `borderRightWidth` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                              // shared single-key emit helper
import type { BorderRightWidthConfig } from './BorderRightWidthConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderRightWidthStyles = Pick<CSSProperties, 'borderRightWidth'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderRightWidth(config: BorderRightWidthConfig): BorderRightWidthStyles {
  return emitWidth('borderRightWidth', config.width) as BorderRightWidthStyles;
}
