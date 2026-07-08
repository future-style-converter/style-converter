// MaskBorderOutsetApplier.ts — standard + Safari legacy name.
import type { MaskBorderOutsetConfig } from './MaskBorderOutsetConfig';

export function applyMaskBorderOutset(config: MaskBorderOutsetConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return {
    maskBorderOutset: config.value,
    WebkitMaskBoxImageOutset: config.value,
  };
}
