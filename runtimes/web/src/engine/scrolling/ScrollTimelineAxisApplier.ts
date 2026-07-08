// ScrollTimelineAxisApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { ScrollTimelineAxisConfig } from './ScrollTimelineAxisConfig';
export type ScrollTimelineAxisStyles = Record<string, string>;
export function applyScrollTimelineAxis(c: ScrollTimelineAxisConfig): ScrollTimelineAxisStyles {
  if (c.value === undefined) return {};
  return ({ scrollTimelineAxis: c.value } as unknown as CSSProperties) as ScrollTimelineAxisStyles;
}
