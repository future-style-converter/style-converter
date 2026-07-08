// MaskBorderWidthApplier.ts — standard + Safari legacy -webkit-mask-box-image-width.
import type { MaskBorderWidthConfig } from './MaskBorderWidthConfig';

export function applyMaskBorderWidth(config: MaskBorderWidthConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return {
    maskBorderWidth: config.value,                                                    // standard
    WebkitMaskBoxImageWidth: config.value,                                            // Safari legacy
  };
}
