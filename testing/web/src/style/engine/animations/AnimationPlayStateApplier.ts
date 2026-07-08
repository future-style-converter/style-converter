// AnimationPlayStateApplier.ts — native `animation-play-state`.
import type { CSSProperties } from 'react';
import type { AnimationPlayStateConfig } from './AnimationPlayStateConfig';
export type AnimationPlayStateStyles = Pick<CSSProperties, 'animationPlayState'>;
export function applyAnimationPlayState(c: AnimationPlayStateConfig): AnimationPlayStateStyles {
  if (c.value === undefined) return {};
  return { animationPlayState: c.value };
}
