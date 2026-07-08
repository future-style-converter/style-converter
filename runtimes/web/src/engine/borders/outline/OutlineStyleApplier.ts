// OutlineStyleApplier.ts — serialise OutlineStyleConfig to inline CSS.
// Emits the native `outlineStyle` CSS property.

import type { CSSProperties } from 'react';
import { emitOutlineStyle } from './_shared';                               // shared emit helper
import type { OutlineStyleConfig } from './OutlineStyleConfig';

// Output type narrowed to the one key this applier owns.
export type OutlineStyleStyles = Pick<CSSProperties, 'outlineStyle'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyOutlineStyle(config: OutlineStyleConfig): OutlineStyleStyles {
  return emitOutlineStyle(config.style) as OutlineStyleStyles;
}
