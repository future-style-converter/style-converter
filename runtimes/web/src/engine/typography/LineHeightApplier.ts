// LineHeightApplier.ts — emits CSS declarations from a LineHeightConfig.
// Web is the privileged platform for typography: native CSS `lineHeight`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { LineHeightConfig } from './LineHeightConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type LineHeightStyles = Pick<CSSProperties, 'lineHeight'>;

export function applyLineHeight(config: LineHeightConfig): LineHeightStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { lineHeight: config.value } as LineHeightStyles;                 // single-key output
}
