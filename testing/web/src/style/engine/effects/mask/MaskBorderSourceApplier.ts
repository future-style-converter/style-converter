// MaskBorderSourceApplier.ts — csstype has `maskBorderSource`.  Safari uses
// legacy `-webkit-mask-box-image-source` so we emit both.  MDN:
// https://developer.mozilla.org/docs/Web/CSS/mask-border-source.
import type { MaskBorderSourceConfig } from './MaskBorderSourceConfig';

export function applyMaskBorderSource(config: MaskBorderSourceConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return {
    maskBorderSource: config.value,                                                   // standard CSS L4
    WebkitMaskBoxImageSource: config.value,                                           // Safari legacy name
  };
}
