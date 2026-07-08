// MaskOriginApplier.ts — standard + -webkit-.
import { emitMasked } from './_mask_shared';
import type { MaskOriginConfig } from './MaskOriginConfig';

export function applyMaskOrigin(config: MaskOriginConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return emitMasked('maskOrigin', config.value);
}
