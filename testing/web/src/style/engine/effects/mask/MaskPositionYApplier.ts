// MaskPositionYApplier.ts — csstype lacks mask-position-y; Record widen.
// MDN: https://developer.mozilla.org/docs/Web/CSS/mask-position-y.
import type { MaskPositionYConfig } from './MaskPositionYConfig';

export function applyMaskPositionY(config: MaskPositionYConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { maskPositionY: config.value, WebkitMaskPositionY: config.value };
}
