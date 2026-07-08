// FontVariantCapsApplier.ts — emits CSS declarations from a FontVariantCapsConfig.
// Web is the privileged platform for typography: native CSS `fontVariantCaps`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariantCapsConfig } from './FontVariantCapsConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariantCapsStyles = Pick<CSSProperties, 'fontVariantCaps'>;

export function applyFontVariantCaps(config: FontVariantCapsConfig): FontVariantCapsStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariantCaps: config.value } as FontVariantCapsStyles;                 // single-key output
}
