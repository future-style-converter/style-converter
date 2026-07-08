// OutlineOffsetApplier.ts — serialise OutlineOffsetConfig to inline CSS.
// Emits the native `outlineOffset` CSS property.

import type { CSSProperties } from 'react';
import { emitOutlineOffset } from './_shared';                               // shared emit helper
import type { OutlineOffsetConfig } from './OutlineOffsetConfig';

// Output type narrowed to the one key this applier owns.
export type OutlineOffsetStyles = Pick<CSSProperties, 'outlineOffset'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyOutlineOffset(config: OutlineOffsetConfig): OutlineOffsetStyles {
  return emitOutlineOffset(config.offset) as OutlineOffsetStyles;
}
