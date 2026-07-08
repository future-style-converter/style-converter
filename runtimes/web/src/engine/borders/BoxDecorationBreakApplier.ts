// BoxDecorationBreakApplier.ts — serialise BoxDecorationBreakConfig to inline CSS.
// Emits the native `boxDecorationBreak` CSS property.  Safari still requires
// the `-webkit-box-decoration-break` vendor prefix (MDN 2025-04 status), so
// we emit both keys for maximum compatibility.  csstype carries the unprefixed
// key; we type the prefixed variant loosely via `Record`.

import type { CSSProperties } from 'react';
import type { BoxDecorationBreakConfig } from './BoxDecorationBreakConfig';

// Include the WebKit prefix — typed as a wide record because csstype's
// vendor-prefixed key name isn't part of CSSProperties' standard keys.
export type BoxDecorationBreakStyles = Pick<CSSProperties, 'boxDecorationBreak'> & {
  WebkitBoxDecorationBreak?: 'slice' | 'clone';                            // Safari prefix
};

// Pure function — emits {} when unset so callers can safely spread.
export function applyBoxDecorationBreak(config: BoxDecorationBreakConfig): BoxDecorationBreakStyles {
  if (!config.value) return {};                                            // unset
  return {
    boxDecorationBreak: config.value,                                      // standard key
    WebkitBoxDecorationBreak: config.value,                                // Safari ≤ TP-189 compat
  };
}
