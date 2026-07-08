// PositionTryOptionsApplier.ts — emits `position-try-options`.  Widened.
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-try-options.

import type { PositionTryOptionsConfig } from './PositionTryOptionsConfig';

export function applyPositionTryOptions(config: PositionTryOptionsConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { positionTryOptions: config.value };
}
