// RubyPositionApplier.ts — emits CSS declarations from a RubyPositionConfig.
// Web is the privileged platform for typography: native CSS `rubyPosition`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { RubyPositionConfig } from './RubyPositionConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type RubyPositionStyles = Pick<CSSProperties, 'rubyPosition'>;

export function applyRubyPosition(config: RubyPositionConfig): RubyPositionStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { rubyPosition: config.value } as RubyPositionStyles;                 // single-key output
}
