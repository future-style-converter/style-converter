// TextUnderlineOffsetApplier.ts — emits CSS declarations from a TextUnderlineOffsetConfig.
// Web is the privileged platform for typography: native CSS `textUnderlineOffset`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextUnderlineOffsetConfig } from './TextUnderlineOffsetConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextUnderlineOffsetStyles = Pick<CSSProperties, 'textUnderlineOffset'>;

export function applyTextUnderlineOffset(config: TextUnderlineOffsetConfig): TextUnderlineOffsetStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textUnderlineOffset: config.value } as TextUnderlineOffsetStyles;                 // single-key output
}
