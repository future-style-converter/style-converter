// TextEmphasisApplier.ts — emits CSS declarations from a TextEmphasisConfig.
// Web is the privileged platform for typography: native CSS `textEmphasis`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextEmphasisConfig } from './TextEmphasisConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextEmphasisStyles = Pick<CSSProperties, 'textEmphasis'>;

export function applyTextEmphasis(config: TextEmphasisConfig): TextEmphasisStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textEmphasis: config.value } as TextEmphasisStyles;                 // single-key output
}
