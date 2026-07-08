// BorderInlineEndStyleApplier.ts — serialise BorderInlineEndStyleConfig to inline CSS.
// Emits the native `borderInlineEndStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderInlineEndStyleConfig } from './BorderInlineEndStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderInlineEndStyleStyles = Pick<CSSProperties, 'borderInlineEndStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderInlineEndStyle(config: BorderInlineEndStyleConfig): BorderInlineEndStyleStyles {
  return emitStyle('borderInlineEndStyle', config.style) as BorderInlineEndStyleStyles;
}
