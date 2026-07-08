// BorderLeftColorApplier.ts — serialise BorderLeftColorConfig to inline CSS.
// Emits the native `borderLeftColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderLeftColorConfig } from './BorderLeftColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderLeftColorStyles = Pick<CSSProperties, 'borderLeftColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderLeftColor(config: BorderLeftColorConfig): BorderLeftColorStyles {
  return emitColor('borderLeftColor', config.color) as BorderLeftColorStyles;
}
