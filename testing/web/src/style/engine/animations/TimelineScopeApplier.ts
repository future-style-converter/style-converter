// TimelineScopeApplier.ts — csstype-widened.
// https://developer.mozilla.org/docs/Web/CSS/timeline-scope
import type { CSSProperties } from 'react';
import type { TimelineScopeConfig } from './TimelineScopeConfig';
export type TimelineScopeStyles = Record<string, string>;
export function applyTimelineScope(c: TimelineScopeConfig): TimelineScopeStyles {
  if (c.value === undefined) return {};
  return ({ timelineScope: c.value } as unknown as CSSProperties) as TimelineScopeStyles;
}
