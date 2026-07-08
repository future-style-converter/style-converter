// WordSpacingApplier.ts — emits CSS declarations from a WordSpacingConfig.
// Web is the privileged platform for typography: native CSS `wordSpacing`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WordSpacingConfig } from './WordSpacingConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type WordSpacingStyles = Pick<CSSProperties, 'wordSpacing'>;

export function applyWordSpacing(config: WordSpacingConfig): WordSpacingStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { wordSpacing: config.value } as WordSpacingStyles;                 // single-key output
}
