// ScaleApplier.ts — csstype lacks the `scale` longhand key (MDN:
// https://developer.mozilla.org/docs/Web/CSS/scale).  Widen via
// Record<string,string> — same pattern as RotateApplier.ts.

import type { ScaleConfig } from './ScaleConfig';

export function applyScale(config: ScaleConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { scale: config.value };                                                   // native CSS key
}
