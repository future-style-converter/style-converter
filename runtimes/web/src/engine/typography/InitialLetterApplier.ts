// InitialLetterApplier.ts — emits CSS declarations from a InitialLetterConfig.
// Web is the privileged platform for typography: native CSS `initialLetter`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { InitialLetterConfig } from './InitialLetterConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type InitialLetterStyles = Pick<CSSProperties, 'initialLetter'>;

export function applyInitialLetter(config: InitialLetterConfig): InitialLetterStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { initialLetter: config.value } as InitialLetterStyles;                 // single-key output
}
