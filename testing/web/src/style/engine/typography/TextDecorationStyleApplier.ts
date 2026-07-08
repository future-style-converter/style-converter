// TextDecorationStyleApplier.ts — emits CSS declarations from a TextDecorationStyleConfig.
// Web is the privileged platform for typography: native CSS `textDecorationStyle`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextDecorationStyleConfig } from './TextDecorationStyleConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextDecorationStyleStyles = Pick<CSSProperties, 'textDecorationStyle'>;

export function applyTextDecorationStyle(config: TextDecorationStyleConfig): TextDecorationStyleStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textDecorationStyle: config.value } as TextDecorationStyleStyles;                 // single-key output
}
