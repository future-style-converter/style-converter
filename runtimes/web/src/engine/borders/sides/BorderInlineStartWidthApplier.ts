// BorderInlineStartWidthApplier.ts — serialise BorderInlineStartWidthConfig to inline CSS.
// Emits the native `borderInlineStartWidth` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                              // shared single-key emit helper
import type { BorderInlineStartWidthConfig } from './BorderInlineStartWidthConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderInlineStartWidthStyles = Pick<CSSProperties, 'borderInlineStartWidth'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderInlineStartWidth(config: BorderInlineStartWidthConfig): BorderInlineStartWidthStyles {
  return emitWidth('borderInlineStartWidth', config.width) as BorderInlineStartWidthStyles;
}
