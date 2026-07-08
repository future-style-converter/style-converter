// ViewTimelineNameApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { ViewTimelineNameConfig } from './ViewTimelineNameConfig';
export type ViewTimelineNameStyles = Record<string, string>;
export function applyViewTimelineName(c: ViewTimelineNameConfig): ViewTimelineNameStyles {
  if (c.value === undefined) return {};
  return ({ viewTimelineName: c.value } as unknown as CSSProperties) as ViewTimelineNameStyles;
}
