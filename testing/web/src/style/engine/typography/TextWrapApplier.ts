// TextWrapApplier.ts — emits CSS declarations from a TextWrapConfig.
// Web is the privileged platform for typography: native CSS `textWrap`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextWrapConfig } from './TextWrapConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextWrapStyles = Pick<CSSProperties, 'textWrap'>;

export function applyTextWrap(config: TextWrapConfig): TextWrapStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textWrap: config.value } as TextWrapStyles;                 // single-key output
}
