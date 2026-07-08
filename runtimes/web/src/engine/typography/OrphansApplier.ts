// OrphansApplier.ts — emits CSS declarations from a OrphansConfig.
// Web is the privileged platform for typography: native CSS `orphans`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { OrphansConfig } from './OrphansConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type OrphansStyles = Pick<CSSProperties, 'orphans'>;

export function applyOrphans(config: OrphansConfig): OrphansStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { orphans: config.value } as OrphansStyles;                 // single-key output
}
