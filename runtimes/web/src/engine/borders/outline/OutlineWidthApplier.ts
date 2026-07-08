// OutlineWidthApplier.ts — serialise OutlineWidthConfig to inline CSS.
// Emits the native `outlineWidth` CSS property.

import type { CSSProperties } from 'react';
import { emitOutlineWidth } from './_shared';                               // shared emit helper
import type { OutlineWidthConfig } from './OutlineWidthConfig';

// Output type narrowed to the one key this applier owns.
export type OutlineWidthStyles = Pick<CSSProperties, 'outlineWidth'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyOutlineWidth(config: OutlineWidthConfig): OutlineWidthStyles {
  return emitOutlineWidth(config.width) as OutlineWidthStyles;
}
