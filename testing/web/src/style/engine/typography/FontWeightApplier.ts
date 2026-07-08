// FontWeightApplier.ts — emits CSS declarations from a FontWeightConfig.
// Web is the privileged platform for typography: native CSS `fontWeight`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontWeightConfig } from './FontWeightConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontWeightStyles = Pick<CSSProperties, 'fontWeight'>;

export function applyFontWeight(config: FontWeightConfig): FontWeightStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontWeight: config.value } as FontWeightStyles;                 // single-key output
}
