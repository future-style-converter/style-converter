// FontVariantEastAsianApplier.ts — emits CSS declarations from a FontVariantEastAsianConfig.
// Web is the privileged platform for typography: native CSS `fontVariantEastAsian`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariantEastAsianConfig } from './FontVariantEastAsianConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariantEastAsianStyles = Pick<CSSProperties, 'fontVariantEastAsian'>;

export function applyFontVariantEastAsian(config: FontVariantEastAsianConfig): FontVariantEastAsianStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariantEastAsian: config.value } as FontVariantEastAsianStyles;                 // single-key output
}
