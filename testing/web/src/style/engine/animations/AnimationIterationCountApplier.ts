// AnimationIterationCountApplier.ts — native `animation-iteration-count`.
import type { CSSProperties } from 'react';
import type { AnimationIterationCountConfig } from './AnimationIterationCountConfig';
export type AnimationIterationCountStyles = Pick<CSSProperties, 'animationIterationCount'>;
export function applyAnimationIterationCount(c: AnimationIterationCountConfig): AnimationIterationCountStyles {
  if (c.value === undefined) return {};
  return { animationIterationCount: c.value };
}
