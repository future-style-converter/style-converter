// BorderRightStyleApplier.ts — serialise BorderRightStyleConfig to inline CSS.
// Emits the native `borderRightStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderRightStyleConfig } from './BorderRightStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderRightStyleStyles = Pick<CSSProperties, 'borderRightStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderRightStyle(config: BorderRightStyleConfig): BorderRightStyleStyles {
  return emitStyle('borderRightStyle', config.style) as BorderRightStyleStyles;
}
