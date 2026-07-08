// CornerShapeApplier.ts — serialise CornerShapeConfig to inline CSS.
// `corner-shape` is an experimental CSS Borders L4 property; Chromium has
// shipped it behind the `CSSCornerShaping` flag (2025-Q4) and WebKit/Gecko
// haven't implemented it yet.  We emit both the standard key and the legacy
// `-webkit-corner-shape` spelling to keep early Chromium flag-builds happy.
//
// TODO: Remove the -webkit- prefix once Chromium ships unflagged (target: Chrome 130).

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
