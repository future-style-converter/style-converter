// HyphenateCharacterApplier.ts — emits CSS declarations from a HyphenateCharacterConfig.
// Web is the privileged platform for typography: native CSS `hyphenateCharacter`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { HyphenateCharacterConfig } from './HyphenateCharacterConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type HyphenateCharacterStyles = Pick<CSSProperties, 'hyphenateCharacter'>;

export function applyHyphenateCharacter(config: HyphenateCharacterConfig): HyphenateCharacterStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { hyphenateCharacter: config.value } as HyphenateCharacterStyles;                 // single-key output
}
