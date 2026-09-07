// CornerShapeApplier.ts — serialise CornerShapeConfig to inline CSS.
// `corner-shape` is an experimental CSS Borders L4 property. We emit the
// standard key plus a `-webkit-corner-shape` spelling that was added for
// early flag-builds of Chromium.
//
// The `-webkit-` half is INERT, and the TODO that used to sit here ("remove
// the prefix once Chromium ships unflagged, target: Chrome 130") was stale
// on both halves — retro P2e, finding A6#15. The capture browser is long
// past that target: `~/.cache/puppeteer/chrome` holds 147/148/150/151, and
// a Chromium that understands `corner-shape` reads the unprefixed key we
// already emit, while any browser that does not simply drops BOTH
// declarations (css-syntax-3 §"Consume a declaration": an unknown property
// name makes the declaration invalid and it is discarded — the same error
// handling CSS 2.1 §4.2 specifies). So the prefixed key can never change a
// render on any engine.
//
// It is kept rather than deleted because deleting it is not a comment
// change: BorderMisc.test.ts asserts `WebkitCornerShape` on all six
// keywords, so the key and its pin must go together. Cost of keeping it is
// zero on the corpus — MEASURED zero `CornerShape` properties across all
// 1435 wave49-final per-test IR documents.

import type { CSSProperties } from 'react';
import type { CornerShapeConfig, CornerShapeValue } from './CornerShapeConfig';

// csstype doesn't know about corner-shape yet — extend CSSProperties
// manually with a wide record type for the experimental keys.
export type CornerShapeStyles = CSSProperties & {
  cornerShape?: CornerShapeValue;                                          // standard key (CSS Borders L4)
  WebkitCornerShape?: CornerShapeValue;                                    // legacy WebKit spelling
};

// Pure function — emits {} when unset so callers can safely spread.
export function applyCornerShape(config: CornerShapeConfig): CornerShapeStyles {
  if (!config.value) return {};                                            // unset
  return {
    cornerShape: config.value,                                             // standard property
    WebkitCornerShape: config.value,                                       // experimental prefix
  };
}
