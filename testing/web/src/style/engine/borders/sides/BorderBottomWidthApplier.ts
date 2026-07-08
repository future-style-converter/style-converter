// BorderBottomWidthApplier.ts — serialise BorderBottomWidthConfig to inline CSS.
// Emits the native `borderBottomWidth` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                              // shared single-key emit helper
import type { BorderBottomWidthConfig } from './BorderBottomWidthConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBottomWidthStyles = Pick<CSSProperties, 'borderBottomWidth'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBottomWidth(config: BorderBottomWidthConfig): BorderBottomWidthStyles {
  return emitWidth('borderBottomWidth', config.width) as BorderBottomWidthStyles;
}
