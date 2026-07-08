// AnimationTimelineApplier.ts — csstype-widened (CSS Scroll-driven Animations L2).
// https://developer.mozilla.org/docs/Web/CSS/animation-timeline
import type { CSSProperties } from 'react';
import type { AnimationTimelineConfig } from './AnimationTimelineConfig';

export type AnimationTimelineStyles = Record<string, string>;

export function applyAnimationTimeline(c: AnimationTimelineConfig): AnimationTimelineStyles {
  if (c.value === undefined) return {};
  // csstype ships `animationTimeline` only intermittently — widen through
  // `as unknown as CSSProperties` and downcast to Record<string,string>.
  return ({ animationTimeline: c.value } as unknown as CSSProperties) as AnimationTimelineStyles;
}
