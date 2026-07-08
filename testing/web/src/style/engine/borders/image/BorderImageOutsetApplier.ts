// BorderImageOutsetApplier.ts — serialise BorderImageOutsetConfig to inline CSS.
// Emits the native `borderImageOutset` CSS property.

import type { CSSProperties } from 'react';
import { emitOutset } from './_shared';                               // shared emit helper
import type { BorderImageOutsetConfig } from './BorderImageOutsetConfig';

// Output type narrowed to the one key this applier owns.
export type BorderImageOutsetStyles = Pick<CSSProperties, 'borderImageOutset'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderImageOutset(config: BorderImageOutsetConfig): BorderImageOutsetStyles {
  return emitOutset(config.quad) as BorderImageOutsetStyles;
}
