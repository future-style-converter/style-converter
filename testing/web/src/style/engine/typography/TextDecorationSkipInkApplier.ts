// TextDecorationSkipInkApplier.ts — emits CSS declarations from a TextDecorationSkipInkConfig.
// Web is the privileged platform for typography: native CSS `textDecorationSkipInk`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextDecorationSkipInkConfig } from './TextDecorationSkipInkConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextDecorationSkipInkStyles = Pick<CSSProperties, 'textDecorationSkipInk'>;

export function applyTextDecorationSkipInk(config: TextDecorationSkipInkConfig): TextDecorationSkipInkStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textDecorationSkipInk: config.value } as TextDecorationSkipInkStyles;                 // single-key output
}
