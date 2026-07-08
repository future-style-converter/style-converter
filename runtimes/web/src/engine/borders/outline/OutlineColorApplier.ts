// OutlineColorApplier.ts — serialise OutlineColorConfig to inline CSS.
// Emits the native `outlineColor` CSS property.

import type { CSSProperties } from 'react';
import { emitOutlineColor } from './_shared';                               // shared emit helper
import type { OutlineColorConfig } from './OutlineColorConfig';

// Output type narrowed to the one key this applier owns.
export type OutlineColorStyles = Pick<CSSProperties, 'outlineColor'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyOutlineColor(config: OutlineColorConfig): OutlineColorStyles {
  return emitOutlineColor(config.color) as OutlineColorStyles;
}
