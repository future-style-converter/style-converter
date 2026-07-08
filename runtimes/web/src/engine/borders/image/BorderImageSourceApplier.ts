// BorderImageSourceApplier.ts — serialise BorderImageSourceConfig to inline CSS.
// Emits the native `borderImageSource` CSS property.

import type { CSSProperties } from 'react';
import { emitSource } from './_shared';                               // shared emit helper
import type { BorderImageSourceConfig } from './BorderImageSourceConfig';

// Output type narrowed to the one key this applier owns.
export type BorderImageSourceStyles = Pick<CSSProperties, 'borderImageSource'>;

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderImageSource(config: BorderImageSourceConfig): BorderImageSourceStyles {
  return emitSource(config.source) as BorderImageSourceStyles;
}
