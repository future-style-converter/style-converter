// RubyAlignApplier.ts — emits CSS declarations from a RubyAlignConfig.
// Web is the privileged platform for typography: native CSS `rubyAlign`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { RubyAlignConfig } from './RubyAlignConfig';

// Output type narrowed to the exactly one CSS key this applier owns.
export type RubyAlignStyles = Pick<CSSProperties, 'rubyAlign'>;

export function applyRubyAlign(config: RubyAlignConfig): RubyAlignStyles {
  if (config.value === undefined) return {};                        // unset -> empty
  return { rubyAlign: config.value } as RubyAlignStyles;                 // single-key output
}
