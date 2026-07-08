// BorderLeftStyleApplier.ts — serialise BorderLeftStyleConfig to inline CSS.
// Emits the native `borderLeftStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderLeftStyleConfig } from './BorderLeftStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderLeftStyleStyles = Pick<CSSProperties, 'borderLeftStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderLeftStyle(config: BorderLeftStyleConfig): BorderLeftStyleStyles {
  return emitStyle('borderLeftStyle', config.style) as BorderLeftStyleStyles;
}
