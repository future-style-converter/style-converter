// FontStyleApplier.ts — emits CSS declarations from a FontStyleConfig.
// Web is the privileged platform for typography: native CSS `fontStyle`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontStyleConfig } from './FontStyleConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontStyleStyles = Pick<CSSProperties, 'fontStyle'>;

export function applyFontStyle(config: FontStyleConfig): FontStyleStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontStyle: config.value } as FontStyleStyles;                 // single-key output
}
