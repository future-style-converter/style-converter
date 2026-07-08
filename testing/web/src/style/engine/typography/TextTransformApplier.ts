// TextTransformApplier.ts — emits CSS declarations from a TextTransformConfig.
// Web is the privileged platform for typography: native CSS `textTransform`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextTransformConfig } from './TextTransformConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextTransformStyles = Pick<CSSProperties, 'textTransform'>;

export function applyTextTransform(config: TextTransformConfig): TextTransformStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textTransform: config.value } as TextTransformStyles;                 // single-key output
}
