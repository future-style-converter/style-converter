// AnimationFillModeExtractor.ts — enum-list.
import { foldLast, kebabEnumList, type IRPropertyLike } from './_shared';
import { ANIMATION_FILL_MODE_PROPERTY_TYPE, type AnimationFillModeConfig } from './AnimationFillModeConfig';
export function extractAnimationFillMode(properties: IRPropertyLike[]): AnimationFillModeConfig {
  return { value: foldLast(properties, ANIMATION_FILL_MODE_PROPERTY_TYPE, kebabEnumList) };
}
