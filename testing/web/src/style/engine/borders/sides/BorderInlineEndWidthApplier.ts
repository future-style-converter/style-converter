// BorderInlineEndWidthApplier.ts — serialise BorderInlineEndWidthConfig to inline CSS.
// Emits the native `borderInlineEndWidth` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                              // shared single-key emit helper
import type { BorderInlineEndWidthConfig } from './BorderInlineEndWidthConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderInlineEndWidthStyles = Pick<CSSProperties, 'borderInlineEndWidth'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderInlineEndWidth(config: BorderInlineEndWidthConfig): BorderInlineEndWidthStyles {
  return emitWidth('borderInlineEndWidth', config.width) as BorderInlineEndWidthStyles;
}
