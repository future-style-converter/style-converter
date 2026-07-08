// FontSizeAdjustApplier.ts — emits CSS declarations from a FontSizeAdjustConfig.
// Web is the privileged platform for typography: native CSS `fontSizeAdjust`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontSizeAdjustConfig } from './FontSizeAdjustConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontSizeAdjustStyles = Pick<CSSProperties, 'fontSizeAdjust'>;

export function applyFontSizeAdjust(config: FontSizeAdjustConfig): FontSizeAdjustStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontSizeAdjust: config.value } as FontSizeAdjustStyles;                 // single-key output
}
