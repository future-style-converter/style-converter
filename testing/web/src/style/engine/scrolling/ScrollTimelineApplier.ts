// ScrollTimelineApplier.ts — csstype-widened (CSS Scroll-driven Animations L2).
import type { CSSProperties } from 'react';
import type { ScrollTimelineConfig } from './ScrollTimelineConfig';
export type ScrollTimelineStyles = Record<string, string>;
export function applyScrollTimeline(c: ScrollTimelineConfig): ScrollTimelineStyles {
  if (c.value === undefined) return {};
  return ({ scrollTimeline: c.value } as unknown as CSSProperties) as ScrollTimelineStyles;
}
