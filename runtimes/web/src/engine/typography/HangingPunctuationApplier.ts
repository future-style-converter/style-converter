// HangingPunctuationApplier.ts — emits CSS declarations from a HangingPunctuationConfig.
// Web is the privileged platform for typography: native CSS `hangingPunctuation`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { HangingPunctuationConfig } from './HangingPunctuationConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type HangingPunctuationStyles = Pick<CSSProperties, 'hangingPunctuation'>;

export function applyHangingPunctuation(config: HangingPunctuationConfig): HangingPunctuationStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { hangingPunctuation: config.value } as HangingPunctuationStyles;                 // single-key output
}
