// BorderBlockStartColorApplier.ts — serialise BorderBlockStartColorConfig to inline CSS.
// Emits the native `borderBlockStartColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderBlockStartColorConfig } from './BorderBlockStartColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBlockStartColorStyles = Pick<CSSProperties, 'borderBlockStartColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBlockStartColor(config: BorderBlockStartColorConfig): BorderBlockStartColorStyles {
  return emitColor('borderBlockStartColor', config.color) as BorderBlockStartColorStyles;
}
