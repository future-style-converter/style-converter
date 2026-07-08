// BorderBottomStyleApplier.ts — serialise BorderBottomStyleConfig to inline CSS.
// Emits the native `borderBottomStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderBottomStyleConfig } from './BorderBottomStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBottomStyleStyles = Pick<CSSProperties, 'borderBottomStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBottomStyle(config: BorderBottomStyleConfig): BorderBottomStyleStyles {
  return emitStyle('borderBottomStyle', config.style) as BorderBottomStyleStyles;
}
