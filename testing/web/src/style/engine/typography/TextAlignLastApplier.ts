// TextAlignLastApplier.ts — emits CSS declarations from a TextAlignLastConfig.
// Web is the privileged platform for typography: native CSS `textAlignLast`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextAlignLastConfig } from './TextAlignLastConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextAlignLastStyles = Pick<CSSProperties, 'textAlignLast'>;

export function applyTextAlignLast(config: TextAlignLastConfig): TextAlignLastStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textAlignLast: config.value } as TextAlignLastStyles;                 // single-key output
}
