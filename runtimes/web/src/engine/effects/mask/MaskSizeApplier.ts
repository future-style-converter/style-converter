// MaskSizeApplier.ts — emit standard + -webkit- prefix.
import { emitMasked } from './_mask_shared';
import type { MaskSizeConfig } from './MaskSizeConfig';

export function applyMaskSize(config: MaskSizeConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return emitMasked('maskSize', config.value);
}
