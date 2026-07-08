// HyphensApplier.ts — emits CSS declarations from a HyphensConfig.
// Web is the privileged platform for typography: native CSS `hyphens`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { HyphensConfig } from './HyphensConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type HyphensStyles = Pick<CSSProperties, 'hyphens'>;

export function applyHyphens(config: HyphensConfig): HyphensStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { hyphens: config.value } as HyphensStyles;                 // single-key output
}
