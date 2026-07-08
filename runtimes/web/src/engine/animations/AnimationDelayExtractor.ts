// AnimationDelayExtractor.ts — IR -> config.  `data` is serialised as the raw
// list because AnimationDelayProperty has a single `delays` field.
import { foldLast, timeListToCss, type IRPropertyLike } from './_shared';
import { ANIMATION_DELAY_PROPERTY_TYPE, type AnimationDelayConfig } from './AnimationDelayConfig';

export function extractAnimationDelay(properties: IRPropertyLike[]): AnimationDelayConfig {
  // IR produces `data = [{ms:...}, ...]` directly (single-field unwrap).
  return { value: foldLast(properties, ANIMATION_DELAY_PROPERTY_TYPE, timeListToCss) };
}
