// TabSizeApplier.ts — emits CSS declarations from a TabSizeConfig.
// Web is the privileged platform for typography: native CSS `tabSize`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TabSizeConfig } from './TabSizeConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TabSizeStyles = Pick<CSSProperties, 'tabSize'>;

export function applyTabSize(config: TabSizeConfig): TabSizeStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { tabSize: config.value } as TabSizeStyles;                 // single-key output
}
