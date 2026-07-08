// BorderBoundaryApplier.ts — serialise BorderBoundaryConfig to inline CSS.
// Emits the `border-boundary` CSS property (camelCased `borderBoundary`).
// No browser implements it yet (2026-04) so the declaration is a no-op in
// practice — but forward-compatible by design.  When a browser ships support
// it Just Works™.

import type { CSSProperties } from 'react';
import type { BorderBoundaryConfig } from './BorderBoundaryConfig';

// csstype doesn't ship `borderBoundary` yet — widen the type manually.
export type BorderBoundaryStyles = CSSProperties & {
  borderBoundary?: 'none' | 'parent' | 'display';                          // CSS Borders L4
};

// Pure function — emits {} when unset so callers can safely spread.
export function applyBorderBoundary(config: BorderBoundaryConfig): BorderBoundaryStyles {
  if (!config.value) return {};                                            // unset
  return { borderBoundary: config.value };                                  // passthrough
}
