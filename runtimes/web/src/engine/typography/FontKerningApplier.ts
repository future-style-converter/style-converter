// FontKerningApplier.ts — emits CSS declarations from a FontKerningConfig.
// Web is the privileged platform for typography: native CSS `fontKerning`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontKerningConfig } from './FontKerningConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontKerningStyles = Pick<CSSProperties, 'fontKerning'>;

export function applyFontKerning(config: FontKerningConfig): FontKerningStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontKerning: config.value } as FontKerningStyles;                 // single-key output
}
