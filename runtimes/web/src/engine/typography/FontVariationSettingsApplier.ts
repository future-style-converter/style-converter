// FontVariationSettingsApplier.ts — emits CSS declarations from a FontVariationSettingsConfig.
// Web is the privileged platform for typography: native CSS `fontVariationSettings`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontVariationSettingsConfig } from './FontVariationSettingsConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontVariationSettingsStyles = Pick<CSSProperties, 'fontVariationSettings'>;

export function applyFontVariationSettings(config: FontVariationSettingsConfig): FontVariationSettingsStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontVariationSettings: config.value } as FontVariationSettingsStyles;                 // single-key output
}
