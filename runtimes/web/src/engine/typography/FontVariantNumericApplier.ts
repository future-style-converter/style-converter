// FontVariantNumericApplier.ts — emits CSS declarations from a FontVariantNumericConfig.
// Web is the privileged platform for typography: native CSS `fontVariantNumeric`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariantNumericConfig } from './FontVariantNumericConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariantNumericStyles = Pick<CSSProperties, 'fontVariantNumeric'>;

export function applyFontVariantNumeric(config: FontVariantNumericConfig): FontVariantNumericStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariantNumeric: config.value } as FontVariantNumericStyles;                 // single-key output
}
