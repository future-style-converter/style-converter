// TextOrientationApplier.ts — emits CSS declarations from a TextOrientationConfig.
// Web is the privileged platform for typography: native CSS `textOrientation`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextOrientationConfig } from './TextOrientationConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextOrientationStyles = Pick<CSSProperties, 'textOrientation'>;

export function applyTextOrientation(config: TextOrientationConfig): TextOrientationStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textOrientation: config.value } as TextOrientationStyles;                 // single-key output
}
