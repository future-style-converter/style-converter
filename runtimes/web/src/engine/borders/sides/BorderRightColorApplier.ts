// BorderRightColorApplier.ts — serialise BorderRightColorConfig to inline CSS.
// Emits the native `borderRightColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderRightColorConfig } from './BorderRightColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderRightColorStyles = Pick<CSSProperties, 'borderRightColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderRightColor(config: BorderRightColorConfig): BorderRightColorStyles {
  return emitColor('borderRightColor', config.color) as BorderRightColorStyles;
}
