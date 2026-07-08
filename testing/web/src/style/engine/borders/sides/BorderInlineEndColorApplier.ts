// BorderInlineEndColorApplier.ts — serialise BorderInlineEndColorConfig to inline CSS.
// Emits the native `borderInlineEndColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderInlineEndColorConfig } from './BorderInlineEndColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderInlineEndColorStyles = Pick<CSSProperties, 'borderInlineEndColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderInlineEndColor(config: BorderInlineEndColorConfig): BorderInlineEndColorStyles {
  return emitColor('borderInlineEndColor', config.color) as BorderInlineEndColorStyles;
}
