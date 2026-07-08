// PositionTryApplier.ts — emits `position-try`.  Widened draft.
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-try.

import type { PositionTryConfig } from './PositionTryConfig';

export function applyPositionTry(config: PositionTryConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { positionTry: config.value };
}
