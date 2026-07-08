// FontOpticalSizingApplier.ts — emits CSS declarations from a FontOpticalSizingConfig.
// Web is the privileged platform for typography: native CSS `fontOpticalSizing`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontOpticalSizingConfig } from './FontOpticalSizingConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontOpticalSizingStyles = Pick<CSSProperties, 'fontOpticalSizing'>;

export function applyFontOpticalSizing(config: FontOpticalSizingConfig): FontOpticalSizingStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontOpticalSizing: config.value } as FontOpticalSizingStyles;                 // single-key output
}
