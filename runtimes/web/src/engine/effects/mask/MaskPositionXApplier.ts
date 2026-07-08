// MaskPositionXApplier.ts — csstype doesn't ship mask-position-x; Record widen.
// MDN: https://developer.mozilla.org/docs/Web/CSS/mask-position-x.
import type { MaskPositionXConfig } from './MaskPositionXConfig';

export function applyMaskPositionX(config: MaskPositionXConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { maskPositionX: config.value, WebkitMaskPositionX: config.value };
}
