// LineBreakApplier.ts — emits CSS declarations from a LineBreakConfig.
// Web is the privileged platform for typography: native CSS `lineBreak`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { LineBreakConfig } from './LineBreakConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type LineBreakStyles = Pick<CSSProperties, 'lineBreak'>;

export function applyLineBreak(config: LineBreakConfig): LineBreakStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { lineBreak: config.value } as LineBreakStyles;                 // single-key output
}
