// BorderTopStyleApplier.ts — serialise BorderTopStyleConfig to inline CSS.
// Emits the native `borderTopStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderTopStyleConfig } from './BorderTopStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderTopStyleStyles = Pick<CSSProperties, 'borderTopStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderTopStyle(config: BorderTopStyleConfig): BorderTopStyleStyles {
  return emitStyle('borderTopStyle', config.style) as BorderTopStyleStyles;
}
