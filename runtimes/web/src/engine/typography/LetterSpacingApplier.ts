// LetterSpacingApplier.ts — emits CSS declarations from a LetterSpacingConfig.
// Web is the privileged platform for typography: native CSS `letterSpacing`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { LetterSpacingConfig } from './LetterSpacingConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type LetterSpacingStyles = Pick<CSSProperties, 'letterSpacing'>;

export function applyLetterSpacing(config: LetterSpacingConfig): LetterSpacingStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { letterSpacing: config.value } as LetterSpacingStyles;                 // single-key output
}
