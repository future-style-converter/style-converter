// WidowsApplier.ts — emits CSS declarations from a WidowsConfig.
// Web is the privileged platform for typography: native CSS `widows`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WidowsConfig } from './WidowsConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type WidowsStyles = Pick<CSSProperties, 'widows'>;

export function applyWidows(config: WidowsConfig): WidowsStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { widows: config.value } as WidowsStyles;                 // single-key output
}
