// AnimationDurationApplier.ts — native `animation-duration`.
import type { CSSProperties } from 'react';
import type { AnimationDurationConfig } from './AnimationDurationConfig';
export type AnimationDurationStyles = Pick<CSSProperties, 'animationDuration'>;
export function applyAnimationDuration(c: AnimationDurationConfig): AnimationDurationStyles {
  if (c.value === undefined) return {};
  return { animationDuration: c.value };
}
