// TextEmphasisPositionApplier.ts — emits CSS declarations from a TextEmphasisPositionConfig.
// Web is the privileged platform for typography: native CSS `textEmphasisPosition`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextEmphasisPositionConfig } from './TextEmphasisPositionConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextEmphasisPositionStyles = Pick<CSSProperties, 'textEmphasisPosition'>;

export function applyTextEmphasisPosition(config: TextEmphasisPositionConfig): TextEmphasisPositionStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textEmphasisPosition: config.value } as TextEmphasisPositionStyles;                 // single-key output
}
