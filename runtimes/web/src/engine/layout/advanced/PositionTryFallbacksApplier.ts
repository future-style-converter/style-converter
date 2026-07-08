// PositionTryFallbacksApplier.ts — emits `position-try-fallbacks`.  Widened.
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-try-fallbacks.

import type { PositionTryFallbacksConfig } from './PositionTryFallbacksConfig';

export function applyPositionTryFallbacks(config: PositionTryFallbacksConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { positionTryFallbacks: config.value };
}
