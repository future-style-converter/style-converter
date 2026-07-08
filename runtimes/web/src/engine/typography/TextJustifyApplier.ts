// TextJustifyApplier.ts — emits CSS declarations from a TextJustifyConfig.
// Web is the privileged platform for typography: native CSS `textJustify`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextJustifyConfig } from './TextJustifyConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextJustifyStyles = Pick<CSSProperties, 'textJustify'>;

export function applyTextJustify(config: TextJustifyConfig): TextJustifyStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textJustify: config.value } as TextJustifyStyles;                 // single-key output
}
