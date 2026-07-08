// FontFamilyApplier.ts — emits CSS declarations from a FontFamilyConfig.
// Web is the privileged platform for typography: native CSS `fontFamily`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontFamilyConfig } from './FontFamilyConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontFamilyStyles = Pick<CSSProperties, 'fontFamily'>;

export function applyFontFamily(config: FontFamilyConfig): FontFamilyStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontFamily: config.value } as FontFamilyStyles;                 // single-key output
}
