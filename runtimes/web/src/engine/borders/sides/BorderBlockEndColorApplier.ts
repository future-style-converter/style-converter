// BorderBlockEndColorApplier.ts — serialise BorderBlockEndColorConfig to inline CSS.
// Emits the native `borderBlockEndColor` CSS property; web browsers render every value
// in the shared alphabet natively.

import type { CSSProperties } from 'react';
import { emitColor } from './_shared';                              // shared single-key emit helper
import type { BorderBlockEndColorConfig } from './BorderBlockEndColorConfig';

// Output type narrowed to exactly the one key this applier owns.
export type BorderBlockEndColorStyles = Pick<CSSProperties, 'borderBlockEndColor'>;

// Pure function — emits {} when unset so callers can spread safely.
export function applyBorderBlockEndColor(config: BorderBlockEndColorConfig): BorderBlockEndColorStyles {
  return emitColor('borderBlockEndColor', config.color) as BorderBlockEndColorStyles;
}
