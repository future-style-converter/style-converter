// BorderInlineStartColorApplier.ts — serialise BorderInlineStartColorConfig to inline CSS.
// Emits the native `borderInlineStartColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderInlineStartColorConfig } from './BorderInlineStartColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderInlineStartColorStyles = Pick<CSSProperties, 'borderInlineStartColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderInlineStartColor(config: BorderInlineStartColorConfig): BorderInlineStartColorStyles {
  return emitColor('borderInlineStartColor', config.color) as BorderInlineStartColorStyles;
}
