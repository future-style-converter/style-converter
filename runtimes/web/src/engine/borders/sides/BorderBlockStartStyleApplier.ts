// BorderBlockStartStyleApplier.ts — serialise BorderBlockStartStyleConfig to inline CSS.
// Emits the native `borderBlockStartStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderBlockStartStyleConfig } from './BorderBlockStartStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBlockStartStyleStyles = Pick<CSSProperties, 'borderBlockStartStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBlockStartStyle(config: BorderBlockStartStyleConfig): BorderBlockStartStyleStyles {
  return emitStyle('borderBlockStartStyle', config.style) as BorderBlockStartStyleStyles;
}
