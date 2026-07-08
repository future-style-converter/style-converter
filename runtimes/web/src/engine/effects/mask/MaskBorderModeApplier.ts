// MaskBorderModeApplier.ts — csstype lacks the property; Record widen.
// MDN: https://developer.mozilla.org/docs/Web/CSS/mask-border-mode.
import type { MaskBorderModeConfig } from './MaskBorderModeConfig';

export function applyMaskBorderMode(config: MaskBorderModeConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return { maskBorderMode: config.value };                                            // no Safari legacy equivalent
}
