// AnimationDirectionApplier.ts — native `animation-direction`.
import type { CSSProperties } from 'react';
import type { AnimationDirectionConfig } from './AnimationDirectionConfig';
export type AnimationDirectionStyles = Pick<CSSProperties, 'animationDirection'>;
export function applyAnimationDirection(c: AnimationDirectionConfig): AnimationDirectionStyles {
  if (c.value === undefined) return {};
  return { animationDirection: c.value };
}
