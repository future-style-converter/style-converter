// FontSizeApplier.ts — emits CSS declarations from a FontSizeConfig.
// Web is the privileged platform for typography: native CSS `fontSize`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontSizeConfig } from './FontSizeConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontSizeStyles = Pick<CSSProperties, 'fontSize'>;

export function applyFontSize(config: FontSizeConfig): FontSizeStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontSize: config.value } as FontSizeStyles;                 // single-key output
}
