// MaskBorderRepeatApplier.ts — standard + Safari legacy.
import type { MaskBorderRepeatConfig } from './MaskBorderRepeatConfig';

export function applyMaskBorderRepeat(config: MaskBorderRepeatConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return {
    maskBorderRepeat: config.value,
    WebkitMaskBoxImageRepeat: config.value,
  };
}
