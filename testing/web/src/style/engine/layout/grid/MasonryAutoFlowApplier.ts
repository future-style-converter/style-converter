// MasonryAutoFlowApplier.ts — emits `masonry-auto-flow`.  L3 draft; widened.
// WHY widen: csstype has no `masonry-auto-flow` key — see
// https://drafts.csswg.org/css-grid-3/#masonry-auto-flow.

import type { MasonryAutoFlowConfig } from './MasonryAutoFlowConfig';

export function applyMasonryAutoFlow(config: MasonryAutoFlowConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { masonryAutoFlow: config.value };
}
