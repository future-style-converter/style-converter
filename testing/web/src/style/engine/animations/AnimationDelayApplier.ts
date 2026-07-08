// AnimationDelayApplier.ts — native `animation-delay`.
import type { CSSProperties } from 'react';
import type { AnimationDelayConfig } from './AnimationDelayConfig';
export type AnimationDelayStyles = Pick<CSSProperties, 'animationDelay'>;
export function applyAnimationDelay(c: AnimationDelayConfig): AnimationDelayStyles {
  if (c.value === undefined) return {};
  return { animationDelay: c.value };
}
