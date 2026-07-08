// BorderInlineStartStyleApplier.ts — serialise BorderInlineStartStyleConfig to inline CSS.
// Emits the native `borderInlineStartStyle` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitStyle } from './_shared';                              // shared single-key emit helper
import type { BorderInlineStartStyleConfig } from './BorderInlineStartStyleConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderInlineStartStyleStyles = Pick<CSSProperties, 'borderInlineStartStyle'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderInlineStartStyle(config: BorderInlineStartStyleConfig): BorderInlineStartStyleStyles {
  return emitStyle('borderInlineStartStyle', config.style) as BorderInlineStartStyleStyles;
}
