// VerticalAlignApplier.ts — emits CSS declarations from a VerticalAlignConfig.
// Web is the privileged platform for typography: native CSS `verticalAlign`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { VerticalAlignConfig } from './VerticalAlignConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type VerticalAlignStyles = Pick<CSSProperties, 'verticalAlign'>;

export function applyVerticalAlign(config: VerticalAlignConfig): VerticalAlignStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { verticalAlign: config.value } as VerticalAlignStyles;                 // single-key output
}
