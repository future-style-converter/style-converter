// TextEmphasisColorApplier.ts — emits CSS declarations from a TextEmphasisColorConfig.
// Web is the privileged platform for typography: native CSS `textEmphasisColor`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextEmphasisColorConfig } from './TextEmphasisColorConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextEmphasisColorStyles = Pick<CSSProperties, 'textEmphasisColor'>;

export function applyTextEmphasisColor(config: TextEmphasisColorConfig): TextEmphasisColorStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textEmphasisColor: config.value } as TextEmphasisColorStyles;                 // single-key output
}
