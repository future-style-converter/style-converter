// WhiteSpaceCollapseApplier.ts — emits CSS declarations from a WhiteSpaceCollapseConfig.
// Web is the privileged platform for typography: native CSS `whiteSpaceCollapse`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WhiteSpaceCollapseConfig } from './WhiteSpaceCollapseConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type WhiteSpaceCollapseStyles = Pick<CSSProperties, 'whiteSpaceCollapse'>;

export function applyWhiteSpaceCollapse(config: WhiteSpaceCollapseConfig): WhiteSpaceCollapseStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { whiteSpaceCollapse: config.value } as WhiteSpaceCollapseStyles;                 // single-key output
}
