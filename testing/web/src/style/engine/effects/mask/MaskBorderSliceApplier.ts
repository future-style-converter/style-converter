// MaskBorderSliceApplier.ts — standard + Safari `-webkit-mask-box-image-slice`.
import type { MaskBorderSliceConfig } from './MaskBorderSliceConfig';

export function applyMaskBorderSlice(config: MaskBorderSliceConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return {
    maskBorderSlice: config.value,                                                    // CSS L4 standard
    WebkitMaskBoxImageSlice: config.value,                                            // Safari legacy
  };
}
