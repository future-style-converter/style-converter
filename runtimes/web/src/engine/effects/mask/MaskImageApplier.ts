// MaskImageApplier.ts — emits both `maskImage` and `WebkitMaskImage` so Safari
// (no unprefixed support as of 2024) still renders masks.  MDN:
// https://developer.mozilla.org/docs/Web/CSS/mask-image#browser_compatibility.
import { emitMasked } from './_mask_shared';
import type { MaskImageConfig } from './MaskImageConfig';

export function applyMaskImage(config: MaskImageConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return emitMasked('maskImage', config.value);                                       // standard + -webkit-
}
