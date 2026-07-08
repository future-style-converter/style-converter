// DirectionApplier.ts — emits CSS declarations from a DirectionConfig.
// Web is the privileged platform for typography: native CSS `direction`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { DirectionConfig } from './DirectionConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type DirectionStyles = Pick<CSSProperties, 'direction'>;

export function applyDirection(config: DirectionConfig): DirectionStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { direction: config.value } as DirectionStyles;                 // single-key output
}
