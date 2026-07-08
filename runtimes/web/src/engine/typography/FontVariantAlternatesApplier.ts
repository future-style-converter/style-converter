// FontVariantAlternatesApplier.ts — emits CSS declarations from a FontVariantAlternatesConfig.
// Web is the privileged platform for typography: native CSS `fontVariantAlternates`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariantAlternatesConfig } from './FontVariantAlternatesConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariantAlternatesStyles = Pick<CSSProperties, 'fontVariantAlternates'>;

export function applyFontVariantAlternates(config: FontVariantAlternatesConfig): FontVariantAlternatesStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariantAlternates: config.value } as FontVariantAlternatesStyles;                 // single-key output
}
