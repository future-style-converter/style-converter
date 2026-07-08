// WordBreakApplier.ts — emits CSS declarations from a WordBreakConfig.
// Web is the privileged platform for typography: native CSS `wordBreak`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WordBreakConfig } from './WordBreakConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type WordBreakStyles = Pick<CSSProperties, 'wordBreak'>;

export function applyWordBreak(config: WordBreakConfig): WordBreakStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { wordBreak: config.value } as WordBreakStyles;                 // single-key output
}
