// MaskRepeatApplier.ts — emit standard + -webkit- for Safari.
import { emitMasked } from './_mask_shared';
import type { MaskRepeatConfig } from './MaskRepeatConfig';

export function applyMaskRepeat(config: MaskRepeatConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return emitMasked('maskRepeat', config.value);
}
