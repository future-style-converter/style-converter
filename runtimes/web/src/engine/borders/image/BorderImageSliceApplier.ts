// BorderImageSliceApplier.ts — serialise BorderImageSliceConfig to inline CSS.
// Emits the native `borderImageSlice` CSS property.

import type { CSSProperties } from 'react';
import { emitSlice } from './_shared';                               // shared emit helper
import type { BorderImageSliceConfig } from './BorderImageSliceConfig';

// Output type narrowed to the one key this applier owns.
export type BorderImageSliceStyles = Pick<CSSProperties, 'borderImageSlice'>;

// Pure function — emits {} when unset so callers can safely spread.
// `fill` rides along so the emitted declaration keeps the middle region
// painted (css-backgrounds-3 §6.1) exactly like the native runtimes.
export function applyBorderImageSlice(config: BorderImageSliceConfig): BorderImageSliceStyles {
  return emitSlice(config.quad, config.fill) as BorderImageSliceStyles;
}
