// PositionAreaApplier.ts — emits `position-area`.  Widened.
// WHY widen: https://drafts.csswg.org/css-anchor-position-1/#position-area.

import type { PositionAreaConfig } from './PositionAreaConfig';

export function applyPositionArea(config: PositionAreaConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { positionArea: config.value };
}
