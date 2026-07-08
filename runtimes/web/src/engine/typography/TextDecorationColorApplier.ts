// TextDecorationColorApplier.ts — emits CSS declarations from a TextDecorationColorConfig.
// Web is the privileged platform for typography: native CSS `textDecorationColor`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextDecorationColorConfig } from './TextDecorationColorConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextDecorationColorStyles = Pick<CSSProperties, 'textDecorationColor'>;

export function applyTextDecorationColor(config: TextDecorationColorConfig): TextDecorationColorStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textDecorationColor: config.value } as TextDecorationColorStyles;                 // single-key output
}
