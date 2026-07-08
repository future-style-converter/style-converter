// AnchorNameApplier.ts — emits `anchor-name`.  Widened.
// WHY widen: draft-level L1 property — https://drafts.csswg.org/css-anchor-position-1/#anchor-name.

import type { AnchorNameConfig } from './AnchorNameConfig';

export function applyAnchorName(config: AnchorNameConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { anchorName: config.value };
}
