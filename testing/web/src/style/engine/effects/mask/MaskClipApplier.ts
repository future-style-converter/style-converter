// MaskClipApplier.ts — standard + -webkit-.
import { emitMasked } from './_mask_shared';
import type { MaskClipConfig } from './MaskClipConfig';

export function applyMaskClip(config: MaskClipConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return emitMasked('maskClip', config.value);
}
