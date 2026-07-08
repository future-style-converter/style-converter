// BorderImageWidthApplier.ts — serialise BorderImageWidthConfig to inline CSS.
// Emits the native `borderImageWidth` CSS property.

import type { CSSProperties } from 'react';
import { emitWidth } from './_shared';                               // shared emit helper
import type { BorderImageWidthConfig } from './BorderImageWidthConfig';

// Output type narrowed to the one key this applier owns.
export type BorderImageWidthStyles = Pick<CSSProperties, 'borderImageWidth'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderImageWidth(config: BorderImageWidthConfig): BorderImageWidthStyles {
  return emitWidth(config.quad) as BorderImageWidthStyles;
}
