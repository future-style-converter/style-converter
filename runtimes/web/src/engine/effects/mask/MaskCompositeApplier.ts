// MaskCompositeApplier.ts — standard + -webkit- prefix.  Safari's -webkit-
// variant uses a different keyword set ('source-over' etc.) but the CSS spec
// mandates Safari translate these keywords — we pass through untranslated and
// rely on Safari's compatibility shim (MDN note on mask-composite).
import { emitMasked } from './_mask_shared';
import type { MaskCompositeConfig } from './MaskCompositeConfig';

export function applyMaskComposite(config: MaskCompositeConfig): Record<string, string> {
  if (config.value === undefined) return {};
  return emitMasked('maskComposite', config.value);
}
