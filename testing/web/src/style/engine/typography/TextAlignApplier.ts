// TextAlignApplier.ts — emits CSS declarations from a TextAlignConfig.
// Web is the privileged platform for typography: native CSS `textAlign`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextAlignConfig } from './TextAlignConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextAlignStyles = Pick<CSSProperties, 'textAlign'>;

export function applyTextAlign(config: TextAlignConfig): TextAlignStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textAlign: config.value } as TextAlignStyles;                 // single-key output
}
