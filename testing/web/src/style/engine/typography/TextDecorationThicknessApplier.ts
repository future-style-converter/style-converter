// TextDecorationThicknessApplier.ts — emits CSS declarations from a TextDecorationThicknessConfig.
// Web is the privileged platform for typography: native CSS `textDecorationThickness`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextDecorationThicknessConfig } from './TextDecorationThicknessConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextDecorationThicknessStyles = Pick<CSSProperties, 'textDecorationThickness'>;

export function applyTextDecorationThickness(config: TextDecorationThicknessConfig): TextDecorationThicknessStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textDecorationThickness: config.value } as TextDecorationThicknessStyles;                 // single-key output
}
