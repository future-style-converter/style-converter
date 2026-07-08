// BorderBlockEndStyleApplier.ts — serialise BorderBlockEndStyleConfig to inline CSS.
// Emits the native `borderBlockEndStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderBlockEndStyleConfig } from './BorderBlockEndStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBlockEndStyleStyles = Pick<CSSProperties, 'borderBlockEndStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBlockEndStyle(config: BorderBlockEndStyleConfig): BorderBlockEndStyleStyles {
  return emitStyle('borderBlockEndStyle', config.style) as BorderBlockEndStyleStyles;
}
