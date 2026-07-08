// BorderImageRepeatApplier.ts — serialise BorderImageRepeatConfig to inline CSS.
// Emits the native `borderImageRepeat` CSS property.

import type { CSSProperties } from 'react';
import { emitRepeat } from './_shared';                               // shared emit helper
import type { BorderImageRepeatConfig } from './BorderImageRepeatConfig';

// Output type narrowed to the one key this applier owns.
export type BorderImageRepeatStyles = Pick<CSSProperties, 'borderImageRepeat'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderImageRepeat(config: BorderImageRepeatConfig): BorderImageRepeatStyles {
  return emitRepeat(config.repeat) as BorderImageRepeatStyles;
}
