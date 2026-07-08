// TextDecorationLineApplier.ts — emits CSS declarations from a TextDecorationLineConfig.
// Web is the privileged platform for typography: native CSS `textDecorationLine`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextDecorationLineConfig } from './TextDecorationLineConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextDecorationLineStyles = Pick<CSSProperties, 'textDecorationLine'>;

export function applyTextDecorationLine(config: TextDecorationLineConfig): TextDecorationLineStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textDecorationLine: config.value } as TextDecorationLineStyles;                 // single-key output
}
