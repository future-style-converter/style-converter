// TextOverflowApplier.ts — emits CSS declarations from a TextOverflowConfig.
// Web is the privileged platform for typography: native CSS `textOverflow`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextOverflowConfig } from './TextOverflowConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextOverflowStyles = Pick<CSSProperties, 'textOverflow'>;

export function applyTextOverflow(config: TextOverflowConfig): TextOverflowStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textOverflow: config.value } as TextOverflowStyles;                 // single-key output
}
