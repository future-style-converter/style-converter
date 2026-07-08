// WhiteSpaceApplier.ts — emits CSS declarations from a WhiteSpaceConfig.
// Web is the privileged platform for typography: native CSS `whiteSpace`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WhiteSpaceConfig } from './WhiteSpaceConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type WhiteSpaceStyles = Pick<CSSProperties, 'whiteSpace'>;

export function applyWhiteSpace(config: WhiteSpaceConfig): WhiteSpaceStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { whiteSpace: config.value } as WhiteSpaceStyles;                 // single-key output
}
