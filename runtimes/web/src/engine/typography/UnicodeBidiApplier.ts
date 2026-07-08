// UnicodeBidiApplier.ts — emits CSS declarations from a UnicodeBidiConfig.
// Web is the privileged platform for typography: native CSS `unicodeBidi`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { UnicodeBidiConfig } from './UnicodeBidiConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type UnicodeBidiStyles = Pick<CSSProperties, 'unicodeBidi'>;

export function applyUnicodeBidi(config: UnicodeBidiConfig): UnicodeBidiStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { unicodeBidi: config.value } as UnicodeBidiStyles;                 // single-key output
}
