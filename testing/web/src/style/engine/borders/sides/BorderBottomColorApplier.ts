// BorderBottomColorApplier.ts — serialise BorderBottomColorConfig to inline CSS.
// Emits the native `borderBottomColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderBottomColorConfig } from './BorderBottomColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBottomColorStyles = Pick<CSSProperties, 'borderBottomColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBottomColor(config: BorderBottomColorConfig): BorderBottomColorStyles {
  return emitColor('borderBottomColor', config.color) as BorderBottomColorStyles;
}
