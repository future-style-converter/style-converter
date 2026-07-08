// TextIndentApplier.ts — emits CSS declarations from a TextIndentConfig.
// Web is the privileged platform for typography: native CSS `textIndent`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextIndentConfig } from './TextIndentConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextIndentStyles = Pick<CSSProperties, 'textIndent'>;

export function applyTextIndent(config: TextIndentConfig): TextIndentStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textIndent: config.value } as TextIndentStyles;                 // single-key output
}
