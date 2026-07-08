// BorderBlockStartWidthApplier.ts — serialise BorderBlockStartWidthConfig to inline CSS.
// Emits the native `borderBlockStartWidth` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                              // shared single-key emit helper
import type { BorderBlockStartWidthConfig } from './BorderBlockStartWidthConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBlockStartWidthStyles = Pick<CSSProperties, 'borderBlockStartWidth'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBlockStartWidth(config: BorderBlockStartWidthConfig): BorderBlockStartWidthStyles {
  return emitWidth('borderBlockStartWidth', config.width) as BorderBlockStartWidthStyles;
}
