// QuotesApplier.ts — emits CSS declarations from a QuotesConfig.
// Web is the privileged platform for typography: native CSS `quotes`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { QuotesConfig } from './QuotesConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type QuotesStyles = Pick<CSSProperties, 'quotes'>;

export function applyQuotes(config: QuotesConfig): QuotesStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { quotes: config.value } as QuotesStyles;                 // single-key output
}
