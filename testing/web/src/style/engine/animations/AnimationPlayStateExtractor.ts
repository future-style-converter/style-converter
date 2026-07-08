// AnimationPlayStateExtractor.ts — enum-list.
import { foldLast, kebabEnumList, type IRPropertyLike } from './_shared';
import { ANIMATION_PLAY_STATE_PROPERTY_TYPE, type AnimationPlayStateConfig } from './AnimationPlayStateConfig';
export function extractAnimationPlayState(properties: IRPropertyLike[]): AnimationPlayStateConfig {
  return { value: foldLast(properties, ANIMATION_PLAY_STATE_PROPERTY_TYPE, kebabEnumList) };
}
