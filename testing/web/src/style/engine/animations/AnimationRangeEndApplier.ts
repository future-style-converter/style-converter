// AnimationRangeEndApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { AnimationRangeEndConfig } from './AnimationRangeEndConfig';
export type AnimationRangeEndStyles = Record<string, string>;
export function applyAnimationRangeEnd(c: AnimationRangeEndConfig): AnimationRangeEndStyles {
  if (c.value === undefined) return {};
  return ({ animationRangeEnd: c.value } as unknown as CSSProperties) as AnimationRangeEndStyles;
}
