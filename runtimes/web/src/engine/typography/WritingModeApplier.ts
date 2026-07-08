// WritingModeApplier.ts — emits CSS declarations from a WritingModeConfig.
// Web is the privileged platform for typography: native CSS `writingMode`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { WritingModeConfig } from './WritingModeConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type WritingModeStyles = Pick<CSSProperties, 'writingMode'>;

export function applyWritingMode(config: WritingModeConfig): WritingModeStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { writingMode: config.value } as WritingModeStyles;                 // single-key output
}
