// BorderLeftWidthApplier.ts — serialise BorderLeftWidthConfig to inline CSS.
// Emits the native `borderLeftWidth` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                              // shared single-key emit helper
import type { BorderLeftWidthConfig } from './BorderLeftWidthConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderLeftWidthStyles = Pick<CSSProperties, 'borderLeftWidth'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderLeftWidth(config: BorderLeftWidthConfig): BorderLeftWidthStyles {
  return emitWidth('borderLeftWidth', config.width) as BorderLeftWidthStyles;
}
