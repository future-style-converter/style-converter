// TextShadowApplier.ts — emits CSS declarations from a TextShadowConfig.
// Web is the privileged platform for typography: native CSS `textShadow`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { TextShadowConfig } from './TextShadowConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type TextShadowStyles = Pick<CSSProperties, 'textShadow'>;

export function applyTextShadow(config: TextShadowConfig): TextShadowStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { textShadow: config.value } as TextShadowStyles;                 // single-key output
}
