// TextRenderingApplier.ts — emits CSS declarations from a TextRenderingConfig.
// Web is the privileged platform for typography: native CSS `textRendering`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextRenderingConfig } from './TextRenderingConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextRenderingStyles = Pick<CSSProperties, 'textRendering'>;

export function applyTextRendering(config: TextRenderingConfig): TextRenderingStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textRendering: config.value } as TextRenderingStyles;                 // single-key output
}
