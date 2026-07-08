// MaxLinesApplier.ts — emits CSS declarations from a MaxLinesConfig.
// Web is the privileged platform for typography: native CSS `maxLines`
// handles every variant we parse.  This file only formats and returns.

import type { CSSProperties } from 'react';
import type { MaxLinesConfig } from './MaxLinesConfig';

// Output type widened to CSSProperties because the legacy `-webkit-*` keys
// we emit for the line-clamp shim are not always present in csstype; see MDN:
//   https://developer.mozilla.org/docs/Web/CSS/line-clamp
export type MaxLinesStyles = CSSProperties;

export function applyMaxLines(config: MaxLinesConfig): MaxLinesStyles {
  const out: Record<string, string | number> = {};                 // loose accumulator
  if (config.value === undefined) return out as MaxLinesStyles;       // nothing to emit
  const v = config.value;                                             // ready string/number
  out.maxLines = v;                                                   // native CSS property
  // Line-clamp shim: Chromium/Safari require the -webkit-box trio for the
  // clamp behaviour to activate.  We emit all four keys; modern browsers
  // prefer the standard `line-clamp` anyway so this is safe to double-stamp.
  if (v !== 'none') {                                                  // keep UA default when 'none'
    out.display = '-webkit-box';                                      // required flex-like container
    (out as Record<string, string | number>)['WebkitBoxOrient'] = 'vertical'; // required axis
    (out as Record<string, string | number>)['WebkitLineClamp'] = v as string | number; // legacy key
    out.overflow = 'hidden';                                          // required for clamp to take effect
  }
  return out as MaxLinesStyles;
}
