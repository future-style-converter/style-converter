// PositionFallbackApplier.ts — emits `position-fallback`.  Widened legacy key.
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-fallback.

import type { PositionFallbackConfig } from './PositionFallbackConfig';

export function applyPositionFallback(config: PositionFallbackConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { positionFallback: config.value };
}
