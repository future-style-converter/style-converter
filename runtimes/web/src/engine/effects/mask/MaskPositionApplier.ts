// MaskPositionApplier.ts — emit standard + -webkit- prefix for Safari.
import { emitMasked } from './_mask_shared';
import type { MaskPositionConfig } from './MaskPositionConfig';

export function applyMaskPosition(config: MaskPositionConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return emitMasked('maskPosition', config.value);
}
