// OverflowWrapApplier.ts — emits CSS declarations from a OverflowWrapConfig.
// Web is the privileged platform for typography: native CSS `overflowWrap`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { OverflowWrapConfig } from './OverflowWrapConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type OverflowWrapStyles = Pick<CSSProperties, 'overflowWrap'>;

export function applyOverflowWrap(config: OverflowWrapConfig): OverflowWrapStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { overflowWrap: config.value } as OverflowWrapStyles;                 // single-key output
}
