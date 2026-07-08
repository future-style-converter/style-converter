// TextEmphasisStyleApplier.ts — emits CSS declarations from a TextEmphasisStyleConfig.
// Web is the privileged platform for typography: native CSS `textEmphasisStyle`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextEmphasisStyleConfig } from './TextEmphasisStyleConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextEmphasisStyleStyles = Pick<CSSProperties, 'textEmphasisStyle'>;

export function applyTextEmphasisStyle(config: TextEmphasisStyleConfig): TextEmphasisStyleStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textEmphasisStyle: config.value } as TextEmphasisStyleStyles;                 // single-key output
}
