// TextUnderlinePositionApplier.ts — emits CSS declarations from a TextUnderlinePositionConfig.
// Web is the privileged platform for typography: native CSS `textUnderlinePosition`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextUnderlinePositionConfig } from './TextUnderlinePositionConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextUnderlinePositionStyles = Pick<CSSProperties, 'textUnderlinePosition'>;

export function applyTextUnderlinePosition(config: TextUnderlinePositionConfig): TextUnderlinePositionStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textUnderlinePosition: config.value } as TextUnderlinePositionStyles;                 // single-key output
}
