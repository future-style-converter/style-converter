// AnimationRangeApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { AnimationRangeConfig } from './AnimationRangeConfig';
export type AnimationRangeStyles = Record<string, string>;
export function applyAnimationRange(c: AnimationRangeConfig): AnimationRangeStyles {
  if (c.value === undefined) return {};
  return ({ animationRange: c.value } as unknown as CSSProperties) as AnimationRangeStyles;
}
