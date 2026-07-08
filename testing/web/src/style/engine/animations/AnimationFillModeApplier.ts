// AnimationFillModeApplier.ts — native `animation-fill-mode`.
import type { CSSProperties } from 'react';
import type { AnimationFillModeConfig } from './AnimationFillModeConfig';
export type AnimationFillModeStyles = Pick<CSSProperties, 'animationFillMode'>;
export function applyAnimationFillMode(c: AnimationFillModeConfig): AnimationFillModeStyles {
  if (c.value === undefined) return {};
  return { animationFillMode: c.value };
}
