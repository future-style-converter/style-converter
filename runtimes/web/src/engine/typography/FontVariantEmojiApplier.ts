// FontVariantEmojiApplier.ts — emits CSS declarations from a FontVariantEmojiConfig.
// Web is the privileged platform for typography: native CSS `fontVariantEmoji`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariantEmojiConfig } from './FontVariantEmojiConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariantEmojiStyles = Pick<CSSProperties, 'fontVariantEmoji'>;

export function applyFontVariantEmoji(config: FontVariantEmojiConfig): FontVariantEmojiStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariantEmoji: config.value } as FontVariantEmojiStyles;                 // single-key output
}
