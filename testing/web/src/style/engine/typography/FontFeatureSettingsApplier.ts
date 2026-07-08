// FontFeatureSettingsApplier.ts — emits CSS declarations from a FontFeatureSettingsConfig.
// Web is the privileged platform for typography: native CSS `fontFeatureSettings`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { FontFeatureSettingsConfig } from './FontFeatureSettingsConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type FontFeatureSettingsStyles = Pick<CSSProperties, 'fontFeatureSettings'>;

export function applyFontFeatureSettings(config: FontFeatureSettingsConfig): FontFeatureSettingsStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { fontFeatureSettings: config.value } as FontFeatureSettingsStyles;                 // single-key output
}
