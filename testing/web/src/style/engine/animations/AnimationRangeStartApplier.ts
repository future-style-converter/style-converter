// AnimationRangeStartApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { AnimationRangeStartConfig } from './AnimationRangeStartConfig';
export type AnimationRangeStartStyles = Record<string, string>;
export function applyAnimationRangeStart(c: AnimationRangeStartConfig): AnimationRangeStartStyles {
  if (c.value === undefined) return {};
  return ({ animationRangeStart: c.value } as unknown as CSSProperties) as AnimationRangeStartStyles;
}
