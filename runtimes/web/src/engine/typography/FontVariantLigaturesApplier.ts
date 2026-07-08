// FontVariantLigaturesApplier.ts — emits CSS declarations from a FontVariantLigaturesConfig.
// Web is the privileged platform for typography: native CSS `fontVariantLigatures`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariantLigaturesConfig } from './FontVariantLigaturesConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariantLigaturesStyles = Pick<CSSProperties, 'fontVariantLigatures'>;

export function applyFontVariantLigatures(config: FontVariantLigaturesConfig): FontVariantLigaturesStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariantLigatures: config.value } as FontVariantLigaturesStyles;                 // single-key output
}
