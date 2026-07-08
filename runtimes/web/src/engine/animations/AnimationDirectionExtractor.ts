// AnimationDirectionExtractor.ts — enum-list.  kebab-cases each entry.
import { foldLast, kebabEnumList, type IRPropertyLike } from './_shared';
import { ANIMATION_DIRECTION_PROPERTY_TYPE, type AnimationDirectionConfig } from './AnimationDirectionConfig';
export function extractAnimationDirection(properties: IRPropertyLike[]): AnimationDirectionConfig {
  return { value: foldLast(properties, ANIMATION_DIRECTION_PROPERTY_TYPE, kebabEnumList) };
}
