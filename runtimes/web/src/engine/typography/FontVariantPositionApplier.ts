// FontVariantPositionApplier.ts — emits CSS declarations from a FontVariantPositionConfig.
// Web is the privileged platform for typography: native CSS `fontVariantPosition`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariantPositionConfig } from './FontVariantPositionConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariantPositionStyles = Pick<CSSProperties, 'fontVariantPosition'>;

export function applyFontVariantPosition(config: FontVariantPositionConfig): FontVariantPositionStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariantPosition: config.value } as FontVariantPositionStyles;                 // single-key output
}
