// BorderTopColorApplier.ts — serialise BorderTopColorConfig to inline CSS.
// Emits the native `borderTopColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderTopColorConfig } from './BorderTopColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderTopColorStyles = Pick<CSSProperties, 'borderTopColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderTopColor(config: BorderTopColorConfig): BorderTopColorStyles {
  return emitColor('borderTopColor', config.color) as BorderTopColorStyles;
}
