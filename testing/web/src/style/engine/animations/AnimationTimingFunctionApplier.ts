// AnimationTimingFunctionApplier.ts — native.
import type { CSSProperties } from 'react';
import type { AnimationTimingFunctionConfig } from './AnimationTimingFunctionConfig';
export type AnimationTimingFunctionStyles = Pick<CSSProperties, 'animationTimingFunction'>;
export function applyAnimationTimingFunction(c: AnimationTimingFunctionConfig): AnimationTimingFunctionStyles {
  if (c.value === undefined) return {};
  return { animationTimingFunction: c.value };
}
