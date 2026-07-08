// ScrollTimelineNameApplier.ts — csstype-widened.
import type { CSSProperties } from 'react';
import type { ScrollTimelineNameConfig } from './ScrollTimelineNameConfig';
export type ScrollTimelineNameStyles = Record<string, string>;
export function applyScrollTimelineName(c: ScrollTimelineNameConfig): ScrollTimelineNameStyles {
  if (c.value === undefined) return {};
  return ({ scrollTimelineName: c.value } as unknown as CSSProperties) as ScrollTimelineNameStyles;
}
