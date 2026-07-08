// ViewTimelineApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { ViewTimelineConfig } from './ViewTimelineConfig';
export type ViewTimelineStyles = Record<string, string>;
export function applyViewTimeline(c: ViewTimelineConfig): ViewTimelineStyles {
  if (c.value === undefined) return {};
  return ({ viewTimeline: c.value } as unknown as CSSProperties) as ViewTimelineStyles;
}
